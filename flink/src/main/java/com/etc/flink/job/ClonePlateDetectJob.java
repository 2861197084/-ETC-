package com.etc.flink.job;

import com.etc.flink.config.FlinkConfig;
import com.etc.flink.model.PassRecordEvent;
import com.etc.flink.source.KafkaSourceFactory;
import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 套牌车实时检测作业
 * - 使用 Flink State 保存最近10分钟的车牌通行记录
 * - 同一车牌短时间内出现在相距较远的两个卡口
 * - 计算理论行驶时间，对比实际时间差
 */
public class ClonePlateDetectJob {
    private static final Logger LOG = LoggerFactory.getLogger(ClonePlateDetectJob.class);
    
    // 卡口之间的距离（公里）- 简化版，实际应从数据库加载
    private static final Map<String, Double> CHECKPOINT_DISTANCES = Map.ofEntries(
        Map.entry("1-7", 150.0), Map.entry("1-9", 180.0), Map.entry("1-13", 200.0),
        Map.entry("2-8", 120.0), Map.entry("3-9", 100.0), Map.entry("4-15", 80.0),
        Map.entry("5-16", 90.0), Map.entry("6-18", 70.0), Map.entry("7-12", 60.0),
        Map.entry("8-11", 50.0), Map.entry("9-13", 110.0), Map.entry("10-14", 130.0)
    );

    public static void register(StreamExecutionEnvironment env) {
        LOG.info("注册套牌车实时检测作业...");
        
        DataStream<PassRecordEvent> source = KafkaSourceFactory.createPassRecordStream(env);
        
        // 按车牌分组，使用 KeyedProcessFunction 检测套牌
        DataStream<ClonePlateRecord> clonePlates = source
                .keyBy(PassRecordEvent::getPlateNumber)
                .process(new ClonePlateDetector())
                .name("Clone-Plate-Detection");
        
        // 写入MySQL
        clonePlates.addSink(new ClonePlateMySQLSink())
                .name("MySQL-ClonePlate-Sink");
        
        LOG.info("套牌车实时检测作业注册完成");
    }
    
    /**
     * 套牌车检测处理函数
     */
    private static class ClonePlateDetector extends KeyedProcessFunction<String, PassRecordEvent, ClonePlateRecord> {
        // 保存每辆车最近的通行记录 <checkpointId, passTime>
        private transient MapState<Long, LocalDateTime> recentRecords;
        
        @Override
        public void open(Configuration parameters) throws Exception {
            MapStateDescriptor<Long, LocalDateTime> descriptor = new MapStateDescriptor<>(
                    "recent-records", Types.LONG, Types.LOCAL_DATE_TIME
            );
            recentRecords = getRuntimeContext().getMapState(descriptor);
        }
        
        @Override
        public void processElement(PassRecordEvent event, Context ctx, Collector<ClonePlateRecord> out) throws Exception {
            String plateNumber = event.getPlateNumber();
            Long currentCheckpoint = event.getCheckpointId();
            LocalDateTime currentTime = LocalDateTime.now();
            
            // 检查是否与最近的记录冲突
            for (Map.Entry<Long, LocalDateTime> entry : recentRecords.entries()) {
                Long prevCheckpoint = entry.getKey();
                LocalDateTime prevTime = entry.getValue();
                
                // 跳过相同卡口
                if (prevCheckpoint.equals(currentCheckpoint)) continue;
                
                // 计算时间差（分钟）
                long timeDiffMinutes = Duration.between(prevTime, currentTime).toMinutes();
                
                // 只检查10分钟内的记录
                if (timeDiffMinutes > 10 || timeDiffMinutes <= 0) continue;
                
                // 获取卡口之间的距离
                String distKey = Math.min(prevCheckpoint, currentCheckpoint) + "-" + Math.max(prevCheckpoint, currentCheckpoint);
                Double distance = CHECKPOINT_DISTANCES.get(distKey);
                
                // 如果没有配置距离，假设默认距离为50公里
                if (distance == null) distance = 50.0;
                
                // 计算理论最短行驶时间（假设最高时速200公里）
                double minTravelTimeMinutes = (distance / 200.0) * 60;
                
                // 如果实际时间差小于理论最短时间的80%，判定为疑似套牌
                if (timeDiffMinutes < minTravelTimeMinutes * 0.8) {
                    // 计算推算速度
                    double calculatedSpeed = (distance / timeDiffMinutes) * 60;
                    
                    // 创建套牌记录
                    ClonePlateRecord record = new ClonePlateRecord();
                    record.plateNumber = plateNumber;
                    record.checkpoint1Id = prevCheckpoint;
                    record.checkpoint1Name = FlinkConfig.CHECKPOINT_NAME_MAP.getOrDefault(prevCheckpoint, "卡口" + prevCheckpoint);
                    record.checkpoint1Time = prevTime;
                    record.checkpoint2Id = currentCheckpoint;
                    record.checkpoint2Name = FlinkConfig.CHECKPOINT_NAME_MAP.getOrDefault(currentCheckpoint, "卡口" + currentCheckpoint);
                    record.checkpoint2Time = currentTime;
                    record.distance = distance;
                    record.timeDiff = (int) timeDiffMinutes;
                    record.calculatedSpeed = calculatedSpeed;
                    record.confidence = Math.min(0.99, 0.7 + (minTravelTimeMinutes - timeDiffMinutes) / minTravelTimeMinutes * 0.3);
                    
                    out.collect(record);
                    LOG.warn("检测到疑似套牌车: {} 在{}分钟内从{}到{}, 推算时速{}km/h", 
                            plateNumber, timeDiffMinutes, record.checkpoint1Name, record.checkpoint2Name, (int)calculatedSpeed);
                }
            }
            
            // 更新状态
            recentRecords.put(currentCheckpoint, currentTime);
            
            // 设置定时器清理10分钟前的记录
            ctx.timerService().registerProcessingTimeTimer(ctx.timerService().currentProcessingTime() + 600000);
        }
        
