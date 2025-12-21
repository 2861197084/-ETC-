package com.etc.service;

import com.etc.common.CheckpointCatalog;
import lombok.RequiredArgsConstructor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.filter.BinaryComparator;
import org.apache.hadoop.hbase.filter.FilterList;
import org.apache.hadoop.hbase.filter.PageFilter;
import org.apache.hadoop.hbase.filter.SingleColumnValueFilter;
import org.apache.hadoop.hbase.util.Bytes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.hadoop.hbase.CompareOperator.*;

@Service
@RequiredArgsConstructor
public class HBasePassRecordService {

    private static final byte[] CF = Bytes.toBytes("d");
    private static final DateTimeFormatter GCSJ_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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

        List<Map<String, Object>> list = new ArrayList<>();
        String nextRowKey = null;

        String checkpointNameFilter = null;
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
        if (startTime != null) {
            filters.addFilter(geFilter("gcsj", startTime.format(GCSJ_FORMATTER)));
        }
        if (endTime != null) {
            filters.addFilter(leFilter("gcsj", endTime.format(GCSJ_FORMATTER)));
        }
        filters.addFilter(new PageFilter(Math.max(1, size)));

        Scan scan = new Scan();
        scan.setCaching(Math.min(Math.max(1, size), 200));
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
            }
        } catch (Exception e) {
            // 上层统一异常处理（GlobalExceptionHandler）会兜底返回 ApiResponse.error
            throw new RuntimeException("HBase query failed: " + e.getMessage(), e);
        }

        long ms = (System.nanoTime() - t0) / 1_000_000;
        boolean hasMoreHistory = list.size() >= Math.max(1, size);
        if (!hasMoreHistory) nextRowKey = null;

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
