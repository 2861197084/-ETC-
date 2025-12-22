#!/usr/bin/env python3
"""
Inject synthetic pass records to trigger ClonePlateDetectorJob.

This sends two pass-record events (same plate, different checkpoints, short time delta)
to Kafka topic `etc-pass-records`, so the Flink job can produce a row in MySQL table
`clone_plate_detection`, which is then queryable by the backend API:
  GET /admin/realtime/clone-plates
"""

from __future__ import annotations

import argparse
import logging
import os
import random
import sys
import time
from dataclasses import dataclass
from datetime import datetime, timedelta
from typing import Optional

import pymysql
import requests

# Ensure "app" is importable when running as module.
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.config import settings
from app.kafka_producer import producer


logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger(__name__)

TIME_FORMAT = "%Y-%m-%d %H:%M:%S"


@dataclass(frozen=True)
class CheckpointInfo:
    code: str
    name: str
    district: str


def parse_time(s: str) -> str:
    dt = datetime.strptime(s, TIME_FORMAT)
    return dt.strftime(TIME_FORMAT)


def get_simulated_time_and_window(time_api: str) -> tuple[str, str, str]:
    """获取模拟时间和当前窗口"""
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
        return parse_time(sim), parse_time(window_start) if window_start else sim, parse_time(window_end) if window_end else sim
    except Exception as e:
        raise RuntimeError(f"Failed to get simulated time from {time_api}: {e}") from e


def mysql_conn():
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
        autocommit=True,
    )


def fetch_checkpoint(code: str) -> Optional[CheckpointInfo]:
    try:
        with mysql_conn() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    "SELECT code, name, district FROM checkpoint WHERE code=%s LIMIT 1",
                    (code,),
                )
                row = cur.fetchone()
                if not row:
                    return None
                return CheckpointInfo(
                    code=row["code"],
                    name=row.get("name") or code,
                    district=row.get("district") or "",
                )
    except Exception as e:
        logger.warning("Failed to fetch checkpoint %s from MySQL: %s", code, e)
        return None


def build_record(
    plate: str,
    gcsj: str,
    checkpoint: CheckpointInfo,
    direction: str = "1",
    plate_type: str = "01",
    vehicle_type: str = "测试车辆",
) -> dict:
    # gcxh: make it stable-ish and unique enough for demo.
    gcxh = f"T{int(time.time() * 1000)}{random.randint(100, 999)}"
    return {
        "hp": plate,
        "gcsj": gcsj,
        "gcxh": gcxh,
        "xzqhmc": checkpoint.district,
        "kkmc": checkpoint.name,
        "fxlx": direction,
        "hpzl": plate_type,
        "clppxh": vehicle_type,
        "checkpointId": checkpoint.code,
    }


def wait_for_mysql_insert(plate: str, timeout_s: int = 30) -> Optional[dict]:
    deadline = time.time() + timeout_s
    last_err = None
    while time.time() < deadline:
        try:
            with mysql_conn() as conn:
                with conn.cursor() as cur:
                    cur.execute(
                        "SELECT * FROM clone_plate_detection WHERE plate_number=%s ORDER BY id DESC LIMIT 1",
                        (plate,),
                    )
                    row = cur.fetchone()
                    if row:
                        return row
        except Exception as e:
            last_err = e
        time.sleep(1)
    if last_err:
        logger.warning("Waiting for insert failed with last error: %s", last_err)
    return None


