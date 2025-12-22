#!/usr/bin/env python3
"""
历史数据生成脚本

直接写入 MySQL (通过 ShardingSphere) 和 HBase
不走 Kafka，避免 Flink 资源问题
"""
import os
import sys
import random
import logging
import argparse
import hashlib
from datetime import datetime, timedelta, timezone

import pymysql
import happybase

# 北京时区 UTC+8
BEIJING_TZ = timezone(timedelta(hours=8))

# 添加父目录到路径
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# 复用 realtime_generator 的配置
from scripts.realtime_generator import (
    CHECKPOINTS, PLATE_PREFIXES_LOCAL, PLATE_PREFIXES_FOREIGN,
    DIRECTIONS, PLATE_TYPES, CAR_BRANDS, TIME_FORMAT
)

# 数据库配置
SHARDINGSPHERE_HOST = os.getenv("MYSQL_HOST", "shardingsphere")
SHARDINGSPHERE_PORT = int(os.getenv("MYSQL_PORT", "3307"))
MYSQL_USER = os.getenv("MYSQL_USER", "root")
MYSQL_PASSWORD = os.getenv("MYSQL_PASSWORD", "root")
HBASE_HOST = os.getenv("HBASE_THRIFT_HOST", "hbase")
HBASE_PORT = int(os.getenv("HBASE_THRIFT_PORT", "9090"))

# 批量插入大小
BATCH_SIZE = 1000


def get_mysql_conn():
    """创建 MySQL 连接 (通过 ShardingSphere)"""
    return pymysql.connect(
        host=SHARDINGSPHERE_HOST,
        port=SHARDINGSPHERE_PORT,
        user=MYSQL_USER,
        password=MYSQL_PASSWORD,
        database="etc",
        charset="utf8mb4",
        autocommit=False,
    )


def get_hbase_conn():
    """创建 HBase 连接"""
    return happybase.Connection(host=HBASE_HOST, port=HBASE_PORT)


def generate_plate() -> tuple[str, bool]:
    """生成随机车牌，返回(车牌号, 是否本地车)"""
    is_local = random.random() < 0.7
    if is_local:
        prefix = random.choice(PLATE_PREFIXES_LOCAL)
    else:
        prefix = random.choice(PLATE_PREFIXES_FOREIGN)
    chars = '0123456789ABCDEFGHJKLMNPQRSTUVWXYZ'
    suffix = ''.join(random.choices(chars, k=5))
    return f"{prefix}{suffix}", is_local


def plate_hash(plate: str) -> int:
    """计算车牌哈希值（用于分片）"""
    return int(hashlib.md5(plate.encode()).hexdigest()[:8], 16) % 1000000


def generate_record_at_time(pass_time: datetime, seq: int) -> dict:
    """生成指定时间的通行记录"""
    cp_id = random.choice(list(CHECKPOINTS.keys()))
    cp_name, district = CHECKPOINTS[cp_id]
    plate, _ = generate_plate()
    
    # 生成唯一ID (时间戳 + 序号)
    record_id = int(pass_time.timestamp() * 1000) * 10000 + seq
    gcxh = f"H{int(pass_time.timestamp() * 1000) % 100000000}{seq:04d}"
    
    return {
        "id": record_id,
        "gcxh": gcxh,
        "xzqhmc": district,
        "kkmc": cp_name,
        "fxlx": random.choice(DIRECTIONS),
        "gcsj": pass_time.strftime(TIME_FORMAT),
        "hpzl": random.choice(PLATE_TYPES),
        "hp": plate,
        "clppxh": random.choice(CAR_BRANDS),
        "plate_hash": plate_hash(plate),
        "checkpoint_id": cp_id,
    }


def batch_insert_mysql(conn, records: list):
    """批量插入 MySQL"""
    if not records:
        return 0
    
    sql = """
        INSERT INTO pass_record 
        (id, gcxh, xzqhmc, kkmc, fxlx, gcsj, hpzl, hp, clppxh, plate_hash, checkpoint_id)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        ON DUPLICATE KEY UPDATE gcxh=gcxh
    """
    
    values = [
        (r["id"], r["gcxh"], r["xzqhmc"], r["kkmc"], r["fxlx"], 
         r["gcsj"], r["hpzl"], r["hp"], r["clppxh"], r["plate_hash"], r["checkpoint_id"])
        for r in records
    ]
    
    with conn.cursor() as cursor:
        cursor.executemany(sql, values)
    conn.commit()
    return len(records)


