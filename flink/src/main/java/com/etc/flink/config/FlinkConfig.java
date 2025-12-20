package com.etc.flink.config;

import java.util.Map;

/**
 * Flink配置常量
 * 注意：这些配置是在 Docker 容器内运行，使用 Docker 服务名
 */
public class FlinkConfig {
    
    // Kafka配置（Docker 内部网络使用服务名:内部端口）
    public static final String KAFKA_BOOTSTRAP_SERVERS = "kafka:9092";
    public static final String KAFKA_TOPIC_PASS_RECORDS = "etc-pass-records";
    public static final String KAFKA_CONSUMER_GROUP = "etc-flink-consumer";
    
    // MySQL配置（通过 ShardingSphere 代理访问）
    public static final String MYSQL_URL = "jdbc:mysql://shardingsphere:3307/etc?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8";
    public static final String MYSQL_USER = "root";
    public static final String MYSQL_PASSWORD = "root";
    
    // HBase配置
    public static final String HBASE_ZOOKEEPER_QUORUM = "zookeeper";
    public static final String HBASE_ZOOKEEPER_PORT = "2181";
    public static final String HBASE_TABLE_PASS_RECORD = "etc:pass_record";
    public static final String HBASE_TABLE_TRAJECTORY = "etc:vehicle_trajectory";
    public static final String HBASE_COLUMN_FAMILY = "d";
    
    // Redis配置
    public static final String REDIS_HOST = "redis";
    public static final int REDIS_PORT = 6379;
    
    // Redis Key前缀 - 实时统计
    public static final String REDIS_KEY_FLOW_TOTAL = "realtime:flow:total";
    public static final String REDIS_KEY_FLOW_CHECKPOINT = "realtime:flow:checkpoint:";
    public static final String REDIS_KEY_REVENUE_TODAY = "realtime:revenue:today";
    public static final String REDIS_KEY_REVENUE_CHECKPOINT = "realtime:revenue:checkpoint:";
    public static final String REDIS_KEY_SOURCE_LOCAL = "realtime:source:local";
    public static final String REDIS_KEY_SOURCE_FOREIGN = "realtime:source:foreign";
    public static final String REDIS_KEY_PRESSURE = "realtime:pressure:";
    public static final String REDIS_KEY_HEAT_RANKING = "realtime:heat:ranking";
    public static final String REDIS_KEY_AVG_SPEED = "realtime:avgspeed";
    public static final String REDIS_KEY_SPEED_SUM = "realtime:speed:sum";
    public static final String REDIS_KEY_SPEED_COUNT = "realtime:speed:count";
    
    // Redis Key前缀 - 计数器（新增）
    public static final String REDIS_KEY_PLATE_COUNT = "plate:count:";           // 车牌通行总数
    public static final String REDIS_KEY_PLATE_COUNT_TODAY = "plate:count:today:"; // 车牌今日通行数
    public static final String REDIS_KEY_CP_COUNT = "cp:count:";                  // 卡口通行总数
    public static final String REDIS_KEY_CP_COUNT_TODAY = "cp:count:today:";       // 卡口今日通行数
    
    // 卡口名称映射
    public static final Map<Long, String> CHECKPOINT_NAME_MAP = Map.ofEntries(
        Map.entry(1L, "苏皖界1(104省道)"),
        Map.entry(2L, "苏皖界2(311国道)"),
        Map.entry(3L, "苏皖界3(徐明高速)"),
        Map.entry(4L, "苏皖界4(宿新高速)"),
        Map.entry(5L, "苏皖界5(徐淮高速)"),
        Map.entry(6L, "苏皖界6(新扬高速)"),
        Map.entry(7L, "苏鲁界1(206国道)"),
        Map.entry(8L, "苏鲁界2(104国道)"),
        Map.entry(9L, "苏鲁界3(京台高速)"),
        Map.entry(10L, "苏鲁界4(枣庄连接线)"),
        Map.entry(11L, "苏鲁界5(京沪高速)"),
        Map.entry(12L, "苏鲁界6(沂河路)"),
        Map.entry(13L, "连云港界1(徐连高速)"),
        Map.entry(14L, "连云港界2(310国道)"),
        Map.entry(15L, "宿迁界1(徐宿高速)"),
        Map.entry(16L, "宿迁界2(徐宿快速)"),
        Map.entry(17L, "宿迁界3(104国道)"),
        Map.entry(18L, "宿迁界4(新扬高速)"),
        Map.entry(19L, "宿迁界5(徐盐高速)")
    );
    
    // 卡口最大容量（辆/小时）
    public static final Map<Long, Integer> CHECKPOINT_CAPACITY = Map.ofEntries(
        Map.entry(1L, 800), Map.entry(2L, 1000), Map.entry(3L, 1500),
        Map.entry(4L, 1200), Map.entry(5L, 1500), Map.entry(6L, 1200),
        Map.entry(7L, 800), Map.entry(8L, 1000), Map.entry(9L, 1500),
        Map.entry(10L, 800), Map.entry(11L, 1500), Map.entry(12L, 600),
        Map.entry(13L, 1500), Map.entry(14L, 800), Map.entry(15L, 1200),
        Map.entry(16L, 1000), Map.entry(17L, 800), Map.entry(18L, 1200),
        Map.entry(19L, 1500)
    );
    
    private FlinkConfig() {}
}
