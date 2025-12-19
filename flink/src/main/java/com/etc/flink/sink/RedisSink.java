package com.etc.flink.sink;

import com.etc.flink.config.FlinkConfig;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Redis Sink - 将实时计算结果写入Redis
 */
public class RedisSink<T> extends RichSinkFunction<T> {
    private static final Logger LOG = LoggerFactory.getLogger(RedisSink.class);
    
    private transient JedisPool jedisPool;
    private final RedisMapper<T> mapper;
    
    public RedisSink(RedisMapper<T> mapper) {
        this.mapper = mapper;
    }
    
    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(10);
        poolConfig.setMaxIdle(5);
        jedisPool = new JedisPool(poolConfig, FlinkConfig.REDIS_HOST, FlinkConfig.REDIS_PORT);
        LOG.info("Redis连接池已初始化: {}:{}", FlinkConfig.REDIS_HOST, FlinkConfig.REDIS_PORT);
    }
    
    @Override
    public void invoke(T value, Context context) throws Exception {
        try (Jedis jedis = jedisPool.getResource()) {
            String command = mapper.getCommand();
            String key = mapper.getKey(value);
            
            switch (command) {
                case "SET":
                    jedis.set(key, mapper.getValue(value));
                    jedis.expire(key, 86400); // 24小时过期
                    break;
                case "INCRBY":
                    jedis.incrBy(key, Long.parseLong(mapper.getValue(value)));
                    jedis.expire(key, 86400);
                    break;
                case "HSET":
                    Map<String, String> hash = mapper.getHash(value);
                    if (hash != null && !hash.isEmpty()) {
                        jedis.hset(key, hash);
                        jedis.expire(key, 86400);
                    }
                    break;
                case "ZADD":
                    jedis.zadd(key, mapper.getScore(value), mapper.getMember(value));
                    jedis.expire(key, 86400);
                    break;
                default:
                    LOG.warn("未知的Redis命令: {}", command);
            }
        } catch (Exception e) {
            LOG.error("Redis写入失败: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public void close() throws Exception {
        if (jedisPool != null) {
            jedisPool.close();
        }
        super.close();
    }
    
    /**
     * Redis映射接口
     */
    public interface RedisMapper<T> {
        String getCommand();
        String getKey(T value);
        default String getValue(T value) { return ""; }
        default Map<String, String> getHash(T value) { return null; }
        default double getScore(T value) { return 0; }
        default String getMember(T value) { return ""; }
    }
}
