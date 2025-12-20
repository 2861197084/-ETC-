package com.etc.flink.job;

import com.etc.flink.config.FlinkConfig;
import com.etc.flink.model.PassRecordEvent;
import com.etc.flink.sink.RedisSink;
import com.etc.flink.source.KafkaSourceFactory;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple4;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 站口压力实时计算作业
 * - 5分钟窗口计算每个卡口的车流量
 * - 计算压力比 = 当前流量 / 最大容量
 * - 生成压力预警等级
 */
public class CheckpointPressureJob {
    private static final Logger LOG = LoggerFactory.getLogger(CheckpointPressureJob.class);

    public static void register(StreamExecutionEnvironment env) {
        LOG.info("注册站口压力计算作业...");
        
        DataStream<PassRecordEvent> source = KafkaSourceFactory.createPassRecordStream(env);
        
        // 按卡口ID分组，5分钟窗口统计流量
        DataStream<Tuple4<Long, String, Long, Integer>> pressureData = source
                .filter(e -> e.getCheckpointIdNum() != null)
                .map(new MapFunction<PassRecordEvent, Tuple2<Long, Long>>() {
                    @Override
                    public Tuple2<Long, Long> map(PassRecordEvent e) {
                        return Tuple2.of(e.getCheckpointIdNum(), 1L);
                    }
                })
                .keyBy(t -> t.f0)
                .window(TumblingProcessingTimeWindows.of(Time.minutes(5)))
                .reduce((a, b) -> Tuple2.of(a.f0, a.f1 + b.f1))
                .map(new MapFunction<Tuple2<Long, Long>, Tuple4<Long, String, Long, Integer>>() {
                    @Override
                    public Tuple4<Long, String, Long, Integer> map(Tuple2<Long, Long> t) {
                        Long checkpointId = t.f0;
                        Long count5min = t.f1;
                        // 转换为小时流量（5分钟 * 12 = 1小时）
                        Long hourlyFlow = count5min * 12;
                        // 获取最大容量
                        Integer maxCapacity = FlinkConfig.CHECKPOINT_CAPACITY.getOrDefault(checkpointId, 1000);
                        // 计算压力百分比
                        Integer pressure = (int) Math.min(100, (hourlyFlow * 100) / maxCapacity);
                        // 获取卡口名称
                        String name = FlinkConfig.CHECKPOINT_NAME_MAP.getOrDefault(checkpointId, "卡口" + checkpointId);
                        
                        return Tuple4.of(checkpointId, name, hourlyFlow, pressure);
                    }
                })
                .name("Checkpoint-Pressure-5min");
        
        // 写入Redis - 每个卡口的压力数据
        pressureData.addSink(new RedisSink<>(new RedisSink.RedisMapper<Tuple4<Long, String, Long, Integer>>() {
            @Override
            public String getCommand() { return "HSET"; }
            
            @Override
            public String getKey(Tuple4<Long, String, Long, Integer> value) {
                return FlinkConfig.REDIS_KEY_PRESSURE + value.f0;
            }
            
            @Override
            public Map<String, String> getHash(Tuple4<Long, String, Long, Integer> value) {
                String level;
                String suggestion = "";
                int pressure = value.f3;
                
                if (pressure >= 90) {
                    level = "critical";
                    suggestion = "严重拥堵，建议立即开启备用车道，调配警力疏导";
                } else if (pressure >= 70) {
                    level = "danger";
                    suggestion = "拥堵预警，建议开启备用车道，提前疏导";
                } else if (pressure >= 50) {
                    level = "warning";
                    suggestion = "车流较大，预计30分钟后达到高峰，建议提前准备";
                } else {
                    level = "normal";
                    suggestion = "";
                }
                
                return Map.of(
                    "checkpointId", String.valueOf(value.f0),
                    "name", value.f1,
                    "currentFlow", String.valueOf(value.f2),
                    "pressure", String.valueOf(pressure),
                    "level", level,
                    "suggestion", suggestion,
                    "maxCapacity", String.valueOf(FlinkConfig.CHECKPOINT_CAPACITY.getOrDefault(value.f0, 1000)),
                    "updateTime", java.time.LocalDateTime.now().toString()
                );
            }
        })).name("Redis-Checkpoint-Pressure");
        
        LOG.info("站口压力计算作业注册完成");
    }
}
