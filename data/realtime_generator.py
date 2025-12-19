#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ETC 实时数据生成器

功能：
1. 持续生成模拟通行记录，写入 MySQL
2. 同步更新 Redis 中的实时流量
3. 发送 Kafka 消息通知后端
4. 支持可配置的生成频率

使用方式：
    python realtime_generator.py                    # 默认每秒10条
    python realtime_generator.py --rate 50          # 每秒50条
    python realtime_generator.py --rate 100 --batch # 批量模式

依赖：
    pip install pymysql redis kafka-python
"""

import os
import sys
import time
import json
import random
import string
import argparse
import signal
from datetime import datetime, timedelta
from decimal import Decimal
from typing import List, Dict, Optional
import threading

# MySQL
import pymysql
from pymysql.cursors import DictCursor

# Redis
import redis

# Kafka (可选)
try:
    from kafka import KafkaProducer
    HAS_KAFKA = True
except ImportError:
    HAS_KAFKA = False
    print("⚠️ kafka-python 未安装，Kafka 功能禁用")

# ============== 配置 ==============

# MySQL 配置 (Docker 集群)
MYSQL_CONFIG = {
    'host': 'localhost',
    'port': 13306,
    'user': 'root',
    'password': 'etc123456',
    'database': 'etc_db',
    'charset': 'utf8mb4',
    'autocommit': True
}

# Redis 配置
REDIS_CONFIG = {
    'host': 'localhost',
    'port': 6379,
    'password': 'etc123456',
    'db': 0,
    'decode_responses': True
}

# Kafka 配置 (使用 kafka 主机名，需要在 hosts 中映射 127.0.0.1 kafka)
KAFKA_CONFIG = {
    'bootstrap_servers': ['kafka:9092'],
    'topic': 'etc-pass-record'  # 与 Docker Kafka 中的 topic 名一致
}

# ============== 卡口数据（19个出市卡口）==============

CHECKPOINTS = [
    {"id": 1, "name": "苏皖卡口1(104省道)", "district": "铜山区", "road": "S104省道", "lng": 117.1847, "lat": 34.0523},
    {"id": 2, "name": "苏皖卡口2(311国道)", "district": "铜山区", "road": "G311国道", "lng": 117.0892, "lat": 34.1156},
    {"id": 3, "name": "苏皖卡口3(徐明高速)", "district": "睢宁县", "road": "徐明高速", "lng": 117.9234, "lat": 33.8901},
    {"id": 4, "name": "苏皖卡口4(235国道)", "district": "丰县", "road": "G235国道", "lng": 116.5678, "lat": 34.5432},
    {"id": 5, "name": "苏皖卡口5(301省道)", "district": "丰县", "road": "S301省道", "lng": 116.4521, "lat": 34.6123},
    {"id": 6, "name": "苏皖卡口6(丰砀路)", "district": "丰县", "road": "丰砀路", "lng": 116.3892, "lat": 34.5891},
    {"id": 7, "name": "苏鲁卡口1(206国道)", "district": "邳州市", "road": "G206国道", "lng": 117.9634, "lat": 34.5123},
    {"id": 8, "name": "苏鲁卡口2(310国道)", "district": "邳州市", "road": "G310国道", "lng": 118.0123, "lat": 34.4567},
    {"id": 9, "name": "苏鲁卡口3(京沪高速)", "district": "新沂市", "road": "京沪高速", "lng": 118.3456, "lat": 34.3789},
    {"id": 10, "name": "苏鲁卡口4(205国道)", "district": "新沂市", "road": "G205国道", "lng": 118.3891, "lat": 34.4012},
    {"id": 11, "name": "苏鲁卡口5(323省道)", "district": "新沂市", "road": "S323省道", "lng": 118.4234, "lat": 34.3567},
    {"id": 12, "name": "苏鲁卡口6(沂河路)", "district": "新沂市", "road": "沂河路", "lng": 118.4567, "lat": 34.3234},
    {"id": 13, "name": "连云港卡口1(徐连高速)", "district": "新沂市", "road": "徐连高速", "lng": 118.5123, "lat": 34.2891},
    {"id": 14, "name": "连云港卡口2(249省道)", "district": "睢宁县", "road": "S249省道", "lng": 118.2345, "lat": 33.9876},
    {"id": 15, "name": "宿迁卡口1(徐宿高速)", "district": "睢宁县", "road": "徐宿高速", "lng": 117.8765, "lat": 33.9234},
    {"id": 16, "name": "宿迁卡口2(324省道)", "district": "睢宁县", "road": "S324省道", "lng": 117.7891, "lat": 33.8567},
    {"id": 17, "name": "宿迁卡口3(104国道)", "district": "铜山区", "road": "G104国道", "lng": 117.2345, "lat": 34.0891},
    {"id": 18, "name": "宿迁卡口4(251省道)", "district": "铜山区", "road": "S251省道", "lng": 117.3456, "lat": 34.0234},
    {"id": 19, "name": "宿迁卡口5(沛丰路)", "district": "沛县", "road": "沛丰路", "lng": 116.9234, "lat": 34.7123},
]

# ============== 车辆数据模板 ==============

# 车牌前缀（江苏徐州为主 + 周边省份）
PLATE_PREFIXES = [
    ("苏C", 0.60),   # 徐州本地 60%
    ("苏A", 0.05),   # 南京
    ("苏B", 0.03),   # 无锡
    ("苏N", 0.05),   # 宿迁
    ("苏H", 0.03),   # 连云港
    ("鲁", 0.10),    # 山东
    ("皖", 0.08),    # 安徽
    ("豫", 0.04),    # 河南
    ("其他", 0.02),  # 其他省份
]

# 车辆类型
VEHICLE_TYPES = [
    ("小型客车", 0.70),
    ("中型客车", 0.05),
    ("大型客车", 0.03),
    ("小型货车", 0.08),
    ("中型货车", 0.06),
    ("大型货车", 0.05),
    ("特种车辆", 0.02),
    ("摩托车", 0.01),
]

# 行驶方向
DIRECTIONS = [("in", 0.52), ("out", 0.48)]

# 时段流量权重（模拟早晚高峰）
HOURLY_WEIGHTS = {
    0: 0.02, 1: 0.01, 2: 0.01, 3: 0.01, 4: 0.02, 5: 0.03,
    6: 0.05, 7: 0.08, 8: 0.10, 9: 0.08, 10: 0.06, 11: 0.06,
    12: 0.05, 13: 0.05, 14: 0.06, 15: 0.06, 16: 0.07, 17: 0.09,
    18: 0.08, 19: 0.05, 20: 0.04, 21: 0.03, 22: 0.02, 23: 0.02
}

# ============== 数据生成器类 ==============

class RealtimeDataGenerator:
    """实时数据生成器"""
    
    def __init__(self, rate: int = 10, enable_kafka: bool = True):
        """
        初始化生成器
        
        Args:
            rate: 每秒生成的记录数
            enable_kafka: 是否启用 Kafka
        """
        self.rate = rate
        self.enable_kafka = enable_kafka and HAS_KAFKA
        self.running = False
        self.stats = {
            'total_generated': 0,
            'total_inserted': 0,
            'violations': 0,
            'clone_detections': 0,
            'alerts': 0,
            'errors': 0,
            'start_time': None
        }
        
        # 用于套牌检测的车辆追踪
        self.recent_plates = {}  # {plate: [(checkpoint_id, time), ...]}
        
        # 数据库连接
        self.mysql_conn = None
        self.redis_client = None
        self.kafka_producer = None
        
        # 连接数据库
        self._connect()
    
    def _connect(self):
        """连接所有数据存储"""
        # MySQL
        try:
            self.mysql_conn = pymysql.connect(**MYSQL_CONFIG)
            print(f"✅ MySQL 已连接: {MYSQL_CONFIG['host']}:{MYSQL_CONFIG['port']}")
        except Exception as e:
            print(f"❌ MySQL 连接失败: {e}")
            sys.exit(1)
        
        # Redis
        try:
            self.redis_client = redis.Redis(**REDIS_CONFIG)
            self.redis_client.ping()
            print(f"✅ Redis 已连接: {REDIS_CONFIG['host']}:{REDIS_CONFIG['port']}")
        except Exception as e:
            print(f"⚠️ Redis 连接失败 (将跳过Redis更新): {e}")
            self.redis_client = None
        
        # Kafka
        if self.enable_kafka:
            try:
                self.kafka_producer = KafkaProducer(
                    bootstrap_servers=KAFKA_CONFIG['bootstrap_servers'],
                    value_serializer=lambda v: json.dumps(v, default=str).encode('utf-8')
                )
                print(f"✅ Kafka 已连接: {KAFKA_CONFIG['bootstrap_servers']}")
            except Exception as e:
                print(f"⚠️ Kafka 连接失败 (将跳过Kafka发送): {e}")
                self.kafka_producer = None
    
    def _weighted_choice(self, choices: List[tuple]) -> str:
        """加权随机选择"""
        items, weights = zip(*choices)
        return random.choices(items, weights=weights)[0]
    
    def _generate_plate_number(self) -> str:
        """生成车牌号"""
        prefix = self._weighted_choice(PLATE_PREFIXES)
        
        if prefix == "其他":
            # 随机其他省份
            other_prefixes = ["京", "津", "冀", "晋", "蒙", "辽", "吉", "黑", 
                            "沪", "浙", "闽", "赣", "湘", "粤", "桂", "琼",
                            "川", "贵", "云", "陕", "甘", "青", "宁", "新"]
            prefix = random.choice(other_prefixes)
        
        if prefix == "鲁":
            # 山东车牌
            cities = ["A", "B", "C", "D", "E", "F", "G", "H", "J", "K", "L", "M", "N", "P", "Q", "R"]
            prefix = "鲁" + random.choice(cities)
        elif prefix == "皖":
            # 安徽车牌
            cities = ["A", "B", "C", "D", "E", "F", "G", "H", "J", "K", "L", "M", "N", "P", "R", "S"]
            prefix = "皖" + random.choice(cities)
        elif prefix == "豫":
            # 河南车牌
            cities = ["A", "B", "C", "D", "E", "F", "G", "H", "J", "K", "L", "M", "N", "P", "Q", "R", "S"]
            prefix = "豫" + random.choice(cities)
        
        # 生成后缀 (5位: 字母+数字混合)
        suffix = ''.join(random.choices(string.ascii_uppercase + string.digits, k=5))
        
        return prefix + suffix
    
    def _generate_speed(self, vehicle_type: str, is_overspeed: bool = False) -> float:
        """根据车型生成速度"""
        if is_overspeed:
            return round(random.uniform(121, 180), 1)
        if "货车" in vehicle_type:
            return round(random.uniform(60, 90), 1)
        elif "客车" in vehicle_type:
            return round(random.uniform(70, 110), 1)
        else:
            return round(random.uniform(60, 120), 1)
    
    def _generate_etc_deduction(self, vehicle_type: str, checkpoint: dict) -> Decimal:
        """生成ETC扣费金额"""
        base_fee = random.uniform(10, 30)
        
        # 大型车辆费用更高
        if "大型" in vehicle_type:
            base_fee *= 2.5
        elif "中型" in vehicle_type:
            base_fee *= 1.5
        
        # 高速公路收费更高
        if "高速" in checkpoint['road']:
            base_fee *= 1.8
        
        return Decimal(str(round(base_fee, 2)))
    
    def generate_record(self) -> dict:
        """生成一条通行记录"""
        # 选择卡口（可以加权，让某些卡口流量更大）
        checkpoint = random.choice(CHECKPOINTS)
        
        # 基础数据
        vehicle_type = self._weighted_choice(VEHICLE_TYPES)
        direction = self._weighted_choice(DIRECTIONS)
        plate_number = self._generate_plate_number()
        
        # 2%概率超速
        is_overspeed = random.random() < 0.02
        speed = self._generate_speed(vehicle_type, is_overspeed)
        etc_deduction = self._generate_etc_deduction(vehicle_type, checkpoint)
        
        # 时间戳（当前时间）
        pass_time = datetime.now()
        
        record = {
            'checkpoint_id': checkpoint['id'],
            'checkpoint_name': checkpoint['name'],
            'plate_number': plate_number,
            'vehicle_type': vehicle_type,
            'pass_time': pass_time,
            'direction': direction,
            'speed': speed,
            'lane_no': random.randint(1, 4),
            'etc_deduction': etc_deduction,
            'image_url': None,
            'is_overspeed': is_overspeed
        }
        
        # 追踪车牌用于套牌检测
        self._track_plate(plate_number, checkpoint['id'], pass_time)
        
        return record
    
    def _track_plate(self, plate: str, checkpoint_id: int, time: datetime):
        """追踪车牌用于套牌检测"""
        if plate not in self.recent_plates:
            self.recent_plates[plate] = []
        
        self.recent_plates[plate].append((checkpoint_id, time))
        
        # 只保留最近5分钟的记录
        cutoff = datetime.now() - timedelta(minutes=5)
        self.recent_plates[plate] = [
            (cp, t) for cp, t in self.recent_plates[plate] if t > cutoff
        ]
        
        # 清理过期的车牌
        if len(self.recent_plates) > 10000:
            expired_plates = [p for p, records in self.recent_plates.items() 
                           if not records or records[-1][1] < cutoff]
            for p in expired_plates[:1000]:
                del self.recent_plates[p]
    
    def check_clone_plate(self, record: dict) -> Optional[dict]:
        """检测套牌嫌疑"""
        plate = record['plate_number']
        if plate not in self.recent_plates or len(self.recent_plates[plate]) < 2:
            return None
        
        records = self.recent_plates[plate]
        # 检查最近两条记录
        if len(records) >= 2:
            cp1_id, time1 = records[-2]
            cp2_id, time2 = records[-1]
            
            # 不同卡口，时间差小于5分钟
            if cp1_id != cp2_id:
                time_diff = (time2 - time1).total_seconds()
                if 60 < time_diff < 300:  # 1-5分钟
                    # 计算距离（模拟）
                    distance = random.uniform(30, 100)
                    calculated_speed = (distance / time_diff) * 3600
                    
                    if calculated_speed > 200:  # 不可能的速度
                        cp1 = next((c for c in CHECKPOINTS if c['id'] == cp1_id), None)
                        cp2 = next((c for c in CHECKPOINTS if c['id'] == cp2_id), None)
                        
                        return {
                            'plate_number': plate,
                            'detection_time': datetime.now(),
                            'checkpoint1_id': cp1_id,
                            'checkpoint1_name': cp1['name'] if cp1 else f'卡口{cp1_id}',
                            'checkpoint1_time': time1,
                            'checkpoint2_id': cp2_id,
                            'checkpoint2_name': cp2['name'] if cp2 else f'卡口{cp2_id}',
                            'checkpoint2_time': time2,
                            'distance': distance,
                            'time_diff': int(time_diff),
                            'calculated_speed': calculated_speed,
                            'confidence': round(random.uniform(0.75, 0.95), 2),
                            'status': 0
                        }
        return None
    
    def generate_violation(self, record: dict) -> Optional[dict]:
        """根据通行记录生成违章记录"""
        violations = []
        
        # 超速违章
        if record.get('is_overspeed') and record['speed'] > 120:
            over_percent = ((record['speed'] - 120) / 120) * 100
            if over_percent < 20:
                fine, points = 200, 3
            elif over_percent < 50:
                fine, points = 500, 6
            else:
                fine, points = 1000, 12
            
            return {
                'plate_number': record['plate_number'],
                'checkpoint_id': record['checkpoint_id'],
                'violation_type': 'overspeed',
                'violation_time': record['pass_time'],
                'description': f"超速行驶，实测速度{record['speed']}km/h，超过限速{int(record['speed']-120)}km/h",
                'fine_amount': fine,
                'points': points,
                'status': 0
            }
        
        return None
    
    def generate_alert(self, record: dict, alert_type: str, extra: dict = None) -> dict:
        """生成告警记录"""
        checkpoint = next((c for c in CHECKPOINTS if c['id'] == record.get('checkpoint_id', 1)), CHECKPOINTS[0])
        
        alert_configs = {
            'overspeed': {
                'level': 'warning',
                'title': f"超速告警 - {checkpoint['name']}",
                'content': f"车辆{record['plate_number']}在{checkpoint['name']}超速行驶，速度{record.get('speed', 0)}km/h"
            },
            'clone_plate': {
                'level': 'danger', 
                'title': f"套牌嫌疑 - {record['plate_number']}",
                'content': f"车辆{record['plate_number']}疑似套牌，短时间内出现在不同卡口"
            },
            'congestion': {
                'level': 'warning',
                'title': f"拥堵预警 - {checkpoint['name']}",
                'content': f"{checkpoint['name']}当前车流量较大，建议分流"
            }
        }
        
        config = alert_configs.get(alert_type, alert_configs['overspeed'])
        
        return {
            'alert_type': alert_type,
            'level': config['level'],
            'title': config['title'],
            'content': config['content'],
            'checkpoint_id': record.get('checkpoint_id'),
            'plate_number': record.get('plate_number'),
            'status': 0,
            'create_time': datetime.now()
        }
    
    def insert_to_mysql(self, records: List[dict]) -> int:
        """批量插入 MySQL"""
        if not self.mysql_conn:
            return 0
        
        sql = """
            INSERT INTO pass_record 
            (checkpoint_id, plate_number, vehicle_type, pass_time, direction, speed, lane_no, etc_deduction, image_url)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
        """
        
        try:
            with self.mysql_conn.cursor() as cursor:
                values = [
                    (r['checkpoint_id'], r['plate_number'], r['vehicle_type'], 
                     r['pass_time'], r['direction'], r['speed'], r['lane_no'],
                     r['etc_deduction'], r['image_url'])
                    for r in records
                ]
                cursor.executemany(sql, values)
                self.mysql_conn.commit()
                return len(records)
        except Exception as e:
            print(f"❌ MySQL 插入错误: {e}")
            self.stats['errors'] += 1
            return 0
    
    def insert_violation(self, violation: dict):
        """插入违章记录"""
        if not self.mysql_conn or not violation:
            return
        
        sql = """
            INSERT INTO violation 
            (plate_number, checkpoint_id, violation_type, violation_time, description, fine_amount, points, status)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
        """
        try:
            with self.mysql_conn.cursor() as cursor:
                cursor.execute(sql, (
                    violation['plate_number'], violation['checkpoint_id'],
                    violation['violation_type'], violation['violation_time'],
                    violation['description'], violation['fine_amount'],
                    violation['points'], violation['status']
                ))
                self.mysql_conn.commit()
                self.stats['violations'] += 1
        except Exception as e:
            print(f"⚠️ 违章记录插入失败: {e}")
    
    def insert_clone_detection(self, detection: dict):
        """插入套牌检测记录"""
        if not self.mysql_conn or not detection:
            return
        
        sql = """
            INSERT INTO clone_plate_detection 
            (plate_number, detection_time, checkpoint1_id, checkpoint1_name, checkpoint1_time,
             checkpoint2_id, checkpoint2_name, checkpoint2_time, distance, time_diff, 
             calculated_speed, confidence, status)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """
        try:
            with self.mysql_conn.cursor() as cursor:
                cursor.execute(sql, (
                    detection['plate_number'], detection['detection_time'],
                    detection['checkpoint1_id'], detection['checkpoint1_name'], detection['checkpoint1_time'],
                    detection['checkpoint2_id'], detection['checkpoint2_name'], detection['checkpoint2_time'],
                    detection['distance'], detection['time_diff'],
                    detection['calculated_speed'], detection['confidence'], detection['status']
                ))
                self.mysql_conn.commit()
                self.stats['clone_detections'] += 1
        except Exception as e:
            print(f"⚠️ 套牌检测记录插入失败: {e}")
    
    def insert_alert(self, alert: dict):
        """插入告警记录"""
        if not self.mysql_conn or not alert:
            return
        
        sql = """
            INSERT INTO alert 
            (alert_type, level, title, content, checkpoint_id, plate_number, status, create_time)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
        """
        try:
            with self.mysql_conn.cursor() as cursor:
                cursor.execute(sql, (
                    alert['alert_type'], alert['level'], alert['title'],
                    alert['content'], alert['checkpoint_id'], alert['plate_number'],
                    alert['status'], alert['create_time']
                ))
                self.mysql_conn.commit()
                self.stats['alerts'] += 1
        except Exception as e:
            print(f"⚠️ 告警记录插入失败: {e}")
    
    def update_redis(self, records: List[dict]):
        """更新 Redis 中的实时流量统计"""
        if not self.redis_client:
            return
        
        try:
            pipe = self.redis_client.pipeline()
            
            # 按卡口统计
            checkpoint_flows = {}
            for r in records:
                cp_id = r['checkpoint_id']
                checkpoint_flows[cp_id] = checkpoint_flows.get(cp_id, 0) + 1
            
            # 更新每个卡口的当前流量
            for cp_id, count in checkpoint_flows.items():
                key = f"etc:checkpoint:{cp_id}:flow"
                pipe.incrby(key, count)
                pipe.expire(key, 3600)  # 1小时过期
            
            # 更新今日总流量
            today = datetime.now().strftime('%Y%m%d')
            pipe.incrby(f"etc:daily:{today}:total", len(records))
            pipe.expire(f"etc:daily:{today}:total", 86400 * 7)  # 7天过期
            
            pipe.execute()
        except Exception as e:
            print(f"⚠️ Redis 更新失败: {e}")
    
    def send_to_kafka(self, records: List[dict]):
        """发送记录到 Kafka"""
        if not self.kafka_producer:
            return
        
        try:
            for record in records:
                # 转换为可序列化格式
                message = {
                    **record,
                    'pass_time': record['pass_time'].isoformat(),
                    'etc_deduction': float(record['etc_deduction'])
                }
                self.kafka_producer.send(KAFKA_CONFIG['topic'], value=message)
            
            self.kafka_producer.flush()
        except Exception as e:
            print(f"⚠️ Kafka 发送失败: {e}")
    
    def run(self):
        """运行生成器"""
        self.running = True
        self.stats['start_time'] = datetime.now()
        
        print(f"\n🚀 开始生成数据 (速率: {self.rate} 条/秒)")
        print("   功能: 通行记录 | 违章检测 | 套牌检测 | 实时告警")
        print("   按 Ctrl+C 停止\n")
        
        # 注册信号处理
        signal.signal(signal.SIGINT, self._signal_handler)
        signal.signal(signal.SIGTERM, self._signal_handler)
        
        batch_size = max(1, self.rate // 10)  # 每100ms一批
        interval = 1.0 / 10  # 100ms
        
        while self.running:
            try:
                start_time = time.time()
                
                # 生成一批记录
                records = []
                for _ in range(batch_size):
                    record = self.generate_record()
                    records.append(record)
                    
                    # 检查超速违章
                    violation = self.generate_violation(record)
                    if violation:
                        self.insert_violation(violation)
                        # 生成超速告警
                        alert = self.generate_alert(record, 'overspeed')
                        self.insert_alert(alert)
                        print(f"  🚨 超速违章: {record['plate_number']} 速度{record['speed']}km/h @ {violation['checkpoint_id']}号卡口")
                    
                    # 检测套牌车
                    clone_detection = self.check_clone_plate(record)
                    if clone_detection:
                        self.insert_clone_detection(clone_detection)
                        # 生成套牌告警
                        alert = self.generate_alert(record, 'clone_plate')
                        self.insert_alert(alert)
                        print(f"  ⚠️ 疑似套牌: {record['plate_number']} 计算速度{clone_detection['calculated_speed']}km/h")
                
                self.stats['total_generated'] += len(records)
                
                # 写入 MySQL
                inserted = self.insert_to_mysql(records)
                self.stats['total_inserted'] += inserted
                
                # 更新 Redis
                self.update_redis(records)
                
                # 发送 Kafka
                self.send_to_kafka(records)
                
                # 每10秒打印统计
                if self.stats['total_generated'] % (self.rate * 10) == 0:
                    self._print_stats()
                
                # 控制速率
                elapsed = time.time() - start_time
                sleep_time = max(0, interval - elapsed)
                if sleep_time > 0:
                    time.sleep(sleep_time)
                    
            except Exception as e:
                print(f"❌ 运行错误: {e}")
                self.stats['errors'] += 1
                time.sleep(1)
        
        self._cleanup()
    
    def _signal_handler(self, signum, frame):
        """信号处理"""
        print("\n\n⏹️ 收到停止信号，正在关闭...")
        self.running = False
    
    def _print_stats(self):
        """打印统计信息"""
        elapsed = (datetime.now() - self.stats['start_time']).total_seconds()
        rate = self.stats['total_inserted'] / elapsed if elapsed > 0 else 0
        
        print(f"📊 统计 | 通行: {self.stats['total_inserted']:,} | "
              f"违章: {self.stats['violations']} | "
              f"套牌: {self.stats['clone_detections']} | "
              f"告警: {self.stats['alerts']} | "
              f"速率: {rate:.1f}/s")
    
    def _cleanup(self):
        """清理资源"""
        print("\n正在清理资源...")
        
        if self.mysql_conn:
            self.mysql_conn.close()
            print("  ✓ MySQL 已关闭")
        
        if self.kafka_producer:
            self.kafka_producer.close()
            print("  ✓ Kafka 已关闭")
        
        self._print_stats()
        print("\n✅ 数据生成器已停止")


def main():
    """主函数"""
    parser = argparse.ArgumentParser(description='ETC 实时数据生成器')
    parser.add_argument('--rate', type=int, default=10, help='每秒生成记录数 (默认: 10)')
    parser.add_argument('--no-kafka', action='store_true', help='禁用 Kafka')
    
    args = parser.parse_args()
    
    print("=" * 60)
    print("        ETC 实时数据生成器")
    print("=" * 60)
    print(f"  生成速率: {args.rate} 条/秒")
    print(f"  Kafka: {'禁用' if args.no_kafka else '启用'}")
    print("=" * 60)
    
    generator = RealtimeDataGenerator(
        rate=args.rate,
        enable_kafka=not args.no_kafka
    )
    generator.run()


if __name__ == '__main__':
    main()
