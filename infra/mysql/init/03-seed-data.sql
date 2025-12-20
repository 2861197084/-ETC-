-- 初始化种子数据
USE etc;

-- 插入卡口维度数据
INSERT INTO checkpoint_dim (checkpoint_id, checkpoint_name, xzqhmc) VALUES
('CP001', '苏皖界1(104省道)', '睢宁县'),
('CP002', '苏皖界2(311国道)', '铜山区'),
('CP003', '苏皖界3(徐明高速)', '铜山区'),
('CP004', '苏皖界4(宿新高速)', '睢宁县'),
('CP005', '苏皖界5(徐淮高速)', '沛县'),
('CP006', '苏皖界6(新扬高速)', '新沂市'),
('CP007', '苏鲁界1(206国道)', '沛县'),
('CP008', '苏鲁界2(104国道)', '邳州市'),
('CP009', '苏鲁界3(京台高速)', '贾汪区'),
('CP010', '苏鲁界4(枣庄连接线)', '邳州市'),
('CP011', '苏鲁界5(京沪高速)', '邳州市'),
('CP012', '苏鲁界6(沂河路)', '新沂市'),
('CP013', '连云港界1(徐连高速)', '邳州市'),
('CP014', '连云港界2(310国道)', '邳州市'),
('CP015', '宿迁界1(徐宿高速)', '铜山区'),
('CP016', '宿迁界2(徐宿快速)', '铜山区'),
('CP017', '宿迁界3(104国道)', '睢宁县'),
('CP018', '宿迁界4(新扬高速)', '睢宁县'),
('CP019', '宿迁界5(徐盐高速)', '睢宁县')
ON DUPLICATE KEY UPDATE checkpoint_name = VALUES(checkpoint_name);

-- 存储过程：生成测试通行记录
DELIMITER //
CREATE PROCEDURE IF NOT EXISTS generate_test_pass_records(IN num_records INT)
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE checkpoint_id VARCHAR(64);
    DECLARE checkpoint_name VARCHAR(128);
    DECLARE district VARCHAR(128);
    DECLARE plate VARCHAR(32);
    DECLARE direction VARCHAR(32);
    DECLARE vehicle_type VARCHAR(32);
    DECLARE pass_time DATETIME;
    DECLARE plate_hash INT;
    DECLARE record_id BIGINT;
    
    -- 车牌前缀
    DECLARE prefixes VARCHAR(200) DEFAULT '苏C,苏A,苏B,苏N,苏H,鲁A,鲁B,鲁Q,皖A,皖L,豫A,豫N';
    DECLARE prefix VARCHAR(10);
    DECLARE suffix VARCHAR(10);
    
    -- 方向
    DECLARE directions VARCHAR(50) DEFAULT '进城,出城';
    
    -- 车辆类型
    DECLARE vehicle_types VARCHAR(200) DEFAULT '小型客车,中型客车,大型客车,小型货车,中型货车,大型货车';
    
    WHILE i < num_records DO
        -- 随机选择卡口
        SET @cp_idx = FLOOR(1 + RAND() * 19);
        SET checkpoint_id = CONCAT('CP', LPAD(@cp_idx, 3, '0'));
        
        SELECT cd.checkpoint_name, cd.xzqhmc INTO checkpoint_name, district
        FROM checkpoint_dim cd WHERE cd.checkpoint_id = checkpoint_id;
        
        IF checkpoint_name IS NULL THEN
            SET checkpoint_name = CONCAT('卡口', @cp_idx);
            SET district = '徐州市';
        END IF;
        
        -- 生成车牌
        SET @prefix_idx = FLOOR(1 + RAND() * 12);
        SET prefix = ELT(@prefix_idx, '苏C', '苏C', '苏C', '苏C', '苏A', '苏B', '苏N', '苏H', '鲁Q', '皖L', '豫N', '鲁A');
        SET suffix = CONCAT(
            CHAR(65 + FLOOR(RAND() * 26)),
            CHAR(65 + FLOOR(RAND() * 26)),
            FLOOR(RAND() * 10),
            FLOOR(RAND() * 10),
            FLOOR(RAND() * 10)
        );
        SET plate = CONCAT(prefix, suffix);
        SET plate_hash = ABS(CRC32(plate)) % 2;
        
        -- 随机时间（最近30天）
        SET pass_time = DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 30 * 24 * 60) MINUTE);
        
        -- 随机方向
        SET direction = IF(RAND() < 0.5, '进城', '出城');
        
        -- 随机车辆类型
        SET @vt_idx = FLOOR(1 + RAND() * 6);
        SET vehicle_type = ELT(@vt_idx, '小型客车', '小型客车', '小型客车', '中型客车', '小型货车', '大型货车');
        
        -- 生成ID
        SET record_id = UNIX_TIMESTAMP(pass_time) * 1000000 + i;
        
        -- 插入到对应分片表
        IF plate_hash = 0 THEN
            INSERT INTO pass_record_0 (id, gcxh, xzqhmc, kkmc, fxlx, gcsj, hpzl, hp, clppxh, plate_hash, checkpoint_id)
            VALUES (record_id, CONCAT('G320300', record_id), district, checkpoint_name, direction, pass_time, '小型汽车号牌', plate, vehicle_type, plate_hash, checkpoint_id);
        ELSE
            INSERT INTO pass_record_1 (id, gcxh, xzqhmc, kkmc, fxlx, gcsj, hpzl, hp, clppxh, plate_hash, checkpoint_id)
            VALUES (record_id, CONCAT('G320300', record_id), district, checkpoint_name, direction, pass_time, '小型汽车号牌', plate, vehicle_type, plate_hash, checkpoint_id);
        END IF;
        
        SET i = i + 1;
        
        -- 每1000条提交一次
        IF i % 1000 = 0 THEN
            SELECT CONCAT('已生成 ', i, ' 条记录') AS progress;
        END IF;
    END WHILE;
    
    SELECT CONCAT('完成! 共生成 ', num_records, ' 条记录') AS result;
END //
DELIMITER ;

-- 调用存储过程生成10000条测试数据
CALL generate_test_pass_records(10000);
