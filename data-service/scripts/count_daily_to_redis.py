#!/usr/bin/env python3
"""
统计 HBase 每日通行量并存入 Redis

运行方式:
    # Docker 内运行
    docker exec -it etc-platform-data-service-1 python scripts/count_daily_to_redis.py
    
    # 本地运行（需要设置环境变量）
    HBASE_HOST=localhost REDIS_HOST=localhost python count_daily_to_redis.py

Redis Key 格式:
    etc:hbase:daily:{yyyyMMdd} -> 当天通行总量
    etc:hbase:daily:range -> Hash {startDate, endDate}
"""

import os
import sys
import logging
from datetime import datetime, timedelta
from collections import defaultdict

import redis
import happybase

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s'
)
logger = logging.getLogger(__name__)

# 环境变量配置
HBASE_HOST = os.getenv('HBASE_HOST', 'hbase')
HBASE_PORT = int(os.getenv('HBASE_THRIFT_PORT', '9090'))
HBASE_TABLE = 'etc:pass_record'

REDIS_HOST = os.getenv('REDIS_HOST', 'redis')
REDIS_PORT = int(os.getenv('REDIS_PORT', '6379'))

# Redis Key 前缀
REDIS_KEY_PREFIX = 'etc:hbase:daily:'
REDIS_KEY_RANGE = 'etc:hbase:daily:range'


def connect_hbase():
    """连接 HBase"""
    logger.info(f"连接 HBase: {HBASE_HOST}:{HBASE_PORT}")
    conn = happybase.Connection(HBASE_HOST, port=HBASE_PORT)
    conn.open()
    return conn


def connect_redis():
    """连接 Redis"""
    logger.info(f"连接 Redis: {REDIS_HOST}:{REDIS_PORT}")
    return redis.Redis(host=REDIS_HOST, port=REDIS_PORT, decode_responses=True)


def count_by_date_from_hbase(hbase_conn) -> dict[str, int]:
    """
    扫描 HBase 统计每日通行量
    
    RowKey 格式: {salt(1)}{yyyyMMdd(8)}{...}
    从 RowKey 的第 1-8 位提取日期
    """
    table = hbase_conn.table(HBASE_TABLE)
    daily_counts = defaultdict(int)
    
    total_scanned = 0
    last_log_time = datetime.now()
    
    logger.info("开始扫描 HBase...")
    
    # 扫描所有数据
    for key, _ in table.scan(columns=[b'd:hp']):  # 只取一列，减少数据传输
        # RowKey 格式: {salt(1)}{yyyyMMdd(8)}{...}
        rowkey = key.decode('utf-8')
        if len(rowkey) >= 9:
            date_str = rowkey[1:9]  # 提取日期部分
            daily_counts[date_str] += 1
        
        total_scanned += 1
        
        # 每 10 秒打印进度
        if (datetime.now() - last_log_time).seconds >= 10:
            logger.info(f"已扫描 {total_scanned:,} 条...")
            last_log_time = datetime.now()
    
    logger.info(f"扫描完成，共 {total_scanned:,} 条，涉及 {len(daily_counts)} 天")
    return dict(daily_counts)


def save_to_redis(redis_client, daily_counts: dict[str, int]):
    """将每日统计存入 Redis"""
    if not daily_counts:
        logger.warning("没有数据需要保存")
        return
    
    pipe = redis_client.pipeline()
    
    # 保存每日计数
    for date_str, count in daily_counts.items():
        key = f"{REDIS_KEY_PREFIX}{date_str}"
        pipe.set(key, count)
        logger.debug(f"  {key} = {count}")
    
    # 保存日期范围
    sorted_dates = sorted(daily_counts.keys())
    pipe.hset(REDIS_KEY_RANGE, mapping={
        'startDate': sorted_dates[0],
        'endDate': sorted_dates[-1],
        'totalDays': len(sorted_dates),
        'totalRecords': sum(daily_counts.values()),
        'updatedAt': datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    })
    
    pipe.execute()
    
    logger.info(f"已保存到 Redis:")
    logger.info(f"  日期范围: {sorted_dates[0]} ~ {sorted_dates[-1]}")
    logger.info(f"  总天数: {len(sorted_dates)}")
    logger.info(f"  总记录: {sum(daily_counts.values()):,}")


def get_count_from_redis(redis_client, start_date: str, end_date: str) -> int:
    """
    从 Redis 获取日期范围内的总量
    
    Args:
        start_date: 开始日期 (yyyyMMdd)
        end_date: 结束日期 (yyyyMMdd)
    
    Returns:
        总通行量
    """
    total = 0
    current = datetime.strptime(start_date, '%Y%m%d')
    end = datetime.strptime(end_date, '%Y%m%d')
    
    keys = []
    while current <= end:
        keys.append(f"{REDIS_KEY_PREFIX}{current.strftime('%Y%m%d')}")
        current += timedelta(days=1)
    
    # 批量获取
    values = redis_client.mget(keys)
    for v in values:
        if v:
            total += int(v)
    
    return total


def main():
    """主函数"""
    logger.info("=" * 50)
    logger.info("HBase 每日通行量统计 -> Redis")
    logger.info("=" * 50)
    
    try:
        # 连接
        hbase_conn = connect_hbase()
        redis_client = connect_redis()
        
        # 统计
        daily_counts = count_by_date_from_hbase(hbase_conn)
        
        # 保存
        save_to_redis(redis_client, daily_counts)
        
        # 验证
        logger.info("\n验证: 查询最近 7 天的总量")
        today = datetime.now()
        for i in range(7):
            date = today - timedelta(days=i)
            date_str = date.strftime('%Y%m%d')
            key = f"{REDIS_KEY_PREFIX}{date_str}"
            count = redis_client.get(key)
            if count:
                logger.info(f"  {date_str}: {int(count):,} 条")
        
        # 清理
        hbase_conn.close()
        logger.info("\n✅ 完成!")
        
    except Exception as e:
        logger.error(f"❌ 出错: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == '__main__':
    main()
