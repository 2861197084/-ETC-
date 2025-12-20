package com.etc.flink.job;

import com.etc.flink.config.FlinkConfig;
import com.etc.flink.model.PassRecordEvent;
import com.etc.flink.source.KafkaSourceFactory;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * 违章实时检测作业
 * - 超速检测: speed > 120
 * - 检测到违章后写入MySQL violation表
 */
public class ViolationDetectJob {
    private static final Logger LOG = LoggerFactory.getLogger(ViolationDetectJob.class);

    public static void register(StreamExecutionEnvironment env) {
        LOG.info("注册违章实时检测作业...");
        
        DataStream<PassRecordEvent> source = KafkaSourceFactory.createPassRecordStream(env);
        
        // 超速检测（速度 > 120）
        DataStream<ViolationRecord> overspeedViolations = source
                .filter(PassRecordEvent::isOverspeed)
                .map(new MapFunction<PassRecordEvent, ViolationRecord>() {
                    @Override
                    public ViolationRecord map(PassRecordEvent e) {
                        ViolationRecord v = new ViolationRecord();
                        v.plateNumber = e.getPlateNumber();
                        v.checkpointId = e.getCheckpointIdNum();
                        v.violationType = "overspeed";
                        v.description = "超速行驶，检测速度: " + e.getSpeed() + " km/h，限速: 120 km/h";
                        v.fineAmount = 200;
                        v.points = 6;
                        v.violationTime = LocalDateTime.now();
                        return v;
                    }
                })
                .name("Overspeed-Detection");
        
        // 写入MySQL
        overspeedViolations.addSink(new ViolationMySQLSink())
                .name("MySQL-Violation-Sink");
        
        LOG.info("违章实时检测作业注册完成");
    }
    
    /**
     * 违章记录
     */
    public static class ViolationRecord implements java.io.Serializable {
        public String plateNumber;
        public Long checkpointId;
        public String violationType;
        public String description;
        public int fineAmount;
        public int points;
        public LocalDateTime violationTime;
    }
    
    /**
     * MySQL Sink - 写入违章记录
     */
    private static class ViolationMySQLSink extends RichSinkFunction<ViolationRecord> {
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
                    "INSERT INTO violation (plate_number, checkpoint_id, violation_type, violation_time, description, fine_amount, points, status, create_time) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, 0, NOW())"
            );
            LOG.info("MySQL连接已建立: {}", FlinkConfig.MYSQL_URL);
        }
        
        @Override
        public void invoke(ViolationRecord value, Context context) throws Exception {
            try {
                statement.setString(1, value.plateNumber);
                statement.setLong(2, value.checkpointId);
                statement.setString(3, value.violationType);
                statement.setTimestamp(4, Timestamp.valueOf(value.violationTime));
                statement.setString(5, value.description);
                statement.setInt(6, value.fineAmount);
                statement.setInt(7, value.points);
                statement.executeUpdate();
                LOG.info("违章记录已写入: {} - {}", value.plateNumber, value.violationType);
            } catch (Exception e) {
                LOG.error("写入违章记录失败: {}", e.getMessage());
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
