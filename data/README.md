# ETC 数据生成脚本

## 依赖安装

```bash
pip install pymysql redis kafka-python
```

## 脚本说明

| 脚本 | 用途 |
|------|------|
| `history_generator.py` | 生成历史数据，写入 MySQL |
| `realtime_generator.py` | 实时生成数据，写入 MySQL + Redis + Kafka |

## 使用方法

### 1. 生成历史数据

```bash
# 生成30天历史数据，每天5000条（默认）
python data/history_generator.py

# 自定义参数
python data/history_generator.py --days 7 --records-per-day 10000
```

### 2. 生成实时数据

```bash
# 每秒10条（默认）
python data/realtime_generator.py

# 每秒50条
python data/realtime_generator.py --rate 50
```

## 数据表结构

写入 `pass_record_0` 和 `pass_record_1` 分片表：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| gcxh | VARCHAR(64) | 过车序号 |
| xzqhmc | VARCHAR(128) | 行政区划 |
| kkmc | VARCHAR(128) | 卡口名称 |
| fxlx | VARCHAR(32) | 方向（进城/出城）|
| gcsj | DATETIME | 过车时间 |
| hpzl | VARCHAR(32) | 号牌种类 |
| hp | VARCHAR(32) | 车牌号 |
| clppxh | VARCHAR(128) | 车辆类型 |
| plate_hash | INT | 分片键 (0或1) |
| checkpoint_id | VARCHAR(64) | 卡口ID |


