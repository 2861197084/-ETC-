-- ETC 大数据管理平台 - MySQL 初始化脚本
-- 分片表: pass_record (按卡口ID分片)
-- 普通表: sys_user, sys_role, vehicle, checkpoint, station 等

-- ==================== 系统用户表 ====================
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT '密码(BCrypt加密)',
    real_name VARCHAR(50) COMMENT '真实姓名',
    phone VARCHAR(20) COMMENT '手机号',
    email VARCHAR(100) COMMENT '邮箱',
    avatar VARCHAR(255) COMMENT '头像URL',
    role_id BIGINT COMMENT '角色ID',
    status TINYINT DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_username (username),
    INDEX idx_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- ==================== 系统角色表 ====================
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '角色ID',
    role_name VARCHAR(50) NOT NULL UNIQUE COMMENT '角色名称',
    role_code VARCHAR(50) NOT NULL UNIQUE COMMENT '角色编码',
    description VARCHAR(255) COMMENT '角色描述',
    permissions TEXT COMMENT '权限列表(JSON)',
    status TINYINT DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色表';

-- ==================== 车辆信息表 ====================
CREATE TABLE IF NOT EXISTS vehicle (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '车辆ID',
    plate_number VARCHAR(20) NOT NULL UNIQUE COMMENT '车牌号',
    plate_color VARCHAR(10) COMMENT '车牌颜色: 蓝/黄/绿/白/黑',
    vehicle_type VARCHAR(20) COMMENT '车辆类型: 小型客车/中型客车/大型客车/小型货车/中型货车/大型货车',
    owner_id BIGINT COMMENT '车主用户ID',
    owner_name VARCHAR(50) COMMENT '车主姓名',
    owner_phone VARCHAR(20) COMMENT '车主电话',
    etc_card_no VARCHAR(30) COMMENT 'ETC卡号',
    etc_status TINYINT DEFAULT 1 COMMENT 'ETC状态: 0-未绑定, 1-正常, 2-挂失, 3-注销',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_plate (plate_number),
    INDEX idx_owner (owner_id),
    INDEX idx_etc (etc_card_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车辆信息表';

-- ==================== 卡口信息表 ====================
CREATE TABLE IF NOT EXISTS checkpoint (
    id BIGINT PRIMARY KEY COMMENT '卡口ID',
    name VARCHAR(100) NOT NULL COMMENT '卡口名称',
    code VARCHAR(50) UNIQUE COMMENT '卡口编码',
    type VARCHAR(20) COMMENT '卡口类型: provincial/municipal',
    province VARCHAR(20) COMMENT '所属省份',
    city VARCHAR(20) COMMENT '所属城市',
    district VARCHAR(20) COMMENT '所属区县',
    longitude DECIMAL(10, 6) COMMENT '经度',
    latitude DECIMAL(10, 6) COMMENT '纬度',
    direction VARCHAR(50) COMMENT '方向: 进城/出城/双向',
    road_name VARCHAR(50) COMMENT '所属道路',
    lane_count INT DEFAULT 4 COMMENT '车道数',
    status TINYINT DEFAULT 1 COMMENT '状态: 0-停用, 1-正常, 2-维护中',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_code (code),
    INDEX idx_location (province, city, district)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='卡口信息表';

-- ==================== 收费站表 ====================
CREATE TABLE IF NOT EXISTS station (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '收费站ID',
    name VARCHAR(100) NOT NULL COMMENT '收费站名称',
    code VARCHAR(50) UNIQUE COMMENT '收费站编码',
    checkpoint_id BIGINT COMMENT '关联卡口ID',
    highway_name VARCHAR(50) COMMENT '所属高速名称',
    province VARCHAR(20) COMMENT '所属省份',
    city VARCHAR(20) COMMENT '所属城市',
    longitude DECIMAL(10, 6) COMMENT '经度',
    latitude DECIMAL(10, 6) COMMENT '纬度',
    status TINYINT DEFAULT 1 COMMENT '状态: 0-关闭, 1-开放',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_checkpoint (checkpoint_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收费站表';

-- ==================== 通行记录表 (分片表) ====================
CREATE TABLE IF NOT EXISTS pass_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '记录ID',
    plate_number VARCHAR(20) NOT NULL COMMENT '车牌号',
    checkpoint_id BIGINT NOT NULL COMMENT '卡口ID',
    pass_time DATETIME NOT NULL COMMENT '通行时间',
    direction VARCHAR(10) COMMENT '方向: in/out',
    speed DECIMAL(5, 2) COMMENT '通行速度(km/h)',
    lane_no INT COMMENT '车道号',
    image_url VARCHAR(255) COMMENT '抓拍图片URL',
    vehicle_type VARCHAR(20) COMMENT '车辆类型',
    etc_deduction DECIMAL(10, 2) DEFAULT 0 COMMENT 'ETC扣款金额',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_plate_time (plate_number, pass_time),
    INDEX idx_checkpoint_time (checkpoint_id, pass_time),
    INDEX idx_pass_time (pass_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通行记录表';

-- ==================== 违章记录表 ====================
CREATE TABLE IF NOT EXISTS violation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '违章ID',
    plate_number VARCHAR(20) NOT NULL COMMENT '车牌号',
    checkpoint_id BIGINT COMMENT '卡口ID',
    violation_type VARCHAR(50) NOT NULL COMMENT '违章类型: 超速/闯红灯/逆行/占道',
    violation_time DATETIME NOT NULL COMMENT '违章时间',
    description VARCHAR(500) COMMENT '违章描述',
    image_url VARCHAR(255) COMMENT '违章图片URL',
    fine_amount DECIMAL(10, 2) DEFAULT 0 COMMENT '罚款金额',
    points INT DEFAULT 0 COMMENT '扣分',
    status TINYINT DEFAULT 0 COMMENT '状态: 0-待处理, 1-已处理, 2-已撤销',
    process_time DATETIME COMMENT '处理时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_plate (plate_number),
    INDEX idx_time (violation_time),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='违章记录表';

-- ==================== 套牌检测表 ====================
CREATE TABLE IF NOT EXISTS clone_plate_detection (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '检测ID',
    plate_number VARCHAR(20) NOT NULL COMMENT '车牌号',
    detection_time DATETIME NOT NULL COMMENT '检测时间',
    checkpoint1_id BIGINT COMMENT '卡口1 ID',
    checkpoint1_name VARCHAR(100) COMMENT '卡口1名称',
    checkpoint1_time DATETIME COMMENT '卡口1通行时间',
    checkpoint2_id BIGINT COMMENT '卡口2 ID',
    checkpoint2_name VARCHAR(100) COMMENT '卡口2名称',
    checkpoint2_time DATETIME COMMENT '卡口2通行时间',
    distance DECIMAL(10, 2) COMMENT '两卡口距离(km)',
    time_diff INT COMMENT '时间差(秒)',
    calculated_speed DECIMAL(10, 2) COMMENT '计算速度(km/h)',
    confidence DECIMAL(5, 2) COMMENT '置信度(%)',
    status TINYINT DEFAULT 0 COMMENT '状态: 0-待确认, 1-已确认, 2-已排除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_plate (plate_number),
    INDEX idx_time (detection_time),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='套牌检测表';

-- ==================== 告警表 ====================
CREATE TABLE IF NOT EXISTS alert (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '告警ID',
    alert_type VARCHAR(50) NOT NULL COMMENT '告警类型: traffic_jam/accident/clone_plate/violation',
    level VARCHAR(20) DEFAULT 'warning' COMMENT '告警级别: info/warning/error/critical',
    title VARCHAR(200) NOT NULL COMMENT '告警标题',
    content TEXT COMMENT '告警内容',
    checkpoint_id BIGINT COMMENT '关联卡口ID',
    plate_number VARCHAR(20) COMMENT '关联车牌号',
    status TINYINT DEFAULT 0 COMMENT '状态: 0-未处理, 1-处理中, 2-已处理, 3-已忽略',
    handler_id BIGINT COMMENT '处理人ID',
    handle_time DATETIME COMMENT '处理时间',
    handle_remark VARCHAR(500) COMMENT '处理备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_type (alert_type),
    INDEX idx_status (status),
    INDEX idx_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警表';

-- ==================== 申诉表 ====================
CREATE TABLE IF NOT EXISTS appeal (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '申诉ID',
    user_id BIGINT NOT NULL COMMENT '申诉用户ID',
    violation_id BIGINT COMMENT '关联违章ID',
    appeal_type VARCHAR(50) NOT NULL COMMENT '申诉类型: violation/billing/other',
    title VARCHAR(200) NOT NULL COMMENT '申诉标题',
    content TEXT NOT NULL COMMENT '申诉内容',
    attachments TEXT COMMENT '附件URL列表(JSON)',
    status TINYINT DEFAULT 0 COMMENT '状态: 0-待审核, 1-审核中, 2-已通过, 3-已驳回',
    reviewer_id BIGINT COMMENT '审核人ID',
    review_time DATETIME COMMENT '审核时间',
    review_remark VARCHAR(500) COMMENT '审核备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user (user_id),
    INDEX idx_status (status),
    INDEX idx_violation (violation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='申诉表';

-- ==================== 卡口流量统计表 ====================
CREATE TABLE IF NOT EXISTS checkpoint_flow (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '记录ID',
    checkpoint_id BIGINT NOT NULL COMMENT '卡口ID',
    stat_date DATE NOT NULL COMMENT '统计日期',
    stat_hour TINYINT COMMENT '统计小时(0-23), NULL表示全天',
    total_count INT DEFAULT 0 COMMENT '总通行量',
    in_count INT DEFAULT 0 COMMENT '进城数量',
    out_count INT DEFAULT 0 COMMENT '出城数量',
    avg_speed DECIMAL(5, 2) COMMENT '平均速度(km/h)',
    peak_count INT DEFAULT 0 COMMENT '峰值流量',
    peak_time TIME COMMENT '峰值时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_checkpoint_date_hour (checkpoint_id, stat_date, stat_hour),
    INDEX idx_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='卡口流量统计表';

-- ==================== 初始数据 ====================

-- 插入角色
INSERT INTO sys_role (id, role_name, role_code, description, permissions) VALUES
(1, '系统管理员', 'admin', '系统管理员，拥有所有权限', '["*"]'),
(2, '普通车主', 'owner', '普通车主用户', '["owner:*"]'),
(3, '监控员', 'monitor', '交通监控人员', '["admin:view", "admin:realtime"]');

-- 插入测试用户 (密码都是 123456，BCrypt加密)
INSERT INTO sys_user (id, username, password, real_name, phone, role_id, status) VALUES
(1, 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iKyhK5qVPZP9.V.QVdVEpDhGqA6G', '系统管理员', '13800000001', 1, 1),
(2, 'owner', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iKyhK5qVPZP9.V.QVdVEpDhGqA6G', '张三', '13800000002', 2, 1),
(3, 'Art', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iKyhK5qVPZP9.V.QVdVEpDhGqA6G', '李明', '13800000003', 2, 1);

-- 插入19个真实卡口数据
INSERT INTO checkpoint (id, name, code, type, province, city, district, longitude, latitude, direction, road_name, lane_count, status) VALUES
-- 苏皖界卡口 (6个)
(1, '苏皖界1(104省道)', 'CP001', 'provincial', '江苏', '徐州', '铜山区', 117.1847, 34.0523, '双向', 'S104省道', 4, 1),
(2, '苏皖界2(311国道)', 'CP002', 'provincial', '江苏', '徐州', '铜山区', 117.0892, 34.1156, '双向', 'G311国道', 6, 1),
(3, '苏皖界3(徐明高速)', 'CP003', 'provincial', '江苏', '徐州', '睢宁县', 117.9234, 33.8901, '双向', '徐明高速', 4, 1),
(4, '苏皖界4(235国道)', 'CP004', 'provincial', '江苏', '徐州', '丰县', 116.5678, 34.5432, '双向', 'G235国道', 4, 1),
(5, '苏皖界5(301省道)', 'CP005', 'provincial', '江苏', '徐州', '丰县', 116.4521, 34.6123, '双向', 'S301省道', 4, 1),
(6, '苏皖界6(丰砀路)', 'CP006', 'provincial', '江苏', '徐州', '丰县', 116.3892, 34.5891, '双向', '丰砀路', 4, 1),

-- 苏鲁界卡口 (6个)
(7, '苏鲁界1(206国道)', 'CP007', 'provincial', '江苏', '徐州', '邳州市', 117.9634, 34.5123, '双向', 'G206国道', 6, 1),
(8, '苏鲁界2(310国道)', 'CP008', 'provincial', '江苏', '徐州', '邳州市', 118.0123, 34.4567, '双向', 'G310国道', 6, 1),
(9, '苏鲁界3(京沪高速)', 'CP009', 'provincial', '江苏', '徐州', '新沂市', 118.3456, 34.3789, '双向', '京沪高速', 8, 1),
(10, '苏鲁界4(205国道)', 'CP010', 'provincial', '江苏', '徐州', '新沂市', 118.3891, 34.4012, '双向', 'G205国道', 6, 1),
(11, '苏鲁界5(323省道)', 'CP011', 'provincial', '江苏', '徐州', '新沂市', 118.4234, 34.3567, '双向', 'S323省道', 4, 1),
(12, '苏鲁界6(沂河路)', 'CP012', 'provincial', '江苏', '徐州', '新沂市', 118.4567, 34.3234, '双向', '沂河路', 4, 1),

-- 连云港界卡口 (2个)
(13, '连云港界1(徐连高速)', 'CP013', 'municipal', '江苏', '徐州', '新沂市', 118.5123, 34.2891, '双向', '徐连高速', 6, 1),
(14, '连云港界2(249省道)', 'CP014', 'municipal', '江苏', '徐州', '睢宁县', 118.2345, 33.9876, '双向', 'S249省道', 4, 1),

-- 宿迁界卡口 (5个)
(15, '宿迁界1(徐宿高速)', 'CP015', 'municipal', '江苏', '徐州', '睢宁县', 117.8765, 33.9234, '双向', '徐宿高速', 6, 1),
(16, '宿迁界2(324省道)', 'CP016', 'municipal', '江苏', '徐州', '睢宁县', 117.7891, 33.8567, '双向', 'S324省道', 4, 1),
(17, '宿迁界3(104国道)', 'CP017', 'municipal', '江苏', '徐州', '铜山区', 117.2345, 34.0891, '双向', 'G104国道', 6, 1),
(18, '宿迁界4(251省道)', 'CP018', 'municipal', '江苏', '徐州', '铜山区', 117.3456, 34.0234, '双向', 'S251省道', 4, 1),
(19, '宿迁界5(沛丰路)', 'CP019', 'municipal', '江苏', '徐州', '沛县', 116.9234, 34.7123, '双向', '沛丰路', 4, 1);

-- 插入测试车辆数据
INSERT INTO vehicle (id, plate_number, plate_color, vehicle_type, owner_id, owner_name, owner_phone, etc_card_no, etc_status) VALUES
(1, '苏C12345', '蓝', '小型客车', 2, '张三', '13800000002', 'ETC320300001', 1),
(2, '苏CA6688', '蓝', '小型客车', 3, '李明', 'ETC320300002', 'ETC320300002', 1),
(3, '苏C88888', '黄', '大型货车', NULL, '王五', '13800000004', 'ETC320300003', 1),
(4, '苏CZ9999', '绿', '小型客车', NULL, '赵六', '13800000005', 'ETC320300004', 1);

-- 插入一些测试通行记录
INSERT INTO pass_record (plate_number, checkpoint_id, pass_time, direction, speed, lane_no, vehicle_type, etc_deduction) VALUES
('苏C12345', 1, DATE_SUB(NOW(), INTERVAL 2 HOUR), 'out', 85.5, 2, '小型客车', 25.00),
('苏C12345', 3, DATE_SUB(NOW(), INTERVAL 1 HOUR), 'in', 92.0, 1, '小型客车', 35.00),
('苏CA6688', 7, DATE_SUB(NOW(), INTERVAL 3 HOUR), 'out', 78.0, 3, '小型客车', 45.00),
('苏CA6688', 9, DATE_SUB(NOW(), INTERVAL 30 MINUTE), 'in', 88.5, 2, '小型客车', 55.00),
('苏C88888', 2, DATE_SUB(NOW(), INTERVAL 4 HOUR), 'out', 65.0, 4, '大型货车', 120.00),
('苏CZ9999', 15, DATE_SUB(NOW(), INTERVAL 5 HOUR), 'out', 95.0, 1, '小型客车', 30.00);

-- 插入今日流量统计
INSERT INTO checkpoint_flow (checkpoint_id, stat_date, stat_hour, total_count, in_count, out_count, avg_speed) VALUES
(1, CURDATE(), 8, 245, 120, 125, 78.5),
(1, CURDATE(), 9, 312, 156, 156, 72.3),
(1, CURDATE(), 10, 287, 140, 147, 75.8),
(7, CURDATE(), 8, 456, 230, 226, 82.1),
(7, CURDATE(), 9, 523, 260, 263, 79.5),
(9, CURDATE(), 8, 678, 340, 338, 85.2),
(9, CURDATE(), 9, 756, 380, 376, 81.7);
