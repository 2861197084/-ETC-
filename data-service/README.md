# ETC 数据服务

Python 数据处理服务，负责：
- CSV 数据读取
- Kafka 消息生产
- 历史数据导入（HBase）
- 实时数据模拟
- 套牌检测测试数据注入（Kafka）

## 快速开始

```bash
# 安装依赖
pip install -r requirements.txt

# 导入历史数据 (2023-12 → HBase，同时写入 Redis 历史统计)
python -m scripts.import_to_hbase

# 启动实时模拟 (2024-01 → Kafka，基于后端虚拟时间窗口)
python -m scripts.realtime_simulator
```

## Docker 运行

```bash
# 一次性执行脚本（推荐，执行完自动删除容器）
docker compose run --rm data-service python -m scripts.import_to_hbase
docker compose run --rm data-service python -m scripts.realtime_simulator
docker compose run --rm data-service python -m scripts.inject_clone_plate --help

# 或启动常驻容器（仅用于查看日志/进入容器调试）
docker compose up -d data-service
```

## 依赖与注意事项

- CSV 编码：数据文件为 `UTF-8-SIG`（脚本已按此读取）。
- 实时链路：若要看到 MySQL/HBase 的实时落库效果，需要 Kafka topic 已创建，并在 Flink 中提交 `MySqlStorageJob`、`HBaseStorageJob`。
- 套牌检测：需要在 Flink 中提交 `ClonePlateDetectorJob`；然后用 `scripts.inject_clone_plate` 向 `etc-pass-records` 注入两条短时间冲突数据，触发写入 MySQL 表 `clone_plate_detection`（推荐 `--auto-time` 基于虚拟时间自动选取 `t1/t2`）。
- 历史导入性能：可通过 `HBASE_IMPORT_BATCH_SIZE` 调整写入批大小（默认 `50000`，不稳定可降到 `20000`/`10000`）。
