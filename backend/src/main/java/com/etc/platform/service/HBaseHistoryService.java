package com.etc.platform.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.hbase.CompareOperator;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.filter.*;
import org.apache.hadoop.hbase.util.Bytes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * HBase 历史数据查询服务
 * 
 * 支持渐进式加载：
 * 1. 首次查询返回 MySQL 7天热数据
 * 2. 用户点击"加载更多"时查询 HBase 历史数据
 * 
 * RowKey 设计: {salt}_{date}_{cpId}_{reverseTs}
 * - salt: 0-15 的前缀，用于负载均衡
 * - date: yyyyMMdd
 * - cpId: 卡口ID
 * - reverseTs: Long.MAX_VALUE - timestamp，实现时间倒序
 */
@Slf4j
@Service
public class HBaseHistoryService {

    @Autowired(required = false)
    private Connection connection;

    private static final TableName TABLE_RECORDS = TableName.valueOf("etc:pass_records");
    private static final byte[] CF = Bytes.toBytes("d");

    // 列名
    private static final byte[] COL_PLATE = Bytes.toBytes("plate");
    private static final byte[] COL_CP_ID = Bytes.toBytes("cp_id");
    private static final byte[] COL_CP_NAME = Bytes.toBytes("cp_name");
    private static final byte[] COL_PASS_TIME = Bytes.toBytes("pass_time");
    private static final byte[] COL_DIRECTION = Bytes.toBytes("direction");
    private static final byte[] COL_SPEED = Bytes.toBytes("speed");
    private static final byte[] COL_LANE = Bytes.toBytes("lane");
    private static final byte[] COL_VEHICLE_TYPE = Bytes.toBytes("vehicle_type");
    private static final byte[] COL_IMAGE_URL = Bytes.toBytes("image_url");
    private static final byte[] COL_ETC_DEDUCTION = Bytes.toBytes("etc_deduction");

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 按车牌查询历史记录
     * 
     * @param plateNumber 车牌号
     * @param startRowKey 起始 RowKey（用于分页，为空则从头开始）
     * @param limit 返回条数
     * @return 记录列表和下一页的 RowKey
     */
    public HistoryQueryResult queryByPlate(String plateNumber, String startRowKey, int limit) {
        if (connection == null) {
            log.warn("HBase 连接不可用");
            return HistoryQueryResult.empty();
        }

        List<Map<String, Object>> records = new ArrayList<>();
        String nextRowKey = null;

        try (Table table = connection.getTable(TABLE_RECORDS)) {
            Scan scan = new Scan();
            scan.addFamily(CF);
            scan.setCaching(Math.min(limit + 1, 500));

            // 设置起始 RowKey
            if (startRowKey != null && !startRowKey.isEmpty()) {
                scan.withStartRow(Bytes.toBytes(startRowKey), false); // exclusive
            }

            // 使用 SingleColumnValueFilter 过滤车牌
            SingleColumnValueFilter plateFilter = new SingleColumnValueFilter(
                CF, COL_PLATE, CompareOperator.EQUAL, 
                new SubstringComparator(plateNumber)
            );
            plateFilter.setFilterIfMissing(true);
            scan.setFilter(plateFilter);

            // 限制返回行数
            scan.setLimit(limit + 1);

            try (ResultScanner scanner = table.getScanner(scan)) {
                int count = 0;
                for (Result result : scanner) {
                    if (count >= limit) {
                        // 有更多数据
                        nextRowKey = Bytes.toString(result.getRow());
                        break;
                    }
                    records.add(resultToMap(result));
                    count++;
                }
            }
        } catch (IOException e) {
            log.error("HBase 查询失败", e);
        }

        return new HistoryQueryResult(records, nextRowKey);
    }

