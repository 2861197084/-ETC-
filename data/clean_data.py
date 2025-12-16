#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
交通流量数据清洗脚本
处理 2023年12月 和 2024年1月 的原始数据
输出按天保存到 data/output/ 文件夹
"""

import os
import pandas as pd
from pathlib import Path
from datetime import datetime
import warnings

warnings.filterwarnings('ignore')

# 配置路径
BASE_DIR = Path(__file__).parent
DEC_2023_DIR = BASE_DIR / "202312（交通流量）"
JAN_2024_DIR = BASE_DIR / "202401(交通流量）"
OUTPUT_DIR = BASE_DIR / "output"

# 统一的列名
STANDARD_COLUMNS = ['GCXH', 'XZQHMC', 'KKMC', 'FXLX', 'GCSJ', 'HPZL', 'HP', 'CLPPXH']

# 缺失值标准化映射
MISSING_VALUE_MAPPING = {
    '-': 'UNKNOWN',
    '': 'UNKNOWN',
    None: 'UNKNOWN'
}


def create_output_dir():
    """创建输出目录"""
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    print(f"输出目录: {OUTPUT_DIR}")


def try_read_csv(filepath, encodings=['gb18030', 'gbk', 'utf-8', 'latin1']):
    """尝试使用不同编码读取 CSV 文件"""
    for encoding in encodings:
        try:
            df = pd.read_csv(filepath, encoding=encoding, dtype=str)
            return df
        except (UnicodeDecodeError, UnicodeError):
            continue
        except Exception as e:
            print(f"读取 {filepath} 时出错 (encoding={encoding}): {e}")
            continue
    print(f"无法读取文件: {filepath}")
    return None


def standardize_columns(df):
    """标准化列名"""
    # 首先处理截断的列名（Excel 可能截断长列名）
    new_columns = {}
    for col in df.columns:
        # 处理 GCXH 的各种变体
        if "'G'||GCXH" in col or col.startswith("'G'||"):
            new_columns[col] = "GCXH"
        # 处理 GCSJ 的各种变体 (TO_CHAR(GCSJ,'YYYY-MM-DD...)
        elif "GCSJ" in col or col.startswith("TO_CHAR(GCSJ"):
            new_columns[col] = "GCSJ"
        # 处理 HP 的各种变体
        elif "HPHM" in col or "SUBSTR(HPHM" in col:
            new_columns[col] = "HP"
    
    df = df.rename(columns=new_columns)
    
    # 处理可能的列名变体
    column_mapping = {
        "SUBSTR(HPHM,1,4)||'***'": "HP",
        "SUBSTR(HPHM,1,4)||'***": "HP",  # 可能的变体
        "SUBSTR(HPHM,0,4)||'***'": "HP",
        "车牌": "HP",
        "号牌": "HP",
    }
    
    df = df.rename(columns=column_mapping)
    
    # 确保所有标准列都存在
    missing_cols = []
    for col in STANDARD_COLUMNS:
        if col not in df.columns:
            missing_cols.append(col)
    
    if missing_cols:
        print(f"  警告: 缺少列 {missing_cols}")
    
    # 只保留标准列（如果存在的话）
    existing_cols = [col for col in STANDARD_COLUMNS if col in df.columns]
    df = df[existing_cols]
    
    return df


def standardize_missing_values(df):
    """标准化缺失值"""
    # 对于 HPZL 和 CLPPXH 列，将缺失值替换为 UNKNOWN
    for col in ['HPZL', 'CLPPXH']:
        if col in df.columns:
            df[col] = df[col].replace(MISSING_VALUE_MAPPING)
            df[col] = df[col].fillna('UNKNOWN')
            # 将空字符串也替换
            df[col] = df[col].apply(lambda x: 'UNKNOWN' if x == '-' or x == '' or pd.isna(x) else x)
    
    return df


def parse_datetime(date_str):
    """解析日期时间字符串"""
    if pd.isna(date_str):
        return None
    
    date_str = str(date_str).strip()
    
    # 尝试多种日期格式
    formats = [
        '%Y/%m/%d %H:%M:%S',
        '%Y/%m/%d %H:%M',
        '%Y/%m/%d',
        '%Y-%m-%d %H:%M:%S',
        '%Y-%m-%d %H:%M',
        '%Y-%m-%d',
    ]
    
    for fmt in formats:
        try:
            return datetime.strptime(date_str, fmt)
        except ValueError:
            continue
    
    return None


def extract_date(date_str):
    """提取日期部分 (YYYY-MM-DD)"""
    dt = parse_datetime(date_str)
    if dt:
        return dt.strftime('%Y-%m-%d')
    return None


def process_december_2023():
    """处理 2023年12月的数据"""
    print("\n" + "="*50)
    print("处理 2023年12月 数据")
    print("="*50)
    
    all_data = []
    
    # 文件名到日期的映射规则
    # to_yqy_1201.csv -> 12月1日
    # to_yqy_1209.csv -> 12月9日
    # to_yqy_12010.csv -> 12月10日
    # to_yqy_12025.csv -> 12月25日
    # 12-01.csv -> 12月1日 (可能是重复数据)
    # to_yqy_12.csv -> 需要检查内容确定日期
    
    csv_files = list(DEC_2023_DIR.glob("*.csv"))
    
    for csv_file in csv_files:
        print(f"\n处理文件: {csv_file.name}")
        
        df = try_read_csv(csv_file)
        if df is None:
            continue
        
        print(f"  原始行数: {len(df)}")
        
        # 标准化列名
        df = standardize_columns(df)
        
        # 标准化缺失值
        df = standardize_missing_values(df)
        
        # 添加来源文件信息
        df['SOURCE_FILE'] = csv_file.name
        
        all_data.append(df)
    
    if not all_data:
        print("没有找到有效的 December 2023 数据")
        return pd.DataFrame()
    
    # 合并所有数据
    combined_df = pd.concat(all_data, ignore_index=True)
    print(f"\n合并后总行数: {len(combined_df)}")
    
    return combined_df


def process_january_2024():
    """处理 2024年1月的数据"""
    print("\n" + "="*50)
    print("处理 2024年1月 数据")
    print("="*50)
    
    all_data = []
    
    xlsx_files = list(JAN_2024_DIR.glob("*.xlsx"))
    
    for xlsx_file in xlsx_files:
        print(f"\n处理文件: {xlsx_file.name}")
        
        try:
            df = pd.read_excel(xlsx_file, dtype=str)
        except Exception as e:
            print(f"  读取失败: {e}")
            continue
        
        print(f"  原始行数: {len(df)}")
        print(f"  列名: {list(df.columns)}")
        
        # 标准化列名
        df = standardize_columns(df)
        
        # 标准化缺失值
        df = standardize_missing_values(df)
        
        # 添加来源文件信息
        df['SOURCE_FILE'] = xlsx_file.name
        
        all_data.append(df)
    
    if not all_data:
        print("没有找到有效的 January 2024 数据")
        return pd.DataFrame()
    
    # 合并所有数据
    combined_df = pd.concat(all_data, ignore_index=True)
    print(f"\n合并后总行数: {len(combined_df)}")
    
    return combined_df


def save_by_date(df, year_month_prefix):
    """按日期保存数据"""
    if df.empty:
        return
    
    # 提取日期
    df['DATA_DATE'] = df['GCSJ'].apply(extract_date)
    
    # 检查无法解析的日期
    invalid_dates = df[df['DATA_DATE'].isna()]
    if len(invalid_dates) > 0:
        print(f"\n警告: {len(invalid_dates)} 行数据日期无法解析")
        # 保存无法解析的数据以供检查
        invalid_file = OUTPUT_DIR / f"invalid_dates_{year_month_prefix}.csv"
        invalid_dates.to_csv(invalid_file, index=False, encoding='utf-8-sig')
        print(f"  已保存到: {invalid_file}")
    
    # 移除无法解析日期的行
    df = df[df['DATA_DATE'].notna()]
    
    # 按日期分组保存
    dates = df['DATA_DATE'].unique()
    dates = sorted([d for d in dates if d is not None])
    
    print(f"\n发现 {len(dates)} 个不同的日期:")
    
    for date in dates:
        date_df = df[df['DATA_DATE'] == date].copy()
        
        # 移除辅助列
        output_df = date_df[STANDARD_COLUMNS].copy()
        
        # 生成输出文件名
        output_file = OUTPUT_DIR / f"{date}.csv"
        
        # 如果文件已存在，合并数据
        if output_file.exists():
            existing_df = pd.read_csv(output_file, dtype=str, encoding='utf-8-sig')
            output_df = pd.concat([existing_df, output_df], ignore_index=True)
            print(f"  {date}: 追加 {len(date_df)} 行 (总计 {len(output_df)} 行)")
        else:
            print(f"  {date}: {len(date_df)} 行")
        
        # 保存到文件
        output_df.to_csv(output_file, index=False, encoding='utf-8-sig')


def print_summary():
    """打印处理结果摘要"""
    print("\n" + "="*50)
    print("处理完成 - 输出文件摘要")
    print("="*50)
    
    output_files = sorted(OUTPUT_DIR.glob("*.csv"))
    
    total_rows = 0
    for f in output_files:
        if f.name.startswith("invalid_"):
            continue
        try:
            df = pd.read_csv(f, encoding='utf-8-sig')
            rows = len(df)
            total_rows += rows
            print(f"  {f.name}: {rows:,} 行")
        except Exception as e:
            print(f"  {f.name}: 读取错误 - {e}")
    
    print(f"\n总计: {total_rows:,} 行")
    print(f"输出目录: {OUTPUT_DIR}")


def main():
    """主函数"""
    print("="*50)
    print("交通流量数据清洗脚本")
    print("="*50)
    
    # 创建输出目录
    create_output_dir()
    
    # 处理 December 2023 数据
    dec_df = process_december_2023()
    if not dec_df.empty:
        save_by_date(dec_df, "2023-12")
    
    # 处理 January 2024 数据
    jan_df = process_january_2024()
    if not jan_df.empty:
        save_by_date(jan_df, "2024-01")
    
    # 打印摘要
    print_summary()


if __name__ == "__main__":
    main()
