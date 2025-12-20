-- ============================================================
-- ETC 大数据平台 - 热点分析任务
-- 
-- 执行方式: trino --server http://localhost:8090 -f hotspot_analysis.sql
-- 调度方式: 每天凌晨 2 点执行
-- ============================================================

-- ============================================================
-- 1. 热点卡口分析（按流量排名）
-- ============================================================

INSERT INTO mysql0.etc_system.analysis_hotspot_checkpoint (
    stat_date,
    checkpoint_id,
    checkpoint_name,
    region_name,
    total_flow,
    peak_hour,
    peak_hour_flow,
    avg_hourly_flow,
    flow_rank,
    created_at
)
WITH hourly_stats AS (
    SELECT 
        checkpoint_id,
        HOUR(pass_time) as hour_of_day,
        COUNT(*) as hour_flow
    FROM mysql0.etc_system.pass_record
    WHERE pass_time >= CURRENT_DATE - INTERVAL '1' DAY
      AND pass_time < CURRENT_DATE
    GROUP BY checkpoint_id, HOUR(pass_time)
),
peak_hours AS (
    SELECT 
        checkpoint_id,
        hour_of_day as peak_hour,
        hour_flow as peak_hour_flow,
        ROW_NUMBER() OVER (PARTITION BY checkpoint_id ORDER BY hour_flow DESC) as rn
    FROM hourly_stats
)
SELECT 
    DATE_FORMAT(CURRENT_DATE - INTERVAL '1' DAY, '%Y-%m-%d') as stat_date,
    p.checkpoint_id,
    MAX(p.checkpoint_name) as checkpoint_name,
    CASE 
        WHEN p.checkpoint_id BETWEEN 1 AND 6 THEN '苏皖界'
        WHEN p.checkpoint_id BETWEEN 7 AND 12 THEN '苏鲁界'
        WHEN p.checkpoint_id BETWEEN 13 AND 14 THEN '连云港界'
        WHEN p.checkpoint_id BETWEEN 15 AND 19 THEN '宿迁界'
        ELSE '未知'
    END as region_name,
    COUNT(*) as total_flow,
    MAX(ph.peak_hour) as peak_hour,
    MAX(ph.peak_hour_flow) as peak_hour_flow,
    COUNT(*) / 24.0 as avg_hourly_flow,
    ROW_NUMBER() OVER (ORDER BY COUNT(*) DESC) as flow_rank,
    NOW() as created_at
FROM mysql0.etc_system.pass_record p
LEFT JOIN peak_hours ph ON p.checkpoint_id = ph.checkpoint_id AND ph.rn = 1
WHERE p.pass_time >= CURRENT_DATE - INTERVAL '1' DAY
  AND p.pass_time < CURRENT_DATE
GROUP BY p.checkpoint_id;


-- ============================================================
-- 2. 高频车辆分析
-- ============================================================

INSERT INTO mysql0.etc_system.analysis_frequent_vehicle (
    stat_date,
    plate_number,
    pass_count,
    total_fee,
    most_frequent_checkpoint,
    first_pass_time,
    last_pass_time,
    is_local,
    created_at
)
WITH vehicle_stats AS (
    SELECT 
        plate_number,
        checkpoint_id,
        COUNT(*) as cp_count,
        ROW_NUMBER() OVER (PARTITION BY plate_number ORDER BY COUNT(*) DESC) as rn
    FROM mysql0.etc_system.pass_record
    WHERE pass_time >= CURRENT_DATE - INTERVAL '1' DAY
      AND pass_time < CURRENT_DATE
    GROUP BY plate_number, checkpoint_id
)
SELECT 
    DATE_FORMAT(CURRENT_DATE - INTERVAL '1' DAY, '%Y-%m-%d') as stat_date,
    p.plate_number,
    COUNT(*) as pass_count,
    COALESCE(SUM(p.etc_deduction), 0) as total_fee,
    MAX(vs.checkpoint_id) as most_frequent_checkpoint,
    MIN(p.pass_time) as first_pass_time,
    MAX(p.pass_time) as last_pass_time,
    CASE WHEN p.plate_number LIKE '苏C%' THEN 1 ELSE 0 END as is_local,
    NOW() as created_at
