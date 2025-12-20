package com.etc.flink.job;

import com.etc.flink.config.FlinkConfig;
import com.etc.flink.model.PassRecordEvent;
import com.etc.flink.sink.RedisSink;
import com.etc.flink.source.KafkaSourceFactory;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 实时车流量统计作业
 * - 5分钟窗口统计每个卡口流量
 * - 实时更新Redis中的流量数据
 */
public class TrafficFlowJob {
    private static final Logger LOG = LoggerFactory.getLogger(TrafficFlowJob.class);

    public static void register(StreamExecutionEnvironment env) {
        LOG.info("注册车流量统计作业...");
        
        DataStream<PassRecordEvent> source = KafkaSourceFactory.createPassRecordStream(env);
        
        // 1. 按卡口ID分组，5分钟窗口统计
        DataStream<Tuple3<Long, String, Long>> checkpointFlow = source
                .filter(event -> event.getCheckpointIdNum() != null)
                .map(new MapFunction<PassRecordEvent, Tuple2<Long, Long>>() {
                    @Override
                    public Tuple2<Long, Long> map(PassRecordEvent event) {
                        return Tuple2.of(event.getCheckpointIdNum(), 1L);
                    }
                })
                .keyBy(t -> t.f0)
                .window(TumblingProcessingTimeWindows.of(Time.minutes(5)))
                .aggregate(new CountAggregate())
                .map(new MapFunction<Tuple2<Long, Long>, Tuple3<Long, String, Long>>() {
                    @Override
                    public Tuple3<Long, String, Long> map(Tuple2<Long, Long> t) {
                        String name = FlinkConfig.CHECKPOINT_NAME_MAP.getOrDefault(t.f0, "卡口" + t.f0);
                        return Tuple3.of(t.f0, name, t.f1);
                    }
                })
                .name("Checkpoint-Flow-5min");
        
        // 写入Redis - 每个卡口的实时流量
        checkpointFlow.addSink(new RedisSink<>(new RedisSink.RedisMapper<Tuple3<Long, String, Long>>() {
            @Override
            public String getCommand() { return "SET"; }
            @Override
            public String getKey(Tuple3<Long, String, Long> value) {
                return FlinkConfig.REDIS_KEY_FLOW_CHECKPOINT + value.f0;
            }
            @Override
            public String getValue(Tuple3<Long, String, Long> value) {
                return String.valueOf(value.f2 * 12); // 转换为小时流量
            }
        })).name("Redis-Checkpoint-Flow");
        
        // 2. 总流量统计
        DataStream<Long> totalFlow = source
                .map(e -> 1L)
                .keyBy(v -> "total")
                .window(TumblingProcessingTimeWindows.of(Time.minutes(5)))
                .sum(0)
                .name("Total-Flow-5min");
        
        // 写入Redis - 今日总流量（累加）
        totalFlow.addSink(new RedisSink<>(new RedisSink.RedisMapper<Long>() {
            @Override
            public String getCommand() { return "INCRBY"; }
            @Override
            public String getKey(Long value) { return FlinkConfig.REDIS_KEY_FLOW_TOTAL; }
            @Override
            public String getValue(Long value) { return String.valueOf(value); }
        })).name("Redis-Total-Flow");
        
        // 3. 平均车速统计
        DataStream<Tuple2<Long, Long>> speedStats = source
                .filter(e -> e.getSpeed() != null && e.getSpeed() > 0)
                .map(e -> Tuple2.of((long) e.getSpeed(), 1L))
                .returns(org.apache.flink.api.common.typeinfo.Types.TUPLE(
                    org.apache.flink.api.common.typeinfo.Types.LONG, 
                    org.apache.flink.api.common.typeinfo.Types.LONG))
                .keyBy(v -> "speed")
                .window(TumblingProcessingTimeWindows.of(Time.minutes(5)))
                .reduce((a, b) -> Tuple2.of(a.f0 + b.f0, a.f1 + b.f1))
                .name("Speed-Stats-5min");
        
        // 写入Redis - 速度总和和计数（用于计算平均值）
        speedStats.addSink(new RedisSink<>(new RedisSink.RedisMapper<Tuple2<Long, Long>>() {
            @Override
            public String getCommand() { return "HSET"; }
            @Override
            public String getKey(Tuple2<Long, Long> value) { return FlinkConfig.REDIS_KEY_AVG_SPEED; }
            @Override
            public java.util.Map<String, String> getHash(Tuple2<Long, Long> value) {
                return java.util.Map.of(
                    "sum", String.valueOf(value.f0),
                    "count", String.valueOf(value.f1),
                    "avg", String.valueOf(value.f1 > 0 ? value.f0 / value.f1 : 0)
                );
            }
        })).name("Redis-Avg-Speed");
        
        LOG.info("车流量统计作业注册完成");
    }
    
    /**
     * 计数聚合函数
     */
    private static class CountAggregate implements AggregateFunction<Tuple2<Long, Long>, Tuple2<Long, Long>, Tuple2<Long, Long>> {
        @Override
        public Tuple2<Long, Long> createAccumulator() {
            return Tuple2.of(0L, 0L);
        }
        @Override
        public Tuple2<Long, Long> add(Tuple2<Long, Long> value, Tuple2<Long, Long> acc) {
            return Tuple2.of(value.f0, acc.f1 + 1);
        }
        @Override
        public Tuple2<Long, Long> getResult(Tuple2<Long, Long> acc) {
            return acc;
        }
        @Override
        public Tuple2<Long, Long> merge(Tuple2<Long, Long> a, Tuple2<Long, Long> b) {
            return Tuple2.of(a.f0, a.f1 + b.f1);
        }
    }
}
