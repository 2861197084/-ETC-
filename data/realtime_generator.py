#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ETC 实时数据生成器

仅写入 Kafka，由 Flink 作业处理后续：
- DataSyncJob: Kafka -> MySQL + HBase 双写
- CounterJob: Kafka -> Redis 计数器更新
- 其他作业: 套牌检测、违章检测、流量统计等

用法:
    python realtime_generator.py                # 每秒10条
    python realtime_generator.py --rate 50      # 每秒50条
"""

import json
import random
import string
import argparse
import signal
import time
from datetime import datetime
from typing import List

from kafka import KafkaProducer

# ============== 配置 ==============

KAFKA_CONFIG = {
    'bootstrap_servers': ['localhost:19092'],
    'topic': 'etc-pass-records'  # 与 Flink FlinkConfig.KAFKA_TOPIC_PASS_RECORDS 保持一致
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


class RealtimeGenerator:
    def __init__(self, rate: int = 10):
        self.rate = rate
        self.running = False
        self.id_counter = int(time.time() * 1000000)
        self.gcxh_counter = 400000000000
        
        self.stats = {'generated': 0, 'sent': 0, 'errors': 0, 'start_time': None}
        self.kafka_producer = None
        
        self._connect()
    
    def _connect(self):
        try:
            self.kafka_producer = KafkaProducer(
                bootstrap_servers=KAFKA_CONFIG['bootstrap_servers'],
                value_serializer=lambda v: json.dumps(v, default=str).encode('utf-8'),
                acks='all',
                retries=3
            )
            print(f"✅ Kafka 已连接: {KAFKA_CONFIG['bootstrap_servers']}")
            print(f"   Topic: {KAFKA_CONFIG['topic']}")
        except Exception as e:
            print(f"❌ Kafka 连接失败: {e}")
            exit(1)
    
    def _generate_plate(self) -> str:
        prefix = random.choice(PLATE_PREFIXES)
        suffix = ''.join([
            random.choice(string.ascii_uppercase),
            random.choice(string.ascii_uppercase),
            str(random.randint(0, 9)),
            str(random.randint(0, 9)),
            str(random.randint(0, 9))
        ])
        return f"{prefix}{suffix}"
    
    def generate_record(self) -> dict:
        """生成一条通行记录（与 Flink PassRecordEvent 结构对应）"""
        checkpoint = random.choice(CHECKPOINTS)
        plate = self._generate_plate()
        
        self.id_counter += 1
        self.gcxh_counter += 1
        
        # 与 Flink PassRecordEvent 字段对应
        return {
            'id': self.id_counter,
            'gcxh': f"G320300{self.gcxh_counter}",
            'xzqhmc': checkpoint['district'],
            'kkmc': checkpoint['name'],
            'fxlx': random.choice(DIRECTIONS),
            'gcsj': datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
            'hpzl': random.choice(PLATE_TYPES),
            'hp': plate,
            'clppxh': random.choice(VEHICLE_TYPES),
            'checkpointId': checkpoint['id'],
            'eventTime': int(time.time() * 1000)  # 毫秒时间戳
        }
    
    def send_to_kafka(self, records: List[dict]):
        """发送到 Kafka，由 Flink 消费处理"""
        try:
            for r in records:
                self.kafka_producer.send(KAFKA_CONFIG['topic'], value=r)
            self.kafka_producer.flush()
            self.stats['sent'] += len(records)
        except Exception as e:
            print(f"❌ Kafka 发送失败: {e}")
            self.stats['errors'] += 1
    
    def run(self):
        self.running = True
        self.stats['start_time'] = datetime.now()
        
        print(f"\n🚀 开始生成数据 (速率: {self.rate} 条/秒)")
        print("   数据流: Python -> Kafka -> Flink -> MySQL/HBase/Redis")
        print("   按 Ctrl+C 停止\n")
        
        signal.signal(signal.SIGINT, lambda s, f: setattr(self, 'running', False))
        signal.signal(signal.SIGTERM, lambda s, f: setattr(self, 'running', False))
        
        batch_size = max(1, self.rate // 10)
        interval = 0.1
        
        while self.running:
            try:
                start = time.time()
                records = [self.generate_record() for _ in range(batch_size)]
                
                self.send_to_kafka(records)
                self.stats['generated'] += len(records)
                
                if self.stats['generated'] % (self.rate * 10) < batch_size:
                    elapsed = (datetime.now() - self.stats['start_time']).total_seconds()
                    rate = self.stats['sent'] / elapsed if elapsed > 0 else 0
                    print(f"📊 已发送: {self.stats['sent']:,} 条 | 速率: {rate:.1f}/s | Kafka待处理")
                
                sleep_time = max(0, interval - (time.time() - start))
                if sleep_time > 0:
                    time.sleep(sleep_time)
            except Exception as e:
                print(f"❌ 错误: {e}")
                self.stats['errors'] += 1
                time.sleep(1)
        
        print(f"\n✅ 停止! 共发送 {self.stats['sent']:,} 条到 Kafka")
        if self.kafka_producer:
            self.kafka_producer.close()


def main():
    parser = argparse.ArgumentParser(description='ETC 实时数据生成器（写入Kafka）')
    parser.add_argument('--rate', type=int, default=10, help='每秒记录数 (默认: 10)')
    args = parser.parse_args()
    
    print("=" * 55)
    print("      ETC 实时数据生成器 (Kafka -> Flink 架构)")
    print("=" * 55)
    print(f"  速率: {args.rate} 条/秒")
    print(f"  Kafka: {KAFKA_CONFIG['bootstrap_servers']}")
    print(f"  Topic: {KAFKA_CONFIG['topic']}")
    print("=" * 55)
    print("\n📌 Flink 作业负责：")
    print("   - DataSyncJob: 双写 MySQL + HBase")
    print("   - CounterJob: 更新 Redis 计数器")
    print("   - ClonePlateDetectJob: 套牌检测")
    print("   - TrafficFlowJob: 流量统计")
    print()
    
    generator = RealtimeGenerator(rate=args.rate)
    generator.run()


if __name__ == '__main__':
    main()
