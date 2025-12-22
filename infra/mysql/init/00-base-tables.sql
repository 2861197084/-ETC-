-- ============================================================
-- ETC 大数据平台 - 基础表结构（广播表）
-- 这些表会通过 ShardingSphere 广播到所有数据源
-- ============================================================

USE etc;

-- ============================================================
-- 卡口信息表
-- ============================================================
CREATE TABLE IF NOT EXISTS checkpoint (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL COMMENT '卡口编码',
    name VARCHAR(128) NOT NULL COMMENT '卡口名称',
    district VARCHAR(64) COMMENT '所属区县',
    city VARCHAR(64) DEFAULT '徐州市' COMMENT '所属城市',
    province VARCHAR(64) DEFAULT '江苏省' COMMENT '所属省份',
    latitude DECIMAL(10,6) COMMENT '纬度',
    longitude DECIMAL(10,6) COMMENT '经度',
    direction VARCHAR(32) DEFAULT '双向' COMMENT '方向',
    road_name VARCHAR(128) COMMENT '道路名称',
    lane_count INT DEFAULT 4 COMMENT '车道数',
    type VARCHAR(32) DEFAULT '省界卡口' COMMENT '卡口类型',
    status TINYINT DEFAULT 1 COMMENT '状态: 1-正常, 0-停用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_code (code),
    KEY idx_district (district),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='卡口信息表';

-- ============================================================
-- 系统用户表
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL COMMENT '用户名',
    password VARCHAR(128) NOT NULL COMMENT '密码(MD5)',
    real_name VARCHAR(64) COMMENT '真实姓名',
    email VARCHAR(128) COMMENT '邮箱',
    phone VARCHAR(20) COMMENT '手机号',
    role_id BIGINT COMMENT '角色ID',
    status TINYINT DEFAULT 1 COMMENT '状态: 1-正常, 0-禁用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_username (username),
    KEY idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- ============================================================
-- 系统角色表
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_name VARCHAR(64) NOT NULL COMMENT '角色名称',
    role_code VARCHAR(32) NOT NULL COMMENT '角色编码',
    description VARCHAR(256) COMMENT '描述',
    status TINYINT DEFAULT 1 COMMENT '状态: 1-正常, 0-禁用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色表';

-- 初始化默认角色
INSERT INTO sys_role (id, role_name, role_code, description) VALUES
(1, '管理员', 'admin', '系统管理员，拥有所有权限'),
(2, '操作员', 'operator', '操作员，拥有数据操作权限'),
(3, '查看员', 'viewer', '查看员，只有查看权限')
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name);

