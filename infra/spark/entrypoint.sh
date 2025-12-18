#!/usr/bin/env bash
set -euo pipefail

MODE="${SPARK_MODE:-master}"

if [[ "${MODE}" == "master" ]]; then
  exec /opt/spark/bin/spark-class org.apache.spark.deploy.master.Master --host 0.0.0.0 --port 7077 --webui-port 8080
fi

if [[ "${MODE}" == "worker" ]]; then
  MASTER_URL="${SPARK_MASTER_URL:-spark://spark-master:7077}"
  exec /opt/spark/bin/spark-class org.apache.spark.deploy.worker.Worker "${MASTER_URL}" --webui-port 8081
fi

echo "Unknown SPARK_MODE=${MODE}. Use SPARK_MODE=master|worker."
exit 1

