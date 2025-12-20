#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ETC 历史数据生成器

生成指定天数的历史通行记录，同时写入:
- MySQL 分片表 (pass_record_0, pass_record_1)
- HBase 表 (etc:pass_records)

用法:
    python history_generator.py                 # 生成30天历史数据，每天5000条
    python history_generator.py --days 7        # 生成7天
    python history_generator.py --days 30 --records-per-day 10000
"""

import random
import string
import argparse
import time
from datetime import datetime, timedelta

import pymysql
import happybase

# ============== 配置 ==============

MYSQL_CONFIG = {
    'host': 'localhost',
    'port': 33070,
    'user': 'root',
    'password': 'root',
    'database': 'etc',
    'charset': 'utf8mb4',
    'autocommit': False
}

HBASE_CONFIG = {
    'host': 'localhost',
    'port': 9090,
    'table': 'etc:pass_records'
}

# 19个卡口
CHECKPOINTS = [
    {"id": "CP001", "name": "苏皖界1(104省道)", "district": "睢宁县"},
    {"id": "CP002", "name": "苏皖界2(311国道)", "district": "铜山区"},
    {"id": "CP003", "name": "苏皖界3(徐明高速)", "district": "铜山区"},
    {"id": "CP004", "name": "苏皖界4(宿新高速)", "district": "睢宁县"},
    {"id": "CP005", "name": "苏皖界5(徐淮高速)", "district": "沛县"},
    {"id": "CP006", "name": "苏皖界6(新扬高速)", "district": "新沂市"},
    {"id": "CP007", "name": "苏鲁界1(206国道)", "district": "沛县"},
    {"id": "CP008", "name": "苏鲁界2(104国道)", "district": "邳州市"},
    {"id": "CP009", "name": "苏鲁界3(京台高速)", "district": "贾汪区"},
    {"id": "CP010", "name": "苏鲁界4(枣庄连接线)", "district": "邳州市"},
    {"id": "CP011", "name": "苏鲁界5(京沪高速)", "district": "邳州市"},
    {"id": "CP012", "name": "苏鲁界6(沂河路)", "district": "新沂市"},
    {"id": "CP013", "name": "连云港界1(徐连高速)", "district": "邳州市"},
    {"id": "CP014", "name": "连云港界2(310国道)", "district": "邳州市"},
    {"id": "CP015", "name": "宿迁界1(徐宿高速)", "district": "铜山区"},
    {"id": "CP016", "name": "宿迁界2(徐宿快速)", "district": "铜山区"},
    {"id": "CP017", "name": "宿迁界3(104国道)", "district": "睢宁县"},
    {"id": "CP018", "name": "宿迁界4(新扬高速)", "district": "睢宁县"},
    {"id": "CP019", "name": "宿迁界5(徐盐高速)", "district": "睢宁县"},
]

PLATE_PREFIXES = ["苏C", "苏C", "苏C", "苏C", "苏A", "苏B", "苏N", "苏H", "鲁Q", "鲁A", "皖L", "皖A", "豫N"]
VEHICLE_TYPES = ["小型客车", "小型客车", "小型客车", "中型客车", "小型货车", "大型货车"]
PLATE_TYPES = ["小型汽车号牌", "小型汽车号牌", "小型汽车号牌", "大型汽车号牌", "新能源小型汽车号牌"]
DIRECTIONS = ["进城", "出城"]

HOUR_WEIGHTS = {
    0: 0.02, 1: 0.01, 2: 0.01, 3: 0.01, 4: 0.02, 5: 0.03,
    6: 0.05, 7: 0.08, 8: 0.10, 9: 0.08, 10: 0.06, 11: 0.06,
    12: 0.05, 13: 0.05, 14: 0.06, 15: 0.06, 16: 0.07, 17: 0.09,
    18: 0.08, 19: 0.05, 20: 0.04, 21: 0.03, 22: 0.02, 23: 0.02
}


def generate_plate():
    prefix = random.choice(PLATE_PREFIXES)
    suffix = ''.join([
        random.choice(string.ascii_uppercase),
        random.choice(string.ascii_uppercase),
        str(random.randint(0, 9)),
        str(random.randint(0, 9)),
        str(random.randint(0, 9))
    ])
    return f"{prefix}{suffix}"


def weighted_hour():
    hours = list(HOUR_WEIGHTS.keys())
    weights = list(HOUR_WEIGHTS.values())
    return random.choices(hours, weights=weights, k=1)[0]


def generate_records_for_day(date: datetime, num_records: int, id_base: int) -> list:
    records = []
    gcxh_base = 300000000000 + id_base
    
    for i in range(num_records):
        checkpoint = random.choice(CHECKPOINTS)
        plate = generate_plate()
        plate_hash = abs(hash(plate)) % 2
        
        hour = weighted_hour()
        minute = random.randint(0, 59)
        second = random.randint(0, 59)
        pass_time = date.replace(hour=hour, minute=minute, second=second)
        
        record_id = id_base + i
        
        records.append({
            'id': record_id,
            'gcxh': f"G320300{gcxh_base + i}",
            'xzqhmc': checkpoint['district'],
            'kkmc': checkpoint['name'],
            'fxlx': random.choice(DIRECTIONS),
            'gcsj': pass_time,
            'hpzl': random.choice(PLATE_TYPES),
            'hp': plate,
            'clppxh': random.choice(VEHICLE_TYPES),
            'plate_hash': plate_hash,
            'checkpoint_id': checkpoint['id']
        })
    
    return records


def insert_to_mysql(conn, records: list):
    """批量插入 MySQL"""
    records_0 = [r for r in records if r['plate_hash'] == 0]
    records_1 = [r for r in records if r['plate_hash'] == 1]
    
    sql = """
        INSERT INTO pass_record_{shard} 
        (id, gcxh, xzqhmc, kkmc, fxlx, gcsj, hpzl, hp, clppxh, plate_hash, checkpoint_id)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
    """
    
    with conn.cursor() as cursor:
        if records_0:
            values = [(r['id'], r['gcxh'], r['xzqhmc'], r['kkmc'], r['fxlx'],
                       r['gcsj'], r['hpzl'], r['hp'], r['clppxh'], r['plate_hash'], r['checkpoint_id'])
                      for r in records_0]
            cursor.executemany(sql.format(shard=0), values)
        
        if records_1:
            values = [(r['id'], r['gcxh'], r['xzqhmc'], r['kkmc'], r['fxlx'],
                       r['gcsj'], r['hpzl'], r['hp'], r['clppxh'], r['plate_hash'], r['checkpoint_id'])
                      for r in records_1]
            cursor.executemany(sql.format(shard=1), values)
    
    conn.commit()


def insert_to_hbase(table, records: list):
    """批量插入 HBase"""
    batch = table.batch()
    
    for r in records:
        row_key = str(r['id']).encode('utf-8')
        data = {
            b'd:gcxh': r['gcxh'].encode('utf-8'),
            b'd:xzqhmc': r['xzqhmc'].encode('utf-8'),
            b'd:kkmc': r['kkmc'].encode('utf-8'),
            b'd:fxlx': r['fxlx'].encode('utf-8'),
            b'd:gcsj': r['gcsj'].strftime('%Y-%m-%d %H:%M:%S').encode('utf-8'),
            b'd:hpzl': r['hpzl'].encode('utf-8'),
            b'd:hp': r['hp'].encode('utf-8'),
            b'd:clppxh': r['clppxh'].encode('utf-8'),
            b'd:checkpoint_id': r['checkpoint_id'].encode('utf-8')
        }
        batch.put(row_key, data)
    
    batch.send()


def init_checkpoint_dim(conn):
    sql = """
        INSERT INTO checkpoint_dim (checkpoint_id, checkpoint_name, xzqhmc) 
        VALUES (%s, %s, %s)
        ON DUPLICATE KEY UPDATE checkpoint_name = VALUES(checkpoint_name)
    """
    with conn.cursor() as cursor:
        for cp in CHECKPOINTS:
            cursor.execute(sql, (cp['id'], cp['name'], cp['district']))
    conn.commit()
    print(f"✅ 初始化 {len(CHECKPOINTS)} 个卡口维度数据")


def main():
    parser = argparse.ArgumentParser(description='ETC 历史数据生成器')
    parser.add_argument('--days', type=int, default=30, help='生成多少天的数据 (默认: 30)')
    parser.add_argument('--records-per-day', type=int, default=5000, help='每天记录数 (默认: 5000)')
    args = parser.parse_args()
    
    print("=" * 50)
    print("        ETC 历史数据生成器")
    print("=" * 50)
    print(f"  天数: {args.days}")
    print(f"  每天记录数: {args.records_per_day}")
    print(f"  预计总记录数: {args.days * args.records_per_day:,}")
    print("=" * 50)
    
    # 连接 MySQL
    mysql_conn = pymysql.connect(**MYSQL_CONFIG)
    print(f"✅ MySQL 已连接: {MYSQL_CONFIG['host']}:{MYSQL_CONFIG['port']}")
    
    # 连接 HBase
    hbase_conn = None
    hbase_table = None
    try:
        hbase_conn = happybase.Connection(
            host=HBASE_CONFIG['host'],
            port=HBASE_CONFIG['port']
        )
        hbase_table = hbase_conn.table(HBASE_CONFIG['table'])
        print(f"✅ HBase 已连接: {HBASE_CONFIG['host']}:{HBASE_CONFIG['port']}")
    except Exception as e:
        print(f"⚠️ HBase 连接失败: {e}")
        print("   继续仅写入 MySQL...")
    
    try:
        # 初始化卡口维度
        init_checkpoint_dim(mysql_conn)
        
        # 生成历史数据
        end_date = datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)
        total_inserted = 0
        id_base = int(time.time() * 1000)
        
        for day in range(args.days, 0, -1):
            date = end_date - timedelta(days=day)
            records = generate_records_for_day(date, args.records_per_day, id_base)
            
            # 写入 MySQL
            insert_to_mysql(mysql_conn, records)
            
            # 写入 HBase
            if hbase_table is not None:
                insert_to_hbase(hbase_table, records)
            
            total_inserted += len(records)
            id_base += args.records_per_day
            
            hbase_status = "MySQL+HBase" if hbase_table else "MySQL"
            print(f"  {date.strftime('%Y-%m-%d')}: {len(records):,} 条 [{hbase_status}] (累计: {total_inserted:,})")
        
        print("=" * 50)
        print(f"✅ 完成! 共生成 {total_inserted:,} 条历史记录")
        if hbase_table:
            print("   已同步写入 MySQL + HBase")
        else:
            print("   仅写入 MySQL (HBase 未连接)")
        print("=" * 50)
        
    finally:
        mysql_conn.close()
        if hbase_conn:
            hbase_conn.close()


if __name__ == '__main__':
    main()
