package com.etc.flink;

import com.etc.flink.model.PassRecord;
import com.google.gson.Gson;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.etc.flink.util.CheckpointAlias;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * HBase 存储 Flink Job
 * 
 * 将过车记录写入 HBase etc:pass_record 表
 * RowKey 设计: {salt(1)}{yyyyMMdd}{checkpointId}{reverse_ts}{plate_hash}
 */
public class HBaseStorageJob {
    private static final Logger LOG = LoggerFactory.getLogger(HBaseStorageJob.class);
    private static final Gson GSON = new Gson();

    // 配置参数
    private static final String KAFKA_BOOTSTRAP_SERVERS = System.getenv().getOrDefault(
            "KAFKA_BOOTSTRAP_SERVERS", "kafka:9092");
    private static final String KAFKA_TOPIC = System.getenv().getOrDefault(
            "KAFKA_TOPIC", "etc-pass-records");
    private static final String HBASE_ZOOKEEPER_QUORUM = System.getenv().getOrDefault(
            "HBASE_ZOOKEEPER_QUORUM", "zookeeper:2181");
    private static final String HBASE_TABLE = "etc:pass_record";

    public static void main(String[] args) throws Exception {
        LOG.info("=== HBase 存储 Flink Job 启动 ===");
        LOG.info("Kafka: {}", KAFKA_BOOTSTRAP_SERVERS);
        LOG.info("HBase ZK: {}", HBASE_ZOOKEEPER_QUORUM);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.enableCheckpointing(60000);

        // Kafka Source
        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers(KAFKA_BOOTSTRAP_SERVERS)
                .setTopics(KAFKA_TOPIC)
                .setGroupId("flink-hbase-storage")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        // 读取 Kafka 数据并解析
        DataStream<PassRecord> recordStream = env.fromSource(
                kafkaSource,
                WatermarkStrategy.noWatermarks(),
                "Kafka Source")
                .map(new MapFunction<String, PassRecord>() {
                    @Override
                    public PassRecord map(String json) throws Exception {
                        try {
                            return GSON.fromJson(json, PassRecord.class);
                        } catch (Exception e) {
                            return null;
                        }
                    }
                })
                .filter(r -> r != null && r.getHp() != null);

        // 写入 HBase
        recordStream.addSink(new HBaseSink());

        env.execute("ETC HBase Storage");
    }

    /**
     * HBase Sink
     */
    public static class HBaseSink extends RichSinkFunction<PassRecord> {
        private static final Logger LOG = LoggerFactory.getLogger(HBaseSink.class);
        private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
        private static final byte[] CF = Bytes.toBytes("d");

        private transient Connection connection;
        private transient BufferedMutator mutator;
        private final List<Put> buffer = new ArrayList<>();
        private static final int BATCH_SIZE = 100;

        @Override
        public void open(Configuration parameters) throws Exception {
            org.apache.hadoop.conf.Configuration hbaseConfig = HBaseConfiguration.create();
            hbaseConfig.set("hbase.zookeeper.quorum", HBASE_ZOOKEEPER_QUORUM);
            hbaseConfig.set("hbase.zookeeper.property.clientPort", "2181");

            try {
                connection = ConnectionFactory.createConnection(hbaseConfig);
                BufferedMutatorParams params = new BufferedMutatorParams(TableName.valueOf(HBASE_TABLE))
                        .writeBufferSize(1024 * 1024);
                mutator = connection.getBufferedMutator(params);
                LOG.info("HBase 连接成功");
            } catch (IOException e) {
                LOG.error("HBase 连接失败: {}", e.getMessage());
                throw e;
            }
        }

        @Override
        public void invoke(PassRecord record, Context context) throws Exception {
            try {
                String checkpointId = record.getCheckpointId();
                if (checkpointId == null || checkpointId.isBlank()) {
                    checkpointId = CheckpointAlias.checkpointIdByRawName(record.getKkmc());
                }

                String rowKey = generateRowKey(record);
                Put put = new Put(Bytes.toBytes(rowKey));

                // 添加列
                put.addColumn(CF, Bytes.toBytes("hp"), Bytes.toBytes(safeStr(record.getHp())));
                put.addColumn(CF, Bytes.toBytes("gcsj"), Bytes.toBytes(safeStr(record.getGcsj())));
                put.addColumn(CF, Bytes.toBytes("kkmc"), Bytes.toBytes(safeStr(record.getKkmc())));
                put.addColumn(CF, Bytes.toBytes("checkpoint_id"), Bytes.toBytes(safeStr(checkpointId)));
                put.addColumn(CF, Bytes.toBytes("xzqhmc"), Bytes.toBytes(safeStr(record.getXzqhmc())));
                put.addColumn(CF, Bytes.toBytes("fxlx"), Bytes.toBytes(safeStr(record.getFxlx())));
                put.addColumn(CF, Bytes.toBytes("hpzl"), Bytes.toBytes(safeStr(record.getHpzl())));
                put.addColumn(CF, Bytes.toBytes("clppxh"), Bytes.toBytes(safeStr(record.getClppxh())));

                mutator.mutate(put);
            } catch (Exception e) {
                LOG.warn("写入 HBase 失败: {}", e.getMessage());
            }
        }

        /**
         * 生成 RowKey
         * 格式: {salt}{yyyyMMdd}{checkpoint_hash}{reverse_ts}{plate_hash}
         */
        private String generateRowKey(PassRecord record) {
            String plateNumber = safeStr(record.getHp());
            String passTime = safeStr(record.getGcsj());
            String checkpointIdOrName = safeStr(record.getCheckpointId());
            if (checkpointIdOrName.isEmpty()) {
                checkpointIdOrName = CheckpointAlias.checkpointIdByRawName(record.getKkmc());
            }
            if (checkpointIdOrName == null || checkpointIdOrName.isEmpty()) checkpointIdOrName = safeStr(record.getKkmc());

            int salt = stableHashMod(plateNumber, 10);

            String dateStr = "20240101";
            try {
                if (passTime.length() >= 10) dateStr = passTime.substring(0, 10).replace("-", "");
            } catch (Exception ignored) {
            }

            long reverseTs = 9999999999999L;
            try {
                LocalDateTime dt = LocalDateTime.parse(passTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                long ts = dt.atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
                reverseTs = 9999999999999L - ts;
            } catch (Exception ignored) {
            }

            int checkpointHash = stableHashMod(checkpointIdOrName, 100000000);
            int plateHash = stableHashMod(plateNumber, 10000);

            return String.format("%d%s%08d%013d%04d", salt, dateStr, checkpointHash, reverseTs, plateHash);
        }

        private String safeStr(String s) {
            return s != null ? s : "";
        }

        private int stableHashMod(String input, int mod) {
            if (input == null || input.isEmpty()) return 0;
            try {
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
                byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                int value = ((digest[0] & 0xff) << 24)
                        | ((digest[1] & 0xff) << 16)
                        | ((digest[2] & 0xff) << 8)
                        | (digest[3] & 0xff);
                value = Math.abs(value);
                return value % mod;
            } catch (Exception e) {
                return Math.abs(input.hashCode()) % mod;
            }
        }

        @Override
        public void close() throws Exception {
            if (mutator != null) {
                mutator.flush();
                mutator.close();
            }
            if (connection != null) {
                connection.close();
            }
            LOG.info("HBase 连接关闭");
        }
    }
}
