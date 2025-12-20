-- ============================================================
-- ETC 大数据平台 - 月度账单生成任务
-- 
-- 执行方式: trino --server http://localhost:8090 -f monthly_bill.sql
-- 调度方式: 每月 1 号凌晨 2 点执行
-- ============================================================

-- 生成上月账单
INSERT INTO mysql0.etc_system.bill (
    id,
    owner_id,
    vehicle_id,
    plate_number,
    bill_month,
    total_count,
    total_fee,
    total_distance,
    payment_status,
    created_at
)
SELECT 
    UUID() as id,
    v.owner_id,
    v.id as vehicle_id,
    v.plate_number,
    DATE_FORMAT(CURRENT_DATE - INTERVAL '1' MONTH, '%Y-%m') as bill_month,
    COUNT(p.id) as total_count,
    COALESCE(SUM(p.etc_deduction), 0) as total_fee,
    -- 假设每次通行平均 50km（实际应从数据中计算）
    COUNT(p.id) * 50.0 as total_distance,
    'unpaid' as payment_status,
    NOW() as created_at
FROM mysql0.etc_system.vehicle v
LEFT JOIN mysql0.etc_system.pass_record p 
    ON v.plate_number = p.plate_number
    AND p.pass_time >= DATE_TRUNC('month', CURRENT_DATE - INTERVAL '1' MONTH)
    AND p.pass_time < DATE_TRUNC('month', CURRENT_DATE)
WHERE v.owner_id IS NOT NULL
GROUP BY v.owner_id, v.id, v.plate_number
HAVING COUNT(p.id) > 0;


-- 更新月度汇总统计
INSERT INTO mysql0.etc_system.stats_monthly_summary (
    stat_month,
    total_vehicles,
    total_pass_count,
    total_revenue,
    local_vehicle_count,
    foreign_vehicle_count,
    avg_daily_flow,
    peak_day,
    peak_day_flow,
    created_at
)
SELECT 
    DATE_FORMAT(CURRENT_DATE - INTERVAL '1' MONTH, '%Y-%m') as stat_month,
    COUNT(DISTINCT plate_number) as total_vehicles,
    COUNT(*) as total_pass_count,
    COALESCE(SUM(etc_deduction), 0) as total_revenue,
    COUNT(DISTINCT CASE WHEN plate_number LIKE '苏C%' THEN plate_number END) as local_vehicle_count,
    COUNT(DISTINCT CASE WHEN plate_number NOT LIKE '苏C%' THEN plate_number END) as foreign_vehicle_count,
    COUNT(*) / DAY(LAST_DAY(CURRENT_DATE - INTERVAL '1' MONTH)) as avg_daily_flow,
    (
        SELECT DATE(pass_time)
        FROM mysql0.etc_system.pass_record
        WHERE pass_time >= DATE_TRUNC('month', CURRENT_DATE - INTERVAL '1' MONTH)
          AND pass_time < DATE_TRUNC('month', CURRENT_DATE)
        GROUP BY DATE(pass_time)
        ORDER BY COUNT(*) DESC
        LIMIT 1
    ) as peak_day,
    (
        SELECT COUNT(*)
        FROM mysql0.etc_system.pass_record
        WHERE pass_time >= DATE_TRUNC('month', CURRENT_DATE - INTERVAL '1' MONTH)
          AND pass_time < DATE_TRUNC('month', CURRENT_DATE)
        GROUP BY DATE(pass_time)
        ORDER BY COUNT(*) DESC
        LIMIT 1
    ) as peak_day_flow,
    NOW() as created_at
FROM mysql0.etc_system.pass_record
WHERE pass_time >= DATE_TRUNC('month', CURRENT_DATE - INTERVAL '1' MONTH)
  AND pass_time < DATE_TRUNC('month', CURRENT_DATE);
