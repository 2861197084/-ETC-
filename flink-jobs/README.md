# ETC Flink Jobs

Flink 流处理作业，包含：

1. **套牌检测** (`ClonePlateDetectorJob`) - 检测同一车牌短时间内出现在不同卡口
2. **HBase 存储** (`HBaseStorageJob`) - 将过车记录写入 HBase (`etc:pass_record`)
3. **MySQL 存储** (`MySqlStorageJob`) - Kafka → ShardingSphere Proxy（逻辑表 `pass_record`）→ MySQL 分片

## 编译

```bash
# 在仓库根目录执行
mvn -f flink-jobs/pom.xml -DskipTests clean package
```

生成 JAR：`flink-jobs/target/etc-flink-jobs-1.0.0.jar`（shade 后会保留 `original-*.jar` 作为备份）

## 提交作业

> `docker-compose.yml` 会把 `./flink-jobs/target` 挂载到 `flink-jobmanager` 容器的 `/opt/flink/jobs`。
> 提交前先确认容器内能看到 JAR：`docker compose exec flink-jobmanager ls -la /opt/flink/jobs`。

### 套牌检测

```bash
docker compose exec flink-jobmanager flink run -d \
  -c com.etc.flink.ClonePlateDetectorJob \
  /opt/flink/jobs/etc-flink-jobs-1.0.0.jar
```

### HBase 存储

```bash
docker compose exec flink-jobmanager flink run -d \
  -c com.etc.flink.HBaseStorageJob \
  /opt/flink/jobs/etc-flink-jobs-1.0.0.jar
```

### MySQL 存储

```bash
docker compose exec flink-jobmanager flink run -d \
  -c com.etc.flink.MySqlStorageJob \
  /opt/flink/jobs/etc-flink-jobs-1.0.0.jar
```

## 配置

环境变量：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| KAFKA_BOOTSTRAP_SERVERS | kafka:9092 | Kafka 地址 |
| KAFKA_TOPIC | etc-pass-records | 过车记录 Topic |
| MYSQL_URL | jdbc:mysql://shardingsphere:3307/etc | ShardingSphere Proxy 连接 |
| HBASE_ZOOKEEPER_QUORUM | zookeeper:2181 | HBase ZK 地址 |

## 常见问题

- 若 `/opt/flink/jobs` 为空：通常是先启动了 Flink 容器、后编译了 `flink-jobs/`，可执行 `docker compose restart flink-jobmanager flink-taskmanager` 后再检查；若仍为空，再检查 Docker Desktop 对项目目录的共享/权限设置。
