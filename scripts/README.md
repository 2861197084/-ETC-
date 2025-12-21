# 数据管道控制脚本

## 📋 概述

这些脚本用于管理ETC系统的数据读写管道，包括：
- 实时数据生成器
- Flink数据处理作业
- Kafka到MySQL/HBase的数据流

## 🛑 停止数据读写

```bash
./scripts/stop-data-pipeline.sh
```

**停止内容：**
- ❌ 停止实时数据模拟器
- ❌ 取消所有运行中的Flink作业
- ❌ 停止 Kafka → MySQL 数据流
- ❌ 停止 Kafka → HBase 数据流

**不影响：**
- ✅ Kafka中的数据（保留）
- ✅ MySQL中已有的数据
- ✅ HBase中已有的数据
- ✅ Docker容器继续运行

## 🚀 启动数据读写

```bash
./scripts/start-data-pipeline.sh
```

**启动内容：**
- ✅ MySQL热数据存储作业（自动）
- ✅ HBase归档存储作业（可选，会询问）
- ✅ 实时数据模拟器

## 📊 状态查询

### 查看Flink作业
```bash
docker compose exec flink-jobmanager flink list
```

### 查看模拟器日志
```bash
docker compose exec data-service cat /tmp/simulator.log
```

### 查看MySQL数据量
```bash
docker compose exec mysql0 sh -c 'mysql -uroot -proot etc -e "SELECT COUNT(*) FROM pass_record_0"'
docker compose exec mysql1 sh -c 'mysql -uroot -proot etc -e "SELECT COUNT(*) FROM pass_record_1"'
```

### 查看Kafka消息量
```bash
docker compose exec kafka /opt/kafka/bin/kafka-run-class.sh kafka.admin.ConsumerGroupCommand \
  --bootstrap-server localhost:9092 --describe --group flink-mysql-storage
```

## 🔧 手动控制

### 停止特定Flink作业
```bash
# 1. 获取作业ID
docker compose exec flink-jobmanager flink list

# 2. 取消作业
docker compose exec flink-jobmanager flink cancel <JOB_ID>
```

### 启动特定Flink作业

**MySQL存储作业：**
```bash
docker compose exec flink-jobmanager flink run -d \
  -c com.etc.flink.MySqlStorageJob \
  /opt/flink/jobs/etc-flink-jobs-1.0.0.jar
```

**HBase存储作业：**
```bash
docker compose exec flink-jobmanager flink run -d \
  -c com.etc.flink.HBaseStorageJob \
  /opt/flink/jobs/etc-flink-jobs-1.0.0.jar
```

**套牌检测作业：**
```bash
docker compose exec flink-jobmanager flink run -d \
  -c com.etc.flink.ClonePlateDetectorJob \
  /opt/flink/jobs/etc-flink-jobs-1.0.0.jar
```

## 💡 使用场景

### 场景1：暂停数据写入进行维护
```bash
# 1. 停止数据管道
./scripts/stop-data-pipeline.sh

# 2. 进行数据库维护
# ... 你的维护操作 ...

# 3. 恢复数据管道
./scripts/start-data-pipeline.sh
```

### 场景2：清空数据重新开始
```bash
# 1. 停止数据管道
./scripts/stop-data-pipeline.sh

# 2. 清空MySQL数据
docker compose exec mysql0 sh -c 'mysql -uroot -proot etc -e "TRUNCATE TABLE pass_record_0"'
docker compose exec mysql1 sh -c 'mysql -uroot -proot etc -e "TRUNCATE TABLE pass_record_1"'

# 3. 重启数据管道
./scripts/start-data-pipeline.sh
```

### 场景3：仅启动历史数据归档
```bash
# 只运行HBase存储作业，不生成新数据
docker compose exec flink-jobmanager flink run -d \
  -c com.etc.flink.HBaseStorageJob \
  /opt/flink/jobs/etc-flink-jobs-1.0.0.jar
```

## ⚠️ 注意事项

1. **Kafka数据保留**：停止Flink作业后，Kafka中的数据会保留，重启作业时会从上次位置继续消费
2. **数据一致性**：停止期间产生的Kafka消息会在重启后被处理
3. **资源占用**：即使停止数据管道，Docker容器仍会占用资源
4. **完全停止**：如需完全停止所有服务，使用 `docker compose down`

## 🔍 故障排查

### 问题1：Flink作业无法启动
```bash
# 查看Flink日志
docker compose logs flink-jobmanager --tail 100
docker compose logs flink-taskmanager --tail 100
```

### 问题2：数据不写入MySQL
```bash
# 检查Flink作业状态
docker compose exec flink-jobmanager flink list

# 检查TaskManager日志
docker compose logs flink-taskmanager | grep -i error
```

### 问题3：实时模拟器停止失败
```bash
# 重启data-service容器
docker compose restart data-service
```

## 📝 更新日志

- 2025-12-22: 初始版本，提供基础的启停控制功能
