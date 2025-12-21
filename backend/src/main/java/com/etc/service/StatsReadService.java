package com.etc.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StatsReadService {

    private static final DateTimeFormatter MYSQL_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final StringRedisTemplate redis;
    private final JdbcTemplate jdbcTemplate;
    private final TimeService timeService;

    @Value("${etc.retention.days:7}")
    private int retentionDays;

    public long getHistoryTotal() {
        return parseLong(redis.opsForValue().get(StatsAggregationService.Keys.historyTotal()));
    }

    public long getMysql7dTotal() {
        String v = redis.opsForValue().get(StatsAggregationService.Keys.mysql7dTotal());
        if (v != null) return parseLong(v);

        LocalDateTime simNow = timeService.getSimulatedTime();
        LocalDateTime cutoff = simNow.minusDays(Math.max(1, retentionDays));
        return queryCount("SELECT COUNT(*) FROM pass_record WHERE gcsj >= ?", cutoff);
    }

    public long getMysqlToday() {
        String v = redis.opsForValue().get(StatsAggregationService.Keys.mysqlToday());
        if (v != null) return parseLong(v);

        LocalDateTime simNow = timeService.getSimulatedTime();
        LocalDateTime start = simNow.toLocalDate().atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        return queryCount("SELECT COUNT(*) FROM pass_record WHERE gcsj >= ? AND gcsj < ?", start, end);
    }

    public Map<String, Long> getMysqlTodayByCheckpoint() {
        Map<Object, Object> raw = redis.opsForHash().entries(StatsAggregationService.Keys.mysqlTodayByCheckpoint());
        if (raw != null && !raw.isEmpty()) {
            Map<String, Long> out = new HashMap<>();
            for (Map.Entry<Object, Object> e : raw.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) continue;
                out.put(e.getKey().toString(), parseLong(e.getValue().toString()));
            }
            return out;
        }

        LocalDateTime simNow = timeService.getSimulatedTime();
        LocalDateTime start = simNow.toLocalDate().atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT checkpoint_id, COUNT(*) AS c FROM pass_record WHERE gcsj >= ? AND gcsj < ? GROUP BY checkpoint_id",
                start.format(MYSQL_TS),
                end.format(MYSQL_TS));
        Map<String, Long> out = new HashMap<>();
        for (Map<String, Object> r : rows) {
            Object checkpointId = r.get("checkpoint_id");
            Object count = r.get("c");
            if (checkpointId == null || count == null) continue;
            out.put(checkpointId.toString(), parseLong(count.toString()));
        }
        return out;
    }

    public long getCombinedTotal() {
        long hotTotal = Math.max(getMysql7dTotal(), getMysqlToday());
        return getHistoryTotal() + hotTotal;
    }

    private long queryCount(String sql, LocalDateTime... args) {
        Object[] params = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            params[i] = args[i].format(MYSQL_TS);
        }
        Long v = jdbcTemplate.queryForObject(sql, Long.class, params);
        return v == null ? 0L : v;
    }

    private long parseLong(String s) {
        if (s == null || s.isBlank()) return 0L;
        try {
            return Long.parseLong(s.trim());
        } catch (Exception ignored) {
            return 0L;
        }
    }
}
