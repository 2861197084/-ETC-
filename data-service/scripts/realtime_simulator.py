#!/usr/bin/env python3
"""
实时数据模拟器 v2

从后端时间服务获取模拟时间，读取对应时间段的 CSV 数据发送到 Kafka
"""
import os
import sys
import time
import json
import csv
import logging
import requests
from datetime import datetime, timedelta

# 添加父目录到路径
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.config import settings
from app.kafka_producer import producer

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# 后端时间 API
BACKEND_TIME_API = os.getenv("BACKEND_TIME_API", "http://localhost:8080/api/time")

# 原始 CSV 的 KKMC 为“长命名”，系统内部使用 CP001..CP019 作为卡口编码。
# 注意：2023-12 中存在 1 个“同一卡口不同写法”的重复命名（S325 淮宿线 K63），这里统一映射到同一编码。
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


def get_checkpoint_id(kkmc: str) -> str | None:
    if not kkmc:
        return None
    return RAW_KKMC_TO_ID.get(kkmc.strip())


class TimeWindow:
    """时间窗口"""
    def __init__(self, start: datetime, end: datetime):
        self.start = start
        self.end = end

    def __eq__(self, other):
        if other is None:
            return False
        return self.start == other.start and self.end == other.end

    def __repr__(self):
        return f"[{self.start.strftime('%H:%M')} - {self.end.strftime('%H:%M')}]"


def get_simulated_time() -> dict:
    """从后端获取当前模拟时间"""
    try:
        resp = requests.get(BACKEND_TIME_API, timeout=5)
        if resp.status_code == 200:
            payload = resp.json()
            # 后端统一响应格式：{ code, msg, data }
            if isinstance(payload, dict) and isinstance(payload.get('data'), dict):
                return payload['data']
            return payload
    except Exception as e:
        logger.warning(f"获取模拟时间失败: {e}")
    return None


def start_simulation():
    """启动后端模拟"""
    try:
        resp = requests.post(f"{BACKEND_TIME_API}/start", timeout=5)
        logger.info(f"启动模拟: {resp.status_code}")
    except Exception as e:
        logger.warning(f"启动模拟失败: {e}")

def reset_simulation():
    """重置后端模拟时间到基准点"""
    try:
        resp = requests.post(f"{BACKEND_TIME_API}/reset", timeout=5)
        logger.info(f"重置模拟: {resp.status_code}")
    except Exception as e:
        logger.warning(f"重置模拟失败: {e}")

class CsvWindowStreamer:
    """
    顺序读取 CSV（按时间递增），按 5 分钟窗口输出记录。

    设计目标：避免一次性索引全量（内存爆炸），同时避免每个窗口重复全文件扫描。
    """

    def __init__(self, data_dir: str):
        self.data_dir = data_dir
        self.current_date: str | None = None
        self.file = None
        self.reader = None
        self.pending_row: dict | None = None

    def close(self):
        if self.file:
            try:
                self.file.close()
            except Exception:
                pass
        self.file = None
        self.reader = None
        self.pending_row = None
        self.current_date = None

    def _open_for_date(self, date_str: str):
        if self.current_date == date_str and self.reader is not None:
            return

        self.close()
        csv_path = os.path.join(self.data_dir, f"{date_str}.csv")
        if not os.path.exists(csv_path):
            logger.warning(f"未找到当日 CSV: {csv_path}")
            return

        # 原始数据文件为 UTF-8-SIG（带 BOM）
        self.file = open(csv_path, 'r', encoding='utf-8-sig', newline='')
        self.reader = csv.DictReader(self.file)
        self.current_date = date_str
        logger.info(f"切换数据文件: {os.path.basename(csv_path)}")

    def get_window_records(self, window_start: datetime, window_end: datetime) -> list[dict]:
        date_str = window_start.strftime('%Y-%m-%d')
        self._open_for_date(date_str)
        if self.reader is None:
            return []

        records: list[dict] = []

        while True:
            row = self.pending_row
            self.pending_row = None

            if row is None:
                try:
                    row = next(self.reader)
                except StopIteration:
                    break
                except Exception:
                    continue

            try:
                gcsj = (row.get('GCSJ') or '').strip()
                if not gcsj:
                    continue
                dt = datetime.strptime(gcsj, '%Y-%m-%d %H:%M:%S')
            except Exception:
                continue

            # 按窗口过滤
            if dt < window_start:
                continue
            if dt >= window_end:
                # 留给下一个窗口
                self.pending_row = row
                break

            kkmc = (row.get('KKMC') or '').strip()
            records.append({
                'gcxh': row.get('GCXH', ''),
                'xzqhmc': row.get('XZQHMC', ''),
                'kkmc': kkmc,
                'fxlx': row.get('FXLX', ''),
                'gcsj': gcsj,
                'hpzl': row.get('HPZL', ''),
                'hp': row.get('HP', ''),
                'clppxh': row.get('CLPPXH', ''),
                'checkpointId': get_checkpoint_id(kkmc),
            })

        return records


def simulate_realtime():
    """基于模拟时间的实时数据流"""
    logger.info("=" * 50)
    logger.info("ETC 实时数据模拟器 v2")
    logger.info("=" * 50)
    logger.info(f"后端时间 API: {BACKEND_TIME_API}")

    streamer = CsvWindowStreamer(settings.data_dir)
    
    # 连接 Kafka
    try:
        producer.connect()
    except Exception as e:
        logger.error(f"Kafka 连接失败: {e}")
        logger.info("将使用日志输出模式...")
        use_kafka = False
    else:
        use_kafka = True
    
    # 启动后端模拟
    reset_simulation()
    start_simulation()
    
    last_window = None
    total_sent = 0
    
    try:
        while True:
            # 获取当前模拟时间
            time_info = get_simulated_time()
            if not time_info:
                logger.warning("等待后端时间服务...")
                time.sleep(1)
                continue
            
            if not time_info.get('isRunning'):
                logger.info("模拟未运行，等待...")
                time.sleep(1)
                continue
            
            # 解析窗口
            window_start = datetime.strptime(time_info['windowStart'], '%Y-%m-%d %H:%M:%S')
            window_end = datetime.strptime(time_info['windowEnd'], '%Y-%m-%d %H:%M:%S')
            current_window = TimeWindow(window_start, window_end)
            
            # 如果进入新窗口，发送该窗口的数据
            if last_window != current_window:
                logger.info(f"进入新窗口: {current_window}")

                records = streamer.get_window_records(window_start, window_end)
                
                if records:
                    logger.info(f"  发送 {len(records)} 条记录")
                    
                    for record in records:
                        if use_kafka:
                            producer.send(record, key=record.get('hp'))
                        total_sent += 1
                    
                    if use_kafka:
                        producer.flush()
                else:
                    logger.info("  该窗口无数据")
                
                last_window = current_window
            
            # 等待 1 秒（真实时间），相当于模拟时间过 5 分钟
            time.sleep(1)
            
    except KeyboardInterrupt:
        logger.info("接收到中断信号")
    finally:
        streamer.close()
        if use_kafka:
            producer.close()
        logger.info(f"模拟结束，共发送 {total_sent} 条记录")


def main():
    simulate_realtime()


if __name__ == "__main__":
    main()
