#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
历史数据生成器
生成一个月内的历史数据，包括：
- 通行记录 (pass_record)
- 违章记录 (violation)  
- 套牌车检测 (clone_plate_detection)
- 告警记录 (alert)
"""

import mysql.connector
import random
from datetime import datetime, timedelta
from decimal import Decimal
import argparse

# 数据库配置
DB_CONFIG = {
    'host': 'localhost',
    'port': 13306,
    'user': 'root',
    'password': 'etc123456',
    'database': 'etc_db',
    'charset': 'utf8mb4'
}

# 19个卡口配置
CHECKPOINTS = {
    1: {'name': '苏皖界1(104省道)', 'region': '睢宁县'},
    2: {'name': '苏皖界2(311国道)', 'region': '铜山区'},
    3: {'name': '苏皖界3(徐明高速)', 'region': '铜山区'},
    4: {'name': '苏皖界4(宿新高速)', 'region': '睢宁县'},
    5: {'name': '苏皖界5(徐淮高速)', 'region': '沛县'},
    6: {'name': '苏皖界6(新扬高速)', 'region': '新沂市'},
    7: {'name': '苏鲁界1(206国道)', 'region': '沛县'},
    8: {'name': '苏鲁界2(104国道)', 'region': '邳州市'},
    9: {'name': '苏鲁界3(京台高速)', 'region': '贾汪区'},
    10: {'name': '苏鲁界4(枣庄连接线)', 'region': '邳州市'},
    11: {'name': '苏鲁界5(京沪高速)', 'region': '邳州市'},
    12: {'name': '苏鲁界6(沂河路)', 'region': '新沂市'},
    13: {'name': '连云港界1(徐连高速)', 'region': '邳州市'},
    14: {'name': '连云港界2(310国道)', 'region': '邳州市'},
    15: {'name': '宿迁界1(徐宿高速)', 'region': '铜山区'},
    16: {'name': '宿迁界2(徐宿快速)', 'region': '铜山区'},
    17: {'name': '宿迁界3(104国道)', 'region': '睢宁县'},
    18: {'name': '宿迁界4(新扬高速)', 'region': '睢宁县'},
    19: {'name': '宿迁界5(徐盐高速)', 'region': '睢宁县'}
}

# 省份简称（车牌）
PROVINCES = ['苏', '皖', '鲁', '豫', '冀', '京', '津', '沪', '浙', '粤', '川', '渝']
LOCAL_PROVINCE = '苏'
LOCAL_CITIES = ['C', 'A', 'B', 'D', 'E', 'F']  # 苏C是徐州

# 车辆类型
VEHICLE_TYPES = ['小型客车', '中型客车', '大型客车', '小型货车', '中型货车', '大型货车', '特大型货车']

# 违章类型
VIOLATION_TYPES = {
    'overspeed': {'name': '超速', 'fine': (200, 2000), 'points': (3, 12)},
    'overload': {'name': '超载', 'fine': (500, 2000), 'points': (3, 6)},
    'illegal_lane': {'name': '违规变道', 'fine': (100, 200), 'points': (2, 3)},
    'no_etc': {'name': '闯卡', 'fine': (200, 500), 'points': (3, 6)},
    'reverse': {'name': '逆行', 'fine': (200, 500), 'points': (6, 12)}
}

def generate_plate_number():
    """生成车牌号"""
    # 70%本地车，30%外地车
    if random.random() < 0.7:
        province = LOCAL_PROVINCE
        city = random.choice(LOCAL_CITIES[:3])  # 主要是苏C
    else:
        province = random.choice(PROVINCES)
        city = random.choice('ABCDEFGH')
    
    # 生成5位字母数字组合
    chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ0123456789'
    suffix = ''.join(random.choices(chars, k=5))
    return f"{province}{city}{suffix}"

def generate_speed(is_overspeed=False):
    """生成车速"""
    if is_overspeed:
        return round(random.uniform(121, 180), 2)
    else:
        return round(random.uniform(60, 120), 2)

def get_hourly_factor(hour):
    """获取小时流量因子（模拟高峰低谷）"""
    if 7 <= hour <= 9:  # 早高峰
        return 1.5
    elif 17 <= hour <= 19:  # 晚高峰
        return 1.6
    elif 11 <= hour <= 14:  # 午间
        return 1.2
    elif 0 <= hour <= 5:  # 深夜
        return 0.3
    else:
        return 1.0

def generate_pass_records(conn, start_date, end_date, records_per_day=5000):
    """生成通行记录"""
    cursor = conn.cursor()
    print(f"开始生成通行记录: {start_date} ~ {end_date}")
    
    current_date = start_date
    total_records = 0
    batch_size = 1000
    
    while current_date <= end_date:
        # 根据星期几调整流量（周末少一些）
        weekday = current_date.weekday()
        day_factor = 0.7 if weekday >= 5 else 1.0
        day_records = int(records_per_day * day_factor)
        
        records = []
        for _ in range(day_records):
            # 随机小时，考虑高峰因子
            hour = random.randint(0, 23)
            if random.random() > get_hourly_factor(hour) / 1.6:
                continue
            
            minute = random.randint(0, 59)
            second = random.randint(0, 59)
            pass_time = current_date.replace(hour=hour, minute=minute, second=second)
            
            checkpoint_id = random.randint(1, 19)
            plate = generate_plate_number()
            direction = random.choice(['in', 'out'])
            speed = generate_speed()
            lane_no = random.randint(1, 4)
            vehicle_type = random.choice(VEHICLE_TYPES)
            etc_deduction = round(random.uniform(5, 100), 2) if random.random() < 0.8 else 0
            
            records.append((
                plate, checkpoint_id, pass_time, direction, 
                speed, lane_no, vehicle_type, etc_deduction
            ))
        
        # 批量插入
        for i in range(0, len(records), batch_size):
            batch = records[i:i+batch_size]
            cursor.executemany('''
                INSERT INTO pass_record 
                (plate_number, checkpoint_id, pass_time, direction, speed, lane_no, vehicle_type, etc_deduction)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
            ''', batch)
            conn.commit()
        
        total_records += len(records)
        print(f"  {current_date.strftime('%Y-%m-%d')}: {len(records)} 条记录")
        current_date += timedelta(days=1)
    
    print(f"通行记录生成完成，共 {total_records} 条")
    return total_records

def generate_violations(conn, start_date, end_date, violations_per_day=50):
    """生成违章记录"""
    cursor = conn.cursor()
    print(f"开始生成违章记录: {start_date} ~ {end_date}")
    
    current_date = start_date
    total_records = 0
    
    while current_date <= end_date:
        day_violations = random.randint(int(violations_per_day * 0.5), int(violations_per_day * 1.5))
        
        for _ in range(day_violations):
            hour = random.randint(6, 22)
            minute = random.randint(0, 59)
            violation_time = current_date.replace(hour=hour, minute=minute, second=random.randint(0,59))
            
            plate = generate_plate_number()
            checkpoint_id = random.randint(1, 19)
            v_type = random.choice(list(VIOLATION_TYPES.keys()))
            v_info = VIOLATION_TYPES[v_type]
            
            fine = random.randint(*v_info['fine'])
            points = random.randint(*v_info['points'])
            description = f"{v_info['name']}违章，在{CHECKPOINTS[checkpoint_id]['name']}被检测"
            
            # 70%已处理，30%未处理
            status = 1 if random.random() < 0.7 else 0
            process_time = violation_time + timedelta(days=random.randint(1, 7)) if status == 1 else None
            
            cursor.execute('''
                INSERT INTO violation 
                (plate_number, checkpoint_id, violation_type, violation_time, description, fine_amount, points, status, process_time)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
            ''', (plate, checkpoint_id, v_type, violation_time, description, fine, points, status, process_time))
        
        conn.commit()
        total_records += day_violations
        current_date += timedelta(days=1)
    
    print(f"违章记录生成完成，共 {total_records} 条")
    return total_records

def generate_clone_plate_detections(conn, start_date, end_date, detections_per_day=5):
    """生成套牌车检测记录"""
    cursor = conn.cursor()
    print(f"开始生成套牌检测记录: {start_date} ~ {end_date}")
    
    current_date = start_date
    total_records = 0
    
    while current_date <= end_date:
        day_detections = random.randint(2, detections_per_day * 2)
        
        for _ in range(day_detections):
            hour = random.randint(6, 22)
            detection_time = current_date.replace(hour=hour, minute=random.randint(0,59), second=random.randint(0,59))
            
            plate = generate_plate_number()
            
            # 两个不同卡口
            cp1_id = random.randint(1, 19)
            cp2_id = random.randint(1, 19)
            while cp2_id == cp1_id:
                cp2_id = random.randint(1, 19)
            
            # 时间差很短（套牌嫌疑）
            time_diff = random.randint(60, 600)  # 1-10分钟
            cp1_time = detection_time
            cp2_time = detection_time + timedelta(seconds=time_diff)
            
            # 距离和计算速度
            distance = random.uniform(20, 100)  # km
            calculated_speed = (distance / time_diff) * 3600  # km/h
            
            confidence = round(random.uniform(0.7, 0.99), 2)
            status = random.choice([0, 0, 0, 1])  # 75%未处理
            
            cursor.execute('''
                INSERT INTO clone_plate_detection 
                (plate_number, detection_time, checkpoint1_id, checkpoint1_name, checkpoint1_time,
                 checkpoint2_id, checkpoint2_name, checkpoint2_time, distance, time_diff, 
                 calculated_speed, confidence, status)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            ''', (plate, detection_time, cp1_id, CHECKPOINTS[cp1_id]['name'], cp1_time,
                  cp2_id, CHECKPOINTS[cp2_id]['name'], cp2_time, distance, time_diff,
                  calculated_speed, confidence, status))
        
        conn.commit()
        total_records += day_detections
        current_date += timedelta(days=1)
    
    print(f"套牌检测记录生成完成，共 {total_records} 条")
    return total_records

def generate_alerts(conn, start_date, end_date, alerts_per_day=30):
    """生成告警记录"""
    cursor = conn.cursor()
    print(f"开始生成告警记录: {start_date} ~ {end_date}")
    
    alert_types = [
        ('overspeed', 'warning', '超速告警'),
        ('clone_plate', 'danger', '套牌嫌疑'),
        ('congestion', 'warning', '拥堵预警'),
        ('equipment', 'info', '设备告警'),
        ('violation', 'warning', '违章告警')
    ]
    
    current_date = start_date
    total_records = 0
    
    while current_date <= end_date:
        day_alerts = random.randint(int(alerts_per_day * 0.5), int(alerts_per_day * 1.5))
        
        for _ in range(day_alerts):
            hour = random.randint(0, 23)
            create_time = current_date.replace(hour=hour, minute=random.randint(0,59), second=random.randint(0,59))
            
            alert_type, level, title_prefix = random.choice(alert_types)
            checkpoint_id = random.randint(1, 19)
            plate = generate_plate_number() if alert_type in ['overspeed', 'clone_plate', 'violation'] else None
            
            title = f"{title_prefix} - {CHECKPOINTS[checkpoint_id]['name']}"
            content = f"在{CHECKPOINTS[checkpoint_id]['name']}检测到{title_prefix.replace('告警','').replace('预警','')}"
            if plate:
                content += f"，车牌号：{plate}"
            
            # 80%已处理
            status = 1 if random.random() < 0.8 else 0
            handle_time = create_time + timedelta(minutes=random.randint(5, 60)) if status == 1 else None
            
            cursor.execute('''
                INSERT INTO alert 
                (alert_type, level, title, content, checkpoint_id, plate_number, status, handle_time, create_time)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
            ''', (alert_type, level, title, content, checkpoint_id, plate, status, handle_time, create_time))
        
        conn.commit()
        total_records += day_alerts
        current_date += timedelta(days=1)
    
    print(f"告警记录生成完成，共 {total_records} 条")
    return total_records

def generate_checkpoint_flow(conn, start_date, end_date):
    """生成卡口流量统计数据"""
    cursor = conn.cursor()
    print(f"开始生成卡口流量统计: {start_date} ~ {end_date}")
    
    current_date = start_date
    total_records = 0
    
    while current_date <= end_date:
        for checkpoint_id in range(1, 20):
            for hour in range(24):
                base_flow = random.randint(100, 300)
                factor = get_hourly_factor(hour)
                total_count = int(base_flow * factor)
                in_count = int(total_count * random.uniform(0.45, 0.55))
                out_count = total_count - in_count
                
                cursor.execute('''
                    INSERT INTO checkpoint_flow 
                    (checkpoint_id, stat_date, stat_hour, total_count, in_count, out_count)
                    VALUES (%s, %s, %s, %s, %s, %s)
                    ON DUPLICATE KEY UPDATE 
                    total_count = VALUES(total_count), in_count = VALUES(in_count), out_count = VALUES(out_count)
                ''', (checkpoint_id, current_date.date(), hour, total_count, in_count, out_count))
        
        conn.commit()
        total_records += 19 * 24
        current_date += timedelta(days=1)
    
    print(f"卡口流量统计生成完成，共 {total_records} 条")
    return total_records

def main():
    parser = argparse.ArgumentParser(description='生成ETC历史数据')
    parser.add_argument('--days', type=int, default=30, help='生成多少天的数据')
    parser.add_argument('--records-per-day', type=int, default=5000, help='每天通行记录数')
    parser.add_argument('--clear', action='store_true', help='清除现有数据')
    args = parser.parse_args()
    
    end_date = datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)
    start_date = end_date - timedelta(days=args.days)
    
    print(f"="*50)
    print(f"ETC历史数据生成器")
    print(f"时间范围: {start_date.strftime('%Y-%m-%d')} ~ {end_date.strftime('%Y-%m-%d')}")
    print(f"="*50)
    
    conn = mysql.connector.connect(**DB_CONFIG)
    
    try:
        if args.clear:
            print("清除现有数据...")
            cursor = conn.cursor()
            cursor.execute("DELETE FROM pass_record WHERE pass_time < NOW()")
            cursor.execute("DELETE FROM violation")
            cursor.execute("DELETE FROM clone_plate_detection")
            cursor.execute("DELETE FROM alert")
            cursor.execute("DELETE FROM checkpoint_flow")
            conn.commit()
            print("数据清除完成")
        
        # 生成各类数据
        generate_pass_records(conn, start_date, end_date, args.records_per_day)
        generate_violations(conn, start_date, end_date)
        generate_clone_plate_detections(conn, start_date, end_date)
        generate_alerts(conn, start_date, end_date)
        generate_checkpoint_flow(conn, start_date, end_date)
        
        print(f"\n{'='*50}")
        print("所有历史数据生成完成！")
        print(f"{'='*50}")
        
    finally:
        conn.close()

if __name__ == '__main__':
    main()
