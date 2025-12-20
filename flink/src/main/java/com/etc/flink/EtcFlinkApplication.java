package com.etc.flink;

import com.etc.flink.job.*;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.RestOptions;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ETC Flink实时计算应用入口
 * 启动所有实时计算作业
 */
public class EtcFlinkApplication {
    private static final Logger LOG = LoggerFactory.getLogger(EtcFlinkApplication.class);

    public static void main(String[] args) throws Exception {
        LOG.info("========================================");
        LOG.info("  ETC Flink 实时计算系统启动中...");
        LOG.info("========================================");

        // 创建配置
        Configuration config = new Configuration();
        config.setInteger(RestOptions.PORT, 8083); // Flink Web UI端口

        // 创建执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment(config);
        
        // 设置检查点（容错）- 间隔拉长减少开销
        env.enableCheckpointing(120000); // 每120秒检查点
        env.setParallelism(1); // 并行度降为1，减少CPU占用

        // 注册并启动所有作业
        LOG.info("注册实时计算作业...");
        
        // ==================== 当前只启用套牌检测（测试） ====================
        // 套牌车实时检测作业 - 唯一需要Flink实时处理的功能
        ClonePlateDetectJob.register(env);
        
        // ==================== 以下作业暂时禁用（改用Spring Boot定时任务实现） ====================
        // DataSyncJob.register(env);           // 数据同步 → 定时批量消费Kafka
        // CounterJob.register(env);            // 计数器 → SQL COUNT
        // TrafficFlowJob.register(env);        // 流量统计 → SQL聚合
        // CheckpointPressureJob.register(env); // 站点压力 → SQL统计
        // RevenueJob.register(env);            // 收入统计 → SQL SUM
        // VehicleSourceJob.register(env);
        // RegionHeatJob.register(env);
        // ViolationDetectJob.register(env);

        LOG.info("所有作业注册完成，开始执行...");
        LOG.info("Flink Web UI: http://localhost:8083");
        
        // 执行
        env.execute("ETC实时计算系统");
    }
}
