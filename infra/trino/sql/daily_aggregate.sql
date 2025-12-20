-- ============================================================
-- ETC 大数据平台 - Trino/Presto SQL 离线任务
-- 
-- 执行方式: trino --server http://localhost:8090 -f daily_aggregate.sql
-- 调度方式: 通过 Cron 或 XXL-Job 定时执行
-- ============================================================

-- ============================================================
-- 1. 日统计汇总任务 (每天凌晨 1 点执行)
-- ============================================================

-- 1.1 按卡口统计昨日数据
INSERT INTO mysql0.etc_system.stats_daily_checkpoint (
    stat_date,
    checkpoint_id,
    checkpoint_name,
    total_count,
    total_fee,
    avg_speed,
    unique_vehicles,
    local_count,
    foreign_count,
    created_at
)
SELECT 
    DATE_FORMAT(CURRENT_DATE - INTERVAL '1' DAY, '%Y-%m-%d') as stat_date,
    checkpoint_id,
    MAX(checkpoint_name) as checkpoint_name,
    COUNT(*) as total_count,
    COALESCE(SUM(etc_deduction), 0) as total_fee,
    COALESCE(AVG(speed), 0) as avg_speed,
    COUNT(DISTINCT plate_number) as unique_vehicles,
    SUM(CASE WHEN plate_number LIKE '苏C%' THEN 1 ELSE 0 END) as local_count,
    SUM(CASE WHEN plate_number NOT LIKE '苏C%' THEN 1 ELSE 0 END) as foreign_count,
    NOW() as created_at
FROM mysql0.etc_system.pass_record
WHERE pass_time >= CURRENT_DATE - INTERVAL '1' DAY
  AND pass_time < CURRENT_DATE
GROUP BY checkpoint_id;


-- 1.2 按小时统计流量分布
INSERT INTO mysql0.etc_system.stats_hourly_flow (
    stat_date,
    checkpoint_id,
    hour_of_day,
    flow_count,
    avg_speed,
    created_at
)
SELECT 
    DATE_FORMAT(CURRENT_DATE - INTERVAL '1' DAY, '%Y-%m-%d') as stat_date,
    checkpoint_id,
    HOUR(pass_time) as hour_of_day,
    COUNT(*) as flow_count,
    COALESCE(AVG(speed), 0) as avg_speed,
    NOW() as created_at
FROM mysql0.etc_system.pass_record
WHERE pass_time >= CURRENT_DATE - INTERVAL '1' DAY
  AND pass_time < CURRENT_DATE
GROUP BY checkpoint_id, HOUR(pass_time);


-- 1.3 按区域统计
INSERT INTO mysql0.etc_system.stats_daily_region (
    stat_date,
    region_name,
    total_count,
    total_fee,
    avg_speed,
    created_at
)
SELECT 
    DATE_FORMAT(CURRENT_DATE - INTERVAL '1' DAY, '%Y-%m-%d') as stat_date,
    CASE 
        WHEN checkpoint_id BETWEEN 1 AND 6 THEN '苏皖界'
        WHEN checkpoint_id BETWEEN 7 AND 12 THEN '苏鲁界'
        WHEN checkpoint_id BETWEEN 13 AND 14 THEN '连云港界'
        WHEN checkpoint_id BETWEEN 15 AND 19 THEN '宿迁界'
        ELSE '未知'
    END as region_name,
    COUNT(*) as total_count,
    COALESCE(SUM(etc_deduction), 0) as total_fee,
    COALESCE(AVG(speed), 0) as avg_speed,
    NOW() as created_at
FROM mysql0.etc_system.pass_record
WHERE pass_time >= CURRENT_DATE - INTERVAL '1' DAY
  AND pass_time < CURRENT_DATE
GROUP BY 
    CASE 
        WHEN checkpoint_id BETWEEN 1 AND 6 THEN '苏皖界'
        WHEN checkpoint_id BETWEEN 7 AND 12 THEN '苏鲁界'
        WHEN checkpoint_id BETWEEN 13 AND 14 THEN '连云港界'
        WHEN checkpoint_id BETWEEN 15 AND 19 THEN '宿迁界'
        ELSE '未知'
    END;


-- 1.4 按省份统计车辆来源
INSERT INTO mysql0.etc_system.stats_daily_province (
    stat_date,
    province,
    vehicle_count,
    total_fee,
    created_at
)
SELECT 
    DATE_FORMAT(CURRENT_DATE - INTERVAL '1' DAY, '%Y-%m-%d') as stat_date,
    SUBSTR(plate_number, 1, 1) as province,
    COUNT(*) as vehicle_count,
    COALESCE(SUM(etc_deduction), 0) as total_fee,
    NOW() as created_at
FROM mysql0.etc_system.pass_record
WHERE pass_time >= CURRENT_DATE - INTERVAL '1' DAY
  AND pass_time < CURRENT_DATE
GROUP BY SUBSTR(plate_number, 1, 1);
