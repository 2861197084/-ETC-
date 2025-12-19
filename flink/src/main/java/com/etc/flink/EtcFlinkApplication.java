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
        
        // 设置检查点（容错）
        env.enableCheckpointing(60000); // 每60秒检查点
        env.setParallelism(2); // 并行度

        // 注册并启动所有作业
        LOG.info("注册实时计算作业...");
        
        // 1. 车流量统计作业
        TrafficFlowJob.register(env);
        
        // 2. 营收统计作业
        RevenueJob.register(env);
        
        // 3. 车辆来源统计作业(本地/外地)
        VehicleSourceJob.register(env);
        
        // 4. 站口压力计算作业
        CheckpointPressureJob.register(env);
        
        // 5. 区域热度排名作业
        RegionHeatJob.register(env);
        
        // 6. 违章实时检测作业
        ViolationDetectJob.register(env);
        
        // 7. 套牌车实时检测作业
        ClonePlateDetectJob.register(env);

        LOG.info("所有作业注册完成，开始执行...");
        LOG.info("Flink Web UI: http://localhost:8083");
        
        // 执行
        env.execute("ETC实时计算系统");
    }
}
