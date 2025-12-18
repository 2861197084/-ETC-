# Docker 环境（后端/大数据组件）

本目录提供 ETC 大数据平台后端所需的基础组件环境：Kafka（KRaft）、Flink、HBase（可选 profile）、MySQL 分片 + ShardingSphere-Proxy、Redis（可选缓存）。

## 版本选型（较新实践）

- Kafka `3.8.1`（KRaft 单节点开发模式）
- Flink `1.20.0` + Kafka SQL Connector `3.4.0-1.20`
- MySQL `8.4`（LTS）
- ShardingSphere-Proxy `5.5.0`
- HBase `2.6.1`（通过 Zookeeper `3.9.3`）
- Spark `3.5.4`（可选 profile）

> 说明：本项目在 `docker-compose.yml` 中尽量使用 `public.ecr.aws/docker/library/*` 镜像，避免 Docker Hub 拉取不稳定导致的环境搭建失败。

## 启动

如需自定义密码/广播地址，可在项目根目录创建 `.env`（参考 `infra/env.example`）。

### 基础（Kafka + Flink + MySQL 分片 + ShardingSphere + Redis）

```bash
docker compose up -d --build
```

### 启用 HBase

```bash
docker compose --profile hbase up -d --build
```

### 启用 Spark（离线训练/预测）

```bash
docker compose --profile spark up -d --build
```

## 常用端口

- Kafka（host 访问）: `localhost:19092`（容器内访问：`kafka:9092`）
- Flink Web UI: `http://localhost:8081`
- ShardingSphere-Proxy(MySQL): `localhost:3307`
- MySQL Shard0: `localhost:33060`（直连调试）
- MySQL Shard1: `localhost:33061`（直连调试）
- Redis: `localhost:6379`
- HBase Master UI（profile hbase）: `http://localhost:16010`
- Spark Master UI（profile spark）: `http://localhost:8080`

## 冒烟验证（可选）

如 `docker compose up` 提示 `kafka` 退出，优先执行：

```bash
docker compose logs --tail=200 kafka
```

```bash
# Kafka（列出 topic）
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --list
```

```bash
# ShardingSphere-Proxy（MySQL 协议）
mysql -h127.0.0.1 -P3307 -uroot -proot -e "SHOW DATABASES;"
mysql -h127.0.0.1 -P3307 -uroot -proot -e "SHOW TABLES FROM etc;"
```

```bash
# HBase（profile hbase）
docker compose --profile hbase exec hbase /opt/hbase/bin/hbase shell -n -e "list_namespace"
```

## MySQL 分片说明（ShardingSphere）

- 逻辑表：`etc.pass_record`
- 分片键：`plate_hash`（应用侧计算后写入）
- 数据源：`ds_0=mysql0`、`ds_1=mysql1`
- 实体表：`pass_record_0`、`pass_record_1`（每个数据源各 2 张表）

ShardingSphere 配置：`infra/shardingsphere/conf/config-sharding.yaml`

## HBase RowKey 设计（建议）

细粒度明细适合落 HBase，推荐 RowKey 兼顾热点与时间范围查询：

```
salt(2) + yyyymmdd + checkpoint_id + reverse_ts + plate_prefix
```

表结构与列族：`etc:pass_record`，列族 `d`（明细字段）。


