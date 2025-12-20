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

import java.math.BigDecimal;

/**
 * 实时营收计算作业
 * - 累计ETC扣费金额
 * - 按卡口分组统计
 */
public class RevenueJob {
    private static final Logger LOG = LoggerFactory.getLogger(RevenueJob.class);

    public static void register(StreamExecutionEnvironment env) {
        LOG.info("注册营收统计作业...");
        
        DataStream<PassRecordEvent> source = KafkaSourceFactory.createPassRecordStream(env);
        
        // 1. 总营收统计（5分钟窗口累加）
        DataStream<Long> totalRevenue = source
                .filter(e -> e.getEtcDeduction() != null && e.getEtcDeduction().compareTo(BigDecimal.ZERO) > 0)
                .map(e -> e.getEtcDeduction().multiply(BigDecimal.valueOf(100)).longValue()) // 转为分
                .keyBy(v -> "revenue")
                .window(TumblingProcessingTimeWindows.of(Time.minutes(5)))
                .sum(0)
                .name("Total-Revenue-5min");
        
        // 写入Redis - 今日总营收（累加）
        totalRevenue.addSink(new RedisSink<>(new RedisSink.RedisMapper<Long>() {
            @Override
            public String getCommand() { return "INCRBY"; }
            @Override
            public String getKey(Long value) { return FlinkConfig.REDIS_KEY_REVENUE_TODAY; }
            @Override
            public String getValue(Long value) { return String.valueOf(value); }
        })).name("Redis-Total-Revenue");
        
        // 2. 按卡口统计营收
        DataStream<Tuple2<Long, Long>> checkpointRevenue = source
                .filter(e -> e.getEtcDeduction() != null && e.getCheckpointIdNum() != null)
                .map(new MapFunction<PassRecordEvent, Tuple2<Long, Long>>() {
                    @Override
                    public Tuple2<Long, Long> map(PassRecordEvent e) {
                        long revenue = e.getEtcDeduction().multiply(BigDecimal.valueOf(100)).longValue();
                        return Tuple2.of(e.getCheckpointIdNum(), revenue);
                    }
                })
                .keyBy(t -> t.f0)
                .window(TumblingProcessingTimeWindows.of(Time.minutes(5)))
                .reduce((a, b) -> Tuple2.of(a.f0, a.f1 + b.f1))
                .name("Checkpoint-Revenue-5min");
        
        // 写入Redis - 每个卡口的营收
        checkpointRevenue.addSink(new RedisSink<>(new RedisSink.RedisMapper<Tuple2<Long, Long>>() {
            @Override
            public String getCommand() { return "INCRBY"; }
            @Override
            public String getKey(Tuple2<Long, Long> value) {
                return FlinkConfig.REDIS_KEY_REVENUE_CHECKPOINT + value.f0;
            }
            @Override
            public String getValue(Tuple2<Long, Long> value) {
                return String.valueOf(value.f1);
            }
        })).name("Redis-Checkpoint-Revenue");
        
        LOG.info("营收统计作业注册完成");
    }
}
