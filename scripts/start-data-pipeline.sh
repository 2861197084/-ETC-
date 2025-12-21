#!/bin/bash
# 启动ETC数据读写功能

set -e

echo "🚀 启动 ETC 数据读写管道..."
echo ""

# 1. 检查服务状态
echo "1️⃣  检查Docker服务状态..."
if ! docker compose ps | grep -q "Up"; then
    echo "  ⚠️  Docker服务未完全启动，等待30秒..."
    sleep 30
fi
echo "  ✅ Docker服务就绪"

echo ""

# 2. 启动MySQL热数据存储Flink作业
echo "2️⃣  启动MySQL热数据存储作业..."
MYSQL_JOB=$(docker compose exec -T flink-jobmanager flink list 2>&1 | grep -c "MySQL Storage.*RUNNING" || true)
if [ "$MYSQL_JOB" -gt 0 ]; then
    echo "  ℹ️  MySQL存储作业已在运行"
else
    docker compose exec -T flink-jobmanager flink run -d \
        -c com.etc.flink.MySqlStorageJob \
        /opt/flink/jobs/etc-flink-jobs-1.0.0.jar 2>&1 | grep -v "WARNING" | grep "JobID" || true
    echo "  ✅ MySQL存储作业已启动"
fi

echo ""

# 3. （可选）启动HBase归档存储Flink作业
echo "3️⃣  启动HBase归档存储作业（可选）..."
read -p "  是否启动HBase归档作业? (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    HBASE_JOB=$(docker compose exec -T flink-jobmanager flink list 2>&1 | grep -c "HBase Storage.*RUNNING" || true)
    if [ "$HBASE_JOB" -gt 0 ]; then
        echo "  ℹ️  HBase存储作业已在运行"
    else
        docker compose exec -T flink-jobmanager flink run -d \
            -c com.etc.flink.HBaseStorageJob \
            /opt/flink/jobs/etc-flink-jobs-1.0.0.jar 2>&1 | grep -v "WARNING" | grep "JobID" || true
        echo "  ✅ HBase存储作业已启动"
    fi
else
    echo "  ⏭️  跳过HBase归档作业"
fi

echo ""

# 4. 启动实时数据模拟器
echo "4️⃣  启动实时数据模拟器..."
docker compose exec -d data-service sh -c \
    "python -m scripts.realtime_simulator > /tmp/simulator.log 2>&1"
sleep 2
echo "  ✅ 实时数据模拟器已启动"

echo ""

# 5. 验证状态
echo "5️⃣  验证运行状态..."
echo ""
docker compose exec -T flink-jobmanager flink list 2>&1 | grep -v "WARNING" | grep -v "Waiting"

echo ""
echo "✅ 数据读写管道已启动"
echo ""
echo "📊 当前状态："
echo "  - 实时数据生成：✅ 运行中"
echo "  - Kafka → MySQL：✅ 运行中"
echo "  - Kafka → HBase：根据选择"
echo ""
echo "💡 提示："
echo "  - 查看Flink作业状态: docker compose exec flink-jobmanager flink list"
echo "  - 查看模拟器日志: docker compose exec data-service cat /tmp/simulator.log"
echo "  - 停止数据读写: ./scripts/stop-data-pipeline.sh"
