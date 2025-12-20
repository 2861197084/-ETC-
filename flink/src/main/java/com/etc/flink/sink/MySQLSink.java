package com.etc.flink.sink;

import com.etc.flink.config.FlinkConfig;
import com.etc.flink.model.PassRecordEvent;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * MySQL Sink - 将通行记录写入 MySQL 分片表
 * 
 * 双写策略：Flink 消费 Kafka 后同时写入 MySQL 和 HBase
 * MySQL 保留最近 7 天数据，用于快速索引查询
 */
public class MySQLSink extends RichSinkFunction<PassRecordEvent> {
    private static final Logger LOG = LoggerFactory.getLogger(MySQLSink.class);
    
    private static final int BATCH_SIZE = 100;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private transient Connection connection;
    private transient PreparedStatement insertStmt;
    private transient List<PassRecordEvent> buffer;
    private transient long lastFlushTime;
    
    private final String jdbcUrl;
    private final String username;
    private final String password;
    
    public MySQLSink() {
        this.jdbcUrl = FlinkConfig.MYSQL_URL;
        this.username = FlinkConfig.MYSQL_USER;
        this.password = FlinkConfig.MYSQL_PASSWORD;
    }
    
    public MySQLSink(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }
    
    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        Class.forName("com.mysql.cj.jdbc.Driver");
        connection = DriverManager.getConnection(jdbcUrl, username, password);
        connection.setAutoCommit(false);
        
        // 匹配数据库表结构：pass_record(id, plate_number, checkpoint_id, pass_time, direction, speed, lane_no, image_url, vehicle_type, etc_deduction, create_time)
        String insertSql = """
            INSERT INTO pass_record (
                plate_number, checkpoint_id, pass_time, direction, 
                speed, vehicle_type, etc_deduction
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        insertStmt = connection.prepareStatement(insertSql);
        buffer = new ArrayList<>(BATCH_SIZE);
        lastFlushTime = System.currentTimeMillis();
        
        LOG.info("MySQL Sink 已初始化，连接: {}", jdbcUrl);
    }
    
    @Override
    public void invoke(PassRecordEvent event, Context context) throws Exception {
        buffer.add(event);
        
        // 达到批次大小或超过 5 秒则刷新
        if (buffer.size() >= BATCH_SIZE || 
            (System.currentTimeMillis() - lastFlushTime) > 5000) {
            flush();
        }
    }
    
    private void flush() throws Exception {
        if (buffer.isEmpty()) return;
        
        try {
            for (PassRecordEvent event : buffer) {
                // 1. plate_number
                insertStmt.setString(1, event.getPlateNumber());
                // 2. checkpoint_id (从字符串提取数字)
                Long cpIdNum = event.getCheckpointIdNum();
                insertStmt.setLong(2, cpIdNum != null ? cpIdNum : 0L);
                // 3. pass_time
                insertStmt.setTimestamp(3, parseTimestamp(event.getPassTime()));
                // 4. direction
                insertStmt.setString(4, event.getDirection());
                // 5. speed (默认 0)
                insertStmt.setInt(5, event.getSpeed() != null ? event.getSpeed() : 0);
                // 6. vehicle_type
                insertStmt.setString(6, event.getVehicleType());
                // 7. etc_deduction
                insertStmt.setBigDecimal(7, event.getEtcDeduction() != null ? event.getEtcDeduction() : java.math.BigDecimal.ZERO);
                insertStmt.addBatch();
            }
            
            insertStmt.executeBatch();
            connection.commit();
            
            LOG.debug("MySQL 批量写入 {} 条记录", buffer.size());
            buffer.clear();
            lastFlushTime = System.currentTimeMillis();
            
        } catch (Exception e) {
            LOG.error("MySQL 写入失败，尝试回滚: {}", e.getMessage(), e);
            connection.rollback();
            throw e;
        }
    }
    
    /**
     * 计算车牌哈希值用于分片
     * 使用简单取模，确保同一车牌路由到同一分片
     */
    private int calculatePlateHash(String plateNumber) {
        if (plateNumber == null || plateNumber.isEmpty()) {
            return 0;
        }
        return Math.abs(plateNumber.hashCode()) % 4;
    }
    
    private Timestamp parseTimestamp(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return new Timestamp(System.currentTimeMillis());
        }
        try {
            LocalDateTime ldt = LocalDateTime.parse(timeStr, FORMATTER);
            return Timestamp.valueOf(ldt);
        } catch (Exception e) {
            return new Timestamp(System.currentTimeMillis());
        }
    }
    
    @Override
    public void close() throws Exception {
        // 关闭前刷新剩余数据
        if (buffer != null && !buffer.isEmpty()) {
            try {
                flush();
            } catch (Exception e) {
                LOG.error("关闭前刷新失败: {}", e.getMessage());
            }
        }
        
        if (insertStmt != null) {
            insertStmt.close();
        }
        if (connection != null) {
            connection.close();
        }
        super.close();
        LOG.info("MySQL Sink 已关闭");
    }
}
