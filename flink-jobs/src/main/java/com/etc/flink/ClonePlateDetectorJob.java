package com.etc.flink;

import com.etc.flink.model.ClonePlateAlert;
import com.etc.flink.model.PassRecord;
import com.google.gson.Gson;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * 套牌车检测 Flink Job
 * 
 * 检测逻辑：同一车牌在 5 分钟内出现在两个不同卡口，触发告警
 */
public class ClonePlateDetectorJob {
    private static final Logger LOG = LoggerFactory.getLogger(ClonePlateDetectorJob.class);
    private static final Gson GSON = new Gson();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 配置参数
    private static final String KAFKA_BOOTSTRAP_SERVERS = System.getenv().getOrDefault(
            "KAFKA_BOOTSTRAP_SERVERS", "kafka:9092");
    private static final String KAFKA_TOPIC = System.getenv().getOrDefault(
            "KAFKA_TOPIC", "etc-pass-records");
    private static final String MYSQL_URL = System.getenv().getOrDefault(
            "MYSQL_URL", "jdbc:mysql://shardingsphere:3307/etc?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai");
    private static final String MYSQL_USER = System.getenv().getOrDefault("MYSQL_USER", "root");
    private static final String MYSQL_PASSWORD = System.getenv().getOrDefault("MYSQL_PASSWORD", "root");

    // 套牌检测阈值：5 分钟内出现在不同卡口
    private static final long CLONE_DETECTION_WINDOW_SECONDS = 300;

    public static void main(String[] args) throws Exception {
        LOG.info("=== 套牌车检测 Flink Job 启动 ===");
        LOG.info("Kafka: {}", KAFKA_BOOTSTRAP_SERVERS);
        LOG.info("Topic: {}", KAFKA_TOPIC);
        LOG.info("MySQL: {}", MYSQL_URL);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.enableCheckpointing(60000);

        // Kafka Source
        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers(KAFKA_BOOTSTRAP_SERVERS)
                .setTopics(KAFKA_TOPIC)
                .setGroupId("flink-clone-plate-detector")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        // 读取 Kafka 数据
        DataStream<String> rawStream = env.fromSource(
                kafkaSource,
                WatermarkStrategy.noWatermarks(),
                "Kafka Source");

        // 解析 JSON
        DataStream<PassRecord> recordStream = rawStream
                .map(new MapFunction<String, PassRecord>() {
                    @Override
                    public PassRecord map(String json) throws Exception {
                        try {
                            return GSON.fromJson(json, PassRecord.class);
                        } catch (Exception e) {
                            LOG.warn("JSON 解析失败: {}", json);
                            return null;
                        }
                    }
                })
                .filter(r -> r != null && r.getHp() != null && !r.getHp().isEmpty());

        // 按车牌分组，检测套牌
        DataStream<ClonePlateAlert> alertStream = recordStream
                .keyBy(PassRecord::getHp)
                .process(new ClonePlateDetector());

        // 输出告警日志
        alertStream.map(alert -> {
            LOG.warn("🚨 套牌告警: {} 在 {}s 内出现在 {} 和 {}",
                    alert.getPlateNumber(),
                    alert.getTimeDiffSeconds(),
                    alert.getCheckpointName1(),
                    alert.getCheckpointName2());
            return alert;
        });

        // 写入 MySQL
        alertStream.addSink(JdbcSink.sink(
                "INSERT INTO clone_plate_detection " +
                        "(plate_number, checkpoint_id_1, checkpoint_id_2, time_1, time_2, " +
                        "time_diff_minutes, confidence_score, status, create_time) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, 'pending', NOW())",
                (ps, alert) -> {
                    ps.setString(1, alert.getPlateNumber());
                    ps.setString(2, alert.getCheckpointId1());
                    ps.setString(3, alert.getCheckpointId2());
                    ps.setString(4, alert.getTime1());
                    ps.setString(5, alert.getTime2());
                    ps.setInt(6, (int) (alert.getTimeDiffSeconds() / 60));
                    ps.setDouble(7, alert.getConfidenceScore());
                },
                JdbcExecutionOptions.builder()
                        .withBatchSize(100)
                        .withBatchIntervalMs(5000)
                        .build(),
                new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                        .withUrl(MYSQL_URL)
                        .withDriverName("com.mysql.cj.jdbc.Driver")
                        .withUsername(MYSQL_USER)
                        .withPassword(MYSQL_PASSWORD)
                        .build()));

        env.execute("ETC Clone Plate Detector");
    }

