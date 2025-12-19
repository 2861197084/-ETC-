package com.etc.flink.config;

import java.util.Map;

/**
 * Flink配置常量
 */
public class FlinkConfig {
    
    // Kafka配置
    public static final String KAFKA_BOOTSTRAP_SERVERS = "localhost:9092";
    public static final String KAFKA_TOPIC_PASS_RECORDS = "etc-pass-records";
    public static final String KAFKA_CONSUMER_GROUP = "etc-flink-consumer";
    
    // MySQL配置
    public static final String MYSQL_URL = "jdbc:mysql://localhost:13306/etc_system?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8";
    public static final String MYSQL_USER = "root";
    public static final String MYSQL_PASSWORD = "root123";
    
    // Redis配置
    public static final String REDIS_HOST = "localhost";
    public static final int REDIS_PORT = 6379;
    
    // Redis Key前缀
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