def stable_hash_mod(s: str, mod: int) -> int:
    """和 Flink 一致的哈希函数"""
    if not s:
        return 0
    digest = hashlib.md5(s.encode('utf-8')).digest()
    value = (digest[0] << 24) | (digest[1] << 16) | (digest[2] << 8) | digest[3]
    return abs(value) % mod


def generate_hbase_rowkey(record: dict) -> str:
    """
    生成 HBase RowKey，格式和 Flink 一致:
    {salt}{yyyyMMdd}{checkpoint_hash}{reverse_ts}{plate_hash}
    """
    plate = record.get("hp", "")
    pass_time = record.get("gcsj", "")
    checkpoint_id = record.get("checkpoint_id", "")
    
    # salt: plate hash mod 10
    salt = stable_hash_mod(plate, 10)
    
    # date: yyyyMMdd
    date_str = pass_time[:10].replace("-", "") if len(pass_time) >= 10 else "20240101"
    
    # reverse timestamp
    try:
        dt = datetime.strptime(pass_time, TIME_FORMAT)
        ts = int(dt.timestamp() * 1000)
        reverse_ts = 9999999999999 - ts
    except:
        reverse_ts = 9999999999999
    
    # checkpoint hash
    checkpoint_hash = stable_hash_mod(checkpoint_id, 100000000)
    
    # plate hash
    plate_hash = stable_hash_mod(plate, 10000)
    
    return f"{salt}{date_str}{checkpoint_hash:08d}{reverse_ts:013d}{plate_hash:04d}"


def batch_insert_hbase(conn, records: list):
    """批量插入 HBase"""
    if not records:
        return 0
    
    try:
        table = conn.table('etc:pass_record')
        
        with table.batch(batch_size=BATCH_SIZE) as batch:
            for r in records:
                rowkey = generate_hbase_rowkey(r)
                
                data = {
                    b'd:hp': r["hp"].encode(),
                    b'd:gcsj': r["gcsj"].encode(),
                    b'd:kkmc': r["kkmc"].encode(),
                    b'd:checkpoint_id': r["checkpoint_id"].encode(),
                    b'd:xzqhmc': r["xzqhmc"].encode(),
                    b'd:fxlx': r["fxlx"].encode(),
                    b'd:hpzl': r["hpzl"].encode(),
                    b'd:clppxh': r["clppxh"].encode(),
                }
                batch.put(rowkey.encode(), data)
        
        return len(records)
    except Exception as e:
        logger.warning(f"HBase 写入失败: {e}")
        return 0


def generate_history(start_time: datetime, end_time: datetime, records_per_second: int = 50):
    """生成指定时间范围的历史数据，直接写入数据库"""
    
    # 去除时区信息用于计算
    if start_time.tzinfo:
        start_time = start_time.replace(tzinfo=None)
    if end_time.tzinfo:
        end_time = end_time.replace(tzinfo=None)
    
    total_seconds = int((end_time - start_time).total_seconds())
    total_records = total_seconds * records_per_second
    
    logger.info("=" * 50)
    logger.info("ETC 历史数据生成器 (直接写入数据库)")
    logger.info("=" * 50)
    logger.info(f"时间范围: {start_time} ~ {end_time}")
    logger.info(f"总时长: {total_seconds} 秒 ({total_seconds / 3600:.1f} 小时)")
    logger.info(f"每秒记录数: {records_per_second}")
    logger.info(f"预计生成: {total_records:,} 条记录")
    logger.info(f"MySQL: {SHARDINGSPHERE_HOST}:{SHARDINGSPHERE_PORT}")
    logger.info(f"HBase: {HBASE_HOST}:{HBASE_PORT}")
    
    # 连接数据库
    try:
        mysql_conn = get_mysql_conn()
        logger.info("✅ MySQL (ShardingSphere) 连接成功")
    except Exception as e:
        logger.error(f"❌ MySQL 连接失败: {e}")
        return
    
    try:
        hbase_conn = get_hbase_conn()
        hbase_conn.open()
        logger.info("✅ HBase 连接成功")
    except Exception as e:
        logger.warning(f"⚠️ HBase 连接失败: {e}，将只写入 MySQL")
        hbase_conn = None
    
    total_mysql = 0
    total_hbase = 0
    batch_records = []
    current_time = start_time
    seq = 0
    
    try:
        while current_time < end_time:
            # 生成这一秒的记录
            for i in range(records_per_second):
                # 在这一秒内随机分布
                offset_ms = random.randint(0, 999)
                record_time = current_time + timedelta(milliseconds=offset_ms)
                
                record = generate_record_at_time(record_time, seq)
                batch_records.append(record)
                seq += 1
            
            # 每 BATCH_SIZE 条写入一次
            if len(batch_records) >= BATCH_SIZE:
                # 写入 MySQL
                try:
                    total_mysql += batch_insert_mysql(mysql_conn, batch_records)
                except Exception as e:
                    logger.error(f"MySQL 批量写入失败: {e}")
                    mysql_conn = get_mysql_conn()  # 重连
                
                # 写入 HBase
                if hbase_conn:
                    total_hbase += batch_insert_hbase(hbase_conn, batch_records)
                
                # 输出进度
                progress = (current_time - start_time).total_seconds() / total_seconds * 100
                logger.info(f"📊 进度: {progress:.1f}% | MySQL: {total_mysql:,} | HBase: {total_hbase:,} | 当前时间: {current_time}")
                
                batch_records = []
            
            current_time += timedelta(seconds=1)
        
        # 处理剩余数据
        if batch_records:
            try:
                total_mysql += batch_insert_mysql(mysql_conn, batch_records)
            except Exception as e:
                logger.error(f"MySQL 批量写入失败: {e}")
            
            if hbase_conn:
                total_hbase += batch_insert_hbase(hbase_conn, batch_records)
        
        logger.info("=" * 50)
        logger.info(f"✅ 完成！")
        logger.info(f"   MySQL 写入: {total_mysql:,} 条")
        logger.info(f"   HBase 写入: {total_hbase:,} 条")
        logger.info("=" * 50)
        
    except KeyboardInterrupt:
        logger.info("接收到中断信号")
    finally:
        mysql_conn.close()
        if hbase_conn:
            hbase_conn.close()


