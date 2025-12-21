package com.etc.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class DataRetentionService {

    private static final DateTimeFormatter MYSQL_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final TimeService timeService;

    @Value("${etc.retention.days:7}")
    private int retentionDays;

    @Value("${etc.retention.batch-size:10000}")
    private int batchSize;

    @Value("${etc.retention.max-batches:50}")
    private int maxBatches;

    @Scheduled(fixedDelayString = "${etc.retention.cleanup-ms:600000}")
    public void cleanupHotData() {
        LocalDateTime simNow = timeService.getSimulatedTime();
        LocalDateTime cutoff = simNow.minusDays(Math.max(1, retentionDays));
        String cutoffStr = cutoff.format(MYSQL_TS);

        // 走 ShardingSphere Proxy：清理逻辑表 pass_record，由分片规则下发到实际分片表
        purgeInBatches("pass_record", "gcsj", cutoffStr);

        purgeInBatches("checkpoint_flow", "flow_time", cutoffStr);
        purgeInBatches("agg_checkpoint_minute", "minute_ts", cutoffStr);

        purgeInBatches("violation", "violation_time", cutoffStr);
        purgeInBatches("clone_plate_detection", "time_2", cutoffStr);
        purgeInBatches("alert", "created_at", cutoffStr);
        purgeInBatches("query_history", "created_at", cutoffStr);
    }

    private void purgeInBatches(String table, String timeColumn, String cutoffStr) {
        String sql = "DELETE FROM " + table + " WHERE " + timeColumn + " < ? LIMIT " + Math.max(1, batchSize);
        for (int i = 0; i < Math.max(1, maxBatches); i++) {
            int affected;
            try {
                affected = jdbcTemplate.update(sql, cutoffStr);
            } catch (Exception e) {
                return;
            }
            if (affected <= 0) return;
        }
    }
}
