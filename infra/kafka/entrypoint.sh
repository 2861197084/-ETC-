#!/usr/bin/env bash
set -euo pipefail

KAFKA_NODE_ID="${KAFKA_NODE_ID:-1}"
KAFKA_CLUSTER_ID="${KAFKA_CLUSTER_ID:-}"
KAFKA_EXTERNAL_HOST="${KAFKA_EXTERNAL_HOST:-localhost}"

DATA_DIR="/var/lib/kafka"
LOG_DIR="${DATA_DIR}/data"
CONF_DIR="${DATA_DIR}/conf"
CONF_FILE="${CONF_DIR}/server.properties"
CLUSTER_ID_FILE="${DATA_DIR}/cluster.id"

mkdir -p "${LOG_DIR}" "${CONF_DIR}"

cat >"${CONF_FILE}" <<EOF
process.roles=broker,controller
node.id=${KAFKA_NODE_ID}
controller.quorum.voters=1@kafka:9093

listeners=INTERNAL://0.0.0.0:9092,EXTERNAL://0.0.0.0:19092,CONTROLLER://0.0.0.0:9093
advertised.listeners=INTERNAL://kafka:9092,EXTERNAL://${KAFKA_EXTERNAL_HOST}:19092
listener.security.protocol.map=INTERNAL:PLAINTEXT,EXTERNAL:PLAINTEXT,CONTROLLER:PLAINTEXT
inter.broker.listener.name=INTERNAL
controller.listener.names=CONTROLLER

log.dirs=${LOG_DIR}
num.partitions=6
auto.create.topics.enable=false
group.initial.rebalance.delay.ms=0

offsets.topic.replication.factor=1
transaction.state.log.replication.factor=1
transaction.state.log.min.isr=1
EOF

if [[ -z "${KAFKA_CLUSTER_ID}" ]]; then
  if [[ -f "${CLUSTER_ID_FILE}" ]]; then
    KAFKA_CLUSTER_ID="$(cat "${CLUSTER_ID_FILE}")"
  else
    KAFKA_CLUSTER_ID="$(/opt/kafka/bin/kafka-storage.sh random-uuid)"
    echo "${KAFKA_CLUSTER_ID}" >"${CLUSTER_ID_FILE}"
  fi
fi

if [[ ! -f "${LOG_DIR}/meta.properties" ]]; then
  /opt/kafka/bin/kafka-storage.sh format -t "${KAFKA_CLUSTER_ID}" -c "${CONF_FILE}" --ignore-formatted
fi

exec /opt/kafka/bin/kafka-server-start.sh "${CONF_FILE}"
