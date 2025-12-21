#!/usr/bin/env python3
"""
历史数据导入 HBase 脚本

将 2023-12 月的 CSV 文件导入到 HBase etc:pass_record 表
"""
import os
import sys
import glob
import csv
import logging
from datetime import datetime
import hashlib

# 添加父目录到路径
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.config import settings

# 使用 happybase 连接 HBase Thrift
try:
    import happybase
except ImportError:
    print("请安装 happybase: pip install happybase")
    sys.exit(1)

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# HBase 配置
HBASE_HOST = os.getenv('HBASE_HOST', 'hbase')
HBASE_PORT = int(os.getenv('HBASE_THRIFT_PORT', '9090'))
HBASE_TABLE = 'etc:pass_record'

REDIS_HOST = os.getenv('REDIS_HOST', 'redis')
REDIS_PORT = int(os.getenv('REDIS_PORT', '6379'))

REDIS_KEYS = {
    'history_total': 'etc:stats:history:pass_record:total',
    'history_by_checkpoint': 'etc:stats:history:pass_record:by_checkpoint',
    'history_last_import': 'etc:stats:history:pass_record:last_import',
}

RAW_KKMC_TO_ID = {
    # 苏皖界（省际）
    "徐州市睢宁县G104北京-福州K873江苏徐州-G104-苏皖界省际卡口": "CP001",
    "徐州市铜山县G311徐州-西峡K207江苏徐州-G311-苏皖界省际卡口": "CP002",
    "徐州市睢宁县S252塔双线K56江苏徐州-S252-苏皖界省际卡口": "CP003",
    "徐州市铜山县G206烟台-汕头K816江苏徐州-G206-苏皖界省际卡口": "CP004",
    "徐州市丰县G518518国道K358马楼公路站省际卡口": "CP005",
    "徐州市丰县G237国道237线K148荣庄卡口省际卡口": "CP006",

    # 苏鲁界（省际）
    "徐州市沛县S253郑沛龙线K0江苏徐州-S253-苏鲁界省际卡口": "CP007",
    "徐州市铜山县G104北京-福州K744江苏徐州-G104-苏鲁界省际卡口": "CP008",
    "G3京台高速K731江苏高速五大队江苏徐州-G3-苏鲁界省际卡口": "CP009",
    "徐州市邳州市S250宿邳线K1江苏徐州-S250-苏鲁界省际卡口": "CP010",
    "徐州市邳州市S251枣睢线K5江苏徐州-S251-苏鲁界省际卡口": "CP011",
    "江苏省徐州市新沂市S323连徐线K96瓦窑检查站市际卡口": "CP012",

    # 连云港界（市际）
    "徐州市新沂市S323连徐线K10阿湖卡口-323省道连云港交界市际卡口": "CP013",
    "徐州市铜山县G310连云港-天水K310江苏徐州-G310-苏皖界省际卡口": "CP014",

    # 宿迁界（市际）
    "徐州市新沂市S505505省道K10新沂高速西出口-505省道宿迁界市际卡口": "CP015",
    "江苏省徐州市睢宁县S325淮宿线K63(325省道)63K+100M东侧-向西卡口市际卡口": "CP016",
    "徐州市睢宁县S325淮宿线K63(325省道)63K+100M东侧-向西卡口市际卡口": "CP016",
    "徐州市睢宁县S324燕沭睢线K201省道桑庄王马路路口西侧-向东卡口市际卡口": "CP017",
    "徐州市新沂市G235国道235K10江苏徐州-G235-交界市际卡口": "CP018",
    "徐州市丰县鹿梁路K19丰县梁寨检查站市际卡口": "CP019",
}


def generate_rowkey(record: dict) -> str:
    """
    生成 RowKey
    格式: {salt}{yyyyMMdd}{checkpoint_hash}{reverse_ts}{plate_hash}
    """
    hp = record.get('hp', '')
    gcsj = record.get('gcsj', '')
    kkmc = record.get('kkmc', '')
    
    def stable_hash_mod(value: str, mod: int) -> int:
        if not value:
            return 0
        digest = hashlib.md5(value.encode('utf-8')).digest()
        n = int.from_bytes(digest[:4], byteorder='big', signed=False)
        return n % mod

    # Salt (0-9) 用于打散热点（稳定哈希，跨语言一致）
    salt = stable_hash_mod(hp, 10)
    
    # 日期
    date_str = "20231201"
    try:
        if gcsj and len(gcsj) >= 10:
            date_str = gcsj[:10].replace("-", "")
    except:
        pass
    
    # 反转时间戳（最新的排在前面）
    try:
        dt = datetime.strptime(gcsj, '%Y-%m-%d %H:%M:%S')
        ts = int(dt.timestamp() * 1000)
        reverse_ts = 9999999999999 - ts
    except:
        reverse_ts = 9999999999999
    
    # 卡口哈希（稳定哈希）
    kkmc_hash = stable_hash_mod(kkmc, 100000000)
    
    # 车牌哈希（稳定哈希）
    plate_hash = stable_hash_mod(hp, 10000)
    
    return f"{salt}{date_str}{kkmc_hash:08d}{reverse_ts:013d}{plate_hash:04d}"


