"""
Vanna 训练数据 - ETC 高速公路大数据管理平台

基于实际数据库结构生成的训练集
"""

# ==================== DDL 表结构 ====================

DDL_STATEMENTS = [
    # 1. 通行记录表（核心表，41万+记录）
    """
    -- 通行记录表 (主表，存储所有车辆通行数据)
    CREATE TABLE pass_record (
        id BIGINT PRIMARY KEY COMMENT '主键ID',
        gcxh VARCHAR(64) NOT NULL COMMENT '过车序号，全局唯一标识',
        xzqhmc VARCHAR(128) NOT NULL COMMENT '行政区划名称，如：铜山区、睢宁县、新沂市、丰县、邳州市、沛县',
        kkmc VARCHAR(128) NOT NULL COMMENT '卡口名称，完整的卡口描述',
        fxlx VARCHAR(32) NOT NULL COMMENT '方向类型：进城、出城、1(进城)、2(出城)',
        gcsj DATETIME NOT NULL COMMENT '过车时间，车辆通过卡口的时间',
        hpzl VARCHAR(32) NOT NULL COMMENT '号牌种类：01(大型汽车)、02(小型汽车)、51(大型新能源)、52(小型新能源)',
        hp VARCHAR(32) NOT NULL COMMENT '车牌号码，如：苏C12345、苏CN9P7E',
        clppxh VARCHAR(128) NOT NULL COMMENT '车辆品牌型号，如：福特福克斯、特斯拉Model3、比亚迪秦',
        plate_hash INT NOT NULL COMMENT '车牌哈希值，用于分片',
        checkpoint_id VARCHAR(64) COMMENT '卡口编码：CP001-CP019'
    );
    """,
    
    # 2. 卡口信息表（19个卡口）
    """
    -- 卡口信息表 (徐州市19个省界/市际卡口)
    CREATE TABLE checkpoint (
        id BIGINT PRIMARY KEY COMMENT '主键ID',
        code VARCHAR(64) NOT NULL COMMENT '卡口编码：CP001-CP019',
        name VARCHAR(128) NOT NULL COMMENT '卡口名称，如：苏皖界1(104省道)',
        district VARCHAR(64) COMMENT '所属区县：铜山区、睢宁县、新沂市、丰县、邳州市、沛县',
        city VARCHAR(64) COMMENT '所属城市：徐州市',
        province VARCHAR(64) COMMENT '所属省份：江苏省',
        latitude DECIMAL(10,6) COMMENT '纬度',
        longitude DECIMAL(10,6) COMMENT '经度',
        direction VARCHAR(32) COMMENT '方向：双向',
        road_name VARCHAR(128) COMMENT '道路名称',
        lane_count INT COMMENT '车道数量',
        type VARCHAR(32) COMMENT '卡口类型：省界卡口、市界卡口',
        status TINYINT COMMENT '状态：1正常、0停用',
        create_time DATETIME COMMENT '创建时间',
        update_time DATETIME COMMENT '更新时间'
    );
    """,
    
    # 3. 套牌检测表（1226条记录）
    """
    -- 套牌车辆检测结果表
    CREATE TABLE clone_plate_detection (
        id BIGINT PRIMARY KEY COMMENT '主键ID',
        plate_number VARCHAR(32) NOT NULL COMMENT '嫌疑套牌车牌号',
        checkpoint_id_1 VARCHAR(64) COMMENT '第一次出现的卡口ID',
        checkpoint_id_2 VARCHAR(64) COMMENT '第二次出现的卡口ID',
        time_1 DATETIME COMMENT '第一次出现时间',
        time_2 DATETIME COMMENT '第二次出现时间',
        distance_km DECIMAL(10,2) COMMENT '两个卡口之间的距离(公里)',
        time_diff_minutes INT COMMENT '两次出现的时间差(分钟)',
        min_speed_required DECIMAL(6,2) COMMENT '如果是同一辆车所需的最低时速(km/h)',
        confidence_score DECIMAL(5,2) COMMENT '套牌置信度分数',
        status VARCHAR(20) COMMENT '状态：pending(待处理)、confirmed(已确认)、dismissed(已排除)',
        create_time DATETIME COMMENT '检测时间'
    );
    """,
    
    # 4. 其他业务表
    """
    -- 告警表
    CREATE TABLE alert (
        id BIGINT PRIMARY KEY,
        alert_type VARCHAR(32) NOT NULL COMMENT '告警类型',
        severity VARCHAR(16) COMMENT '严重程度',
        plate_number VARCHAR(32) COMMENT '相关车牌',
        checkpoint_id BIGINT COMMENT '相关卡口',
        description TEXT COMMENT '告警描述',
        status VARCHAR(20) COMMENT '状态：pending、processed、ignored',
        processed_by BIGINT COMMENT '处理人ID',
        processed_at DATETIME COMMENT '处理时间',
        created_at DATETIME COMMENT '创建时间'
    );
    
    -- 违章记录表
    CREATE TABLE violation (
        id BIGINT PRIMARY KEY,
        plate_number VARCHAR(32) NOT NULL COMMENT '违章车牌',
        violation_type VARCHAR(64) NOT NULL COMMENT '违章类型',
        checkpoint_id VARCHAR(64) COMMENT '违章卡口ID',
        checkpoint_name VARCHAR(128) COMMENT '违章卡口名称',
        violation_time DATETIME COMMENT '违章时间',
        fine_amount DECIMAL(10,2) COMMENT '罚款金额',
        status VARCHAR(20) COMMENT '状态：pending、paid、appealed',
        create_time DATETIME COMMENT '创建时间'
    );
    
    -- 申诉记录表
    CREATE TABLE appeal (
        id BIGINT PRIMARY KEY,
        violation_id BIGINT COMMENT '关联的违章ID',
        plate_number VARCHAR(32) NOT NULL COMMENT '申诉车牌',
        appeal_type VARCHAR(32) COMMENT '申诉类型',
        appeal_reason TEXT COMMENT '申诉理由',
        status VARCHAR(20) COMMENT '状态：pending、approved、rejected',
        create_time DATETIME COMMENT '申诉时间'
    );
    """
]

