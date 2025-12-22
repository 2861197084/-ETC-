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
            String direction,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String lastRowKey,
            int page,
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
        // 方向筛选 - 兼容 "1"/"2" 和 "进城"/"出城"
        if (direction != null && !direction.isBlank()) {
            String d = direction.trim();
            if ("1".equals(d)) {
                // 进城：匹配 "1" 或 "进城"
                filters.addFilter(directionFilter("1", "进城"));
            } else if ("2".equals(d)) {
                // 出城：匹配 "2" 或 "出城"
                filters.addFilter(directionFilter("2", "出城"));
            } else {
                filters.addFilter(eqFilter("fxlx", d));
            }
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
        scan.setCaching(Math.min(500, 200));
        scan.setFilter(filters);
        scan.addFamily(CF);
        
        // 游标分页模式：如果提供了 lastRowKey，从该位置开始扫描
        // page=1 时从头开始并计算 total；page>1 时使用 lastRowKey 跳过
        boolean needCountTotal = (page == 1 && (lastRowKey == null || lastRowKey.isBlank()));
        
        if (lastRowKey != null && !lastRowKey.isBlank()) {
            scan.withStartRow(Bytes.toBytes(lastRowKey), false); // false = 不包含起始行
        }
        
        long totalCount = -1; // -1 表示不计算总数（使用缓存的值）

        try (Table table = connection.getTable(TableName.valueOf(tableName));
             ResultScanner scanner = table.getScanner(scan)) {
            
            if (needCountTotal) {
                // 首次查询：遍历全部计算 total
                for (Result r : scanner) {
                    if (r == null || r.isEmpty()) continue;

                    String gcsj = getString(r, "gcsj");
                    if (!HBaseTimeUtils.isInRange(gcsj, startTime, endTime)) {
                        continue;
                    }
                    
                    if (totalCount < 0) totalCount = 0;
                    totalCount++;
                    
                    // 只收集当前页数据
                    if (list.size() < limit) {
                        String rowKey = Bytes.toString(r.getRow());
                        list.add(buildRecordMap(r, rowKey, checkpointId, gcsj));
                        nextRowKey = rowKey;
                    }
                }
            } else {
                // 后续翻页：只获取当前页数据，不计算 total
                int count = 0;
                for (Result r : scanner) {
                    if (r == null || r.isEmpty()) continue;

                    String gcsj = getString(r, "gcsj");
                    if (!HBaseTimeUtils.isInRange(gcsj, startTime, endTime)) {
                        continue;
                    }
                    
                    count++;
                    if (count <= limit) {
                        String rowKey = Bytes.toString(r.getRow());
                        list.add(buildRecordMap(r, rowKey, checkpointId, gcsj));
                        nextRowKey = rowKey;
                    }
                    
                    // 获取到 fetch 条就停止
                    if (count >= fetch) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("HBase query failed: " + e.getMessage(), e);
        }

        long ms = (System.nanoTime() - t0) / 1_000_000;
        // 游标模式：有更多数据 = 获取到的数据超过 limit
        boolean hasMoreHistory = (list.size() > limit) || (nextRowKey != null && list.size() == limit);

        return new QueryResult(list, nextRowKey, hasMoreHistory, ms, totalCount);
    }

    /**
     * 构建记录 Map
     */
    private Map<String, Object> buildRecordMap(Result r, String rowKey, String checkpointId, String gcsj) {
        Map<String, Object> item = new HashMap<>();
        item.put("rowKey", rowKey);

        String hp = getString(r, "hp");
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

        return item;
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

    /**
     * 方向筛选过滤器 - 匹配数字或中文
     * 使用 OR 逻辑：fxlx = value1 OR fxlx = value2
     */
    private static FilterList directionFilter(String numValue, String cnValue) {
        FilterList orFilter = new FilterList(FilterList.Operator.MUST_PASS_ONE);
        orFilter.addFilter(eqFilter("fxlx", numValue));
        orFilter.addFilter(eqFilter("fxlx", cnValue));
        return orFilter;
    }

    private static String getString(Result r, String qualifier) {
        byte[] v = r.getValue(CF, Bytes.toBytes(qualifier));
        return v == null ? "" : Bytes.toString(v);
    }

    public record QueryResult(List<Map<String, Object>> list, String nextRowKey, boolean hasMoreHistory, long queryTimeMs, long total) {}
}
