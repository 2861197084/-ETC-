package com.etc.flink.sink;

import com.etc.flink.config.FlinkConfig;
import com.etc.flink.model.PassRecordEvent;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * HBase Sink - 将通行记录写入 HBase
 * 
 * 双写策略：Flink 消费 Kafka 后同时写入 MySQL 和 HBase
 * HBase 保留全量历史数据，用于历史查询和轨迹分析
 * 
 * RowKey 设计: {salt(2位)}{yyyyMMdd}{checkpoint_id(3位)}{reverse_timestamp}
 * 示例: 03_20251220_001_9999999999999
 */
public class HBaseSink extends RichSinkFunction<PassRecordEvent> {
    private static final Logger LOG = LoggerFactory.getLogger(HBaseSink.class);
    
    private static final int BATCH_SIZE = 100;
    private static final int SALT_BUCKETS = 16;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private transient Connection connection;
    private transient BufferedMutator mutator;
    private transient List<Put> buffer;
    private transient long lastFlushTime;
    private transient Random random;
    
    private final byte[] columnFamily;
    private final String tableName;
    private final String zkQuorum;
    private final String zkPort;
    
    public HBaseSink() {
        this.tableName = FlinkConfig.HBASE_TABLE_PASS_RECORD;
        this.columnFamily = Bytes.toBytes(FlinkConfig.HBASE_COLUMN_FAMILY);
        this.zkQuorum = FlinkConfig.HBASE_ZOOKEEPER_QUORUM;
        this.zkPort = FlinkConfig.HBASE_ZOOKEEPER_PORT;
    }
    
    public HBaseSink(String tableName, String columnFamily, String zkQuorum, String zkPort) {
        this.tableName = tableName;
        this.columnFamily = Bytes.toBytes(columnFamily);
        this.zkQuorum = zkQuorum;
        this.zkPort = zkPort;
    }
    
    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        
        org.apache.hadoop.conf.Configuration config = HBaseConfiguration.create();
        config.set("hbase.zookeeper.quorum", zkQuorum);
        config.set("hbase.zookeeper.property.clientPort", zkPort);
        
        connection = ConnectionFactory.createConnection(config);
        
        BufferedMutatorParams mutatorParams = new BufferedMutatorParams(TableName.valueOf(tableName))
                .writeBufferSize(4 * 1024 * 1024);  // 4MB buffer
        mutator = connection.getBufferedMutator(mutatorParams);
        
        buffer = new ArrayList<>(BATCH_SIZE);
        lastFlushTime = System.currentTimeMillis();
        random = new Random();
        
        LOG.info("HBase Sink 已初始化，表: {}, ZK: {}:{}", tableName, zkQuorum, zkPort);
    }
    
    @Override
    public void invoke(PassRecordEvent event, Context context) throws Exception {
        Put put = createPut(event);
        buffer.add(put);
        
        // 达到批次大小或超过 5 秒则刷新
        if (buffer.size() >= BATCH_SIZE || 
            (System.currentTimeMillis() - lastFlushTime) > 5000) {
            flush();
        }
    }
    
    private Put createPut(PassRecordEvent event) {
        String rowKey = buildRowKey(event);
        Put put = new Put(Bytes.toBytes(rowKey));
        
        // 添加列
        addColumn(put, "plate", event.getPlateNumber());
        addColumn(put, "vtype", event.getVehicleType());
        addColumn(put, "vcolor", event.getVehicleColor());
        addColumn(put, "cp_id", event.getCheckpointId());
        addColumn(put, "cp_name", event.getCheckpointName());
        addColumn(put, "pass_time", event.getPassTime());
        addColumn(put, "speed", event.getSpeed() != null ? String.valueOf(event.getSpeed()) : "0");
        addColumn(put, "direction", event.getDirection());
        addColumn(put, "etc_card", event.getEtcCardId());
        addColumn(put, "fee", event.getEtcDeduction() != null ? event.getEtcDeduction().toString() : "0");
        addColumn(put, "region", event.getRegion());
        addColumn(put, "is_local", event.isLocalVehicle() ? "1" : "0");
        
        return put;
    }
    
    private void addColumn(Put put, String qualifier, String value) {
        if (value != null) {
            put.addColumn(columnFamily, Bytes.toBytes(qualifier), Bytes.toBytes(value));
        }
    }
    
    /**
     * 构建 RowKey
     * 格式: {salt(2位)}_{yyyyMMdd}_{checkpoint_id(3位)}_{reverse_timestamp}
     * 
     * 设计理由:
     * 1. salt: 打散热点，避免写入集中到单个 Region
     * 2. date: 支持按天范围扫描
     * 3. checkpoint_id: 支持按卡口过滤
     * 4. reverse_timestamp: 最新数据排在前面
     */
    private String buildRowKey(PassRecordEvent event) {
        // 盐值：根据车牌 hash 决定，确保同一车牌路由到同一分区便于查询
        int salt = Math.abs(event.getPlateNumber().hashCode()) % SALT_BUCKETS;
        
        // 日期
        String date;
        try {
            LocalDateTime ldt = LocalDateTime.parse(event.getPassTime(), TIME_FORMATTER);
            date = ldt.format(DATE_FORMATTER);
        } catch (Exception e) {
            date = LocalDateTime.now().format(DATE_FORMATTER);
        }
        
        // 卡口ID（从字符串提取数字，3位不足补零）
        Long cpNum = event.getCheckpointIdNum();
        String cpId = String.format("%03d", cpNum != null ? cpNum : 0);
        
        // 反转时间戳（使最新数据排在前面）
        long reverseTs = Long.MAX_VALUE - System.currentTimeMillis();
        
        return String.format("%02d_%s_%s_%d", salt, date, cpId, reverseTs);
    }
    
    private void flush() throws Exception {
        if (buffer.isEmpty()) return;
        
        try {
            for (Put put : buffer) {
                mutator.mutate(put);
            }
            mutator.flush();
            
            LOG.debug("HBase 批量写入 {} 条记录", buffer.size());
            buffer.clear();
            lastFlushTime = System.currentTimeMillis();
            
        } catch (Exception e) {
            LOG.error("HBase 写入失败: {}", e.getMessage(), e);
            throw e;
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
        
        if (mutator != null) {
            mutator.close();
        }
        if (connection != null) {
            connection.close();
        }
        super.close();
        LOG.info("HBase Sink 已关闭");
    }
}
