package com.etc.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ForecastService {

    private static final DateTimeFormatter MYSQL_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static String formatMysqlTs(Object v) {
        if (v == null) return "";
        if (v instanceof LocalDateTime ldt) return ldt.format(MYSQL_TS);
        if (v instanceof Timestamp ts) return ts.toLocalDateTime().format(MYSQL_TS);
        if (v instanceof java.util.Date d) return new Timestamp(d.getTime()).toLocalDateTime().format(MYSQL_TS);
        String s = String.valueOf(v);
        // Common JDBC string formats: "yyyy-MM-dd HH:mm:ss.0" or "yyyy-MM-ddTHH:mm:ss"
        s = s.replace('T', ' ');
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        if (s.length() > 19) s = s.substring(0, 19);
        return s;
    }

    public long createForecastRequest(String checkpointId, String fxlx, LocalDateTime asOfTime, String modelVersion) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO forecast_request (checkpoint_id, fxlx, as_of_time, status, model_version, created_at, updated_at) " +
                            "VALUES (?, ?, ?, 'pending', ?, NOW(), NOW())",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, checkpointId);
            ps.setString(2, fxlx);
            ps.setString(3, asOfTime.format(MYSQL_TS));
            ps.setString(4, modelVersion);
            return ps;
        }, keyHolder);
        Number k = keyHolder.getKey();
        return k == null ? -1L : k.longValue();
    }

    /**
     * 刷新预测时的“去重”写入：
     * - 若该卡口×方向×模型已有 pending 请求，则更新其 as_of_time（避免前端轮询导致 pending 堆积）
     * - 否则插入新请求
     */
    public long upsertPendingForecastRequest(String checkpointId, String fxlx, LocalDateTime asOfTime, String modelVersion) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id FROM forecast_request " +
                        "WHERE checkpoint_id=? AND fxlx=? AND model_version=? AND status='pending' " +
                        "ORDER BY updated_at DESC, created_at DESC LIMIT 1",
                checkpointId, fxlx, modelVersion
        );
        if (!rows.isEmpty()) {
            long id = ((Number) rows.get(0).get("id")).longValue();
            jdbcTemplate.update(
                    "UPDATE forecast_request SET as_of_time=?, updated_at=NOW() WHERE id=?",
                    asOfTime.format(MYSQL_TS),
                    id
            );
            return id;
        }
        return createForecastRequest(checkpointId, fxlx, asOfTime, modelVersion);
    }

    public Optional<PendingRequest> findLatestPendingRequest(String checkpointId, String fxlx, String modelVersion) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, created_at FROM forecast_request " +
                        "WHERE checkpoint_id=? AND fxlx=? AND model_version=? AND status='pending' " +
                        "ORDER BY updated_at DESC, created_at DESC LIMIT 1",
                checkpointId, fxlx, modelVersion
        );
        if (rows.isEmpty()) return Optional.empty();
        Map<String, Object> r = rows.get(0);
        long id = ((Number) r.get("id")).longValue();
        String createdAt = formatMysqlTs(r.get("created_at"));
        return Optional.of(new PendingRequest(id, createdAt));
    }

    public Optional<ForecastRow> findLatestForecast(String checkpointId, String fxlx, String modelVersion) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT checkpoint_id, fxlx, start_time, values_json, model_version, updated_at " +
                        "FROM checkpoint_flow_forecast_5m " +
                        "WHERE checkpoint_id=? AND fxlx=? AND model_version=? " +
                        "ORDER BY start_time DESC, updated_at DESC LIMIT 1",
                checkpointId, fxlx, modelVersion
        );
        if (rows.isEmpty()) return Optional.empty();
        Map<String, Object> r = rows.get(0);
        String startTime = formatMysqlTs(r.get("start_time"));
        String updatedAt = formatMysqlTs(r.get("updated_at"));
        String valuesJson = String.valueOf(r.get("values_json"));
        List<Double> values;
        try {
            values = objectMapper.readValue(valuesJson, new TypeReference<List<Double>>() {});
        } catch (Exception e) {
            values = List.of();
        }
        if (values == null) values = List.of();
        return Optional.of(
                new ForecastRow(
                        checkpointId,
                        fxlx,
                        startTime,
                        values,
                        modelVersion,
                        updatedAt
                )
        );
    }

    public record PendingRequest(long id, String createdAt) {}

    public record ForecastRow(
            String checkpointId,
            String fxlx,
            String startTime,
            List<Double> values,
            String modelVersion,
            String updatedAt
    ) {}
}


