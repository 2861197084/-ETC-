package com.etc.platform.service;

import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.filter.FilterList;
import org.apache.hadoop.hbase.filter.PrefixFilter;
import org.apache.hadoop.hbase.util.Bytes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

@Service
public class HBaseService {

    @Autowired(required = false)
    private Connection connection;

    // Table & column family names - adjust to your HBase schema
    private static final TableName TABLE = TableName.valueOf("etc:records");
    private static final byte[] CF = Bytes.toBytes("cf");

    // RowKey design suggestion: yyyyMMdd|province|gantryId|ts|vehicleId

    public long getTotalCount() {
        if (connection == null) {
            // HBase 未连接，返回空数据而不是抛异常
            return 0L;
        }
        try (Table table = connection.getTable(TABLE)) {
            Scan scan = new Scan();
            // Only count rows - use small caching to avoid memory spike
            scan.setCaching(1000);
            ResultScanner scanner = table.getScanner(scan);
            long count = 0;
            Iterator<Result> it = scanner.iterator();
            while (it.hasNext()) {
                it.next();
                count++;
            }
            scanner.close();
            return count;
        } catch (IOException e) {
            return 0L;
        }
    }

    public List<Map<String, Object>> getProvinceRank(int limit) {
        if (connection == null) {
            return Collections.emptyList();
        }
        Map<String, LongAdder> counter = new HashMap<>();
        try (Table table = connection.getTable(TABLE)) {
            Scan scan = new Scan();
            scan.addColumn(CF, Bytes.toBytes("province"));
            scan.setCaching(1000);
            try (ResultScanner scanner = table.getScanner(scan)) {
                for (Result r : scanner) {
                    String province = Bytes.toString(r.getValue(CF, Bytes.toBytes("province")));
                    if (province == null) continue;
                    counter.computeIfAbsent(province, k -> new LongAdder()).increment();
                }
            }
        } catch (IOException ignored) {
        }
        return counter.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().longValue(), a.getValue().longValue()))
                .limit(limit)
                .map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("province", e.getKey());
                    m.put("count", e.getValue().longValue());
                    return m;
                })
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getGantryRank(int limit) {
        if (connection == null) {
            return Collections.emptyList();
        }
        Map<String, LongAdder> counter = new HashMap<>();
        try (Table table = connection.getTable(TABLE)) {
            Scan scan = new Scan();
            scan.addColumn(CF, Bytes.toBytes("gantry_id"));
            scan.setCaching(1000);
            try (ResultScanner scanner = table.getScanner(scan)) {
                for (Result r : scanner) {
                    String gantry = Bytes.toString(r.getValue(CF, Bytes.toBytes("gantry_id")));
                    if (gantry == null) continue;
                    counter.computeIfAbsent(gantry, k -> new LongAdder()).increment();
                }
            }
        } catch (IOException ignored) {
        }
        return counter.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().longValue(), a.getValue().longValue()))
                .limit(limit)
                .map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("gantryId", e.getKey());
                    m.put("count", e.getValue().longValue());
                    return m;
                })
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getHeatmap() {
        if (connection == null) {
            return Collections.emptyList();
        }
        // Here we reuse province aggregation as a simple heatmap source
        return getProvinceRank(Integer.MAX_VALUE).stream().map(m -> {
            Map<String, Object> r = new HashMap<>();
            r.put("name", m.get("province"));
            r.put("value", m.get("count"));
            return r;
        }).collect(Collectors.toList());
    }

    /**
     * 拥堵预测：当前先返回模拟数据，方便前端联调
     * GET /api/stats/predict/congestion
     */
    public List<Map<String, Object>> predictCongestion() {
        List<Map<String, Object>> list = new ArrayList<>();

        Map<String, Object> item1 = new HashMap<>();
        item1.put("gantryId", "G001");
        item1.put("level", "HIGH");
        item1.put("predictTime", System.currentTimeMillis() + 5 * 60_000);
        item1.put("desc", "G001 预计 5 分钟后出现严重拥堵");
        list.add(item1);

        Map<String, Object> item2 = new HashMap<>();
        item2.put("gantryId", "G002");
        item2.put("level", "MEDIUM");
        item2.put("predictTime", System.currentTimeMillis() + 10 * 60_000);
        item2.put("desc", "G002 预计 10 分钟后出现中度拥堵");
        list.add(item2);

        return list;
    }

    public Map<String, Object> pageQuery(Map<String, Object> params) {
        if (connection == null) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("total", 0L);
            empty.put("records", Collections.emptyList());
            return empty;
        }
        long current = Long.parseLong(params.getOrDefault("current", "1").toString());
        long size = Long.parseLong(params.getOrDefault("size", "10").toString());
        long offset = (current - 1) * size;

        String plate = params.getOrDefault("plate", "").toString();
        String gantryId = params.getOrDefault("gantryId", "").toString();
        String startTime = params.getOrDefault("startTime", "").toString();
        String endTime = params.getOrDefault("endTime", "").toString();

        List<Map<String, Object>> rows = new ArrayList<>();
        long total = 0;
        try (Table table = connection.getTable(TABLE)) {
            Scan scan = new Scan();
            scan.addFamily(CF);
            scan.setCaching(1000);

            // Example: if gantryId is provided, use PrefixFilter on RowKey if RowKey encodes gantry
            FilterList filters = new FilterList();
            if (!gantryId.isEmpty()) {
                // Assuming RowKey contains gantryId segment; adjust prefix if your rowkey design differs
                filters.addFilter(new PrefixFilter(Bytes.toBytes(gantryId)));
            }
            if (!filters.getFilters().isEmpty()) {
                scan.setFilter(filters);
            }

            try (ResultScanner scanner = table.getScanner(scan)) {
                long idx = 0;
                for (Result r : scanner) {
                    // Basic field extraction
                    String recPlate = Bytes.toString(r.getValue(CF, Bytes.toBytes("plate")));
                    String recGantry = Bytes.toString(r.getValue(CF, Bytes.toBytes("gantry_id")));
                    String recProvince = Bytes.toString(r.getValue(CF, Bytes.toBytes("province")));
                    String recPassTime = Bytes.toString(r.getValue(CF, Bytes.toBytes("pass_time")));

                    // simple time filter if provided
                    if (!startTime.isEmpty() && recPassTime != null && recPassTime.compareTo(startTime) < 0) continue;
                    if (!endTime.isEmpty() && recPassTime != null && recPassTime.compareTo(endTime) > 0) continue;
                    if (!plate.isEmpty() && (recPlate == null || !recPlate.contains(plate))) continue;

                    total++;
                    if (idx >= offset && rows.size() < size) {
                        Map<String, Object> item = new HashMap<>();
                        item.put("plate", recPlate);
                        item.put("gantryId", recGantry);
                        item.put("province", recProvince);
                        item.put("passTime", recPassTime);
                        rows.add(item);
                    }
                    idx++;
                }
            }
        } catch (IOException ignored) {
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("records", rows);
        return result;
    }
}
