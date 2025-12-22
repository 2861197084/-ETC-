package com.etc.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trino 联邦查询服务
 * <p>
 * 功能：
 * 1. 通过 Trino 统一查询 MySQL 分片数据
 * 2. 执行跨数据源的复杂聚合查询
 * 3. 支持 MySQL0 + MySQL1 的联邦统计
 * <p>
 * 注意：Trino 目前配置了 mysql0/mysql1 两个 catalog，
 * 可以直接用 SQL 跨库查询和聚合
 */
@Slf4j
@Service
public class TrinoQueryService {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Value("${trino.url:jdbc:trino://trino:8080}")
    private String trinoUrl;

    @Value("${trino.user:trino}")
    private String trinoUser;

    @Value("${trino.enabled:true}")
    private boolean trinoEnabled;

    private Connection connection;
    private final Object connLock = new Object();

    // 简单结果缓存
    private final Map<String, CachedResult> queryCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 60_000; // 1 分钟缓存

    @PostConstruct
    public void init() {
        if (trinoEnabled) {
            try {
                connect();
                log.info("Trino connection initialized: {}", trinoUrl);
            } catch (Exception e) {
                log.warn("Failed to initialize Trino connection: {}. Trino features will be disabled.", e.getMessage());
            }
        }
    }

    @PreDestroy
    public void cleanup() {
        closeConnection();
    }

    private void connect() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            return;
        }
        Properties props = new Properties();
        props.setProperty("user", trinoUser);
        connection = DriverManager.getConnection(trinoUrl, props);
    }

    private void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                log.warn("Error closing Trino connection: {}", e.getMessage());
            }
        }
    }

    /**
     * 检查 Trino 是否可用
     */
    public boolean isAvailable() {
        if (!trinoEnabled) return false;
        try {
            synchronized (connLock) {
                connect();
                return connection != null && connection.isValid(5);
            }
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * 获取 MySQL 分片总记录数（联邦查询 mysql0 + mysql1）
     */
    public long getMySqlTotalCount(LocalDateTime startTime, LocalDateTime endTime) {
        String cacheKey = "mysql_total:" + startTime + ":" + endTime;
        CachedResult cached = queryCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return (Long) cached.value;
        }

        String sql = """
            SELECT 
                (SELECT COUNT(*) FROM mysql0.etc_system.pass_record 
                 WHERE gcsj >= TIMESTAMP '%s' AND gcsj <= TIMESTAMP '%s')
                +
                (SELECT COUNT(*) FROM mysql1.etc_system.pass_record 
                 WHERE gcsj >= TIMESTAMP '%s' AND gcsj <= TIMESTAMP '%s')
                AS total
            """.formatted(
                startTime.format(TS_FMT), endTime.format(TS_FMT),
                startTime.format(TS_FMT), endTime.format(TS_FMT)
        );

        try {
            long count = executeCountQuery(sql);
            queryCache.put(cacheKey, new CachedResult(count));
            return count;
        } catch (Exception e) {
            log.error("Trino query failed: {}", e.getMessage());
            return -1;
        }
    }

    /**
     * 获取按收费站的统计
     */
    public Map<String, Long> getMySqlCountByCheckpoint(LocalDateTime startTime, LocalDateTime endTime) {
        String cacheKey = "mysql_by_cp:" + startTime + ":" + endTime;
        CachedResult cached = queryCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            @SuppressWarnings("unchecked")
            Map<String, Long> result = (Map<String, Long>) cached.value;
            return result;
        }

        String sql = """
            SELECT checkpoint_id, SUM(cnt) as total FROM (
                SELECT checkpoint_id, COUNT(*) as cnt 
                FROM mysql0.etc_system.pass_record 
                WHERE gcsj >= TIMESTAMP '%s' AND gcsj <= TIMESTAMP '%s'
                GROUP BY checkpoint_id
                UNION ALL
                SELECT checkpoint_id, COUNT(*) as cnt 
                FROM mysql1.etc_system.pass_record 
                WHERE gcsj >= TIMESTAMP '%s' AND gcsj <= TIMESTAMP '%s'
                GROUP BY checkpoint_id
            ) t
            GROUP BY checkpoint_id
            """.formatted(
                startTime.format(TS_FMT), endTime.format(TS_FMT),
                startTime.format(TS_FMT), endTime.format(TS_FMT)
        );

        try {
            Map<String, Long> result = executeGroupByQuery(sql);
            queryCache.put(cacheKey, new CachedResult(result));
            return result;
        } catch (Exception e) {
            log.error("Trino query failed: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 获取按日期的每日统计
     */
    public Map<String, Long> getDailyStats(LocalDate startDate, LocalDate endDate) {
        String cacheKey = "daily_stats:" + startDate + ":" + endDate;
        CachedResult cached = queryCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            @SuppressWarnings("unchecked")
            Map<String, Long> result = (Map<String, Long>) cached.value;
            return result;
        }

        String sql = """
            SELECT DATE(gcsj) as stat_date, SUM(cnt) as total FROM (
                SELECT DATE(gcsj) as gcsj, COUNT(*) as cnt 
                FROM mysql0.etc_system.pass_record 
                WHERE gcsj >= DATE '%s' AND gcsj < DATE '%s'
                GROUP BY DATE(gcsj)
                UNION ALL
                SELECT DATE(gcsj) as gcsj, COUNT(*) as cnt 
                FROM mysql1.etc_system.pass_record 
                WHERE gcsj >= DATE '%s' AND gcsj < DATE '%s'
                GROUP BY DATE(gcsj)
            ) t
            GROUP BY DATE(gcsj)
            ORDER BY stat_date
            """.formatted(
                startDate.format(DATE_FMT), endDate.plusDays(1).format(DATE_FMT),
                startDate.format(DATE_FMT), endDate.plusDays(1).format(DATE_FMT)
        );

        try {
            Map<String, Long> result = executeGroupByQuery(sql);
            queryCache.put(cacheKey, new CachedResult(result));
            return result;
        } catch (Exception e) {
            log.error("Trino query failed: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 执行自定义 SQL 查询
     */
    public List<Map<String, Object>> executeQuery(String sql) throws SQLException {
        synchronized (connLock) {
            connect();
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();
                
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                    results.add(row);
                }
                return results;
            }
        }
    }

    private long executeCountQuery(String sql) throws SQLException {
        synchronized (connLock) {
            connect();
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
                return 0;
            }
        }
    }

    private Map<String, Long> executeGroupByQuery(String sql) throws SQLException {
        synchronized (connLock) {
            connect();
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                Map<String, Long> result = new LinkedHashMap<>();
                while (rs.next()) {
                    String key = rs.getString(1);
                    long value = rs.getLong(2);
                    if (key != null) {
                        result.put(key, value);
                    }
                }
                return result;
            }
        }
    }

    /**
     * 清除缓存
     */
    public void clearCache() {
        queryCache.clear();
    }

    private record CachedResult(Object value, long timestamp) {
        CachedResult(Object value) {
            this(value, System.currentTimeMillis());
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }
}
