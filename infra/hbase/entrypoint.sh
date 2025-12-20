#!/usr/bin/env bash
set -euo pipefail

export HBASE_HOME="/opt/hbase"
export HBASE_LOG_DIR="/data/logs"
export HBASE_PID_DIR="/data/pids"

mkdir -p "${HBASE_LOG_DIR}" "${HBASE_PID_DIR}" /data/hbase

if [[ "${1:-}" == "shell" ]]; then
  shift
  exec "${HBASE_HOME}/bin/hbase" shell "$@"
fi

"${HBASE_HOME}/bin/hbase-daemon.sh" start master
"${HBASE_HOME}/bin/hbase-daemon.sh" start regionserver
"${HBASE_HOME}/bin/hbase-daemon.sh" start thrift

tail -F "${HBASE_LOG_DIR}"/*.log
