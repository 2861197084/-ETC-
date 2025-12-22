package com.etc.service;

import com.etc.common.CheckpointCatalog;
import com.etc.common.HBaseTimeUtils;
import lombok.RequiredArgsConstructor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.filter.BinaryComparator;
import org.apache.hadoop.hbase.filter.FilterList;
import org.apache.hadoop.hbase.filter.SingleColumnValueFilter;
import org.apache.hadoop.hbase.util.Bytes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.hadoop.hbase.CompareOperator.*;

@Service
@RequiredArgsConstructor
public class HBasePassRecordService {

    private static final byte[] CF = Bytes.toBytes("d");

    private final Connection connection;

    @Value("${HBASE_TABLE:etc:pass_record}")
    private String tableName;

    public QueryResult query(
            String plateNumber,
            String checkpointId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String lastRowKey,
            int size) {
        long t0 = System.nanoTime();

        int limit = Math.max(1, size);
        // Fetch one extra row to detect "has more" without scanning the whole table.
        int fetch = limit + 1;

        List<Map<String, Object>> list = new ArrayList<>();
        String nextRowKey = null;

        String checkpointIdFilter = null;
        if (checkpointId != null && !checkpointId.isBlank()) {
            checkpointIdFilter = checkpointId.trim();
        }

        FilterList filters = new FilterList(FilterList.Operator.MUST_PASS_ALL);
        if (plateNumber != null && !plateNumber.isBlank()) {
            filters.addFilter(eqFilter("hp", plateNumber.trim()));
        }
        if (checkpointIdFilter != null && !checkpointIdFilter.isBlank()) {
            filters.addFilter(eqFilter("checkpoint_id", checkpointIdFilter));
        }
        // 时间范围过滤 - 使用字典序兼容多种格式
        if (startTime != null) {
            // 使用最小的格式确保不漏数据
            filters.addFilter(geFilter("gcsj", HBaseTimeUtils.getMinTimeString(startTime)));
        }
        if (endTime != null) {
            // 使用最大的格式确保不漏数据
            filters.addFilter(leFilter("gcsj", HBaseTimeUtils.getMaxTimeString(endTime)));
        }

        Scan scan = new Scan();
        scan.setCaching(Math.min(fetch, 200));
        scan.setFilter(filters);
        scan.addFamily(CF);
        if (lastRowKey != null && !lastRowKey.isBlank()) {
            scan.withStartRow(Bytes.toBytes(lastRowKey), false);
        }

        try (Table table = connection.getTable(TableName.valueOf(tableName));
             ResultScanner scanner = table.getScanner(scan)) {
            for (Result r : scanner) {
                if (r == null || r.isEmpty()) continue;

                String rowKey = Bytes.toString(r.getRow());
                Map<String, Object> item = new HashMap<>();
                item.put("rowKey", rowKey);

                String hp = getString(r, "hp");
                String gcsj = getString(r, "gcsj");
                
                // 二次验证时间范围（字典序过滤可能带入越界数据）
                if (!HBaseTimeUtils.isInRange(gcsj, startTime, endTime)) {
                    continue;
                }
                
                String kkmc = getString(r, "kkmc");
                String checkpointCode = getString(r, "checkpoint_id");
                String xzqhmc = getString(r, "xzqhmc");
                String fxlx = getString(r, "fxlx");
                String hpzl = getString(r, "hpzl");
                String clppxh = getString(r, "clppxh");

                String code = (checkpointCode != null && !checkpointCode.isBlank())
                        ? checkpointCode
                        : CheckpointCatalog.codeByName(kkmc);

                item.put("plateNumber", hp);
                item.put("passTime", gcsj);
                item.put("checkpointName", kkmc);
                item.put("checkpointId", code != null ? code : checkpointId);
                item.put("district", xzqhmc);
                item.put("direction", fxlx);
                item.put("plateType", hpzl);
                item.put("vehicleType", clppxh);
                item.put("source", "hbase");

                list.add(item);
                nextRowKey = rowKey;

                if (list.size() >= fetch) {
                    break;
                }
            }
        } catch (Exception e) {
            // 上层统一异常处理（GlobalExceptionHandler）会兜底返回 ApiResponse.error
            throw new RuntimeException("HBase query failed: " + e.getMessage(), e);
        }

        long ms = (System.nanoTime() - t0) / 1_000_000;
        boolean hasMoreHistory = list.size() > limit;
        if (hasMoreHistory) {
            // Keep only the requested page size; nextRowKey should be the last returned row key.
            list = new ArrayList<>(list.subList(0, limit));
            nextRowKey = String.valueOf(list.get(list.size() - 1).get("rowKey"));
        } else {
            nextRowKey = null;
        }

        return new QueryResult(list, nextRowKey, hasMoreHistory, ms);
    }

    private static SingleColumnValueFilter eqFilter(String qualifier, String value) {
        SingleColumnValueFilter f = new SingleColumnValueFilter(
                CF,
                Bytes.toBytes(qualifier),
                EQUAL,
                new BinaryComparator(value.getBytes(StandardCharsets.UTF_8)));
        f.setFilterIfMissing(true);
        return f;
    }

    private static SingleColumnValueFilter geFilter(String qualifier, String value) {
        SingleColumnValueFilter f = new SingleColumnValueFilter(
                CF,
                Bytes.toBytes(qualifier),
                GREATER_OR_EQUAL,
                new BinaryComparator(value.getBytes(StandardCharsets.UTF_8)));
        f.setFilterIfMissing(true);
        return f;
    }

    private static SingleColumnValueFilter leFilter(String qualifier, String value) {
        SingleColumnValueFilter f = new SingleColumnValueFilter(
                CF,
                Bytes.toBytes(qualifier),
                LESS_OR_EQUAL,
                new BinaryComparator(value.getBytes(StandardCharsets.UTF_8)));
        f.setFilterIfMissing(true);
        return f;
    }

    private static String getString(Result r, String qualifier) {
        byte[] v = r.getValue(CF, Bytes.toBytes(qualifier));
        return v == null ? "" : Bytes.toString(v);
    }

    public record QueryResult(List<Map<String, Object>> list, String nextRowKey, boolean hasMoreHistory, long queryTimeMs) {}
}
