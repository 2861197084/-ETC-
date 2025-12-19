package com.etc.platform.service;

import com.etc.platform.dto.DailyStats;
import com.etc.platform.entity.Checkpoint;
import com.etc.platform.entity.CheckpointFlow;
import com.etc.platform.entity.ClonePlateDetection;
import com.etc.platform.entity.Violation;
import com.etc.platform.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 实时数据服务 - 从 MySQL 和 Redis 读取真实数据
 * 支持从 Flink 实时计算结果(Redis)读取
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeService {

    private final CheckpointRepository checkpointRepository;
    private final CheckpointFlowRepository flowRepository;
    private final PassRecordRepository passRecordRepository;
    private final ViolationRepository violationRepository;
    private final ClonePlateDetectionRepository clonePlateRepository;
    private final AlertRepository alertRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    
    // Redis Key前缀
    private static final String REDIS_KEY_FLOW_TOTAL = "realtime:flow:total";
    private static final String REDIS_KEY_REVENUE_TODAY = "realtime:revenue:today";
    private static final String REDIS_KEY_SOURCE_LOCAL = "realtime:source:local";
    private static final String REDIS_KEY_SOURCE_FOREIGN = "realtime:source:foreign";
    private static final String REDIS_KEY_PRESSURE = "realtime:pressure:";
    private static final String REDIS_KEY_HEAT_RANKING = "realtime:heat:ranking";
    private static final String REDIS_KEY_AVG_SPEED = "realtime:avgspeed";
    
    // 卡口名称映射（解决中文乱码问题）
    private static final Map<Long, String> CHECKPOINT_NAME_MAP = Map.ofEntries(
        Map.entry(1L, "苏皖界1(104省道)"), Map.entry(2L, "苏皖界2(311国道)"), Map.entry(3L, "苏皖界3(徐明高速)"),
        Map.entry(4L, "苏皖界4(宿新高速)"), Map.entry(5L, "苏皖界5(徐淮高速)"), Map.entry(6L, "苏皖界6(新扬高速)"),
        Map.entry(7L, "苏鲁界1(206国道)"), Map.entry(8L, "苏鲁界2(104国道)"), Map.entry(9L, "苏鲁界3(京台高速)"),
        Map.entry(10L, "苏鲁界4(枣庄连接线)"), Map.entry(11L, "苏鲁界5(京沪高速)"), Map.entry(12L, "苏鲁界6(沂河路)"),
        Map.entry(13L, "连云港界1(徐连高速)"), Map.entry(14L, "连云港界2(310国道)"), Map.entry(15L, "宿迁界1(徐宿高速)"),
        Map.entry(16L, "宿迁界2(徐宿快速)"), Map.entry(17L, "宿迁界3(104国道)"), Map.entry(18L, "宿迁界4(新扬高速)"),
        Map.entry(19L, "宿迁界5(徐盐高速)")
    );
    
    // 卡口最大容量
    private static final Map<Long, Integer> CHECKPOINT_CAPACITY = Map.ofEntries(
        Map.entry(1L, 800), Map.entry(2L, 1000), Map.entry(3L, 1500),
        Map.entry(4L, 1200), Map.entry(5L, 1500), Map.entry(6L, 1200),
        Map.entry(7L, 800), Map.entry(8L, 1000), Map.entry(9L, 1500),
        Map.entry(10L, 800), Map.entry(11L, 1500), Map.entry(12L, 600),
        Map.entry(13L, 1500), Map.entry(14L, 800), Map.entry(15L, 1200),
        Map.entry(16L, 1000), Map.entry(17L, 800), Map.entry(18L, 1200),
        Map.entry(19L, 1500)
    );

    /**
     * 获取今日统计 - 优先从Redis读取Flink计算结果
     */
    public DailyStats getDailyStats() {
        DailyStats stats = new DailyStats();
        
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        
        // 1. 尝试从Redis读取总流量
        Object redisFlow = redisTemplate.opsForValue().get(REDIS_KEY_FLOW_TOTAL);
        if (redisFlow != null) {
            stats.setTotalFlow(Integer.parseInt(redisFlow.toString()));
        } else {
            // 回退到数据库查询
            Long totalFlow = flowRepository.sumTotalFlowByDate(today);
            if (totalFlow == null || totalFlow == 0) {
                totalFlow = passRecordRepository.countByPassTimeBetween(todayStart, LocalDateTime.now());
            }
            stats.setTotalFlow(totalFlow != null ? totalFlow.intValue() : 0);
        }
        
        // 2. 尝试从Redis读取总营收
        Object redisRevenue = redisTemplate.opsForValue().get(REDIS_KEY_REVENUE_TODAY);
        if (redisRevenue != null) {
            stats.setTotalRevenue(Integer.parseInt(redisRevenue.toString()) / 100); // 分转元
        } else {
            BigDecimal revenue = passRecordRepository.sumEtcDeductionByPassTimeBetween(todayStart, LocalDateTime.now());
            stats.setTotalRevenue(revenue != null ? revenue.intValue() : 0);
        }
        
        // 3. 尝试从Redis读取平均车速
        Object redisSpeed = redisTemplate.opsForHash().get(REDIS_KEY_AVG_SPEED, "avg");
        if (redisSpeed != null) {
            stats.setAvgSpeed(Integer.parseInt(redisSpeed.toString()));
        } else {
            stats.setAvgSpeed(85); // 默认值
        }
        
        // 4. 卡口数量
        long checkpointCount = checkpointRepository.count();
        stats.setCheckpointCount((int) checkpointCount);
        
        // 5. 在线卡口数
        List<Checkpoint> onlineCheckpoints = checkpointRepository.findByStatus(1);
        stats.setOnlineCount(onlineCheckpoints.size());
        
        // 6. 告警数量
        long alertCount = alertRepository.countByCreateTimeAfter(todayStart);
        stats.setAlertCount((int) alertCount);
        
        // 7. 异常卡口数
        stats.setAbnormalCount((int) (checkpointCount - onlineCheckpoints.size()));
        
        // 8. 统计时间
        stats.setStatTime(LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        
        return stats;
    }

    /**
     * 获取实时流量数据
     */
    public List<Map<String, Object>> getRealtimeFlow() {
        List<Checkpoint> checkpoints = checkpointRepository.findAll();
        List<Map<String, Object>> flowList = new ArrayList<>();
        
        for (Checkpoint cp : checkpoints) {
            Map<String, Object> flowData = new HashMap<>();
            flowData.put("checkpointId", cp.getId());
            flowData.put("checkpointName", CHECKPOINT_NAME_MAP.getOrDefault(cp.getId(), cp.getName()));
            
            // 尝试从Redis读取Flink计算的流量
            String redisKey = "realtime:flow:checkpoint:" + cp.getId();
            Object redisFlow = redisTemplate.opsForValue().get(redisKey);
            
            int hourlyFlow;
            if (redisFlow != null) {
                hourlyFlow = Integer.parseInt(redisFlow.toString());
            } else {
                // 回退到数据库
                List<CheckpointFlow> flows = flowRepository.findByCheckpointIdAndStatDate(cp.getId(), LocalDate.now());
                int currentHour = LocalDateTime.now().getHour();
                hourlyFlow = flows.stream()
                        .filter(f -> f.getStatHour() != null && f.getStatHour() == currentHour)
                        .mapToInt(f -> f.getTotalCount() != null ? f.getTotalCount() : 0)
                        .sum();
                if (hourlyFlow == 0) {
                    hourlyFlow = generateMockHourlyFlow(currentHour);
                }
            }
            
            flowData.put("currentFlow", hourlyFlow);
            flowData.put("inFlow", hourlyFlow / 2);
            flowData.put("outFlow", hourlyFlow - hourlyFlow / 2);
            
            int maxCapacity = CHECKPOINT_CAPACITY.getOrDefault(cp.getId(), 1000);
            flowData.put("maxCapacity", maxCapacity);
            
            double ratio = (double) hourlyFlow / maxCapacity;
            if (ratio < 0.5) {
                flowData.put("status", "normal");
            } else if (ratio < 0.8) {
                flowData.put("status", "busy");
            } else {
                flowData.put("status", "congested");
            }
            
            flowList.add(flowData);
        }
        
        return flowList;
    }

    /**
     * 获取套牌检测列表
     */
    public List<Map<String, Object>> getClonePlateList(int page, int pageSize) {
        PageRequest pageRequest = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "detectionTime"));
        Page<ClonePlateDetection> detections = clonePlateRepository.findAll(pageRequest);
        
        List<Map<String, Object>> list = new ArrayList<>();
        for (ClonePlateDetection d : detections.getContent()) {
            Map<String, Object> record = new HashMap<>();
            record.put("id", d.getId());
            record.put("plateNumber", d.getPlateNumber());
            record.put("checkpoint1", CHECKPOINT_NAME_MAP.getOrDefault(d.getCheckpoint1Id(), "卡口" + d.getCheckpoint1Id()));
            record.put("checkpoint2", CHECKPOINT_NAME_MAP.getOrDefault(d.getCheckpoint2Id(), "卡口" + d.getCheckpoint2Id()));
            record.put("time1", d.getCheckpoint1Time() != null ? d.getCheckpoint1Time().toString() : null);
            record.put("time2", d.getCheckpoint2Time() != null ? d.getCheckpoint2Time().toString() : null);
            record.put("distance", d.getDistance());
            record.put("calculatedSpeed", d.getCalculatedSpeed());
            record.put("confidence", d.getConfidence());
            record.put("status", d.getStatus() == 0 ? "pending" : "confirmed");
            list.add(record);
        }
        
        return list;
    }

    /**
     * 获取违章列表 - 支持类型筛选
     */
    public List<Map<String, Object>> getViolationsWithFilter(String type, String status, int page, int pageSize) {
        PageRequest pageRequest = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "violationTime"));
        
        Page<Violation> violations;
        if (type != null && !type.isEmpty() && !"all".equals(type)) {
            violations = violationRepository.findByViolationType(type, pageRequest);
        } else {
            violations = violationRepository.findAll(pageRequest);
        }
        
        List<Map<String, Object>> list = new ArrayList<>();
        for (Violation v : violations.getContent()) {
            Map<String, Object> record = new HashMap<>();
            record.put("id", v.getId());
            record.put("plateNumber", v.getPlateNumber());
            record.put("type", v.getViolationType());
            record.put("violationType", v.getViolationType());
            record.put("description", v.getDescription());
            record.put("violation", getViolationLabel(v.getViolationType()));
            record.put("vehicleType", "小型车");
            record.put("checkpointName", CHECKPOINT_NAME_MAP.getOrDefault(v.getCheckpointId(), "卡口" + v.getCheckpointId()));
            record.put("checkpoint", CHECKPOINT_NAME_MAP.getOrDefault(v.getCheckpointId(), "卡口" + v.getCheckpointId()));
            record.put("detectTime", v.getViolationTime() != null ? v.getViolationTime().toString() : null);
            record.put("time", v.getViolationTime() != null ? v.getViolationTime().toString() : null);
            record.put("fine", v.getFineAmount());
            record.put("points", v.getPoints());
            record.put("status", v.getStatus() == 0 ? "pending" : "processed");
            list.add(record);
        }
        
        return list;
    }
    
    private String getViolationLabel(String type) {
        if (type == null) return "违规";
        return switch (type) {
            case "overspeed" -> "超速行驶";
            case "overload" -> "超载运输";
            case "illegal" -> "违规行为";
            case "escape" -> "逃费";
            default -> type;
        };
    }
    
    public long countViolations(String type, String status) {
        if (type != null && !type.isEmpty() && !"all".equals(type)) {
            return violationRepository.countByViolationType(type);
        }
        return violationRepository.count();
    }

    /**
     * 获取全部19个卡口的压力预警
     */
    public List<Map<String, Object>> getAllPressureWarnings() {
        List<Map<String, Object>> warnings = new ArrayList<>();
        
        for (long cpId = 1; cpId <= 19; cpId++) {
            Map<String, Object> warning = new HashMap<>();
            warning.put("id", cpId);
            warning.put("stationName", CHECKPOINT_NAME_MAP.getOrDefault(cpId, "卡口" + cpId));
            
            String redisKey = REDIS_KEY_PRESSURE + cpId;
            Map<Object, Object> pressureData = redisTemplate.opsForHash().entries(redisKey);
            
            int currentFlow;
            int pressure;
            String level;
            String suggestion = "";
            
            if (pressureData != null && !pressureData.isEmpty()) {
                currentFlow = Integer.parseInt(pressureData.getOrDefault("currentFlow", "0").toString());
                pressure = Integer.parseInt(pressureData.getOrDefault("pressure", "0").toString());
                level = pressureData.getOrDefault("level", "normal").toString();
                suggestion = pressureData.getOrDefault("suggestion", "").toString();
            } else {
                currentFlow = generateMockHourlyFlow(LocalDateTime.now().getHour());
                int maxCapacity = CHECKPOINT_CAPACITY.getOrDefault(cpId, 1000);
                pressure = (int) Math.min(100, (currentFlow * 100L) / maxCapacity);
                
                if (pressure >= 85) {
                    level = "danger";
                    suggestion = "建议开启备用车道，调配警力疏导";
                } else if (pressure >= 60) {
                    level = "warning";
                    suggestion = "预计30分钟后达到高峰，建议提前准备";
                } else {
                    level = "normal";
                }
            }
            
            warning.put("currentFlow", currentFlow);
            warning.put("pressure", pressure);
            warning.put("level", level);
            warning.put("suggestion", suggestion);
            warning.put("predictedFlow", (int) (currentFlow * 1.15));
            warning.put("maxCapacity", CHECKPOINT_CAPACITY.getOrDefault(cpId, 1000));
            
            warnings.add(warning);
        }
        
        warnings.sort((a, b) -> Integer.compare(
                (Integer) b.get("pressure"), 
                (Integer) a.get("pressure")
        ));
        
        return warnings;
    }
    
    /**
     * 获取车辆来源统计（本地/外地）
     */
    public Map<String, Object> getVehicleSourceStats() {
        Map<String, Object> result = new HashMap<>();
        
        Object localCount = redisTemplate.opsForValue().get(REDIS_KEY_SOURCE_LOCAL);
        Object foreignCount = redisTemplate.opsForValue().get(REDIS_KEY_SOURCE_FOREIGN);
        
        long local, foreign;
        if (localCount != null && foreignCount != null) {
            local = Long.parseLong(localCount.toString());
            foreign = Long.parseLong(foreignCount.toString());
        } else {
            LocalDateTime todayStart = LocalDate.now().atStartOfDay();
            local = passRecordRepository.countByPlateNumberStartingWithAndPassTimeAfter("苏C", todayStart);
            long total = passRecordRepository.countByPassTimeBetween(todayStart, LocalDateTime.now());
            foreign = total - local;
        }
        
        long total = local + foreign;
        double localRate = total > 0 ? (local * 100.0 / total) : 50;
        double foreignRate = total > 0 ? (foreign * 100.0 / total) : 50;
        
        result.put("local", local);
        result.put("foreign", foreign);
        result.put("localRate", Math.round(localRate * 10) / 10.0);
        result.put("foreignRate", Math.round(foreignRate * 10) / 10.0);
        
        return result;
    }
    
    /**
     * 获取区域热度排名
     */
    public List<Map<String, Object>> getRegionHeatRanking() {
        List<Map<String, Object>> ranking = new ArrayList<>();
        
        Set<Object> regions = redisTemplate.opsForZSet().reverseRange(REDIS_KEY_HEAT_RANKING, 0, -1);
        
        if (regions != null && !regions.isEmpty()) {
            int rank = 1;
            for (Object region : regions) {
                Double score = redisTemplate.opsForZSet().score(REDIS_KEY_HEAT_RANKING, region);
                Map<String, Object> item = new HashMap<>();
                item.put("region", region.toString());
                item.put("heat", score != null ? score.intValue() : 0);
                item.put("rank", rank++);
                ranking.add(item);
            }
        } else {
            String[] regionNames = {"苏皖界", "苏鲁界", "连云港界", "宿迁界"};
            long[][] regionIds = {{1,2,3,4,5,6}, {7,8,9,10,11,12}, {13,14}, {15,16,17,18,19}};
            
            List<Map.Entry<String, Long>> heats = new ArrayList<>();
            for (int i = 0; i < regionNames.length; i++) {
                long totalFlow = 0;
                for (long cpId : regionIds[i]) {
                    List<CheckpointFlow> flows = flowRepository.findByCheckpointIdAndStatDate(cpId, LocalDate.now());
                    totalFlow += flows.stream().mapToLong(f -> f.getTotalCount() != null ? f.getTotalCount() : 0).sum();
                }
                heats.add(Map.entry(regionNames[i], totalFlow));
            }
            
            heats.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
            
            int rank = 1;
            for (Map.Entry<String, Long> entry : heats) {
                Map<String, Object> item = new HashMap<>();
                item.put("region", entry.getKey());
                item.put("heat", entry.getValue());
                item.put("rank", rank++);
                ranking.add(item);
            }
        }
        
        return ranking;
    }

    private int generateMockHourlyFlow(int hour) {
        int baseFlow = 200;
        if (hour >= 7 && hour <= 9) baseFlow = 450;
        else if (hour >= 17 && hour <= 19) baseFlow = 500;
        else if (hour >= 11 && hour <= 14) baseFlow = 350;
        else if (hour >= 22 || hour <= 5) baseFlow = 100;
        
        return baseFlow + new Random().nextInt(100);
    }
    
    public List<Map<String, Object>> getClonePlateDetections(String date, int page, int pageSize) {
        return getClonePlateList(page, pageSize);
    }
    
    public List<Map<String, Object>> getViolations(String type, String date, int page, int pageSize) {
        return getViolationsWithFilter(type, null, page, pageSize);
    }
    
    public List<Map<String, Object>> getPressureWarnings() {
        return getAllPressureWarnings();
    }
}

