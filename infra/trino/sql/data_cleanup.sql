-- ============================================================
-- ETC 大数据平台 - 数据清理任务
-- 
-- 执行方式: trino --server http://localhost:8090 -f data_cleanup.sql
-- 调度方式: 每天凌晨 3 点执行
-- ============================================================

-- ============================================================
-- MySQL 数据清理（保留最近 7 天）
-- 
-- 说明：
-- - MySQL 仅保留最近 7 天的通行记录，用于快速索引查询
-- - 7 天前的数据已经同步写入 HBase，可以安全删除
-- - 删除操作分批执行，避免锁表
-- ============================================================

-- 清理 7 天前的通行记录（分批删除，每次 10000 条）
-- 注意：Trino 的 DELETE 语法可能需要根据实际版本调整

-- 方式 1：直接删除（适用于数据量不大的情况）
DELETE FROM mysql0.etc_system.pass_record
WHERE pass_time < CURRENT_DATE - INTERVAL '7' DAY;


-- ============================================================
-- 统计表数据清理（保留最近 90 天）
-- ============================================================

-- 清理 90 天前的日统计数据
DELETE FROM mysql0.etc_system.stats_daily_checkpoint
WHERE stat_date < DATE_FORMAT(CURRENT_DATE - INTERVAL '90' DAY, '%Y-%m-%d');

DELETE FROM mysql0.etc_system.stats_hourly_flow
WHERE stat_date < DATE_FORMAT(CURRENT_DATE - INTERVAL '90' DAY, '%Y-%m-%d');

DELETE FROM mysql0.etc_system.stats_daily_region
WHERE stat_date < DATE_FORMAT(CURRENT_DATE - INTERVAL '90' DAY, '%Y-%m-%d');

DELETE FROM mysql0.etc_system.stats_daily_province
WHERE stat_date < DATE_FORMAT(CURRENT_DATE - INTERVAL '90' DAY, '%Y-%m-%d');


-- ============================================================
-- 日志和历史记录清理
-- ============================================================

-- 清理 30 天前的查询历史
DELETE FROM mysql0.etc_system.query_history
WHERE created_at < CURRENT_DATE - INTERVAL '30' DAY;

-- 清理 180 天前的已处理告警
DELETE FROM mysql0.etc_system.alert
WHERE status = 'processed'
  AND created_at < CURRENT_DATE - INTERVAL '180' DAY;
