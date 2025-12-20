package com.etc.flink.job;

import com.etc.flink.config.FlinkConfig;
import com.etc.flink.model.PassRecordEvent;
import com.etc.flink.source.KafkaSourceFactory;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 计数器维护作业 - 实时维护 Redis 计数器
 * 
 * 解决的问题：
 * HBase 没有全局 COUNT 功能，查询总数需要全表扫描，非常慢。
 * 通过 Flink 实时维护 Redis 计数器，查询时直接读取 Redis，毫秒级返回。
 * 
 * 维护的计数器：
 * 1. plate:count:{plateNumber}         - 车牌通行总数（永久）
 * 2. plate:count:today:{plateNumber}   - 车牌今日通行数（TTL 2天）
 * 3. cp:count:{checkpointId}           - 卡口通行总数（永久）
 * 4. cp:count:today:{checkpointId}     - 卡口今日通行数（TTL 2天）
 * 
 * 用途：
 * - 渐进式查询时快速返回总数
 * - 实时大屏展示累计/当日流量
 * - 车辆画像展示通行次数
 */
public class CounterJob {
    private static final Logger LOG = LoggerFactory.getLogger(CounterJob.class);

    public static void register(StreamExecutionEnvironment env) {
        LOG.info("注册计数器维护作业...");
        
        DataStream<PassRecordEvent> source = KafkaSourceFactory.createPassRecordStream(env);
        
        // 维护所有计数器
        source.addSink(new CounterSink())
              .name("Redis-Counter-Sink")
              .setParallelism(2);
        
        LOG.info("计数器维护作业注册完成");
    }
    
    /**
     * 计数器 Sink - 维护多个 Redis 计数器
     */
    public static class CounterSink extends RichSinkFunction<PassRecordEvent> {
        private static final Logger LOG = LoggerFactory.getLogger(CounterSink.class);
        private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
        private static final int TODAY_KEY_TTL = 2 * 24 * 3600; // 2天过期
        
        private transient JedisPool jedisPool;
        
        @Override
        public void open(Configuration parameters) throws Exception {
            super.open(parameters);
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(20);
            poolConfig.setMaxIdle(10);
            poolConfig.setMinIdle(5);
            jedisPool = new JedisPool(poolConfig, FlinkConfig.REDIS_HOST, FlinkConfig.REDIS_PORT);
            LOG.info("Counter Sink Redis 连接池已初始化");
        }
        
        @Override
        public void invoke(PassRecordEvent event, Context context) throws Exception {
            if (event == null || event.getPlateNumber() == null) {
                return;
            }
            
            String today = LocalDate.now().format(DATE_FORMATTER);
            String plateNumber = event.getPlateNumber();
            String checkpointId = event.getCheckpointId();
            
            try (Jedis jedis = jedisPool.getResource()) {
                // 1. 车牌通行总数（永久）
                String plateCountKey = FlinkConfig.REDIS_KEY_PLATE_COUNT + plateNumber;
                jedis.incr(plateCountKey);
                
                // 2. 车牌今日通行数（TTL 2天）
                String plateTodayKey = FlinkConfig.REDIS_KEY_PLATE_COUNT_TODAY + today + ":" + plateNumber;
                jedis.incr(plateTodayKey);
                jedis.expire(plateTodayKey, TODAY_KEY_TTL);
                
                // 3. 卡口通行总数（永久）
                if (checkpointId != null) {
                    String cpCountKey = FlinkConfig.REDIS_KEY_CP_COUNT + checkpointId;
                    jedis.incr(cpCountKey);
                    
                    // 4. 卡口今日通行数（TTL 2天）
                    String cpTodayKey = FlinkConfig.REDIS_KEY_CP_COUNT_TODAY + today + ":" + checkpointId;
                    jedis.incr(cpTodayKey);
                    jedis.expire(cpTodayKey, TODAY_KEY_TTL);
                }
                
            } catch (Exception e) {
                LOG.error("计数器更新失败: {}", e.getMessage(), e);
            }
        }
        
        @Override
        public void close() throws Exception {
            if (jedisPool != null) {
                jedisPool.close();
            }
            super.close();
            LOG.info("Counter Sink 已关闭");
        }
    }
    
    /**
     * 独立运行模式
     */
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        register(env);
        env.execute("ETC-CounterJob");
    }
}
