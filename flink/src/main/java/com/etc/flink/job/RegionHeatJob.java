package com.etc.flink.job;

import com.etc.flink.config.FlinkConfig;
import com.etc.flink.model.PassRecordEvent;
import com.etc.flink.sink.RedisSink;
import com.etc.flink.source.KafkaSourceFactory;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 区域热度排名作业
 * - 按区域分组(苏皖界/苏鲁界/连云港界/宿迁界)
 * - 10分钟滚动窗口计算热度
 */
public class RegionHeatJob {
    private static final Logger LOG = LoggerFactory.getLogger(RegionHeatJob.class);

    public static void register(StreamExecutionEnvironment env) {
        LOG.info("注册区域热度排名作业...");
        
        DataStream<PassRecordEvent> source = KafkaSourceFactory.createPassRecordStream(env);
        
        // 按区域分组，10分钟窗口统计
        DataStream<Tuple2<String, Long>> regionHeat = source
                .map(new MapFunction<PassRecordEvent, Tuple2<String, Long>>() {
                    @Override
                    public Tuple2<String, Long> map(PassRecordEvent e) {
                        return Tuple2.of(e.getRegion(), 1L);
                    }
                })
                .keyBy(t -> t.f0)
                .window(TumblingProcessingTimeWindows.of(Time.minutes(10)))
                .reduce((a, b) -> Tuple2.of(a.f0, a.f1 + b.f1))
                .name("Region-Heat-10min");
        
        // 写入Redis有序集合 - 区域热度排名
        regionHeat.addSink(new RedisSink<>(new RedisSink.RedisMapper<Tuple2<String, Long>>() {
            @Override
            public String getCommand() { return "ZADD"; }
            
            @Override
            public String getKey(Tuple2<String, Long> value) {
                return FlinkConfig.REDIS_KEY_HEAT_RANKING;
            }
            
            @Override
            public double getScore(Tuple2<String, Long> value) {
                return value.f1.doubleValue();
            }
            
            @Override
            public String getMember(Tuple2<String, Long> value) {
                return value.f0;
            }
        })).name("Redis-Region-Heat");
        
        LOG.info("区域热度排名作业注册完成");
    }
}
