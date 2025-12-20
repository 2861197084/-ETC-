#!/bin/bash
# ============================================================
# ETC 大数据平台 - Trino 任务调度脚本
# 
# 使用方式:
#   ./run-trino-task.sh daily      # 运行日统计
#   ./run-trino-task.sh monthly    # 运行月度账单
#   ./run-trino-task.sh cleanup    # 运行数据清理
#   ./run-trino-task.sh hotspot    # 运行热点分析
#   ./run-trino-task.sh all        # 运行所有任务
#
# Cron 配置示例:
#   0 1 * * * /path/to/run-trino-task.sh daily
#   0 2 1 * * /path/to/run-trino-task.sh monthly
#   0 3 * * * /path/to/run-trino-task.sh cleanup
#   0 2 * * * /path/to/run-trino-task.sh hotspot
# ============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TRINO_SERVER="${TRINO_SERVER:-http://localhost:8090}"
SQL_DIR="${SCRIPT_DIR}"
LOG_DIR="${SCRIPT_DIR}/logs"

# 创建日志目录
mkdir -p "${LOG_DIR}"

# 日志函数
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "${LOG_DIR}/trino-tasks.log"
}

# 执行 SQL 文件
run_sql() {
    local sql_file=$1
    local task_name=$2
    
    log "开始执行: ${task_name}"
    
    if docker exec -i trino trino --server http://localhost:8080 < "${sql_file}" >> "${LOG_DIR}/${task_name}.log" 2>&1; then
        log "✓ ${task_name} 执行成功"
        return 0
    else
        log "✗ ${task_name} 执行失败，详见 ${LOG_DIR}/${task_name}.log"
        return 1
    fi
}

# 日统计任务
run_daily() {
    log "========== 日统计任务开始 =========="
    run_sql "${SQL_DIR}/daily_aggregate.sql" "daily_aggregate"
    log "========== 日统计任务结束 =========="
}

# 月度账单任务
run_monthly() {
    log "========== 月度账单任务开始 =========="
    run_sql "${SQL_DIR}/monthly_bill.sql" "monthly_bill"
    log "========== 月度账单任务结束 =========="
}

# 数据清理任务
run_cleanup() {
    log "========== 数据清理任务开始 =========="
    run_sql "${SQL_DIR}/data_cleanup.sql" "data_cleanup"
    log "========== 数据清理任务结束 =========="
}

# 热点分析任务
run_hotspot() {
    log "========== 热点分析任务开始 =========="
    run_sql "${SQL_DIR}/hotspot_analysis.sql" "hotspot_analysis"
    log "========== 热点分析任务结束 =========="
}

# 主逻辑
case "${1:-help}" in
    daily)
        run_daily
        ;;
    monthly)
        run_monthly
        ;;
    cleanup)
        run_cleanup
        ;;
    hotspot)
        run_hotspot
        ;;
    all)
        run_daily
        run_hotspot
        run_cleanup
        ;;
    help|*)
        echo "使用方式: $0 {daily|monthly|cleanup|hotspot|all}"
        echo ""
        echo "任务说明:"
        echo "  daily   - 日统计汇总（每天凌晨1点）"
        echo "  monthly - 月度账单生成（每月1号凌晨2点）"
        echo "  cleanup - 数据清理（每天凌晨3点）"
        echo "  hotspot - 热点分析（每天凌晨2点）"
        echo "  all     - 运行所有任务"
        exit 1
        ;;
esac