def main() -> int:
    parser = argparse.ArgumentParser(description="Inject data to trigger clone-plate detection.")
    parser.add_argument("--plate", required=True, help="Plate number, e.g. 苏A88888 or TEST-CLONE-001")
    parser.add_argument("--cp1", required=True, help="Checkpoint code 1, e.g. CP001")
    parser.add_argument("--cp2", required=True, help="Checkpoint code 2, e.g. CP009")
    parser.add_argument("--t1", help=f"Pass time 1 in format {TIME_FORMAT}")
    parser.add_argument("--t2", help=f"Pass time 2 in format {TIME_FORMAT}")
    parser.add_argument(
        "--auto-time",
        action="store_true",
        help="Auto-pick t1/t2 based on backend simulated time (t2=now-60s, t1=t2-120s).",
    )
    parser.add_argument(
        "--time-api",
        default=os.getenv("BACKEND_TIME_API", "http://backend:8080/api/time"),
        help="Backend time API for --auto-time (default: $BACKEND_TIME_API or http://backend:8080/api/time)",
    )
    parser.add_argument("--direction1", default="1", help="Direction for record 1 (fxlx)")
    parser.add_argument("--direction2", default="2", help="Direction for record 2 (fxlx)")
    parser.add_argument("--verify", action="store_true", help="Poll MySQL and print inserted row if any")
    parser.add_argument("--timeout", type=int, default=30, help="Verify timeout seconds (default: 30)")
    args = parser.parse_args()

    if args.auto_time:
        sim_now_str, window_start_str, window_end_str = get_simulated_time_and_window(args.time_api)
        sim_now = datetime.strptime(sim_now_str, TIME_FORMAT)
        window_start = datetime.strptime(window_start_str, TIME_FORMAT)
        
        # 在当前窗口内生成两个时间点，相差2分钟（在5分钟检测阈值内）
        # t1 在窗口开始后1分钟，t2 在窗口开始后3分钟
        t1_dt = window_start + timedelta(minutes=1)
        t2_dt = window_start + timedelta(minutes=3)
        t1 = t1_dt.strftime(TIME_FORMAT)
        t2 = t2_dt.strftime(TIME_FORMAT)
        
        logger.info(f"⏰ 模拟时间: {sim_now_str}, 窗口: {window_start_str} ~ {window_end_str}")
    else:
        if not args.t1 or not args.t2:
            parser.error("either provide --t1 and --t2, or use --auto-time")
        # Validate time format early (Flink parses strictly).
        t1 = parse_time(args.t1)
        t2 = parse_time(args.t2)

    cp1 = fetch_checkpoint(args.cp1) or CheckpointInfo(args.cp1, args.cp1, "")
    cp2 = fetch_checkpoint(args.cp2) or CheckpointInfo(args.cp2, args.cp2, "")
    if cp1.name == cp2.name:
        logger.warning("cp1 and cp2 have the same name; ClonePlateDetectorJob compares kkmc, so this may not trigger.")

    logger.info("Kafka bootstrap: %s", settings.kafka_bootstrap_servers)
    logger.info("Kafka topic: %s", settings.kafka_topic_pass_records)
    logger.info("Injecting plate=%s cp1=%s cp2=%s t1=%s t2=%s", args.plate, cp1.code, cp2.code, t1, t2)

    r1 = build_record(args.plate, t1, cp1, direction=args.direction1)
    r2 = build_record(args.plate, t2, cp2, direction=args.direction2)

    # Use plate as key so records are in-order per partition (helps deterministic testing).
    producer.connect()
    producer.send(r1, key=args.plate)
    producer.send(r2, key=args.plate)
    producer.flush()
    producer.close()

    logger.info("Sent 2 records. If ClonePlateDetectorJob is running, it should insert into MySQL soon.")
    logger.info("Backend API check: GET http://localhost:8080/admin/realtime/clone-plates?page=1&pageSize=10")

    if args.verify:
        row = wait_for_mysql_insert(args.plate, timeout_s=args.timeout)
        if not row:
            logger.error("No clone_plate_detection row found for plate=%s within %ss.", args.plate, args.timeout)
            logger.error("Common causes: ClonePlateDetectorJob not running; wrong Kafka topic; gcsj format mismatch.")
            return 2
        logger.info("Inserted clone_plate_detection row: id=%s status=%s create_time=%s", row.get("id"), row.get("status"), row.get("create_time"))
        logger.info(
            "Details: plate=%s cp1=%s cp2=%s time_1=%s time_2=%s time_diff_minutes=%s confidence_score=%s",
            row.get("plate_number"),
            row.get("checkpoint_id_1"),
            row.get("checkpoint_id_2"),
            row.get("time_1"),
            row.get("time_2"),
            row.get("time_diff_minutes"),
            row.get("confidence_score"),
        )

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
