package com.etc.flink;

import com.etc.flink.model.PassRecord;
import com.google.gson.Gson;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import com.etc.flink.util.CheckpointAlias;

/**
 * MySQL 存储 Flink Job
 *
 * 将实时过车记录写入 MySQL（热数据，按 plate_hash % 2 写入 pass_record_0/1）。
 */
public class MySqlStorageJob {
    private static final Logger LOG = LoggerFactory.getLogger(MySqlStorageJob.class);
    private static final Gson GSON = new Gson();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String KAFKA_BOOTSTRAP_SERVERS = System.getenv().getOrDefault(
            "KAFKA_BOOTSTRAP_SERVERS", "kafka:9092");
    private static final String KAFKA_TOPIC = System.getenv().getOrDefault(
            "KAFKA_TOPIC", "etc-pass-records");

    private static final String MYSQL_URL = System.getenv().getOrDefault(
            "MYSQL_URL", "jdbc:mysql://shardingsphere:3307/etc?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai");
    private static final String MYSQL_USER = System.getenv().getOrDefault("MYSQL_USER", "root");
    private static final String MYSQL_PASSWORD = System.getenv().getOrDefault("MYSQL_PASSWORD", "root");

    public static void main(String[] args) throws Exception {
        LOG.info("=== MySQL 存储 Flink Job 启动 ===");
        LOG.info("Kafka: {}", KAFKA_BOOTSTRAP_SERVERS);
        LOG.info("Topic: {}", KAFKA_TOPIC);
        LOG.info("MySQL: {}", MYSQL_URL);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.enableCheckpointing(60000);

        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers(KAFKA_BOOTSTRAP_SERVERS)
                .setTopics(KAFKA_TOPIC)
                .setGroupId("flink-mysql-storage")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        DataStream<PassRecordWithHash> stream = env.fromSource(
                        kafkaSource,
                        WatermarkStrategy.noWatermarks(),
                        "Kafka Source")
                .map((MapFunction<String, PassRecordWithHash>) json -> {
                    try {
                        PassRecord record = GSON.fromJson(json, PassRecord.class);
                        if (record == null || record.getHp() == null || record.getHp().isEmpty()) return null;
                        int plateHash = stableHash(record.getHp());
                        return new PassRecordWithHash(record, plateHash);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(r -> r != null);

        // 走 ShardingSphere Proxy：写逻辑表 pass_record，由 sharding 规则路由到 mysql0/mysql1 的 pass_record_0/1
        stream.addSink(buildSink("pass_record"));

        env.execute("ETC MySQL Storage");
    }

    private static org.apache.flink.streaming.api.functions.sink.SinkFunction<PassRecordWithHash> buildSink(String table) {
        // 使用 ON DUPLICATE KEY UPDATE 实现幂等写入，避免重复数据
        String sql = "INSERT INTO " + table +
                " (gcxh, xzqhmc, kkmc, fxlx, gcsj, hpzl, hp, clppxh, plate_hash, checkpoint_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE gcsj = VALUES(gcsj)";

        return JdbcSink.sink(
                sql,
                (ps, r) -> {
                    PassRecord record = r.record;
                    String checkpointId = record.getCheckpointId();
                    if (checkpointId == null || checkpointId.isBlank()) {
                        checkpointId = CheckpointAlias.checkpointIdByRawName(record.getKkmc());
                    }
                    ps.setString(1, safe(record.getGcxh()));
                    ps.setString(2, safe(record.getXzqhmc()));
                    ps.setString(3, safe(record.getKkmc()));
                    ps.setString(4, safe(record.getFxlx()));
                    ps.setTimestamp(5, parseTimestamp(record.getGcsj()));
                    ps.setString(6, safe(record.getHpzl()));
                    ps.setString(7, safe(record.getHp()));
                    ps.setString(8, safe(record.getClppxh()));
                    ps.setInt(9, r.plateHash);
                    ps.setString(10, emptyToNull(checkpointId));
                },
                JdbcExecutionOptions.builder()
                        .withBatchSize(200)
                        .withBatchIntervalMs(3000)
                        .withMaxRetries(3)
                        .build(),
                new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                        .withUrl(MYSQL_URL)
                        .withDriverName("com.mysql.cj.jdbc.Driver")
                        .withUsername(MYSQL_USER)
                        .withPassword(MYSQL_PASSWORD)
                        .build());
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }

    private static String emptyToNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Timestamp parseTimestamp(String gcsj) {
        try {
            LocalDateTime dt = LocalDateTime.parse(gcsj, FORMATTER);
            return Timestamp.from(dt.atZone(ZoneId.of("Asia/Shanghai")).toInstant());
        } catch (Exception e) {
            return new Timestamp(System.currentTimeMillis());
        }
    }

    private static int stableHash(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            int value = ((digest[0] & 0xff) << 24)
                    | ((digest[1] & 0xff) << 16)
                    | ((digest[2] & 0xff) << 8)
                    | (digest[3] & 0xff);
            return Math.abs(value);
        } catch (Exception e) {
            return Math.abs(input.hashCode());
        }
    }

    private static class PassRecordWithHash {
        final PassRecord record;
        final int plateHash;

        PassRecordWithHash(PassRecord record, int plateHash) {
            this.record = record;
            this.plateHash = plateHash;
        }
    }
}