FROM mysql0.etc_system.pass_record p
LEFT JOIN vehicle_stats vs ON p.plate_number = vs.plate_number AND vs.rn = 1
WHERE p.pass_time >= CURRENT_DATE - INTERVAL '1' DAY
  AND p.pass_time < CURRENT_DATE
GROUP BY p.plate_number
HAVING COUNT(*) >= 3  -- 一天通行 3 次以上为高频车辆
ORDER BY pass_count DESC
LIMIT 1000;


-- ============================================================
-- 3. 时段流量分布分析
-- ============================================================

INSERT INTO mysql0.etc_system.analysis_time_distribution (
    stat_date,
    time_period,
    time_period_name,
    total_flow,
    percentage,
    avg_speed,
    created_at
)
SELECT 
    DATE_FORMAT(CURRENT_DATE - INTERVAL '1' DAY, '%Y-%m-%d') as stat_date,
    CASE 
        WHEN HOUR(pass_time) BETWEEN 0 AND 5 THEN 1
        WHEN HOUR(pass_time) BETWEEN 6 AND 8 THEN 2
        WHEN HOUR(pass_time) BETWEEN 9 AND 11 THEN 3
        WHEN HOUR(pass_time) BETWEEN 12 AND 13 THEN 4
        WHEN HOUR(pass_time) BETWEEN 14 AND 16 THEN 5
        WHEN HOUR(pass_time) BETWEEN 17 AND 19 THEN 6
        WHEN HOUR(pass_time) BETWEEN 20 AND 23 THEN 7
    END as time_period,
    CASE 
        WHEN HOUR(pass_time) BETWEEN 0 AND 5 THEN '凌晨(0-5时)'
        WHEN HOUR(pass_time) BETWEEN 6 AND 8 THEN '早高峰(6-8时)'
        WHEN HOUR(pass_time) BETWEEN 9 AND 11 THEN '上午(9-11时)'
        WHEN HOUR(pass_time) BETWEEN 12 AND 13 THEN '午间(12-13时)'
        WHEN HOUR(pass_time) BETWEEN 14 AND 16 THEN '下午(14-16时)'
        WHEN HOUR(pass_time) BETWEEN 17 AND 19 THEN '晚高峰(17-19时)'
        WHEN HOUR(pass_time) BETWEEN 20 AND 23 THEN '夜间(20-23时)'
    END as time_period_name,
    COUNT(*) as total_flow,
    CAST(COUNT(*) * 100.0 / SUM(COUNT(*)) OVER () AS DECIMAL(5,2)) as percentage,
    COALESCE(AVG(speed), 0) as avg_speed,
    NOW() as created_at
FROM mysql0.etc_system.pass_record
WHERE pass_time >= CURRENT_DATE - INTERVAL '1' DAY
  AND pass_time < CURRENT_DATE
GROUP BY 
    CASE 
        WHEN HOUR(pass_time) BETWEEN 0 AND 5 THEN 1
        WHEN HOUR(pass_time) BETWEEN 6 AND 8 THEN 2
        WHEN HOUR(pass_time) BETWEEN 9 AND 11 THEN 3
        WHEN HOUR(pass_time) BETWEEN 12 AND 13 THEN 4
        WHEN HOUR(pass_time) BETWEEN 14 AND 16 THEN 5
        WHEN HOUR(pass_time) BETWEEN 17 AND 19 THEN 6
        WHEN HOUR(pass_time) BETWEEN 20 AND 23 THEN 7
    END,
    CASE 
        WHEN HOUR(pass_time) BETWEEN 0 AND 5 THEN '凌晨(0-5时)'
        WHEN HOUR(pass_time) BETWEEN 6 AND 8 THEN '早高峰(6-8时)'
        WHEN HOUR(pass_time) BETWEEN 9 AND 11 THEN '上午(9-11时)'
        WHEN HOUR(pass_time) BETWEEN 12 AND 13 THEN '午间(12-13时)'
        WHEN HOUR(pass_time) BETWEEN 14 AND 16 THEN '下午(14-16时)'
        WHEN HOUR(pass_time) BETWEEN 17 AND 19 THEN '晚高峰(17-19时)'
        WHEN HOUR(pass_time) BETWEEN 20 AND 23 THEN '夜间(20-23时)'
    END
ORDER BY time_period;