def import_csv_to_hbase(csv_path: str, table, redis_client=None, batch_size: int = 1000):
    """导入单个 CSV 文件到 HBase"""
    filename = os.path.basename(csv_path)
    logger.info(f"正在处理: {filename}")
    
    if os.path.getsize(csv_path) == 0:
        logger.warning(f"文件为空，跳过: {filename}")
        return 0
    
    inserted = 0
    batch = table.batch()
    redis_batch_total = 0
    redis_checkpoint_counts: dict[str, int] = {}
    
    try:
        # 原始数据文件为 UTF-8-SIG（带 BOM）
        with open(csv_path, 'r', encoding='utf-8-sig', newline='') as f:
            reader = csv.DictReader(f)
            
            for row in reader:
                try:
                    kkmc = row.get('KKMC', '')
                    record = {
                        'gcxh': row.get('GCXH', ''),
                        'xzqhmc': row.get('XZQHMC', ''),
                        'kkmc': kkmc,
                        'fxlx': row.get('FXLX', ''),
                        'gcsj': row.get('GCSJ', ''),
                        'hpzl': row.get('HPZL', ''),
                        'hp': row.get('HP', ''),
                        'clppxh': row.get('CLPPXH', ''),
                        'checkpoint_id': RAW_KKMC_TO_ID.get(kkmc.strip(), ''),
                    }
                    
                    # 生成 RowKey
                    rowkey = generate_rowkey(record)
                    
                    # 准备列数据
                    data = {
                        b'd:gcxh': record['gcxh'].encode('utf-8'),
                        b'd:xzqhmc': record['xzqhmc'].encode('utf-8'),
                        b'd:kkmc': record['kkmc'].encode('utf-8'),
                        b'd:fxlx': record['fxlx'].encode('utf-8'),
                        b'd:gcsj': record['gcsj'].encode('utf-8'),
                        b'd:hpzl': record['hpzl'].encode('utf-8'),
                        b'd:hp': record['hp'].encode('utf-8'),
                        b'd:clppxh': record['clppxh'].encode('utf-8'),
                        b'd:checkpoint_id': record['checkpoint_id'].encode('utf-8'),
                    }
                    
                    batch.put(rowkey.encode('utf-8'), data)
                    inserted += 1
                    if redis_client is not None:
                        redis_batch_total += 1
                        cp = record['checkpoint_id']
                        if cp:
                            redis_checkpoint_counts[cp] = redis_checkpoint_counts.get(cp, 0) + 1
                    
                    if inserted % batch_size == 0:
                        batch.send()
                        logger.info(f"  已插入 {inserted} 条...")
                        flush_redis(redis_client, redis_batch_total, redis_checkpoint_counts)
                        redis_batch_total = 0
                        redis_checkpoint_counts = {}
                        
                except Exception as e:
                    continue
        
        # 发送剩余数据
        batch.send()
        flush_redis(redis_client, redis_batch_total, redis_checkpoint_counts)
        
    except Exception as e:
        logger.error(f"处理文件失败: {e}")
    
    logger.info(f"完成: {filename}, 共插入 {inserted} 条")
    return inserted


def flush_redis(redis_client, batch_total: int, checkpoint_counts: dict[str, int]):
    if redis_client is None:
        return
    if batch_total <= 0 and not checkpoint_counts:
        return
    try:
        pipe = redis_client.pipeline(transaction=False)
        if batch_total > 0:
            pipe.incrby(REDIS_KEYS['history_total'], batch_total)
        for cp, c in checkpoint_counts.items():
            if c > 0:
                pipe.hincrby(REDIS_KEYS['history_by_checkpoint'], cp, c)
        pipe.set(REDIS_KEYS['history_last_import'], datetime.now().strftime('%Y-%m-%d %H:%M:%S'))
        pipe.execute()
    except Exception:
        pass


def main():
    logger.info("=" * 50)
    logger.info("ETC 历史数据导入 HBase")
    logger.info("=" * 50)
    
    # 连接 HBase
    try:
        connection = happybase.Connection(HBASE_HOST, port=HBASE_PORT)
        connection.open()
        logger.info(f"HBase 连接成功: {HBASE_HOST}:{HBASE_PORT}")
    except Exception as e:
        logger.error(f"HBase 连接失败: {e}")
        return

    # 连接 Redis（用于历史统计汇总）
    redis_client = None
    try:
        import redis as redis_lib
        redis_client = redis_lib.Redis(host=REDIS_HOST, port=REDIS_PORT, decode_responses=True)
        redis_client.ping()
        logger.info(f"Redis 连接成功: {REDIS_HOST}:{REDIS_PORT}")
    except Exception as e:
        redis_client = None
        logger.warning(f"Redis 连接失败，将跳过统计汇总: {e}")
    
    # 获取表
    try:
        table = connection.table(HBASE_TABLE)
    except Exception as e:
        logger.error(f"获取表失败: {e}")
        connection.close()
        return
    
    # 查找 2023-12 的 CSV 文件
    data_dir = settings.data_dir
    csv_pattern = os.path.join(data_dir, "2023-12-*.csv")
    csv_files = sorted(glob.glob(csv_pattern))
    
    if not csv_files:
        logger.error(f"未找到 CSV 文件: {csv_pattern}")
        logger.info(f"请确认数据目录: {os.path.abspath(data_dir)}")
        connection.close()
        return
    
    logger.info(f"找到 {len(csv_files)} 个 CSV 文件")
    
    total_inserted = 0
    for csv_path in csv_files:
        try:
            count = import_csv_to_hbase(csv_path, table, redis_client=redis_client)
            total_inserted += count
        except Exception as e:
            logger.error(f"导入失败: {e}")
            continue
    
    connection.close()
    
    logger.info("=" * 50)
    logger.info(f"导入完成! 共插入 {total_inserted:,} 条记录到 HBase")
    logger.info("=" * 50)


if __name__ == "__main__":
    main()
