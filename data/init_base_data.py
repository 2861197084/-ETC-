#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
初始化所有基础数据：checkpoint、sys_user、checkpoint_dim 等
"""

import pymysql
import hashlib

MYSQL_CONFIG = {
    'host': 'localhost',
    'port': 3307,  # 通过 ShardingSphere 插入，广播表会自动同步到所有节点
    'user': 'root',
    'password': 'root',
    'database': 'etc',
    'charset': 'utf8mb4'
}

# 19个卡口
CHECKPOINTS = [
    {"id": "CP001", "name": "苏皖界1(104省道)", "district": "睢宁县", "lat": 33.9851, "lng": 117.9423},
    {"id": "CP002", "name": "苏皖界2(311国道)", "district": "铜山区", "lat": 34.1872, "lng": 117.1834},
    {"id": "CP003", "name": "苏皖界3(徐明高速)", "district": "铜山区", "lat": 34.0534, "lng": 117.0823},
    {"id": "CP004", "name": "苏皖界4(宿新高速)", "district": "睢宁县", "lat": 33.8923, "lng": 117.8234},
    {"id": "CP005", "name": "苏皖界5(徐淮高速)", "district": "沛县", "lat": 34.7234, "lng": 116.9312},
    {"id": "CP006", "name": "苏皖界6(新扬高速)", "district": "新沂市", "lat": 34.3845, "lng": 118.3542},
    {"id": "CP007", "name": "苏鲁界1(206国道)", "district": "沛县", "lat": 34.8123, "lng": 116.8734},
    {"id": "CP008", "name": "苏鲁界2(104国道)", "district": "邳州市", "lat": 34.5234, "lng": 118.0123},
    {"id": "CP009", "name": "苏鲁界3(京台高速)", "district": "贾汪区", "lat": 34.4512, "lng": 117.4523},
    {"id": "CP010", "name": "苏鲁界4(枣庄连接线)", "district": "邳州市", "lat": 34.6123, "lng": 117.9534},
    {"id": "CP011", "name": "苏鲁界5(京沪高速)", "district": "邳州市", "lat": 34.5834, "lng": 118.1234},
    {"id": "CP012", "name": "苏鲁界6(沂河路)", "district": "新沂市", "lat": 34.4234, "lng": 118.4123},
    {"id": "CP013", "name": "连云港界1(徐连高速)", "district": "邳州市", "lat": 34.3123, "lng": 118.5234},
    {"id": "CP014", "name": "连云港界2(310国道)", "district": "邳州市", "lat": 34.2834, "lng": 118.4834},
    {"id": "CP015", "name": "宿迁界1(徐宿高速)", "district": "铜山区", "lat": 34.0123, "lng": 117.3234},
    {"id": "CP016", "name": "宿迁界2(徐宿快速)", "district": "铜山区", "lat": 33.9834, "lng": 117.2534},
    {"id": "CP017", "name": "宿迁界3(104国道)", "district": "睢宁县", "lat": 33.8534, "lng": 117.7234},
    {"id": "CP018", "name": "宿迁界4(新扬高速)", "district": "睢宁县", "lat": 33.8234, "lng": 117.8934},
    {"id": "CP019", "name": "宿迁界5(徐盐高速)", "district": "睢宁县", "lat": 33.7834, "lng": 118.0234},
]

def md5_password(password: str) -> str:
    return hashlib.md5(password.encode()).hexdigest()

def init_all():
    conn = pymysql.connect(**MYSQL_CONFIG)
    cursor = conn.cursor()
    
    print("=" * 50)
    print("    初始化所有基础数据")
    print("=" * 50)
    
    # 1. 初始化 checkpoint 表
    print("\n[1/4] 初始化 checkpoint 表...")
    cursor.execute("DELETE FROM checkpoint")
    for i, cp in enumerate(CHECKPOINTS):
        cursor.execute("""
            INSERT INTO checkpoint (id, code, name, district, city, province, latitude, longitude, 
                                    direction, road_name, lane_count, type, status, create_time)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, NOW())
        """, (i+1, cp['id'], cp['name'], cp['district'], '徐州市', '江苏省', 
              cp['lat'], cp['lng'], '双向', cp['name'], 4, '省界卡口', 1))
    print(f"   ✅ 插入 {len(CHECKPOINTS)} 个卡口")
    
    # 2. 初始化 checkpoint_dim 表
    print("\n[2/4] 初始化 checkpoint_dim 表...")
    cursor.execute("DELETE FROM checkpoint_dim")
    for cp in CHECKPOINTS:
        cursor.execute("""
            INSERT INTO checkpoint_dim (checkpoint_id, checkpoint_name, xzqhmc)
            VALUES (%s, %s, %s)
            ON DUPLICATE KEY UPDATE checkpoint_name = VALUES(checkpoint_name)
        """, (cp['id'], cp['name'], cp['district']))
    print(f"   ✅ 插入 {len(CHECKPOINTS)} 个卡口维度")
    
    # 3. 初始化 sys_user 表
    print("\n[3/4] 初始化 sys_user 表...")
    cursor.execute("DELETE FROM sys_user")
    users = [
        (1, 'admin', md5_password('admin123'), '管理员', 'admin@etc.com', '13800000001', 1, 1),
        (2, 'operator', md5_password('operator123'), '操作员', 'operator@etc.com', '13800000002', 2, 1),
        (3, 'viewer', md5_password('viewer123'), '查看员', 'viewer@etc.com', '13800000003', 3, 1),
    ]
    for u in users:
        cursor.execute("""
            INSERT INTO sys_user (id, username, password, real_name, email, phone, role_id, status, create_time, update_time)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, NOW(), NOW())
        """, u)
    print(f"   ✅ 插入 {len(users)} 个用户")
    print("      - admin / admin123")
    print("      - operator / operator123")
    print("      - viewer / viewer123")
    
    # 4. 检查通行记录（通过 ShardingSphere 查询逻辑表）
    print("\n[4/4] 检查通行记录...")
    cursor.execute("SELECT COUNT(*) FROM pass_record")
    total_count = cursor.fetchone()[0]
    print(f"   ✅ pass_record 总计: {total_count:,} 条")
    
    conn.commit()
    cursor.close()
    conn.close()
    
    print("\n" + "=" * 50)
    print("    ✅ 所有基础数据初始化完成!")
    print("=" * 50)
    print("\n现在可以刷新前端页面查看数据了")

if __name__ == '__main__':
    init_all()
