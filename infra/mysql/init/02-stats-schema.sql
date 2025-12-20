-- ============================================================
-- ETC 大数据平台 - 统计表结构
-- 用于 Presto/Trino 离线任务写入
-- ============================================================

USE etc;

-- ============================================================
-- 日统计表
-- ============================================================

-- 卡口日统计
CREATE TABLE IF NOT EXISTS stats_daily_checkpoint (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stat_date VARCHAR(10) NOT NULL COMMENT '统计日期 yyyy-MM-dd',
    checkpoint_id BIGINT NOT NULL COMMENT '卡口ID',
    checkpoint_name VARCHAR(128) COMMENT '卡口名称',
    total_count BIGINT DEFAULT 0 COMMENT '通行总数',
    total_fee DECIMAL(12,2) DEFAULT 0 COMMENT '收费总额',
    avg_speed DECIMAL(6,2) DEFAULT 0 COMMENT '平均速度',
    unique_vehicles BIGINT DEFAULT 0 COMMENT '独立车辆数',
    local_count BIGINT DEFAULT 0 COMMENT '本地车辆数',
    foreign_count BIGINT DEFAULT 0 COMMENT '外地车辆数',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_date_cp (stat_date, checkpoint_id),
    KEY idx_stat_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='卡口日统计表';

-- 小时流量统计
CREATE TABLE IF NOT EXISTS stats_hourly_flow (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stat_date VARCHAR(10) NOT NULL COMMENT '统计日期',
    checkpoint_id BIGINT NOT NULL COMMENT '卡口ID',
    hour_of_day TINYINT NOT NULL COMMENT '小时 0-23',
    flow_count BIGINT DEFAULT 0 COMMENT '流量',
    avg_speed DECIMAL(6,2) DEFAULT 0 COMMENT '平均速度',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_date_cp_hour (stat_date, checkpoint_id, hour_of_day),
    KEY idx_stat_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小时流量统计表';

-- 区域日统计
CREATE TABLE IF NOT EXISTS stats_daily_region (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stat_date VARCHAR(10) NOT NULL COMMENT '统计日期',
    region_name VARCHAR(32) NOT NULL COMMENT '区域名称',
    total_count BIGINT DEFAULT 0 COMMENT '通行总数',
    total_fee DECIMAL(12,2) DEFAULT 0 COMMENT '收费总额',
    avg_speed DECIMAL(6,2) DEFAULT 0 COMMENT '平均速度',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_date_region (stat_date, region_name),
    KEY idx_stat_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='区域日统计表';

-- 省份车辆统计
CREATE TABLE IF NOT EXISTS stats_daily_province (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stat_date VARCHAR(10) NOT NULL COMMENT '统计日期',
    province CHAR(1) NOT NULL COMMENT '省份简称',
    vehicle_count BIGINT DEFAULT 0 COMMENT '车辆数',
    total_fee DECIMAL(12,2) DEFAULT 0 COMMENT '收费总额',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_date_province (stat_date, province),
    KEY idx_stat_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='省份车辆统计表';


-- ============================================================
-- 月统计表
-- ============================================================

-- 月度汇总
CREATE TABLE IF NOT EXISTS stats_monthly_summary (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stat_month VARCHAR(7) NOT NULL COMMENT '统计月份 yyyy-MM',
    total_vehicles BIGINT DEFAULT 0 COMMENT '总车辆数',
    total_pass_count BIGINT DEFAULT 0 COMMENT '总通行次数',
    total_revenue DECIMAL(14,2) DEFAULT 0 COMMENT '总收入',
    local_vehicle_count BIGINT DEFAULT 0 COMMENT '本地车辆数',
    foreign_vehicle_count BIGINT DEFAULT 0 COMMENT '外地车辆数',
    avg_daily_flow BIGINT DEFAULT 0 COMMENT '日均流量',
    peak_day DATE COMMENT '峰值日期',
    peak_day_flow BIGINT DEFAULT 0 COMMENT '峰值日流量',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_month (stat_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='月度汇总表';


-- ============================================================
-- 分析结果表
-- ============================================================

-- 热点卡口分析
CREATE TABLE IF NOT EXISTS analysis_hotspot_checkpoint (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stat_date VARCHAR(10) NOT NULL COMMENT '统计日期',
    checkpoint_id BIGINT NOT NULL COMMENT '卡口ID',
    checkpoint_name VARCHAR(128) COMMENT '卡口名称',
    region_name VARCHAR(32) COMMENT '区域名称',
    total_flow BIGINT DEFAULT 0 COMMENT '总流量',
    peak_hour TINYINT COMMENT '高峰小时',
    peak_hour_flow BIGINT DEFAULT 0 COMMENT '高峰小时流量',
    avg_hourly_flow DECIMAL(10,2) DEFAULT 0 COMMENT '平均小时流量',
    flow_rank INT COMMENT '流量排名',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_date_cp (stat_date, checkpoint_id),
    KEY idx_stat_date (stat_date),
    KEY idx_flow_rank (flow_rank)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='热点卡口分析表';

-- 高频车辆分析
CREATE TABLE IF NOT EXISTS analysis_frequent_vehicle (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stat_date VARCHAR(10) NOT NULL COMMENT '统计日期',
    plate_number VARCHAR(32) NOT NULL COMMENT '车牌号',
    pass_count INT DEFAULT 0 COMMENT '通行次数',
    total_fee DECIMAL(10,2) DEFAULT 0 COMMENT '消费金额',
    most_frequent_checkpoint BIGINT COMMENT '最常通行卡口',
    first_pass_time DATETIME COMMENT '首次通行时间',
    last_pass_time DATETIME COMMENT '末次通行时间',
    is_local TINYINT DEFAULT 0 COMMENT '是否本地车',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_date_plate (stat_date, plate_number),
    KEY idx_stat_date (stat_date),
    KEY idx_pass_count (pass_count DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='高频车辆分析表';

-- 时段流量分布
CREATE TABLE IF NOT EXISTS analysis_time_distribution (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stat_date VARCHAR(10) NOT NULL COMMENT '统计日期',
    time_period TINYINT NOT NULL COMMENT '时段编号',
    time_period_name VARCHAR(32) NOT NULL COMMENT '时段名称',
    total_flow BIGINT DEFAULT 0 COMMENT '总流量',
    percentage DECIMAL(5,2) DEFAULT 0 COMMENT '占比',
    avg_speed DECIMAL(6,2) DEFAULT 0 COMMENT '平均速度',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_date_period (stat_date, time_period),
    KEY idx_stat_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='时段流量分布表';


-- ============================================================
-- 账单表
-- ============================================================

CREATE TABLE IF NOT EXISTS bill (
    id VARCHAR(36) NOT NULL COMMENT '账单ID',
    owner_id BIGINT NOT NULL COMMENT '车主ID',
    vehicle_id BIGINT NOT NULL COMMENT '车辆ID',
    plate_number VARCHAR(32) NOT NULL COMMENT '车牌号',
    bill_month VARCHAR(7) NOT NULL COMMENT '账单月份',
    total_count INT DEFAULT 0 COMMENT '通行次数',
    total_fee DECIMAL(12,2) DEFAULT 0 COMMENT '总费用',
    total_distance DECIMAL(12,2) DEFAULT 0 COMMENT '总里程km',
    payment_status VARCHAR(20) DEFAULT 'unpaid' COMMENT '支付状态',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_owner_vehicle_month (owner_id, vehicle_id, bill_month),
    KEY idx_bill_month (bill_month),
    KEY idx_plate_number (plate_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账单表';


-- ============================================================
-- 告警表
-- ============================================================

CREATE TABLE IF NOT EXISTS alert (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    alert_type VARCHAR(32) NOT NULL COMMENT '告警类型: clone_plate/overspeed/pressure',
    severity VARCHAR(16) DEFAULT 'warning' COMMENT '严重程度: info/warning/critical',
    plate_number VARCHAR(32) COMMENT '相关车牌',
    checkpoint_id BIGINT COMMENT '相关卡口',
    description TEXT COMMENT '告警描述',
    status VARCHAR(20) DEFAULT 'pending' COMMENT '状态: pending/processed/ignored',
    processed_by BIGINT COMMENT '处理人ID',
    processed_at DATETIME COMMENT '处理时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_alert_type (alert_type),
    KEY idx_status (status),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警表';


-- ============================================================
-- 查询历史表
-- ============================================================

CREATE TABLE IF NOT EXISTS query_history (
    id VARCHAR(36) NOT NULL COMMENT '记录ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    query_text TEXT NOT NULL COMMENT '查询内容',
    generated_sql TEXT COMMENT '生成的SQL',
    result_count INT DEFAULT 0 COMMENT '结果数量',
    execution_time INT DEFAULT 0 COMMENT '执行时间ms',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='查询历史表';
