package com.etc.flink.job;

import com.etc.flink.config.FlinkConfig;
import com.etc.flink.model.PassRecordEvent;
import com.etc.flink.sink.RedisSink;
import com.etc.flink.source.KafkaSourceFactory;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 车辆来源统计作业
 * - 统计本地车辆（苏C）和外地车辆数量
 * - 5分钟滚动窗口
 */
public class VehicleSourceJob {
    private static final Logger LOG = LoggerFactory.getLogger(VehicleSourceJob.class);

    public static void register(StreamExecutionEnvironment env) {
        LOG.info("注册车辆来源统计作业...");
        
        DataStream<PassRecordEvent> source = KafkaSourceFactory.createPassRecordStream(env);
        
        // 1. 本地车辆统计（苏C开头）
        DataStream<Long> localCount = source
                .filter(PassRecordEvent::isLocalVehicle)
                .map(e -> 1L)
                .keyBy(v -> "local")
                .window(TumblingProcessingTimeWindows.of(Time.minutes(5)))
                .sum(0)
                .name("Local-Vehicle-Count");
        
        // 写入Redis - 本地车辆数
        localCount.addSink(new RedisSink<>(new RedisSink.RedisMapper<Long>() {
            @Override
            public String getCommand() { return "INCRBY"; }
            @Override
            public String getKey(Long value) { return FlinkConfig.REDIS_KEY_SOURCE_LOCAL; }
            @Override
            public String getValue(Long value) { return String.valueOf(value); }
        })).name("Redis-Local-Count");
        
        // 2. 外地车辆统计（非苏C）
        DataStream<Long> foreignCount = source
                .filter(e -> !e.isLocalVehicle())
                .map(e -> 1L)
                .keyBy(v -> "foreign")
                .window(TumblingProcessingTimeWindows.of(Time.minutes(5)))
                .sum(0)
                .name("Foreign-Vehicle-Count");
        
        // 写入Redis - 外地车辆数
        foreignCount.addSink(new RedisSink<>(new RedisSink.RedisMapper<Long>() {
            @Override
            public String getCommand() { return "INCRBY"; }
            @Override
            public String getKey(Long value) { return FlinkConfig.REDIS_KEY_SOURCE_FOREIGN; }
            @Override
            public String getValue(Long value) { return String.valueOf(value); }
        })).name("Redis-Foreign-Count");
        
        LOG.info("车辆来源统计作业注册完成");
    }
}
