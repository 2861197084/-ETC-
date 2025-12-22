#!/usr/bin/env python3
"""
Inject synthetic pass records to trigger flow peak alert.

Usage:
    # 从 Docker 容器内运行
    docker compose run --rm data-service python -m scripts.inject_flow_peak --checkpoint CP001 --count 500
    
    # 或本地运行 (需要设置环境变量)
    python -m scripts.inject_flow_peak --checkpoint CP001 --count 500
"""

from __future__ import annotations

import argparse
import logging
import os
import random
import sys
import time
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from typing import Optional

# 北京时区 UTC+8
BEIJING_TZ = timezone(timedelta(hours=8))

import pymysql
import requests

# Ensure "app" is importable when running as module.
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.config import settings
from app.kafka_producer import producer


logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger(__name__)

TIME_FORMAT = "%Y-%m-%d %H:%M:%S"

# 站点信息
CHECKPOINTS = {
    "CP001": ("睢宁卡1(104省道)", "睢宁县"),
    "CP002": ("沛县卡2(311国道)", "沛县"),
    "CP003": ("沛县卡3(徐丰高速)", "沛县"),
    "CP004": ("沛县卡4(宿新高速)", "沛县"),
    "CP005": ("新沂卡1(205国道)", "新沂市"),
    "CP006": ("新沂卡2(新长铁路)", "新沂市"),
    "CP007": ("新沂卡3(323省道)", "新沂市"),
    "CP008": ("邳州卡1(250省道)", "邳州市"),
    "CP009": ("邳州卡2(310国道)", "邳州市"),
    "CP010": ("邳州卡4(邳新高速)", "邳州市"),
    "CP011": ("贾汪卡5(京沪高速)", "贾汪区"),
    "CP012": ("贾汪卡1(206国道)", "贾汪区"),
    "CP013": ("贾汪卡2(310国道)", "贾汪区"),
    "CP014": ("连云港卡2(310国道)", "连云港市"),
    "CP015": ("宿迁卡1(京沪高速)", "宿迁市"),
    "CP016": ("宿迁卡2(徐宿快速)", "宿迁市"),
    "CP017": ("铜山卡1(206国道)", "铜山区"),
    "CP018": ("铜山卡2(104国道)", "铜山区"),
    "CP019": ("铜山卡4(徐济高速)", "铜山区"),
}

PLATE_PREFIXES = ["苏C", "苏A", "苏B", "鲁B", "豫N", "皖A", "苏D", "苏E"]
DIRECTIONS = ["进城", "出城"]
PLATE_TYPES = ["01", "02", "52"]  # 小型汽车, 大型汽车, 新能源
CAR_BRANDS = ["大众朗逸", "丰田卡罗拉", "本田思域", "别克英朗", "日产轩逸", "现代领动", "福特福克斯"]


def get_simulated_time_and_window(time_api: str) -> tuple[datetime, datetime, datetime]:
    """从后端获取模拟时间和当前窗口"""
    try:
        resp = requests.get(time_api, timeout=5)
        resp.raise_for_status()
        payload = resp.json()
        data = payload.get("data", {})
        sim = data.get("simulatedTime")
        window_start = data.get("windowStart")
        window_end = data.get("windowEnd")
        if not sim:
            raise ValueError("missing data.simulatedTime")
        sim_dt = datetime.strptime(sim, TIME_FORMAT)
        start_dt = datetime.strptime(window_start, TIME_FORMAT) if window_start else sim_dt - timedelta(minutes=5)
        end_dt = datetime.strptime(window_end, TIME_FORMAT) if window_end else sim_dt
        return sim_dt, start_dt, end_dt
    except Exception as e:
        logger.warning(f"Failed to get simulated time: {e}, using current time")
        now = datetime.now(BEIJING_TZ)
        return now, now - timedelta(minutes=5), now


def mysql_conn():
    """创建 MySQL 连接 (通过 ShardingSphere)"""
    host = os.getenv("MYSQL_HOST", "shardingsphere")
    port = int(os.getenv("MYSQL_PORT", "3307"))
    user = os.getenv("MYSQL_USER", "root")
    password = os.getenv("MYSQL_PASSWORD", "root")
    return pymysql.connect(
        host=host,
        port=port,
        user=user,
        password=password,
        database="etc",
        charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor,
        autocommit=False,
    )


def generate_plate() -> str:
    """生成随机车牌"""
    prefix = random.choice(PLATE_PREFIXES)
    num = ''.join(random.choices('0123456789ABCDEFGHJKLMNPQRSTUVWXYZ', k=5))
    return f"{prefix}{num}"


