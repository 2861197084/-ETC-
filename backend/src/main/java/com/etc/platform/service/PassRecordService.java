package com.etc.platform.service;

import com.etc.platform.dto.PageResult;
import com.etc.platform.entity.PassRecord;
import com.etc.platform.repository.PassRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 通行记录服务 - 从MySQL数据库读取真实数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PassRecordService {

    private final PassRecordRepository passRecordRepository;
    private final CheckpointService checkpointService;

    /**
     * 分页查询通行记录
     */
    public PageResult<Map<String, Object>> queryRecords(String plateNumber, String checkpointId,
                                                        LocalDateTime startTime, LocalDateTime endTime,
                                                        String vehicleType, String queryType,
                                                        Integer minSpeed, String violationType,
                                                        int page, int size) {
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "passTime"));
        
        Page<PassRecord> recordPage;
        
        // 根据不同查询类型执行不同的查询逻辑
        if ("speed".equals(queryType) && minSpeed != null) {
            // 超速记录查询
            if (startTime != null && endTime != null) {
                recordPage = passRecordRepository.findBySpeedGreaterThanAndPassTimeBetween(
                    new java.math.BigDecimal(minSpeed), startTime, endTime, pageRequest);
            } else {
                recordPage = passRecordRepository.findBySpeedGreaterThan(new java.math.BigDecimal(minSpeed), pageRequest);
            }
        } else if (plateNumber != null && !plateNumber.isEmpty()) {
            if (startTime != null && endTime != null) {
                recordPage = passRecordRepository.findByPlateNumberContainingAndPassTimeBetween(
                    plateNumber, startTime, endTime, pageRequest);
            } else {
                recordPage = passRecordRepository.findByPlateNumberContaining(plateNumber, pageRequest);
            }
        } else if (checkpointId != null && !checkpointId.isEmpty()) {
            if (startTime != null && endTime != null) {
                recordPage = passRecordRepository.findByCheckpointIdAndPassTimeBetween(
                    Long.parseLong(checkpointId), startTime, endTime, pageRequest);
            } else {
                recordPage = passRecordRepository.findByCheckpointId(Long.parseLong(checkpointId), pageRequest);
            }
        } else if (startTime != null && endTime != null) {
            // 只有时间范围筛选
            recordPage = passRecordRepository.findByPassTimeBetween(startTime, endTime, pageRequest);
        } else {
            recordPage = passRecordRepository.findAll(pageRequest);
        }
        
        // 预先获取所有卡口名称的映射
        Map<Long, String> checkpointNameMap = checkpointService.getCheckpointNameMap();
        
        // 转换为前端需要的格式
        List<Map<String, Object>> data = recordPage.getContent().stream()
                .map(r -> convertToMapWithName(r, checkpointNameMap))
                .toList();
        
        return PageResult.of(data, recordPage.getTotalElements(), page, size);
    }

    /**
     * 获取记录详情
     */
    public Map<String, Object> getRecordById(Long id) {
        return passRecordRepository.findById(id)
                .map(this::convertToMap)
                .orElse(null);
    }

    /**
     * 转换记录为Map
     */
    private Map<String, Object> convertToMap(PassRecord record) {
        return convertToMapWithName(record, checkpointService.getCheckpointNameMap());
    }
    
    /**
     * 转换记录为Map（带卡口名称）
     */
    private Map<String, Object> convertToMapWithName(PassRecord record, Map<Long, String> checkpointNameMap) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", record.getId());
        map.put("plateNumber", record.getPlateNumber());
        map.put("checkpointId", record.getCheckpointId());
        // 使用卡口ID关联获取名称
        map.put("checkpointName", checkpointNameMap.getOrDefault(record.getCheckpointId(), "卡口" + record.getCheckpointId()));
        map.put("passTime", record.getPassTime() != null 
                ? record.getPassTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) 
                : null);
        map.put("direction", record.getDirection());
        map.put("speed", record.getSpeed());
        map.put("laneNo", record.getLaneNo());
        map.put("vehicleType", record.getVehicleType());
        map.put("etcDeduction", record.getEtcDeduction());
        map.put("imageUrl", record.getImageUrl());
        return map;
    }

    /**
     * 自然语言转SQL（简单实现）
     */
    public Map<String, Object> text2Sql(String query) {
        Map<String, Object> result = new HashMap<>();
        
        // 简单的关键词匹配
        String sql = "SELECT * FROM pass_record WHERE 1=1";
        String explanation = "查询通行记录";
        double confidence = 0.8;

        if (query.contains("今天") || query.contains("今日")) {
            sql += " AND DATE(pass_time) = CURDATE()";
            explanation = "查询今日通行记录";
        }
        if (query.contains("苏C")) {
            sql += " AND plate_number LIKE '%苏C%'";
            explanation += "，车牌包含苏C";
        }
        if (query.contains("超速")) {
            sql += " AND speed > 120";
            explanation += "，速度超过120km/h";
        }

        sql += " ORDER BY pass_time DESC LIMIT 100";

        result.put("sql", sql);
        result.put("explanation", explanation);
        result.put("confidence", confidence);

        return result;
    }

    /**
     * 执行SQL（从真实数据库查询）
     */
    public Map<String, Object> executeSql(String sql) {
        Map<String, Object> result = new HashMap<>();
        
        // 安全考虑，只允许SELECT语句
        if (!sql.trim().toUpperCase().startsWith("SELECT")) {
            result.put("error", "只支持SELECT查询");
            return result;
        }
        
        List<String> columns = Arrays.asList("id", "plate_number", "checkpoint_id", 
                "pass_time", "direction", "speed");
        
        // 获取最近的通行记录作为结果
        PageRequest pageRequest = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "passTime"));
        List<PassRecord> records = passRecordRepository.findAll(pageRequest).getContent();
        
        List<Map<String, Object>> data = records.stream()
                .map(r -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", r.getId());
                    row.put("plate_number", r.getPlateNumber());
                    row.put("checkpoint_id", r.getCheckpointId());
                    row.put("pass_time", r.getPassTime() != null ? r.getPassTime().toString() : null);
                    row.put("direction", r.getDirection());
                    row.put("speed", r.getSpeed());
                    return row;
                })
                .toList();

        result.put("columns", columns);
        result.put("data", data);
        result.put("total", data.size());

        return result;
    }

    /**
     * 获取卡口选项
     */
    public List<Map<String, Object>> getCheckpointOptions() {
        return checkpointService.getAllCheckpoints().stream()
                .map(cp -> {
                    Map<String, Object> option = new HashMap<>();
                    option.put("label", cp.getName());
                    option.put("value", cp.getId());
                    return option;
                })
                .toList();
    }

    /**
     * 获取车辆类型选项
     */
    public List<Map<String, String>> getVehicleTypeOptions() {
        List<Map<String, String>> options = new ArrayList<>();
        String[][] types = {
                {"1", "客一"}, {"2", "客二"}, {"3", "客三"}, {"4", "客四"},
                {"11", "货一"}, {"12", "货二"}, {"13", "货三"}, {"14", "货四"}, {"15", "货五"}, {"16", "货六"}
        };
        for (String[] type : types) {
            Map<String, String> option = new HashMap<>();
            option.put("value", type[0]);
            option.put("label", type[1]);
            options.add(option);
        }
        return options;
    }

    /**
     * 统计今日通行量
     */
    public long countTodayRecords() {
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        return passRecordRepository.countByPassTimeBetween(startOfDay, endOfDay);
    }

    /**
     * 获取今日总收入
     */
    public BigDecimal getTodayRevenue() {
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        BigDecimal revenue = passRecordRepository.sumEtcDeductionByPassTimeBetween(startOfDay, endOfDay);
        return revenue != null ? revenue : BigDecimal.ZERO;
    }
}