    /**
     * 按卡口查询历史记录
     */
    public HistoryQueryResult queryByCheckpoint(String cpId, String startRowKey, int limit) {
        if (connection == null) {
            return HistoryQueryResult.empty();
        }

        List<Map<String, Object>> records = new ArrayList<>();
        String nextRowKey = null;

        try (Table table = connection.getTable(TABLE_RECORDS)) {
            Scan scan = new Scan();
            scan.addFamily(CF);
            scan.setCaching(Math.min(limit + 1, 500));

            if (startRowKey != null && !startRowKey.isEmpty()) {
                scan.withStartRow(Bytes.toBytes(startRowKey), false);
            }

            // 按卡口ID过滤
            SingleColumnValueFilter cpFilter = new SingleColumnValueFilter(
                CF, COL_CP_ID, CompareOperator.EQUAL,
                Bytes.toBytes(cpId)
            );
            cpFilter.setFilterIfMissing(true);
            scan.setFilter(cpFilter);
            scan.setLimit(limit + 1);

            try (ResultScanner scanner = table.getScanner(scan)) {
                int count = 0;
                for (Result result : scanner) {
                    if (count >= limit) {
                        nextRowKey = Bytes.toString(result.getRow());
                        break;
                    }
                    records.add(resultToMap(result));
                    count++;
                }
            }
        } catch (IOException e) {
            log.error("HBase 查询失败", e);
        }

        return new HistoryQueryResult(records, nextRowKey);
    }

    /**
     * 按时间范围查询历史记录
     */
    public HistoryQueryResult queryByTimeRange(
            LocalDateTime startTime, LocalDateTime endTime, 
            String startRowKey, int limit) {
        
        if (connection == null) {
            return HistoryQueryResult.empty();
        }

        List<Map<String, Object>> records = new ArrayList<>();
        String nextRowKey = null;

        try (Table table = connection.getTable(TABLE_RECORDS)) {
            Scan scan = new Scan();
            scan.addFamily(CF);
            scan.setCaching(Math.min(limit + 1, 500));

            // 利用 RowKey 中的日期部分进行范围扫描
            if (startTime != null) {
                // RowKey 前缀: {salt}_{startDate}
                // 由于 salt 是 0-15，需要扫描所有 salt
                String startDate = startTime.format(DATE_FMT);
                scan.withStartRow(Bytes.toBytes("0_" + startDate));
            }
            if (endTime != null) {
                String endDate = endTime.format(DATE_FMT);
                scan.withStopRow(Bytes.toBytes("g_" + endDate)); // g > f (15的16进制)
            }

            if (startRowKey != null && !startRowKey.isEmpty()) {
                scan.withStartRow(Bytes.toBytes(startRowKey), false);
            }

            scan.setLimit(limit + 1);

            try (ResultScanner scanner = table.getScanner(scan)) {
                int count = 0;
                for (Result result : scanner) {
                    if (count >= limit) {
                        nextRowKey = Bytes.toString(result.getRow());
                        break;
                    }
                    
                    // 验证时间范围
                    String passTimeStr = Bytes.toString(result.getValue(CF, COL_PASS_TIME));
                    if (passTimeStr != null) {
                        try {
                            LocalDateTime passTime = LocalDateTime.parse(passTimeStr, DT_FMT);
                            if (startTime != null && passTime.isBefore(startTime)) continue;
                            if (endTime != null && passTime.isAfter(endTime)) continue;
                        } catch (Exception ignored) {}
                    }
                    
                    records.add(resultToMap(result));
                    count++;
                }
            }
        } catch (IOException e) {
            log.error("HBase 查询失败", e);
        }

        return new HistoryQueryResult(records, nextRowKey);
    }