# ==================== 业务文档 ====================

DOCUMENTATION = """
## ETC 高速公路大数据管理平台 - 数据库说明

### 一、数据概览
- 徐州市 19 个省际/市际卡口的 ETC 通行数据
- 通行记录总量：41万+ 条
- 套牌检测记录：1200+ 条

### 二、核心表 pass_record 字段说明
- hp: 车牌号码，如 苏C12345、苏CN9P7E（苏C开头为本地车）
- gcsj: 过车时间（datetime类型）
- xzqhmc: 行政区划（铜山区、睢宁县、新沂市、丰县、邳州市、沛县）
- checkpoint_id: 卡口编码（CP001-CP019）
- fxlx: 方向（进城、出城、1、2）
- hpzl: 号牌种类（01大型汽车、02小型汽车、51大型新能源、52小型新能源）
- clppxh: 车辆品牌型号

### 三、方向编码
- 进城 或 1：进入徐州市区
- 出城 或 2：离开徐州市区

### 四、区县数据分布（按通行量排序）
1. 铜山区：约 10.5万 条（25%）
2. 睢宁县：约 8.8万 条（21%）
3. 新沂市：约 8.4万 条（20%）
4. 丰县：约 6.3万 条（15%）
5. 邳州市：约 4.3万 条（10%）
6. 沛县：约 2.5万 条（6%）

### 五、套牌检测 clone_plate_detection
- status: pending(待处理)、confirmed(已确认)、dismissed(已排除)
- 检测原理：同一车牌短时间内出现在距离远的卡口
"""

# ==================== 示例问答对（80+个） ====================

