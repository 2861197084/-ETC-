package com.etc.service;

import com.etc.common.CheckpointCatalog;
import com.etc.common.HBaseTimeUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

import static org.apache.hadoop.hbase.CompareOperator.*;

/**
 * HBase 通行记录查询服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HBasePassRecordService {

    private static final byte[] CF = Bytes.toBytes("d");
    private static final int SALT_COUNT = 10;
    private static final String REDIS_DAILY_PREFIX = "etc:hbase:daily:";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final Connection connection;
    private final StringRedisTemplate redisTemplate;
    
    // 并行扫描线程池
    private final ExecutorService scanExecutor = Executors.newFixedThreadPool(SALT_COUNT);

    @Value("${HBASE_TABLE:etc:pass_record}")
    private String tableName;

    // ==================== 原有查询方法（完全保持不变） ====================

    /**
     * 原有查询方法 - 带筛选条件时使用
     * 支持：车牌、卡口、方向、时间范围筛选
     */
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
        if (direction != null && !direction.isBlank()) {
            String d = direction.trim();
            if ("1".equals(d)) {
                filters.addFilter(directionFilter("1", "进城"));
            } else if ("2".equals(d)) {
                filters.addFilter(directionFilter("2", "出城"));
            } else {
                filters.addFilter(eqFilter("fxlx", d));
            }
        }
        if (startTime != null) {
            filters.addFilter(geFilter("gcsj", HBaseTimeUtils.getMinTimeString(startTime)));
        }
        if (endTime != null) {
            filters.addFilter(leFilter("gcsj", HBaseTimeUtils.getMaxTimeString(endTime)));
        }

        Scan scan = new Scan();
        scan.setCaching(Math.min(500, 200));
        scan.setFilter(filters);
        scan.addFamily(CF);
        
        boolean needCountTotal = (page == 1 && (lastRowKey == null || lastRowKey.isBlank()));
        
        if (lastRowKey != null && !lastRowKey.isBlank()) {
            scan.withStartRow(Bytes.toBytes(lastRowKey), false);
        }
        
        long totalCount = -1;

        try (Table table = connection.getTable(TableName.valueOf(tableName));
             ResultScanner scanner = table.getScanner(scan)) {
            
            if (needCountTotal) {
                for (Result r : scanner) {
                    if (r == null || r.isEmpty()) continue;

                    String gcsj = getString(r, "gcsj");
                    if (!HBaseTimeUtils.isInRange(gcsj, startTime, endTime)) {
                        continue;
                    }
                    
                    if (totalCount < 0) totalCount = 0;
                    totalCount++;
                    
                    if (list.size() < limit) {
                        String rowKey = Bytes.toString(r.getRow());
                        list.add(buildRecordMap(r, rowKey, checkpointId, gcsj));
                        nextRowKey = rowKey;
                    }
                }
            } else {
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
                    
                    if (count >= fetch) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("HBase query failed: " + e.getMessage(), e);
        }

        long ms = (System.nanoTime() - t0) / 1_000_000;
        boolean hasMoreHistory = (list.size() > limit) || (nextRowKey != null && list.size() == limit);

        return new QueryResult(list, nextRowKey, hasMoreHistory, ms, totalCount);
    }

    // ==================== 新增：并行扫描查询（仅用于纯时间范围查询） ====================

    /**
     * 并行扫描 + K路归并（仅用于"情况1：纯时间范围查询"）
     * 
     * 特点：
     * - 10 个 salt 分区并行扫描
     * - K 路归并按时间降序排序
     * - 分布式游标分页
     * - 从 Redis 获取总数（需提前运行 count_daily_to_redis.py）
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param cursorJson 游标 JSON（格式：{"0":"rowkey0", "5":"rowkey5", ...}）
     * @param size 每页大小
     * @return 查询结果
     */
    public QueryResult queryParallel(
            LocalDateTime startTime,
            LocalDateTime endTime,
            String cursorJson,
            int size) {
        
        long t0 = System.nanoTime();
        int limit = Math.max(1, size);
        int fetchPerPartition = limit * 2;
        
        Map<Integer, String> cursor = parseCursor(cursorJson);
        boolean isFirstPage = cursor.isEmpty();
        
        log.info("🚀 HBase 并行扫描: 10 分区, 时间 {} ~ {}, 首页={}", 
                startTime.format(DATE_FORMAT), endTime.format(DATE_FORMAT), isFirstPage);
        
        // 并行扫描 10 个分区
        List<CompletableFuture<SaltScanResult>> futures = new ArrayList<>();
        for (int salt = 0; salt < SALT_COUNT; salt++) {
            final int s = salt;
            String startRowKey = cursor.get(s);
            
            CompletableFuture<SaltScanResult> future = CompletableFuture.supplyAsync(
                    () -> scanSaltPartition(s, startTime, endTime, startRowKey, fetchPerPartition),
                    scanExecutor
            );
            futures.add(future);
        }
        
        List<SaltScanResult> partitionResults = futures.stream()
                .map(CompletableFuture::join)
                .toList();
        
        // K 路归并
        MergeResult mergeResult = kWayMerge(partitionResults, limit);
        
        String nextCursor = mergeResult.hasMore ? serializeCursor(mergeResult.cursor) : null;
        
        // 从 Redis 获取总数（仅首页）
        long totalCount = isFirstPage ? getTotalFromRedis(startTime, endTime) : -1;
        
        long ms = (System.nanoTime() - t0) / 1_000_000;
        log.info("✅ 并行扫描完成: {} 条, hasMore={}, 总数={}, 耗时 {} ms", 
                mergeResult.data.size(), mergeResult.hasMore, totalCount, ms);
        
        return new QueryResult(mergeResult.data, nextCursor, mergeResult.hasMore, ms, totalCount);
    }

    /**
     * 扫描单个 salt 分区（反向扫描，最新时间优先）
     * 
     * RowKey 设计: {salt(1)}{yyyyMMdd(8)}{checkpoint_hash(8)}{reverse_ts(13)}{plate_hash(4)}
     * 其中 reverse_ts = Long.MAX_VALUE - timestamp，时间越新 reverse_ts 越小
     * 
     * 使用 Reversed Scan：
     * - startRow 设为 endDate + "~"（大端，扫描起点）
     * - stopRow 设为 startDate（小端，扫描终点）
     * - 这样从大到小扫描，先获取 endDate 的数据
     */
    private SaltScanResult scanSaltPartition(
            int salt,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String startRowKey,
            int fetchLimit) {
        
        List<Map<String, Object>> data = new ArrayList<>();
        String lastRowKey = null;
        
        try (Table table = connection.getTable(TableName.valueOf(tableName))) {
            Scan scan = new Scan();
            scan.setCaching(500);
            scan.addFamily(CF);
            scan.setReversed(true);  // ⭐ 关键：启用反向扫描
            
            String startDate = startTime.format(DATE_FORMAT);
            String endDate = endTime.format(DATE_FORMAT);
            
            // 反向扫描：startRow > stopRow
            // startRow: 从 endDate 最后一条开始（包含）
            // stopRow: 到 startDate 第一条结束（不包含）
            if (startRowKey != null && !startRowKey.isBlank()) {
                // 翻页时，从游标位置继续（不包含游标本身）
                scan.withStartRow(Bytes.toBytes(startRowKey), false);
            } else {
                // 首页：从 endDate 末尾开始
                scan.withStartRow(Bytes.toBytes(salt + endDate + "~"));
            }
            // 反向扫描的 stopRow 需要小于 startDate
            scan.withStopRow(Bytes.toBytes(String.valueOf(salt) + startDate));
            
            FilterList filters = new FilterList(FilterList.Operator.MUST_PASS_ALL);
            filters.addFilter(geFilter("gcsj", HBaseTimeUtils.getMinTimeString(startTime)));
            filters.addFilter(leFilter("gcsj", HBaseTimeUtils.getMaxTimeString(endTime)));
            scan.setFilter(filters);
            
            try (ResultScanner scanner = table.getScanner(scan)) {
                for (Result r : scanner) {
                    if (r == null || r.isEmpty()) continue;
                    
                    String gcsj = getString(r, "gcsj");
                    if (!HBaseTimeUtils.isInRange(gcsj, startTime, endTime)) {
                        continue;
                    }
                    
                    String rowKey = Bytes.toString(r.getRow());
                    data.add(buildRecordMap(r, rowKey, null, gcsj));
                    lastRowKey = rowKey;
                    
                    if (data.size() >= fetchLimit) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.error("扫描分区 {} 失败: {}", salt, e.getMessage());
        }
        
        return new SaltScanResult(salt, data, lastRowKey);
    }

    /**
     * K 路归并（按 gcsj 时间降序）
     */
    private MergeResult kWayMerge(List<SaltScanResult> partitions, int limit) {
        List<Map<String, Object>> result = new ArrayList<>();
        Map<Integer, String> newCursor = new HashMap<>();
        
        List<List<Map<String, Object>>> lists = new ArrayList<>();
        for (int i = 0; i < SALT_COUNT; i++) {
            lists.add(new ArrayList<>());
        }
        for (SaltScanResult r : partitions) {
            lists.set(r.salt, r.data);
            if (r.lastRowKey != null) {
                newCursor.put(r.salt, r.lastRowKey);
            }
        }
        
        // 优先队列：按 gcsj 降序（时间最新的优先）
        PriorityQueue<SaltEntry> pq = new PriorityQueue<>((a, b) -> b.gcsj.compareTo(a.gcsj));
        
        // 初始化：每个分区放入第一条
        for (int salt = 0; salt < SALT_COUNT; salt++) {
            if (!lists.get(salt).isEmpty()) {
                Map<String, Object> record = lists.get(salt).get(0);
                String gcsj = (String) record.get("passTime");
                pq.offer(new SaltEntry(salt, 0, gcsj, record));
            }
        }
        
        // 归并
        while (!pq.isEmpty() && result.size() < limit) {
            SaltEntry entry = pq.poll();
            result.add(entry.record);
            
            String rowKey = (String) entry.record.get("rowKey");
            newCursor.put(entry.salt, rowKey);
            
            int nextIdx = entry.index + 1;
            if (nextIdx < lists.get(entry.salt).size()) {
                Map<String, Object> nextRecord = lists.get(entry.salt).get(nextIdx);
                String nextGcsj = (String) nextRecord.get("passTime");
                pq.offer(new SaltEntry(entry.salt, nextIdx, nextGcsj, nextRecord));
            }
        }
        
        boolean hasMore = !pq.isEmpty();
        if (!hasMore) {
            for (SaltScanResult r : partitions) {
                if (r.data.size() >= limit * 2) {
                    hasMore = true;
                    break;
                }
            }
        }
        
        return new MergeResult(result, newCursor, hasMore);
    }

    /**
     * 从 Redis 获取时间范围内的总量
     */
    private long getTotalFromRedis(LocalDateTime startTime, LocalDateTime endTime) {
        long total = 0;
        LocalDate current = startTime.toLocalDate();
        LocalDate end = endTime.toLocalDate();
        
        List<String> keys = new ArrayList<>();
        while (!current.isAfter(end)) {
            keys.add(REDIS_DAILY_PREFIX + current.format(DATE_FORMAT));
            current = current.plusDays(1);
        }
        
        try {
            List<String> values = redisTemplate.opsForValue().multiGet(keys);
            if (values != null) {
                for (String v : values) {
                    if (v != null) {
                        total += Long.parseLong(v);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("从 Redis 获取总数失败: {}", e.getMessage());
            return -1;
        }
        
        return total;
    }

    private Map<Integer, String> parseCursor(String cursorJson) {
        if (cursorJson == null || cursorJson.isBlank()) {
            return new HashMap<>();
        }
        try {
            return MAPPER.readValue(cursorJson, new TypeReference<Map<Integer, String>>() {});
        } catch (Exception e) {
            log.warn("游标解析失败: {}", cursorJson);
            return new HashMap<>();
        }
    }

    private String serializeCursor(Map<Integer, String> cursor) {
        try {
            return MAPPER.writeValueAsString(cursor);
        } catch (Exception e) {
            return "{}";
        }
    }

    // ==================== 内部记录类 ====================
    
    private record SaltScanResult(int salt, List<Map<String, Object>> data, String lastRowKey) {}
    private record MergeResult(List<Map<String, Object>> data, Map<Integer, String> cursor, boolean hasMore) {}
    private record SaltEntry(int salt, int index, String gcsj, Map<String, Object> record) {}

    // ==================== 工具方法 ====================

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
                CF, Bytes.toBytes(qualifier), EQUAL,
                new BinaryComparator(value.getBytes(StandardCharsets.UTF_8)));
        f.setFilterIfMissing(true);
        return f;
    }

    private static SingleColumnValueFilter geFilter(String qualifier, String value) {
        SingleColumnValueFilter f = new SingleColumnValueFilter(
                CF, Bytes.toBytes(qualifier), GREATER_OR_EQUAL,
                new BinaryComparator(value.getBytes(StandardCharsets.UTF_8)));
        f.setFilterIfMissing(true);
        return f;
    }

    private static SingleColumnValueFilter leFilter(String qualifier, String value) {
        SingleColumnValueFilter f = new SingleColumnValueFilter(
                CF, Bytes.toBytes(qualifier), LESS_OR_EQUAL,
                new BinaryComparator(value.getBytes(StandardCharsets.UTF_8)));
        f.setFilterIfMissing(true);
        return f;
    }

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
