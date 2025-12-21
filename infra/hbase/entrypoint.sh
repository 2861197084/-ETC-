#!/usr/bin/env bash
set -euo pipefail

export HBASE_HOME="/opt/hbase"
export HBASE_LOG_DIR="/data/logs"
export HBASE_PID_DIR="/data/pids"

mkdir -p "${HBASE_LOG_DIR}" "${HBASE_PID_DIR}" /data/hbase

# 清理残留的 PID 文件（容器重启时可能残留）
rm -f "${HBASE_PID_DIR}"/*.pid

if [[ "${1:-}" == "shell" ]]; then
  shift
  exec "${HBASE_HOME}/bin/hbase" shell "$@"
fi

"${HBASE_HOME}/bin/hbase-daemon.sh" start master
"${HBASE_HOME}/bin/hbase-daemon.sh" start regionserver
"${HBASE_HOME}/bin/hbase-daemon.sh" start thrift

tail -F "${HBASE_LOG_DIR}"/*.log