EXAMPLE_QA_PAIRS = [
    # ========== 基础统计（无日期条件 - 查全部历史数据） ==========
    {"question": "总车流量是多少", "sql": "SELECT COUNT(*) as total FROM pass_record"},
    {"question": "有多少通行记录", "sql": "SELECT COUNT(*) as total FROM pass_record"},
    {"question": "数据库总共有多少通行记录", "sql": "SELECT COUNT(*) as total FROM pass_record"},
    {"question": "查询通行记录数量", "sql": "SELECT COUNT(*) as count FROM pass_record"},
    {"question": "统计通行记录总数", "sql": "SELECT COUNT(*) as count FROM pass_record"},
    
    # ========== 带"今天"关键词的才加 CURDATE() ==========
    {"question": "今天的总车流量是多少", "sql": "SELECT COUNT(*) as total FROM pass_record WHERE DATE(gcsj) = CURDATE()"},
    {"question": "查询今天有多少车辆通行", "sql": "SELECT COUNT(*) as count FROM pass_record WHERE DATE(gcsj) = CURDATE()"},
    {"question": "统计今日通行记录数量", "sql": "SELECT COUNT(*) as count FROM pass_record WHERE DATE(gcsj) = CURDATE()"},
    {"question": "昨天的车流量", "sql": "SELECT COUNT(*) as total FROM pass_record WHERE DATE(gcsj) = DATE_SUB(CURDATE(), INTERVAL 1 DAY)"},
    {"question": "最近7天的总通行量", "sql": "SELECT COUNT(*) as total FROM pass_record WHERE gcsj >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)"},
    
    # ========== 卡口流量排名（默认查全部） ==========
    {"question": "各卡口的车流量排名", "sql": "SELECT c.name as checkpoint_name, COUNT(*) as flow_count FROM pass_record p LEFT JOIN checkpoint c ON p.checkpoint_id = c.code GROUP BY p.checkpoint_id, c.name ORDER BY flow_count DESC"},
    {"question": "查询各卡口通行量", "sql": "SELECT checkpoint_id, COUNT(*) as count FROM pass_record GROUP BY checkpoint_id ORDER BY count DESC"},
    {"question": "哪个卡口车流量最大", "sql": "SELECT checkpoint_id, COUNT(*) as count FROM pass_record GROUP BY checkpoint_id ORDER BY count DESC LIMIT 1"},
    {"question": "卡口流量排名", "sql": "SELECT checkpoint_id, COUNT(*) as count FROM pass_record GROUP BY checkpoint_id ORDER BY count DESC"},
    {"question": "今天各卡口的车流量排名", "sql": "SELECT c.name as checkpoint_name, COUNT(*) as flow_count FROM pass_record p LEFT JOIN checkpoint c ON p.checkpoint_id = c.code WHERE DATE(p.gcsj) = CURDATE() GROUP BY p.checkpoint_id, c.name ORDER BY flow_count DESC"},
    {"question": "CP001卡口的通行量", "sql": "SELECT COUNT(*) as count FROM pass_record WHERE checkpoint_id = 'CP001'"},
    {"question": "苏皖界卡口的流量", "sql": "SELECT c.name, COUNT(*) as count FROM pass_record p JOIN checkpoint c ON p.checkpoint_id = c.code WHERE c.type = '省界卡口' GROUP BY c.name ORDER BY count DESC"},
    
    # ========== 区县统计（默认查全部） ==========
    {"question": "各区县的车流量对比", "sql": "SELECT xzqhmc as district, COUNT(*) as count FROM pass_record GROUP BY xzqhmc ORDER BY count DESC"},
    {"question": "哪个区县车流量最多", "sql": "SELECT xzqhmc as district, COUNT(*) as count FROM pass_record GROUP BY xzqhmc ORDER BY count DESC LIMIT 1"},
    {"question": "区县流量排名", "sql": "SELECT xzqhmc as district, COUNT(*) as count FROM pass_record GROUP BY xzqhmc ORDER BY count DESC"},
    {"question": "铜山区的通行量", "sql": "SELECT COUNT(*) as count FROM pass_record WHERE xzqhmc = '铜山区'"},
    {"question": "睢宁县的车流量", "sql": "SELECT COUNT(*) as count FROM pass_record WHERE xzqhmc = '睢宁县'"},
    {"question": "新沂市通行数据", "sql": "SELECT COUNT(*) as count FROM pass_record WHERE xzqhmc = '新沂市'"},
    {"question": "丰县车流量统计", "sql": "SELECT COUNT(*) as count FROM pass_record WHERE xzqhmc = '丰县'"},
    {"question": "邳州市通行量", "sql": "SELECT COUNT(*) as count FROM pass_record WHERE xzqhmc = '邳州市'"},
    {"question": "沛县的车流", "sql": "SELECT COUNT(*) as count FROM pass_record WHERE xzqhmc = '沛县'"},
    {"question": "铜山区今天的通行量", "sql": "SELECT COUNT(*) as count FROM pass_record WHERE xzqhmc = '铜山区' AND DATE(gcsj) = CURDATE()"},
    
    # ========== 方向统计（默认查全部） ==========
    {"question": "进城和出城的车辆数量", "sql": "SELECT CASE WHEN fxlx IN ('进城', '1') THEN '进城' ELSE '出城' END as direction, COUNT(*) as count FROM pass_record GROUP BY CASE WHEN fxlx IN ('进城', '1') THEN '进城' ELSE '出城' END"},
    {"question": "进城车辆有多少", "sql": "SELECT COUNT(*) as count FROM pass_record WHERE fxlx IN ('进城', '1')"},
    {"question": "出城车辆统计", "sql": "SELECT COUNT(*) as count FROM pass_record WHERE fxlx IN ('出城', '2')"},
    {"question": "方向分布", "sql": "SELECT fxlx, COUNT(*) as count FROM pass_record GROUP BY fxlx ORDER BY count DESC"},
    {"question": "今天进城和出城的车辆数量", "sql": "SELECT CASE WHEN fxlx IN ('进城', '1') THEN '进城' ELSE '出城' END as direction, COUNT(*) as count FROM pass_record WHERE DATE(gcsj) = CURDATE() GROUP BY CASE WHEN fxlx IN ('进城', '1') THEN '进城' ELSE '出城' END"},
    
    # ========== 本地/外地车辆（默认查全部） ==========
    {"question": "本地车辆和外地车辆的占比", "sql": "SELECT CASE WHEN hp LIKE '苏C%' THEN '本地车辆' ELSE '外地车辆' END as type, COUNT(*) as count FROM pass_record GROUP BY CASE WHEN hp LIKE '苏C%' THEN '本地车辆' ELSE '外地车辆' END"},
    {"question": "本地车有多少", "sql": "SELECT COUNT(*) as count FROM pass_record WHERE hp LIKE '苏C%'"},
    {"question": "外地车辆数量", "sql": "SELECT COUNT(*) as count FROM pass_record WHERE hp NOT LIKE '苏C%'"},
    {"question": "外省车辆来源分布", "sql": "SELECT LEFT(hp, 2) as province, COUNT(*) as count FROM pass_record WHERE hp NOT LIKE '苏C%' GROUP BY LEFT(hp, 2) ORDER BY count DESC LIMIT 10"},
    {"question": "山东车辆有多少", "sql": "SELECT COUNT(*) as count FROM pass_record WHERE hp LIKE '鲁%'"},
    {"question": "安徽车辆通行量", "sql": "SELECT COUNT(*) as count FROM pass_record WHERE hp LIKE '皖%'"},
    {"question": "河南车辆统计", "sql": "SELECT COUNT(*) as count FROM pass_record WHERE hp LIKE '豫%'"},
    
    # ========== 车辆类型（默认查全部） ==========
    {"question": "各车辆类型的通行量", "sql": "SELECT CASE hpzl WHEN '01' THEN '大型汽车' WHEN '02' THEN '小型汽车' WHEN '51' THEN '大型新能源' WHEN '52' THEN '小型新能源' ELSE '其他' END as type, COUNT(*) as count FROM pass_record GROUP BY hpzl ORDER BY count DESC"},
    {"question": "小型汽车有多少", "sql": "SELECT COUNT(*) as count FROM pass_record WHERE hpzl = '02'"},
    {"question": "大型汽车通行量", "sql": "SELECT COUNT(*) as count FROM pass_record WHERE hpzl = '01'"},
    {"question": "新能源车辆统计", "sql": "SELECT COUNT(*) as count FROM pass_record WHERE hpzl IN ('51', '52')"},
    {"question": "货车通行量", "sql": "SELECT COUNT(*) as count FROM pass_record WHERE hpzl = '01'"},
    {"question": "车辆类型分布", "sql": "SELECT hpzl, COUNT(*) as count FROM pass_record GROUP BY hpzl ORDER BY count DESC"},
    
    # ========== 时间维度分析 ==========
    {"question": "按小时统计车流量", "sql": "SELECT HOUR(gcsj) as hour, COUNT(*) as count FROM pass_record GROUP BY HOUR(gcsj) ORDER BY hour"},
    {"question": "哪个时段车流量最大", "sql": "SELECT HOUR(gcsj) as hour, COUNT(*) as count FROM pass_record GROUP BY HOUR(gcsj) ORDER BY count DESC LIMIT 1"},
    {"question": "按日期统计车流量", "sql": "SELECT DATE(gcsj) as date, COUNT(*) as count FROM pass_record GROUP BY DATE(gcsj) ORDER BY date DESC LIMIT 30"},
    {"question": "每天的车流量趋势", "sql": "SELECT DATE(gcsj) as date, COUNT(*) as count FROM pass_record GROUP BY DATE(gcsj) ORDER BY date"},
    {"question": "早高峰车流量", "sql": "SELECT COUNT(*) as count FROM pass_record WHERE HOUR(gcsj) BETWEEN 7 AND 9"},
    {"question": "晚高峰通行量", "sql": "SELECT COUNT(*) as count FROM pass_record WHERE HOUR(gcsj) BETWEEN 17 AND 19"},
    {"question": "凌晨时段车流", "sql": "SELECT COUNT(*) as count FROM pass_record WHERE HOUR(gcsj) BETWEEN 0 AND 5"},
    
    # ========== 车辆轨迹查询 ==========
    {"question": "查询苏C12345的通行记录", "sql": "SELECT gcsj, kkmc, fxlx, xzqhmc FROM pass_record WHERE hp = '苏C12345' ORDER BY gcsj DESC LIMIT 100"},
    {"question": "查询车牌苏CN9P7E的轨迹", "sql": "SELECT gcsj, checkpoint_id, kkmc, fxlx FROM pass_record WHERE hp = '苏CN9P7E' ORDER BY gcsj DESC"},
    {"question": "最近的通行记录", "sql": "SELECT hp, gcsj, kkmc, fxlx FROM pass_record ORDER BY gcsj DESC LIMIT 10"},
    
    # ========== 套牌检测 ==========
    {"question": "查询套牌嫌疑车辆", "sql": "SELECT plate_number, status, checkpoint_id_1, time_1, checkpoint_id_2, time_2, time_diff_minutes FROM clone_plate_detection ORDER BY create_time DESC LIMIT 50"},
    {"question": "待处理的套牌车辆有多少", "sql": "SELECT COUNT(*) as count FROM clone_plate_detection WHERE status = 'pending'"},
    {"question": "套牌嫌疑车有多少", "sql": "SELECT COUNT(*) as count FROM clone_plate_detection"},
    {"question": "已确认的套牌车辆", "sql": "SELECT plate_number, time_1, time_2 FROM clone_plate_detection WHERE status = 'confirmed' ORDER BY create_time DESC"},
    {"question": "套牌检测统计", "sql": "SELECT status, COUNT(*) as count FROM clone_plate_detection GROUP BY status"},
    
    # ========== 卡口信息 ==========
    {"question": "查询所有卡口", "sql": "SELECT code, name, district, type FROM checkpoint ORDER BY code"},
    {"question": "有多少个卡口", "sql": "SELECT COUNT(*) as count FROM checkpoint"},
    {"question": "铜山区有哪些卡口", "sql": "SELECT code, name, type FROM checkpoint WHERE district = '铜山区'"},
    {"question": "省界卡口有哪些", "sql": "SELECT code, name, district FROM checkpoint WHERE type = '省界卡口'"},
    {"question": "市界卡口列表", "sql": "SELECT code, name, district FROM checkpoint WHERE type = '市界卡口'"},
    
    # ========== 品牌分析（默认查全部） ==========
    {"question": "最常见的车辆品牌", "sql": "SELECT clppxh, COUNT(*) as count FROM pass_record GROUP BY clppxh ORDER BY count DESC LIMIT 10"},
    {"question": "特斯拉车辆有多少", "sql": "SELECT COUNT(*) as count FROM pass_record WHERE clppxh LIKE '%特斯拉%'"},
    {"question": "比亚迪车辆统计", "sql": "SELECT COUNT(*) as count FROM pass_record WHERE clppxh LIKE '%比亚迪%'"},
    {"question": "品牌排名", "sql": "SELECT clppxh, COUNT(*) as count FROM pass_record GROUP BY clppxh ORDER BY count DESC LIMIT 20"},
    
    # ========== 综合分析 ==========
    {"question": "最早的通行记录", "sql": "SELECT MIN(gcsj) as earliest FROM pass_record"},
    {"question": "最新的通行记录", "sql": "SELECT MAX(gcsj) as latest FROM pass_record"},
    {"question": "数据时间范围", "sql": "SELECT MIN(gcsj) as earliest, MAX(gcsj) as latest FROM pass_record"},
    {"question": "通行次数最多的车牌", "sql": "SELECT hp, COUNT(*) as count FROM pass_record GROUP BY hp ORDER BY count DESC LIMIT 10"},
    {"question": "高频车辆", "sql": "SELECT hp, COUNT(*) as count FROM pass_record GROUP BY hp ORDER BY count DESC LIMIT 20"},
]