    /**
     * 综合查询（支持多条件组合）
     */
    public HistoryQueryResult query(
            String plateNumber, String cpId,
            LocalDateTime startTime, LocalDateTime endTime,
            String startRowKey, int limit) {
        
        if (connection == null) {
            return HistoryQueryResult.empty();
        }

        List<Map<String, Object>> records = new ArrayList<>();
        String nextRowKey = null;

        try (Table table = connection.getTable(TABLE_RECORDS)) {
            Scan scan = new Scan();
            scan.addFamily(CF);
            scan.setCaching(Math.min(limit + 1, 500));

            // 构建过滤器列表
            FilterList filters = new FilterList(FilterList.Operator.MUST_PASS_ALL);

            if (plateNumber != null && !plateNumber.isEmpty()) {
                SingleColumnValueFilter f = new SingleColumnValueFilter(
                    CF, COL_PLATE, CompareOperator.EQUAL,
                    new SubstringComparator(plateNumber)
                );
                f.setFilterIfMissing(true);
                filters.addFilter(f);
            }

            if (cpId != null && !cpId.isEmpty()) {
                SingleColumnValueFilter f = new SingleColumnValueFilter(
                    CF, COL_CP_ID, CompareOperator.EQUAL,
                    Bytes.toBytes(cpId)
                );
                f.setFilterIfMissing(true);
                filters.addFilter(f);
            }

            if (!filters.getFilters().isEmpty()) {
                scan.setFilter(filters);
            }

            if (startRowKey != null && !startRowKey.isEmpty()) {
                scan.withStartRow(Bytes.toBytes(startRowKey), false);
            }

            scan.setLimit(limit + 1);

            try (ResultScanner scanner = table.getScanner(scan)) {
                int count = 0;
                for (Result result : scanner) {
                    if (count >= limit) {
                        nextRowKey = Bytes.toString(result.getRow());
                        break;
                    }

                    // 时间范围过滤
                    if (startTime != null || endTime != null) {
                        String passTimeStr = Bytes.toString(result.getValue(CF, COL_PASS_TIME));
                        if (passTimeStr != null) {
                            try {
                                LocalDateTime passTime = LocalDateTime.parse(passTimeStr, DT_FMT);
                                if (startTime != null && passTime.isBefore(startTime)) continue;
                                if (endTime != null && passTime.isAfter(endTime)) continue;
                            } catch (Exception ignored) {}
                        }
                    }

                    records.add(resultToMap(result));
                    count++;
                }
            }
        } catch (IOException e) {
            log.error("HBase 查询失败", e);
        }

        return new HistoryQueryResult(records, nextRowKey);
    }

    /**
     * 将 HBase Result 转换为 Map
     */
    private Map<String, Object> resultToMap(Result result) {
        Map<String, Object> map = new HashMap<>();
        map.put("rowKey", Bytes.toString(result.getRow()));
        map.put("plateNumber", Bytes.toString(result.getValue(CF, COL_PLATE)));
        map.put("checkpointId", Bytes.toString(result.getValue(CF, COL_CP_ID)));
        map.put("checkpointName", Bytes.toString(result.getValue(CF, COL_CP_NAME)));
        map.put("passTime", Bytes.toString(result.getValue(CF, COL_PASS_TIME)));
        map.put("direction", Bytes.toString(result.getValue(CF, COL_DIRECTION)));
        
        byte[] speedBytes = result.getValue(CF, COL_SPEED);
        if (speedBytes != null) {
            try {
                map.put("speed", Bytes.toDouble(speedBytes));
            } catch (Exception e) {
                map.put("speed", Bytes.toString(speedBytes));
            }
        }
        
        map.put("laneNo", Bytes.toString(result.getValue(CF, COL_LANE)));
        map.put("vehicleType", Bytes.toString(result.getValue(CF, COL_VEHICLE_TYPE)));
        map.put("imageUrl", Bytes.toString(result.getValue(CF, COL_IMAGE_URL)));
        
        byte[] deductionBytes = result.getValue(CF, COL_ETC_DEDUCTION);
        if (deductionBytes != null) {
            try {
                map.put("etcDeduction", Bytes.toBigDecimal(deductionBytes));
            } catch (Exception e) {
                map.put("etcDeduction", Bytes.toString(deductionBytes));
            }
        }
        
        map.put("source", "hbase");
        return map;
    }

    /**
     * 检查 HBase 连接状态
     */
    public boolean isConnected() {
        return connection != null && !connection.isClosed();
    }

    /**
     * 历史查询结果封装
     */
    public record HistoryQueryResult(List<Map<String, Object>> records, String nextRowKey) {
        public static HistoryQueryResult empty() {
            return new HistoryQueryResult(Collections.emptyList(), null);
        }
        
        public boolean hasMore() {
            return nextRowKey != null && !nextRowKey.isEmpty();
        }
    }
}
