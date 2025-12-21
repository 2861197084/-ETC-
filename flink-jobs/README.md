# ETC Flink Jobs

Flink 流处理作业，包含：

1. **套牌检测** (`ClonePlateDetectorJob`) - 检测同一车牌短时间内出现在不同卡口
2. **HBase 存储** (`HBaseStorageJob`) - 将过车记录写入 HBase (`etc:pass_record`)
3. **MySQL 存储** (`MySqlStorageJob`) - 将过车记录写入 MySQL 热表（`pass_record_0/1`）

## 编译

```bash
cd flink-jobs
mvn clean package -DskipTests
```

生成 JAR: `target/etc-flink-jobs-1.0.0.jar`

## 提交作业

### 套牌检测

```bash
docker exec flink-jobmanager flink run \
    -c com.etc.flink.ClonePlateDetectorJob \
    /opt/flink/jobs/etc-flink-jobs-1.0.0.jar
```

### HBase 存储

```bash
docker exec flink-jobmanager flink run \
    -c com.etc.flink.HBaseStorageJob \
    /opt/flink/jobs/etc-flink-jobs-1.0.0.jar
```

### MySQL 存储

```bash
docker exec flink-jobmanager flink run \
    -c com.etc.flink.MySqlStorageJob \
    /opt/flink/jobs/etc-flink-jobs-1.0.0.jar
```

## 配置

环境变量：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| KAFKA_BOOTSTRAP_SERVERS | kafka:9092 | Kafka 地址 |
| KAFKA_TOPIC | etc-pass-records | 过车记录 Topic |
| MYSQL_URL | jdbc:mysql://mysql0:3306/etc | MySQL 连接 |
| HBASE_ZOOKEEPER_QUORUM | zookeeper:2181 | HBase ZK 地址 |
