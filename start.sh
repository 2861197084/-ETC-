#!/bin/bash
# ETC 平台启动脚本

set -e

echo "=========================================="
echo "    ETC 大数据管理平台 启动脚本"
echo "=========================================="

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# 1. 启动基础设施
echo -e "${YELLOW}[1/4] 启动基础设施 (MySQL, Redis, Kafka)...${NC}"
docker compose up -d mysql0 mysql1 redis zookeeper kafka kafka-init

echo "等待服务就绪..."
sleep 30

# 2. 启动 Flink
echo -e "${YELLOW}[2/4] 启动 Flink...${NC}"
docker compose up -d flink-jobmanager flink-taskmanager

# 3. 启动后端
echo -e "${YELLOW}[3/4] 启动后端服务...${NC}"
docker compose up -d backend

echo "等待后端启动..."
sleep 20

# 4. 验证
echo -e "${YELLOW}[4/4] 验证服务状态...${NC}"
docker compose ps

echo ""
echo -e "${GREEN}=========================================="
echo "  启动完成!"
echo ""
echo "  后端 API: http://localhost:8080"
echo "  API 文档: http://localhost:8080/docs"
echo "  Flink UI: http://localhost:8081"
echo ""
echo "  启动前端:"
echo "    cd frontend && pnpm dev"
echo "==========================================${NC}"
