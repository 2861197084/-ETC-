package com.etc.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StatsAggregationService {

    private static final DateTimeFormatter MYSQL_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redis;
    private final TimeService timeService;

    @Value("${etc.retention.days:7}")
    private int retentionDays;

    @Value("${etc.stats.ttl-seconds:60}")
    private long ttlSeconds;

    @Scheduled(fixedDelayString = "${etc.stats.refresh-ms:30000}")
    public void refreshHotStatsToRedis() {
        LocalDateTime simNow = timeService.getSimulatedTime();
        LocalDateTime cutoff7d = simNow.minusDays(Math.max(1, retentionDays));

        LocalDateTime startOfDay = simNow.toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        try {
            long mysqlTotal7d = queryCount("SELECT COUNT(*) FROM pass_record WHERE gcsj >= ?", cutoff7d);
            long mysqlToday = queryCount("SELECT COUNT(*) FROM pass_record WHERE gcsj >= ? AND gcsj < ?", startOfDay, endOfDay);

            Duration ttl = Duration.ofSeconds(Math.max(5, ttlSeconds));
            redis.opsForValue().set(Keys.mysql7dTotal(), String.valueOf(mysqlTotal7d), ttl);
            redis.opsForValue().set(Keys.mysqlToday(), String.valueOf(mysqlToday), ttl);
            redis.opsForValue().set(Keys.simulatedNow(), simNow.format(MYSQL_TS), ttl);

            redis.delete(Keys.mysqlTodayByCheckpoint());
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT checkpoint_id, COUNT(*) AS c FROM pass_record WHERE gcsj >= ? AND gcsj < ? GROUP BY checkpoint_id",
                    startOfDay.format(MYSQL_TS),
                    endOfDay.format(MYSQL_TS));
            for (Map<String, Object> r : rows) {
                Object checkpointId = r.get("checkpoint_id");
                Object count = r.get("c");
                if (checkpointId == null || count == null) continue;
                redis.opsForHash().put(Keys.mysqlTodayByCheckpoint(), checkpointId.toString(), count.toString());
            }
            redis.expire(Keys.mysqlTodayByCheckpoint(), ttl);
        } catch (DataAccessException e) {
            // MySQL or Redis issues should not crash the app; next tick will retry
        }
    }

    private long queryCount(String sql, LocalDateTime... args) {
        Object[] params = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            params[i] = args[i].format(MYSQL_TS);
        }
        Long v = jdbcTemplate.queryForObject(sql, Long.class, params);
        return v == null ? 0L : v;
    }

    public static final class Keys {
        private Keys() {}

        private static final String PREFIX = "etc:stats:";

        public static String simulatedNow() {
            return PREFIX + "simulated_now";
        }

        public static String historyTotal() {
            return PREFIX + "history:pass_record:total";
        }

        public static String mysql7dTotal() {
            return PREFIX + "hot:mysql7d:pass_record:total";
        }

        public static String mysqlToday() {
            return PREFIX + "hot:mysql7d:pass_record:today";
        }

        public static String mysqlTodayByCheckpoint() {
            return PREFIX + "hot:mysql7d:pass_record:today:by_checkpoint";
        }
    }
}
