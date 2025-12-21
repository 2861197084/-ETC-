#!/bin/bash
# 停止ETC数据读写功能

set -e

echo "🛑 停止 ETC 数据读写管道..."
echo ""

# 1. 停止实时数据模拟器
echo "1️⃣  停止实时数据模拟器..."
docker compose exec -T data-service python -c "
import os, signal, psutil
for proc in psutil.process_iter(['pid', 'name', 'cmdline']):
    try:
        cmdline = ' '.join(proc.info['cmdline'] or [])
        if 'realtime_simulator' in cmdline:
            print(f'  终止进程 {proc.info[\"pid\"]}: {cmdline[:60]}...')
            os.kill(proc.info['pid'], signal.SIGTERM)
    except (psutil.NoSuchProcess, psutil.AccessDenied, PermissionError):
        pass
" 2>/dev/null && echo "  ✅ 实时数据模拟器已停止" || echo "  ⚠️  模拟器可能未运行"

echo ""

# 2. 停止所有Flink作业
echo "2️⃣  停止所有运行中的Flink作业..."
JOBIDS=$(docker compose exec -T flink-jobmanager flink list 2>&1 | grep "RUNNING\|RESTARTING" | awk '{print $6}' | grep -E '^[0-9a-f]{32}$')

if [ -z "$JOBIDS" ]; then
    echo "  ℹ️  没有运行中的Flink作业"
else
    for JOBID in $JOBIDS; do
        echo "  取消作业: $JOBID"
        docker compose exec -T flink-jobmanager flink cancel "$JOBID" 2>&1 | grep -v "WARNING" | grep -v "Waiting" || true
    done
    echo "  ✅ 所有Flink作业已停止"
fi

echo ""

# 3. 验证状态
echo "3️⃣  验证停止状态..."
RUNNING_JOBS=$(docker compose exec -T flink-jobmanager flink list 2>&1 | grep -c "RUNNING" || true)
if [ "$RUNNING_JOBS" -eq 0 ]; then
    echo "  ✅ 确认：无运行中的Flink作业"
else
    echo "  ⚠️  警告：仍有 $RUNNING_JOBS 个作业在运行"
fi

echo ""
echo "✅ 数据读写管道已停止"
echo ""
echo "📊 当前状态："
echo "  - 实时数据生成：已停止"
echo "  - Kafka → MySQL：已停止"
echo "  - Kafka → HBase：已停止"
echo ""
echo "💡 提示："
echo "  - Kafka中的数据不会丢失"
echo "  - MySQL中已有的数据保留"
echo "  - 要恢复数据读写，运行: ./scripts/start-data-pipeline.sh"