        @Override
        public void onTimer(long timestamp, OnTimerContext ctx, Collector<ClonePlateRecord> out) throws Exception {
            // 清理过期记录
            LocalDateTime expireTime = LocalDateTime.now().minusMinutes(10);
            recentRecords.entries().removeIf(entry -> entry.getValue().isBefore(expireTime));
        }
    }
    
    /**
     * 套牌车记录
     */
    public static class ClonePlateRecord implements java.io.Serializable {
        public String plateNumber;
        public Long checkpoint1Id;
        public String checkpoint1Name;
        public LocalDateTime checkpoint1Time;
        public Long checkpoint2Id;
        public String checkpoint2Name;
        public LocalDateTime checkpoint2Time;
        public double distance;
        public int timeDiff;
        public double calculatedSpeed;
        public double confidence;
    }
    
    /**
     * MySQL Sink - 写入套牌检测记录
     */
    private static class ClonePlateMySQLSink extends RichSinkFunction<ClonePlateRecord> {
        private transient Connection connection;
        private transient PreparedStatement statement;
        
        @Override
        public void open(Configuration parameters) throws Exception {
            super.open(parameters);
            connection = DriverManager.getConnection(
                    FlinkConfig.MYSQL_URL, 
                    FlinkConfig.MYSQL_USER, 
                    FlinkConfig.MYSQL_PASSWORD
            );
            statement = connection.prepareStatement(
                    "INSERT INTO clone_plate_detection (plate_number, detection_time, checkpoint1_id, checkpoint1_name, checkpoint1_time, " +
                    "checkpoint2_id, checkpoint2_name, checkpoint2_time, distance, time_diff, calculated_speed, confidence, status) " +
                    "VALUES (?, NOW(), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)"
            );
            LOG.info("MySQL连接已建立: {}", FlinkConfig.MYSQL_URL);
        }
        
        @Override
        public void invoke(ClonePlateRecord value, Context context) throws Exception {
            try {
                statement.setString(1, value.plateNumber);
                statement.setLong(2, value.checkpoint1Id);
                statement.setString(3, value.checkpoint1Name);
                statement.setTimestamp(4, Timestamp.valueOf(value.checkpoint1Time));
                statement.setLong(5, value.checkpoint2Id);
                statement.setString(6, value.checkpoint2Name);
                statement.setTimestamp(7, Timestamp.valueOf(value.checkpoint2Time));
                statement.setDouble(8, value.distance);
                statement.setInt(9, value.timeDiff);
                statement.setDouble(10, value.calculatedSpeed);
                statement.setDouble(11, value.confidence);
                statement.executeUpdate();
                LOG.info("套牌检测记录已写入: {} - 置信度{}", value.plateNumber, String.format("%.2f", value.confidence));
            } catch (Exception e) {
                LOG.error("写入套牌检测记录失败: {}", e.getMessage());
            }
        }
        
        @Override
        public void close() throws Exception {
            if (statement != null) statement.close();
            if (connection != null) connection.close();
            super.close();
        }
    }
}