def has_any_pass_record(mysql_conn) -> bool:
    """判断 ShardingSphere 逻辑表 pass_record 是否已有数据（用于 demo 预热幂等）。"""
    try:
        with mysql_conn.cursor() as cursor:
            cursor.execute("SELECT 1 FROM pass_record LIMIT 1")
            row = cursor.fetchone()
        return row is not None
    except Exception as e:
        # 如果查询失败，不做拦截，继续走生成逻辑（避免误判导致永远不生成）
        logger.warning(f"检查 pass_record 是否为空失败，将继续生成：{e}")
        return False


def main():
    parser = argparse.ArgumentParser(description="生成历史数据（直接写入数据库）")
    parser.add_argument("--start", help="开始时间 (YYYY-MM-DD HH:MM:SS)，默认今天00:00")
    parser.add_argument("--end", help="结束时间 (YYYY-MM-DD HH:MM:SS)，默认当前时间")
    parser.add_argument("--hours", type=float, help="从当前时间往前多少小时，优先于 --start")
    parser.add_argument("--rate", type=int, default=50, help="每秒记录数（默认50）")
    parser.add_argument(
        "--only-if-empty",
        action="store_true",
        help="仅当 MySQL(ShardingSphere) 逻辑表 pass_record 为空时才生成（用于容器启动预热，避免重复灌数据）",
    )
    
    args = parser.parse_args()
    
    now = datetime.now(BEIJING_TZ)
    
    if args.hours:
        start_time = now - timedelta(hours=args.hours)
        end_time = now
    elif args.start:
        start_time = datetime.strptime(args.start, TIME_FORMAT).replace(tzinfo=BEIJING_TZ)
        end_time = datetime.strptime(args.end, TIME_FORMAT).replace(tzinfo=BEIJING_TZ) if args.end else now
    else:
        start_time = now.replace(hour=0, minute=0, second=0, microsecond=0)
        end_time = now
    
    # 如果只在空库时才生成：提前连库检查一次，避免容器每次 up 都重复灌数据
    if args.only_if_empty:
        mysql_conn = None
        try:
            mysql_conn = get_mysql_conn()
            if has_any_pass_record(mysql_conn):
                logger.info("✅ 检测到 pass_record 已有数据，跳过历史数据预热（--only-if-empty）")
                return
            logger.info("pass_record 为空，开始执行历史数据预热…")
        except Exception as e:
            logger.warning(f"预检查 MySQL 失败，将继续生成：{e}")
        finally:
            try:
                if mysql_conn:
                    mysql_conn.close()
            except Exception:
                pass

    generate_history(start_time, end_time, args.rate)


if __name__ == "__main__":
    main()