    /**
     * 套牌检测处理函数
     */
    public static class ClonePlateDetector extends KeyedProcessFunction<String, PassRecord, ClonePlateAlert> {

        // 保存上一条记录
        private transient ValueState<PassRecord> lastRecordState;
        // 保存清理定时器时间戳，避免旧定时器误清理新状态
        private transient ValueState<Long> cleanupTimerState;

        @Override
        public void open(Configuration parameters) {
            ValueStateDescriptor<PassRecord> descriptor = new ValueStateDescriptor<>(
                    "lastRecord",
                    TypeInformation.of(PassRecord.class));
            lastRecordState = getRuntimeContext().getState(descriptor);

            ValueStateDescriptor<Long> timerDesc = new ValueStateDescriptor<>(
                    "cleanupTimer",
                    TypeInformation.of(Long.class));
            cleanupTimerState = getRuntimeContext().getState(timerDesc);
        }

        @Override
        public void processElement(PassRecord current, Context ctx, Collector<ClonePlateAlert> out) throws Exception {
            PassRecord last = lastRecordState.value();

            if (last != null) {
                // 检查是否在不同卡口
                String lastCp = last.getKkmc();
                String currentCp = current.getKkmc();

                if (lastCp != null && currentCp != null && !lastCp.equals(currentCp)) {
                    // 计算时间差
                    try {
                        LocalDateTime lastTime = LocalDateTime.parse(last.getGcsj(), FORMATTER);
                        LocalDateTime currentTime = LocalDateTime.parse(current.getGcsj(), FORMATTER);
                        long diffSeconds = Math.abs(ChronoUnit.SECONDS.between(lastTime, currentTime));

                        // 如果时间差小于阈值，触发告警
                        if (diffSeconds <= CLONE_DETECTION_WINDOW_SECONDS) {
                            ClonePlateAlert alert = new ClonePlateAlert();
                            alert.setPlateNumber(current.getHp());
                            alert.setCheckpointId1(last.getCheckpointId());
                            alert.setCheckpointId2(current.getCheckpointId());
                            alert.setCheckpointName1(lastCp);
                            alert.setCheckpointName2(currentCp);
                            alert.setTime1(last.getGcsj());
                            alert.setTime2(current.getGcsj());
                            alert.setTimeDiffSeconds(diffSeconds);
                            // 置信度：时间越短越可疑
                            alert.setConfidenceScore(Math.min(100, 100 - (diffSeconds / 3.0)));

                            out.collect(alert);
                        }
                    } catch (Exception e) {
                        // 时间解析失败，跳过
                    }
                }
            }

            // 更新状态
            lastRecordState.update(current);

            // 设置清理定时器（5分钟后清理状态）
            Long prevTimer = cleanupTimerState.value();
            if (prevTimer != null && prevTimer > 0) {
                ctx.timerService().deleteProcessingTimeTimer(prevTimer);
            }
            long nextTimer = ctx.timerService().currentProcessingTime() + CLONE_DETECTION_WINDOW_SECONDS * 1000;
            cleanupTimerState.update(nextTimer);
            ctx.timerService().registerProcessingTimeTimer(nextTimer);
        }

        @Override
        public void onTimer(long timestamp, OnTimerContext ctx, Collector<ClonePlateAlert> out) {
            try {
                Long expected = cleanupTimerState.value();
                if (expected != null && expected == timestamp) {
                    lastRecordState.clear();
                    cleanupTimerState.clear();
                }
            } catch (Exception ignored) {
                // ignore
            }
        }
    }
}