-- ============================================================
-- 车辆信息表
-- ============================================================
CREATE TABLE IF NOT EXISTS vehicle (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plate_number VARCHAR(32) NOT NULL COMMENT '车牌号',
    plate_type VARCHAR(32) DEFAULT '小型汽车号牌' COMMENT '车牌类型',
    vehicle_type VARCHAR(32) COMMENT '车辆类型',
    vehicle_brand VARCHAR(64) COMMENT '车辆品牌',
    owner_name VARCHAR(64) COMMENT '车主姓名',
    owner_phone VARCHAR(20) COMMENT '车主电话',
    province CHAR(1) COMMENT '省份简称',
    is_local TINYINT DEFAULT 0 COMMENT '是否本地车: 1-是, 0-否',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_plate (plate_number),
    KEY idx_province (province)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车辆信息表';

-- ============================================================
-- 卡口流量实时表
-- ============================================================
CREATE TABLE IF NOT EXISTS checkpoint_flow (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    checkpoint_id VARCHAR(64) NOT NULL COMMENT '卡口ID',
    flow_time DATETIME NOT NULL COMMENT '统计时间',
    flow_count INT DEFAULT 0 COMMENT '流量',
    avg_speed DECIMAL(6,2) DEFAULT 0 COMMENT '平均速度',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_checkpoint_time (checkpoint_id, flow_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='卡口流量实时表';

-- ============================================================
-- 违法记录表
-- ============================================================
CREATE TABLE IF NOT EXISTS violation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plate_number VARCHAR(32) NOT NULL COMMENT '车牌号',
    violation_type VARCHAR(64) NOT NULL COMMENT '违法类型',
    checkpoint_id VARCHAR(64) COMMENT '卡口ID',
    checkpoint_name VARCHAR(128) COMMENT '卡口名称',
    violation_time DATETIME COMMENT '违法时间',
    fine_amount DECIMAL(10,2) DEFAULT 0 COMMENT '罚款金额',
    status VARCHAR(20) DEFAULT 'pending' COMMENT '状态',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_plate (plate_number),
    KEY idx_checkpoint (checkpoint_id),
    KEY idx_time (violation_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='违法记录表';

-- ============================================================
-- 套牌检测表
-- ============================================================
CREATE TABLE IF NOT EXISTS clone_plate_detection (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plate_number VARCHAR(32) NOT NULL COMMENT '车牌号',
    checkpoint_id_1 VARCHAR(64) COMMENT '第一次出现卡口',
    checkpoint_id_2 VARCHAR(64) COMMENT '第二次出现卡口',
    time_1 DATETIME COMMENT '第一次出现时间',
    time_2 DATETIME COMMENT '第二次出现时间',
    distance_km DECIMAL(10,2) COMMENT '两卡口距离(km)',
    time_diff_minutes INT COMMENT '时间差(分钟)',
    min_speed_required DECIMAL(6,2) COMMENT '所需最低时速',
    confidence_score DECIMAL(5,2) COMMENT '置信度',
    status VARCHAR(20) DEFAULT 'pending' COMMENT '状态',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_plate (plate_number),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='套牌检测表';

-- ============================================================
-- 申诉记录表
-- ============================================================
CREATE TABLE IF NOT EXISTS appeal (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    violation_id BIGINT COMMENT '关联违法记录ID',
    plate_number VARCHAR(32) NOT NULL COMMENT '车牌号',
    appeal_type VARCHAR(32) COMMENT '申诉类型',
    appeal_reason TEXT COMMENT '申诉原因',
    evidence_url VARCHAR(512) COMMENT '证据链接',
    status VARCHAR(20) DEFAULT 'pending' COMMENT '状态: pending/approved/rejected',
    reviewer_id BIGINT COMMENT '审核人ID',
    review_comment VARCHAR(512) COMMENT '审核意见',
    review_time DATETIME COMMENT '审核时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_plate (plate_number),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='申诉记录表';

-- ============================================================
-- Time-MoE 预测分析（5 分钟粒度，未来 12 点）
-- ============================================================

CREATE TABLE IF NOT EXISTS checkpoint_flow_forecast_5m (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    checkpoint_id VARCHAR(64) NOT NULL COMMENT '卡口ID（CP001..CP019）',
    fxlx VARCHAR(32) NOT NULL COMMENT '方向类型 FXLX（1=进城，2=出城）',
    start_time DATETIME NOT NULL COMMENT '预测起点（对齐 5min，预测第 1 个点的时间）',
    freq_min INT NOT NULL DEFAULT 5 COMMENT '时间粒度（分钟），固定 5',
    context_length INT NOT NULL DEFAULT 256 COMMENT '上下文长度（5min 点数）',
    prediction_length INT NOT NULL DEFAULT 12 COMMENT '预测长度（未来点数）',
    values_json JSON NOT NULL COMMENT '预测值数组（长度=prediction_length）',
    model_version VARCHAR(64) NOT NULL DEFAULT 'timemoe_etc_flow_v1' COMMENT '模型版本标识',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_series_time_model (checkpoint_id, fxlx, start_time, model_version),
    KEY idx_start_time (start_time),
    KEY idx_checkpoint_time (checkpoint_id, start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='卡口×方向 5min 流量预测（未来 12 点）';

CREATE TABLE IF NOT EXISTS forecast_request (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    checkpoint_id VARCHAR(64) NOT NULL COMMENT '卡口ID（CP001..CP019）',
    fxlx VARCHAR(32) NOT NULL COMMENT '方向类型 FXLX（1=进城，2=出城）',
    as_of_time DATETIME NOT NULL COMMENT '请求发起时间（用于标记“新一轮预测”）',
    status VARCHAR(16) NOT NULL DEFAULT 'pending' COMMENT '状态：pending/done/failed',
    result_start_time DATETIME NULL COMMENT '预测起点（done 时写入，便于查询结果）',
    model_version VARCHAR(64) NOT NULL DEFAULT 'timemoe_etc_flow_v1' COMMENT '模型版本标识',
    err_msg VARCHAR(512) NULL COMMENT '失败原因（failed 时）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_status_created (status, created_at),
    KEY idx_series_created (checkpoint_id, fxlx, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预测刷新请求队列（前端每分钟触发）';