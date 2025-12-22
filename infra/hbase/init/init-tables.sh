#!/bin/bash
# HBase 表初始化脚本

set -e

HBASE_HOME=${HBASE_HOME:-/opt/hbase}
SCRIPT_DIR=$(dirname "$0")

echo "Waiting for HBase to be ready..."
sleep 5

echo "Creating HBase tables..."
$HBASE_HOME/bin/hbase shell < "$SCRIPT_DIR/create-tables.hbase"

echo "HBase tables created successfully!"
