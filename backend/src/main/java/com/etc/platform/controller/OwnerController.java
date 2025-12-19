package com.etc.platform.controller;

import com.etc.platform.dto.ApiResponse;
import com.etc.platform.dto.PageResult;
import com.etc.platform.entity.Alert;
import com.etc.platform.entity.Appeal;
import com.etc.platform.service.PassRecordService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 车主接口
 */
@RestController
@RequestMapping("/owner")
public class OwnerController {

    private final PassRecordService passRecordService;

    public OwnerController(PassRecordService passRecordService) {
        this.passRecordService = passRecordService;
    }

    // ========== 通行记录 ==========

    /**
     * 通行记录列表
     */
    @GetMapping("/records/pass")
    public ApiResponse<PageResult<Map<String, Object>>> getPassRecords(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return ApiResponse.ok(passRecordService.queryRecords(null, null, null, null, null, null, null, null, page, pageSize));
    }

    /**
     * 记录详情
     */
    @GetMapping("/records/pass/{id}")
    public ApiResponse<Map<String, Object>> getPassRecord(@PathVariable Long id) {
        return ApiResponse.ok(passRecordService.getRecordById(id));
    }

    /**
     * 搜索记录
     */
    @GetMapping("/records/search")
    public ApiResponse<PageResult<Map<String, Object>>> searchRecords(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.ok(passRecordService.queryRecords(keyword, null, null, null, null, null, null, null, page, pageSize));
    }

    /**
     * 自然语言查询
     */
    @PostMapping("/records/text2sql")
    public ApiResponse<Map<String, Object>> text2Sql(@RequestBody Map<String, String> params) {
        return ApiResponse.ok(passRecordService.text2Sql(params.get("query")));
    }

    /**
     * 执行查询
     */
    @PostMapping("/records/execute")
    public ApiResponse<Map<String, Object>> execute(@RequestBody Map<String, String> params) {
        return ApiResponse.ok(passRecordService.executeSql(params.get("sql")));
    }

    // ========== 账单 ==========

    /**
     * 账单汇总
     */
    @GetMapping("/bills/summary")
    public ApiResponse<Map<String, Object>> getBillSummary(
            @RequestParam String month,
            @RequestParam(required = false) String vehicleId) {
        Random rand = new Random();
        Map<String, Object> summary = new HashMap<>();
        summary.put("month", month);
        summary.put("totalTrips", 20 + rand.nextInt(30));
        summary.put("totalFee", BigDecimal.valueOf(200 + rand.nextInt(500)));
        summary.put("totalDistance", BigDecimal.valueOf(500 + rand.nextInt(1000)));
        summary.put("unpaidFee", BigDecimal.valueOf(rand.nextInt(50)));
        return ApiResponse.ok(summary);
    }

    /**
     * 账单明细
     */
    @GetMapping("/bills/details")
    public ApiResponse<PageResult<Map<String, Object>>> getBillDetails(
            @RequestParam String month,
            @RequestParam(required = false) String vehicleId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        List<Map<String, Object>> details = new ArrayList<>();
        Random rand = new Random();
        
        for (int i = 0; i < 10; i++) {
            Map<String, Object> detail = new HashMap<>();
            detail.put("id", "BILL-" + (1000 + i));
            detail.put("date", LocalDateTime.now().minusDays(rand.nextInt(30)).toString());
            detail.put("entryCheckpoint", "入口站" + (i + 1));
            detail.put("exitCheckpoint", "出口站" + (i + 1));
            detail.put("fee", BigDecimal.valueOf(10 + rand.nextInt(50)));
            detail.put("status", rand.nextBoolean() ? "paid" : "unpaid");
            details.add(detail);
        }
        
        return ApiResponse.ok(PageResult.of(details, 10L, page, pageSize));
    }

    /**
     * 导出账单
     */
    @GetMapping("/bills/export")
    public ApiResponse<String> exportBill(
            @RequestParam String month,
            @RequestParam(required = false) String vehicleId,
            @RequestParam(defaultValue = "xlsx") String format) {
        return ApiResponse.ok("/api/download/bill-" + month + "." + format);
    }

    /**
     * 申请发票
     */
    @PostMapping("/bills/invoice")
    public ApiResponse<Map<String, Object>> applyInvoice(@RequestBody Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        result.put("invoiceId", "INV-" + System.currentTimeMillis());
        result.put("status", "pending");
        result.put("message", "发票申请已提交，预计3个工作日内处理");
        return ApiResponse.ok(result);
    }

    /**
     * 发票记录
     */
    @GetMapping("/bills/invoices")
    public ApiResponse<PageResult<Map<String, Object>>> getInvoices(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.ok(PageResult.of(List.of(), 0L, page, pageSize));
    }

    // ========== 告警 ==========

