package com.etc.flink.job;

import com.etc.flink.model.PassRecordEvent;
import com.etc.flink.sink.HBaseSink;
import com.etc.flink.sink.MySQLSink;
import com.etc.flink.source.KafkaSourceFactory;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 数据同步作业 - Flink 消费 Kafka 后双写 MySQL 和 HBase
 * 
 * 这是新架构的核心作业，实现：
 * 1. 从 Kafka 消费原始通行记录
 * 2. 同时写入 MySQL（7天温数据，支持快速索引查询）
 * 3. 同时写入 HBase（全量历史数据，支持范围扫描）
 * 
 * 为什么双写而不是先 MySQL 再迁移 HBase？
 * - 简化数据流，无需定时迁移任务
 * - 保证数据一致性，两边数据实时同步
 * - MySQL 用于快速查询，HBase 用于历史追溯
 */
public class DataSyncJob {
    private static final Logger LOG = LoggerFactory.getLogger(DataSyncJob.class);

    /**
     * 注册数据同步作业到 Flink 执行环境
     */
    public static void register(StreamExecutionEnvironment env) {
        LOG.info("注册数据同步作业（双写 MySQL + HBase）...");
        
        // 从 Kafka 消费通行记录
        DataStream<PassRecordEvent> source = KafkaSourceFactory.createPassRecordStream(env);
        
        // 1. 写入 MySQL（温数据层，保留7天）
        source.addSink(new MySQLSink())
              .name("MySQL-Sink-PassRecord")
              .setParallelism(2);
        
        // 2. 写入 HBase（全量历史层）
        source.addSink(new HBaseSink())
              .name("HBase-Sink-PassRecord")
              .setParallelism(2);
        
        LOG.info("数据同步作业注册完成 - MySQL + HBase 双写");
    }
    
    /**
     * 独立运行模式（用于测试或单独部署）
     */
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        
        // 设置检查点（生产环境建议开启）
        // env.enableCheckpointing(60000);  // 每分钟检查点
        
        register(env);
        
        env.execute("ETC-DataSyncJob");
    }
}
