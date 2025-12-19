package com.etc.flink.source;

import com.etc.flink.config.FlinkConfig;
import com.etc.flink.model.PassRecordEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.AbstractDeserializationSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;

/**
 * Kafka数据源工厂 - 从Kafka读取通行记录
 */
public class KafkaSourceFactory {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaSourceFactory.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    /**
     * 创建通行记录数据流
     */
    public static DataStream<PassRecordEvent> createPassRecordStream(StreamExecutionEnvironment env) {
        LOG.info("创建Kafka数据源: {} -> {}", 
                FlinkConfig.KAFKA_BOOTSTRAP_SERVERS, 
                FlinkConfig.KAFKA_TOPIC_PASS_RECORDS);

        KafkaSource<PassRecordEvent> source = KafkaSource.<PassRecordEvent>builder()
                .setBootstrapServers(FlinkConfig.KAFKA_BOOTSTRAP_SERVERS)
                .setTopics(FlinkConfig.KAFKA_TOPIC_PASS_RECORDS)
                .setGroupId(FlinkConfig.KAFKA_CONSUMER_GROUP)
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new PassRecordDeserializer())
                .build();

        // 设置水印策略（允许5秒延迟）
        WatermarkStrategy<PassRecordEvent> watermarkStrategy = WatermarkStrategy
                .<PassRecordEvent>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                .withIdleness(Duration.ofMinutes(1));

        return env.fromSource(source, watermarkStrategy, "Kafka-PassRecords")
                .filter(record -> record != null && record.getPlateNumber() != null)
                .name("PassRecord-Filter");
    }

    /**
     * 通行记录反序列化器
     */
    private static class PassRecordDeserializer extends AbstractDeserializationSchema<PassRecordEvent> {
        @Override
        public PassRecordEvent deserialize(byte[] message) throws IOException {
            try {
                return MAPPER.readValue(message, PassRecordEvent.class);
            } catch (Exception e) {
                LOG.warn("反序列化失败: {}", new String(message), e);
                return null;
            }
        }
    }
}