    /**
     * 告警列表
     */
    @GetMapping("/alerts")
    public ApiResponse<Map<String, Object>> getAlerts(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean isRead,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        List<Map<String, Object>> alerts = new ArrayList<>();
        Random rand = new Random();
        String[] types = {"fee_unpaid", "violation", "system"};
        String[] titles = {"待缴费提醒", "违规通知", "系统消息"};
        
        for (int i = 0; i < 5; i++) {
            int typeIdx = rand.nextInt(types.length);
            Map<String, Object> alert = new HashMap<>();
            alert.put("id", 3000 + i);
            alert.put("alertType", types[typeIdx]);
            alert.put("title", titles[typeIdx]);
            alert.put("content", "这是一条" + titles[typeIdx]);
            alert.put("level", "info");
            alert.put("status", rand.nextInt(2));
            alert.put("createTime", LocalDateTime.now().minusHours(rand.nextInt(72)).toString());
            alerts.add(alert);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("list", alerts);
        result.put("total", 5);
        result.put("unreadCount", alerts.stream().filter(a -> (Integer)a.get("status") == 0).count());
        return ApiResponse.ok(result);
    }

    /**
     * 告警详情
     */
    @GetMapping("/alerts/{id}")
    public ApiResponse<Map<String, Object>> getAlert(@PathVariable Long id) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("id", id);
        alert.put("alertType", "system");
        alert.put("title", "系统消息");
        alert.put("content", "这是告警详情内容");
        alert.put("status", 0);
        alert.put("createTime", LocalDateTime.now().toString());
        return ApiResponse.ok(alert);
    }

    /**
     * 标记已读
     */
    @PutMapping("/alerts/{id}/read")
    public ApiResponse<Void> markAsRead(@PathVariable Long id) {
        return ApiResponse.ok(null);
    }

    /**
     * 全部已读
     */
    @PutMapping("/alerts/read-all")
    public ApiResponse<Void> markAllAsRead() {
        return ApiResponse.ok(null);
    }

    /**
     * 忽略告警
     */
    @PutMapping("/alerts/{id}/dismiss")
    public ApiResponse<Void> dismissAlert(@PathVariable String id) {
        return ApiResponse.ok(null);
    }

    /**
     * 提交申诉
     */
    @PostMapping("/alerts/appeal")
    public ApiResponse<Appeal> submitAppeal(@RequestBody Map<String, Object> params) {
        Appeal appeal = new Appeal();
        appeal.setId("APL-" + System.currentTimeMillis());
        appeal.setAlertId((String) params.get("alertId"));
        appeal.setAppealType((String) params.get("appealType"));
        appeal.setReason((String) params.get("reason"));
        appeal.setStatus("pending");
        appeal.setCreatedAt(LocalDateTime.now());
        return ApiResponse.ok(appeal);
    }

    /**
     * 申诉列表
     */
    @GetMapping("/alerts/appeals")
    public ApiResponse<PageResult<Appeal>> getAppeals(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.ok(PageResult.of(List.of(), 0L, page, pageSize));
    }

    /**
     * 申诉详情
     */
    @GetMapping("/alerts/appeals/{id}")
    public ApiResponse<Appeal> getAppeal(@PathVariable String id) {
        Appeal appeal = new Appeal();
        appeal.setId(id);
        appeal.setStatus("pending");
        appeal.setCreatedAt(LocalDateTime.now());
        return ApiResponse.ok(appeal);
    }

    // ========== 地图 ==========

    /**
     * 绑定车辆
     */
    @GetMapping("/map/vehicles")
    public ApiResponse<List<Map<String, Object>>> getVehicles() {
        List<Map<String, Object>> vehicles = new ArrayList<>();
        Map<String, Object> v1 = new HashMap<>();
        v1.put("id", "VEH-001");
        v1.put("plateNumber", "苏C12345");
        v1.put("plateColor", "blue");
        v1.put("vehicleType", "1");
        v1.put("isDefault", true);
        vehicles.add(v1);
        
        Map<String, Object> v2 = new HashMap<>();
        v2.put("id", "VEH-002");
        v2.put("plateNumber", "苏CA1234");
        v2.put("plateColor", "yellow");
        v2.put("vehicleType", "11");
        v2.put("isDefault", false);
        vehicles.add(v2);
        
        return ApiResponse.ok(vehicles);
    }

    /**
     * 车辆轨迹
     */
    @GetMapping("/map/trajectory")
    public ApiResponse<Map<String, Object>> getTrajectory(
            @RequestParam String vehicleId,
            @RequestParam String startTime,
            @RequestParam String endTime) {
        Map<String, Object> trajectory = new HashMap<>();
        trajectory.put("vehicleId", vehicleId);
        trajectory.put("points", List.of(
                Map.of("lng", 117.2, "lat", 34.2, "time", "2025-12-18 10:00:00"),
                Map.of("lng", 117.3, "lat", 34.3, "time", "2025-12-18 10:30:00"),
                Map.of("lng", 117.4, "lat", 34.4, "time", "2025-12-18 11:00:00")
        ));
        return ApiResponse.ok(trajectory);
    }

    /**
     * 个人统计
     */
    @GetMapping("/map/stats")
    public ApiResponse<Map<String, Object>> getStats(
            @RequestParam(required = false) String vehicleId,
            @RequestParam(required = false) String month) {
        Random rand = new Random();
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalTrips", 30 + rand.nextInt(50));
        stats.put("totalFee", BigDecimal.valueOf(300 + rand.nextInt(500)));
        stats.put("totalDistance", BigDecimal.valueOf(800 + rand.nextInt(1200)));
        stats.put("avgSpeed", BigDecimal.valueOf(60 + rand.nextInt(40)));
        return ApiResponse.ok(stats);
    }
}
