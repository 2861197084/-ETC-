#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
交通流量数据扩充脚本

功能：
1. 补充缺失日期的数据（基于相邻日期的特征）
2. 扩充数据到每秒50条（每天 50 * 60 * 60 * 24 = 4,320,000 条）

扩充策略：
- 基于现有数据的统计特征（车牌分布、卡口分布、时间分布等）
- 使用加权随机采样保持数据分布特征
- 时间戳均匀分布，每秒生成50条记录
"""

import os
import sys
import random
import string
import argparse
import pandas as pd
import numpy as np
from pathlib import Path
from datetime import datetime, timedelta
from collections import Counter
import warnings

warnings.filterwarnings('ignore')

# 配置
BASE_DIR = Path(__file__).parent
INPUT_DIR = BASE_DIR / "output"
OUTPUT_DIR = BASE_DIR / "expanded"

# 默认配置（可通过命令行参数覆盖）
DEFAULT_RECORDS_PER_SECOND = 50  # 每秒50条
# 每天记录数将在运行时计算

# 缺失的日期
MISSING_DATES_DEC = ['2023-12-18', '2023-12-19', '2023-12-20', '2023-12-21', '2023-12-31']
MISSING_DATES_JAN = ['2024-01-13']

# 设置随机种子保证可重复性
random.seed(42)
np.random.seed(42)


def calculate_records_per_day(records_per_second):
    """计算每天的记录数"""
    return records_per_second * 60 * 60 * 24


class DataFeatureExtractor:
    """从现有数据中提取特征分布"""
    
    def __init__(self):
        self.xzqhmc_weights = {}  # 行政区划权重
        self.kkmc_by_xzqhmc = {}  # 每个区划下的卡口
        self.fxlx_weights = {}   # 方向权重
        self.hpzl_weights = {}   # 号牌种类权重
        self.hp_prefixes = []    # 车牌前缀
        self.clppxh_list = []    # 车辆品牌型号
        self.hourly_weights = {} # 每小时流量权重
        self.gcxh_prefix = "G320300"  # 序号前缀
        self.gcxh_counter = 200000000000  # 序号计数器
        
    def extract_from_files(self, file_list):
        """从文件列表中提取特征"""
        all_data = []
        
        for f in file_list:
            if f.exists():
                df = pd.read_csv(f, encoding='utf-8-sig', dtype=str)
                all_data.append(df)
        
        if not all_data:
            raise ValueError("没有找到有效的数据文件")
        
        combined = pd.concat(all_data, ignore_index=True)
        print(f"分析 {len(all_data)} 个文件，共 {len(combined)} 条记录")
        
        # 提取行政区划分布
        xzqhmc_counts = combined['XZQHMC'].value_counts()
        total = xzqhmc_counts.sum()
        self.xzqhmc_weights = {k: v/total for k, v in xzqhmc_counts.items()}
        
        # 提取每个区划下的卡口
        for xzqhmc in self.xzqhmc_weights.keys():
            subset = combined[combined['XZQHMC'] == xzqhmc]
            self.kkmc_by_xzqhmc[xzqhmc] = subset['KKMC'].unique().tolist()
        
        # 提取方向分布
        fxlx_counts = combined['FXLX'].value_counts()
        total = fxlx_counts.sum()
        self.fxlx_weights = {k: v/total for k, v in fxlx_counts.items()}
        
        # 提取号牌种类分布
        hpzl_counts = combined['HPZL'].value_counts()
        total = hpzl_counts.sum()
        self.hpzl_weights = {k: v/total for k, v in hpzl_counts.items()}
        
        # 提取车牌前缀（省份+城市）
        hp_prefixes = combined['HP'].str[:2].dropna().unique()
        self.hp_prefixes = [p for p in hp_prefixes if len(p) == 2 and not p.startswith('*')]
        
        # 提取车辆品牌型号
        clppxh = combined['CLPPXH'].unique()
        self.clppxh_list = [c for c in clppxh if c and c != 'UNKNOWN']
        if not self.clppxh_list:
            self.clppxh_list = ['UNKNOWN']
        
        # 提取时间分布（按小时）
        combined['hour'] = pd.to_datetime(combined['GCSJ'], errors='coerce').dt.hour
        hourly_counts = combined.groupby('hour').size()
        total = hourly_counts.sum()
        self.hourly_weights = {h: c/total for h, c in hourly_counts.items()}
        
        # 填充缺失的小时
        for h in range(24):
            if h not in self.hourly_weights:
                self.hourly_weights[h] = 1/24
        
        print(f"提取完成: {len(self.xzqhmc_weights)} 个区划, {len(self.hp_prefixes)} 种车牌前缀")
        
    def get_next_gcxh(self):
        """生成下一个序号"""
        self.gcxh_counter += 1
        return f"{self.gcxh_prefix}{self.gcxh_counter}"
    
    def generate_plate(self):
        """生成虚拟车牌"""
        if self.hp_prefixes:
            prefix = random.choice(self.hp_prefixes)
        else:
            prefix = random.choice(['苏C', '鲁Q', '皖L', '豫N'])
        
        # 生成车牌中间部分（字母+数字混合）
        chars = string.ascii_uppercase + string.digits
        middle = ''.join(random.choices(chars, k=2))
        
        return f"{prefix}{middle}***"
    
    def weighted_choice(self, weights_dict):
        """加权随机选择"""
        items = list(weights_dict.keys())
        weights = list(weights_dict.values())
        return random.choices(items, weights=weights, k=1)[0]


class DataExpander:
    """数据扩充器"""
    
    def __init__(self, feature_extractor):
        self.fe = feature_extractor
        
    def generate_records_for_day(self, date_str, num_records):
        """为指定日期生成记录"""
        print(f"生成 {date_str} 的数据: {num_records:,} 条...")
        
        records = []
        date = datetime.strptime(date_str, '%Y-%m-%d')
        
        # 计算每小时应该生成的记录数（基于时间分布）
        hourly_records = {}
        for hour in range(24):
            weight = self.fe.hourly_weights.get(hour, 1/24)
            hourly_records[hour] = int(num_records * weight)
        
        # 调整以确保总数正确
        diff = num_records - sum(hourly_records.values())
        for i in range(abs(diff)):
            hour = i % 24
            hourly_records[hour] += 1 if diff > 0 else -1
        
        # 为每个小时生成记录
        for hour in range(24):
            hour_count = hourly_records[hour]
            seconds_in_hour = 3600
            records_per_second = hour_count / seconds_in_hour
            
            # 在这个小时内均匀分布
            for sec in range(seconds_in_hour):
                # 这一秒生成的记录数
                n = int(records_per_second)
                if random.random() < (records_per_second - n):
                    n += 1
                
                for _ in range(n):
                    # 生成时间戳（精确到秒内随机毫秒）
                    timestamp = date + timedelta(hours=hour, seconds=sec)
                    timestamp_str = timestamp.strftime('%Y-%m-%d %H:%M:%S')
                    
                    # 选择行政区划
                    xzqhmc = self.fe.weighted_choice(self.fe.xzqhmc_weights)
                    
                    # 选择对应的卡口
                    kkmc_list = self.fe.kkmc_by_xzqhmc.get(xzqhmc, ['未知卡口'])
                    kkmc = random.choice(kkmc_list)
                    
                    # 其他字段
                    record = {
                        'GCXH': self.fe.get_next_gcxh(),
                        'XZQHMC': xzqhmc,
                        'KKMC': kkmc,
                        'FXLX': self.fe.weighted_choice(self.fe.fxlx_weights),
                        'GCSJ': timestamp_str,
                        'HPZL': self.fe.weighted_choice(self.fe.hpzl_weights),
                        'HP': self.fe.generate_plate(),
                        'CLPPXH': random.choice(self.fe.clppxh_list)
                    }
                    records.append(record)
        
        return pd.DataFrame(records)
    
    def expand_existing_data(self, input_file, date_str, target_records):
        """扩充已有数据到目标数量"""
        df = pd.read_csv(input_file, encoding='utf-8-sig', dtype=str)
        current_count = len(df)
        
        print(f"扩充 {date_str}: {current_count:,} -> {target_records:,} 条")
        
        if current_count >= target_records:
            # 如果已有数据足够，随机采样
            return df.sample(n=target_records, replace=False).reset_index(drop=True)
        
        # 需要补充的数量
        need_count = target_records - current_count
        
        # 从现有数据中学习特征
        local_fe = DataFeatureExtractor()
        local_fe.xzqhmc_weights = dict(df['XZQHMC'].value_counts(normalize=True))
        for xzqhmc in local_fe.xzqhmc_weights.keys():
            subset = df[df['XZQHMC'] == xzqhmc]
            local_fe.kkmc_by_xzqhmc[xzqhmc] = subset['KKMC'].unique().tolist()
        local_fe.fxlx_weights = dict(df['FXLX'].value_counts(normalize=True))
        local_fe.hpzl_weights = dict(df['HPZL'].value_counts(normalize=True))
        local_fe.hp_prefixes = self.fe.hp_prefixes
        local_fe.clppxh_list = df['CLPPXH'].unique().tolist()
        local_fe.hourly_weights = self.fe.hourly_weights
        local_fe.gcxh_counter = self.fe.gcxh_counter
        
        # 生成补充数据
        expander = DataExpander(local_fe)
        new_df = expander.generate_records_for_day(date_str, need_count)
        
        # 更新计数器
        self.fe.gcxh_counter = local_fe.gcxh_counter
        
        # 合并数据
        combined = pd.concat([df, new_df], ignore_index=True)
        
        # 按时间排序
        combined['GCSJ_dt'] = pd.to_datetime(combined['GCSJ'], errors='coerce')
        combined = combined.sort_values('GCSJ_dt').drop(columns=['GCSJ_dt']).reset_index(drop=True)
        
        return combined


def get_reference_files_for_date(date_str, input_dir):
    """获取用于参考的相邻日期文件"""
    date = datetime.strptime(date_str, '%Y-%m-%d')
    reference_files = []
    
    # 查找前后7天的文件作为参考
    for delta in range(-7, 8):
        ref_date = date + timedelta(days=delta)
        ref_file = input_dir / f"{ref_date.strftime('%Y-%m-%d')}.csv"
        if ref_file.exists():
            reference_files.append(ref_file)
    
    return reference_files


def main():
    """主函数"""
    # 解析命令行参数
    parser = argparse.ArgumentParser(description='交通流量数据扩充脚本')
    parser.add_argument('--rps', type=int, default=DEFAULT_RECORDS_PER_SECOND,
                        help=f'每秒生成的记录数 (默认: {DEFAULT_RECORDS_PER_SECOND})')
    parser.add_argument('--test', action='store_true',
                        help='测试模式：只处理前3天')
    parser.add_argument('--dates', type=str, nargs='+',
                        help='指定要处理的日期，格式: YYYY-MM-DD')
    args = parser.parse_args()
    
    records_per_second = args.rps
    records_per_day = calculate_records_per_day(records_per_second)
    
    print("="*60)
    print("交通流量数据扩充脚本")
    print(f"目标: 每秒 {records_per_second} 条，每天 {records_per_day:,} 条")
    print("="*60)
    
    # 创建输出目录
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    
    # 获取所有现有数据文件
    existing_files = sorted(INPUT_DIR.glob("*.csv"))
    print(f"\n发现 {len(existing_files)} 个已清洗的数据文件")
    
    # 提取全局特征
    print("\n提取数据特征...")
    feature_extractor = DataFeatureExtractor()
    feature_extractor.extract_from_files(existing_files)
    
    # 创建扩充器
    expander = DataExpander(feature_extractor)
    
    # 确定要处理的日期
    if args.dates:
        all_dates = args.dates
    else:
        all_dates = []
        # December 2023: 1-31
        for day in range(1, 32):
            all_dates.append(f"2023-12-{day:02d}")
        # January 2024: 1-31
        for day in range(1, 32):
            all_dates.append(f"2024-01-{day:02d}")
    
    if args.test:
        all_dates = all_dates[:3]
        print(f"\n[测试模式] 只处理 {len(all_dates)} 天")
    
    print(f"\n处理 {len(all_dates)} 天的数据...")
    print("-"*60)
    
    for i, date_str in enumerate(all_dates):
        input_file = INPUT_DIR / f"{date_str}.csv"
        output_file = OUTPUT_DIR / f"{date_str}.csv"
        
        print(f"[{i+1}/{len(all_dates)}] ", end="")
        
        if input_file.exists():
            # 扩充已有数据
            df = expander.expand_existing_data(input_file, date_str, records_per_day)
        else:
            # 生成缺失日期的数据
            print(f"[缺失] ", end="")
            # 使用相邻日期的特征
            ref_files = get_reference_files_for_date(date_str, INPUT_DIR)
            if ref_files:
                local_fe = DataFeatureExtractor()
                local_fe.extract_from_files(ref_files)
                local_fe.gcxh_counter = feature_extractor.gcxh_counter
                local_expander = DataExpander(local_fe)
                df = local_expander.generate_records_for_day(date_str, records_per_day)
                feature_extractor.gcxh_counter = local_fe.gcxh_counter
            else:
                df = expander.generate_records_for_day(date_str, records_per_day)
        
        # 保存
        df.to_csv(output_file, index=False, encoding='utf-8-sig')
        print(f"  -> 保存: {output_file.name} ({len(df):,} 条)")
    
    # 打印摘要
    print("\n" + "="*60)
    print("扩充完成!")
    print("="*60)
    
    output_files = sorted(OUTPUT_DIR.glob("*.csv"))
    total_records = 0
    for f in output_files:
        with open(f, 'r', encoding='utf-8-sig') as file:
            lines = sum(1 for _ in file) - 1  # 减去表头
        total_records += lines
    
    print(f"输出目录: {OUTPUT_DIR}")
    print(f"文件数量: {len(output_files)}")
    print(f"总记录数: {total_records:,}")
    print(f"每天记录: {records_per_day:,}")
    print(f"每秒记录: {records_per_second}")


if __name__ == "__main__":
    main()
