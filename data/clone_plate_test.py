#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
套牌车测试数据生成器
模拟同一车牌短时间内出现在不同卡口，触发套牌检测
"""

import json
import time
import random
from datetime import datetime
from kafka import KafkaProducer

# Kafka配置
KAFKA_BOOTSTRAP_SERVERS = 'localhost:19092'
KAFKA_TOPIC = 'etc-pass-records'

# 卡口列表（模拟相距较远的卡口对）
CHECKPOINT_PAIRS = [
    (1, 7),   # 距离150km
    (2, 8),   # 距离120km
    (3, 9),   # 距离100km
    (1, 13),  # 距离200km
    (5, 16),  # 距离90km
]

def create_producer():
    """创建Kafka生产者"""
    return KafkaProducer(
        bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS,
        value_serializer=lambda v: json.dumps(v, ensure_ascii=False).encode('utf-8'),
        key_serializer=lambda k: k.encode('utf-8') if k else None
    )

def generate_plate_number(index):
    """生成车牌号 苏C00001, 苏C00002, ..."""
    return f"苏C{index:05d}"

def generate_clone_plate_record(plate_number, checkpoint_id):
    """生成过车记录"""
    now = datetime.now()
    return {
        "hp": plate_number,                              # 车牌号
        "gcsj": now.strftime("%Y-%m-%d %H:%M:%S"),       # 过车时间
        "checkpointId": f"CP{checkpoint_id:03d}",        # 卡口ID
        "direction": random.choice(["进城", "出城"]),
        "speed": random.randint(60, 120),
        "laneNo": random.randint(1, 4),
        "vehicleType": random.choice(["小型车", "中型车", "大型车"]),
        "etcDeduction": round(random.uniform(10, 50), 2)
    }

def main():
    print("=" * 60)
    print("🚗 套牌车测试数据生成器")
    print("=" * 60)
    print(f"Kafka地址: {KAFKA_BOOTSTRAP_SERVERS}")
    print(f"Topic: {KAFKA_TOPIC}")
    print("-" * 60)
    
    producer = create_producer()
    plate_index = 1
    
    try:
        while True:
            # 生成当前测试车牌
            plate_number = generate_plate_number(plate_index)
            
            # 随机选择一对相距较远的卡口
            cp1, cp2 = random.choice(CHECKPOINT_PAIRS)
            
            print(f"\n🔴 模拟套牌车: {plate_number}")
            print(f"   将在3秒内出现在卡口 {cp1} 和 {cp2}")
            
            # 第一条记录：卡口1
            record1 = generate_clone_plate_record(plate_number, cp1)
            producer.send(KAFKA_TOPIC, key=plate_number, value=record1)
            print(f"   ✓ 发送记录1: 卡口{cp1} @ {record1['gcsj']}")
            
            # 等待2-3秒（短到不可能真的开过去）
            time.sleep(random.uniform(2, 3))
            
            # 第二条记录：卡口2
            record2 = generate_clone_plate_record(plate_number, cp2)
            producer.send(KAFKA_TOPIC, key=plate_number, value=record2)
            print(f"   ✓ 发送记录2: 卡口{cp2} @ {record2['gcsj']}")
            
            producer.flush()
            print(f"   ⚠️  应触发套牌检测！")
            
            # 下一辆车
            plate_index += 1
            
            # 每辆车之间间隔5秒
            print(f"\n等待5秒后生成下一辆套牌车...")
            time.sleep(5)
            
    except KeyboardInterrupt:
        print("\n\n已停止生成")
    finally:
        producer.close()

if __name__ == "__main__":
    main()