def inject_flow_peak(checkpoint_id: str, count: int, time_api: str, use_kafka: bool = False):
    """注入车流量高峰数据"""
    
    if checkpoint_id not in CHECKPOINTS:
        logger.error(f"Unknown checkpoint: {checkpoint_id}")
        logger.info(f"Available checkpoints: {list(CHECKPOINTS.keys())}")
        return False
    
    cp_name, cp_district = CHECKPOINTS[checkpoint_id]
    
    # 获取模拟时间和当前窗口
    sim_time, window_start, window_end = get_simulated_time_and_window(time_api)
    logger.info(f"📍 目标站点: {cp_name} ({checkpoint_id})")
    logger.info(f"⏰ 模拟时间: {sim_time}")
    logger.info(f"📍 当前窗口: {window_start} ~ {window_end}")
    logger.info(f"📊 注入数量: {count} 条")
    
    # 计算需要的车流量
    # maxCapacity = 4车道 × 800辆/小时 = 3200辆/小时
    # 要触发30%阈值，需要 3200 × 0.3 = 960辆/小时
    # 5分钟窗口 = 960 / 12 = 80条记录
    # 要触发70%阈值，需要 3200 × 0.7 / 12 = 187条/窗口
    logger.info(f"💡 提示: 要触发30%阈值需要~80条/窗口, 70%需要~187条/窗口")
    
    # 覆盖当前窗口和未来5个窗口（共30分钟），确保数据能被查询到
    total_window_seconds = 30 * 60  # 30分钟
    
    records = []
    for i in range(count):
        # 在当前窗口开始到未来20分钟内随机分布
        offset_seconds = random.randint(0, total_window_seconds - 1)
        pass_time = window_start + timedelta(seconds=offset_seconds)
        
        plate = generate_plate()
        gcxh = f"PEAK{int(time.time() * 1000) % 100000000}{i:05d}"
        
        record = {
            "gcxh": gcxh,
            "xzqhmc": cp_district,
            "kkmc": cp_name,
            "fxlx": random.choice(DIRECTIONS),
            "gcsj": pass_time.strftime(TIME_FORMAT),
            "hpzl": random.choice(PLATE_TYPES),
            "hp": plate,
            "clppxh": random.choice(CAR_BRANDS),
            "plate_hash": hash(plate) & 0x7FFFFFFF,
            "checkpoint_id": checkpoint_id,
        }
        records.append(record)
    
    if use_kafka:
        # 通过 Kafka 发送
        logger.info("🚀 通过 Kafka 发送数据...")
        producer.connect()
        for i, rec in enumerate(records):
            producer.send(rec, key=rec.get('hp'))
            if (i + 1) % 100 == 0:
                producer.flush()
                logger.info(f"  已发送 {i + 1}/{count}")
        producer.flush()
        producer.close()
    else:
        # 直接写入 MySQL
        logger.info("🚀 直接写入 MySQL...")
        conn = mysql_conn()
        try:
            with conn.cursor() as cur:
                sql = """
                    INSERT INTO pass_record (gcxh, xzqhmc, kkmc, fxlx, gcsj, hpzl, hp, clppxh, plate_hash, checkpoint_id)
                    VALUES (%(gcxh)s, %(xzqhmc)s, %(kkmc)s, %(fxlx)s, %(gcsj)s, %(hpzl)s, %(hp)s, %(clppxh)s, %(plate_hash)s, %(checkpoint_id)s)
                """
                batch_size = 100
                for i in range(0, len(records), batch_size):
                    batch = records[i:i + batch_size]
                    cur.executemany(sql, batch)
                    conn.commit()
                    logger.info(f"  已写入 {min(i + batch_size, count)}/{count}")
        finally:
            conn.close()
    
    logger.info(f"✅ 完成！已注入 {count} 条记录到 {cp_name}")
    logger.info(f"💡 现在刷新大屏页面，应该能看到车流量高峰告警")
    return True


def main() -> int:
    parser = argparse.ArgumentParser(description="Inject data to trigger flow peak alert.")
    parser.add_argument("--checkpoint", "-c", required=True, help="Checkpoint ID (e.g. CP001)")
    parser.add_argument("--count", "-n", type=int, default=500, help="Number of records to inject (default: 500)")
    parser.add_argument("--kafka", action="store_true", help="Send via Kafka instead of direct MySQL insert")
    parser.add_argument("--time-api", default=os.getenv("BACKEND_TIME_API", "http://backend:8080/api/time"), help="Time API endpoint")
    parser.add_argument("--list", "-l", action="store_true", help="List available checkpoints")
    
    args = parser.parse_args()
    
    if args.list:
        print("\n可用站点列表:")
        print("-" * 50)
        for code, (name, district) in sorted(CHECKPOINTS.items()):
            print(f"  {code}: {name} ({district})")
        print()
        return 0
    
    success = inject_flow_peak(
        checkpoint_id=args.checkpoint,
        count=args.count,
        time_api=args.time_api,
        use_kafka=args.kafka,
    )
    
    return 0 if success else 1


if __name__ == "__main__":
    sys.exit(main())
