# ETC 高速公路大数据管理平台

> 面向高速公路 ETC 系统的大数据管理平台，支持实时数据接入、可视化大屏、交互式查询和智能预测分析。

## 系统架构

```
┌─────────────────────────────────────────────────────────────────┐
│                     展示层 (Frontend)                            │
│  Vue 3 + Vite + TypeScript + Element Plus                       │
├─────────────────────────────────────────────────────────────────┤
│                     服务层 (Backend)                             │
│  Spring Boot 3.x + REST API + Redis                             │
├─────────────────────────────────────────────────────────────────┤
│                     处理层 (Processing)                          │
│  Python 数据服务 (模拟) + Flink (套牌检测/存储)                    │
├─────────────────────────────────────────────────────────────────┤
│                     存储层 (Storage)                             │
│  MySQL + Redis + Kafka + HBase                            │
└─────────────────────────────────────────────────────────────────┘
```

## 快速开始

### 1 数据导入与实时模拟

> 历史数据仅入 HBase；实时数据通过 Kafka→Flink 落 MySQL（热数据）并写入 HBase（归档）。

```bash
# 1) 提交 Flink 作业（先编译 flink-jobs；在仓库根目录执行）

# A. 本机有 JDK 17+ 时
mvn -f flink-jobs/pom.xml -DskipTests clean package

# B. 本机没 JDK，用 Docker 编译
docker run --rm -v "$PWD":/workspace -w /workspace maven:3.9-eclipse-temurin-17 \
  mvn -f flink-jobs/pom.xml -DskipTests clean package

# 确认 JobManager 容器能看到 JAR（docker-compose.yml 会挂载 ./flink-jobs/target → /opt/flink/jobs）
docker compose exec flink-jobmanager ls -la /opt/flink/jobs

### 2 启动 Docker 服务

```bash
# 启动所有服务（首次需要 --build）
docker compose up -d --build
```

# 提交作业（-d 表示后台运行；JAR 文件名以 /opt/flink/jobs 实际输出为准）
docker compose exec flink-jobmanager flink run -d -c com.etc.flink.MySqlStorageJob /opt/flink/jobs/etc-flink-jobs-1.0.0.jar
docker compose exec flink-jobmanager flink run -d -c com.etc.flink.HBaseStorageJob /opt/flink/jobs/etc-flink-jobs-1.0.0.jar

# 2) 导入历史数据（2023-12 → HBase，同时写入 Redis 历史统计；一次性容器用 --rm 自动清理）
docker compose run --rm data-service python -m scripts.import_to_hbase

# 3) 启动实时模拟（2024-01 → Kafka，基于后端虚拟时间窗口）
docker compose run --rm data-service python -m scripts.realtime_simulator
```

> 若 `/opt/flink/jobs` 为空：通常是先启动了 Flink 容器、后编译了 `flink-jobs/`，可执行 `docker compose restart flink-jobmanager flink-taskmanager` 后再检查；若仍为空，再检查 Docker Desktop 对项目目录的共享/权限设置。

### 2. 启动前端

```bash
cd frontend
pnpm install
pnpm dev
```

### 3. 访问地址

| 服务 | 地址 |
|------|------|
| 前端 | http://localhost:5173 |
| 后端 API | http://localhost:8080 |
| API 文档 | http://localhost:8080/docs |
| Flink UI | http://localhost:8081 |

## 时间模拟系统

系统支持时间模拟，用于演示：
- 模拟时间从 **2024-01-01 00:00** 开始
- 每 **1 真实秒 = 5 模拟分钟**
- 前端页面右上角显示当前模拟时间（替代真实时间显示）

**API 接口：**
```
GET  /api/time        # 获取当前时间状态
POST /api/time/start  # 启动模拟
POST /api/time/pause  # 暂停模拟
POST /api/time/reset  # 重置
```

## 查询路由与统计

- 热数据（近 7 天）：ShardingSphere Proxy（逻辑表 `pass_record` → `mysql0/mysql1` 的 `pass_record_0/1`）
- 历史数据（全量）：HBase（`etc:pass_record`）
- 统计汇总：Redis（后端定时刷新热数据统计；历史导入时写入历史总量统计）
- MySQL 热数据清理：后端按虚拟时间执行 7 天游标清理（见 `backend/src/main/resources/application.yml` 的 `etc.retention.*`）

## 项目结构

```
/
├── backend/           # Java Spring Boot 后端
├── frontend/          # Vue 3 前端
├── data-service/      # Python 数据服务
├── flink-jobs/        # Flink 流处理作业
├── infra/             # Docker 基础设施配置
├── data/              # CSV 数据文件
├── doc/               # 项目文档
└── docker-compose.yml
```

## 技术栈

| 模块 | 技术 |
|------|------|
| 前端 | Vue 3, TypeScript, Vite, Element Plus |
| 后端 | Spring Boot 3.3.6, JDK 17, Spring Data JPA |
| 数据服务 | Python 3.11（脚本：HBase 导入 / Kafka 实时模拟） |
| 流处理 | Apache Flink 1.20 |
| 数据库 | MySQL 8, Redis 7 |
| 消息队列 | Apache Kafka |
| 大数据存储 | HBase |

## 文档

- [API 接口文档](doc/API接口文档.md)
- [数据库设计](doc/数据库设计总表.md)
- [系统设计](doc/设计文档.md)
- [需求文档](doc/需求文档.md)

## 默认账户

- 用户名: `admin`
- 密码: `admin123`

## License

MIT License
