# ETC 后端服务

基于 Spring Boot 3.3.6 + JDK 17 构建的 ETC 大数据管理平台后端服务。

## 技术栈

- Spring Boot 3.3.6
- Spring Data JPA
- Spring Data Redis
- Spring Security
- MySQL 8.x
- Redis 7.x
- JWT (jjwt 0.12.6)
- OpenAPI/Swagger (springdoc 2.6.0)

## 快速开始

### 前置条件

确保 Docker 环境中 MySQL 和 Redis 已启动：

```bash
docker compose up -d mysql0 redis
```

### 本地运行

```bash
cd backend
mvn spring-boot:run
```

### Docker 运行

```bash
docker compose up -d backend
```

## API 文档

启动后访问：http://localhost:8080/docs

## 接口列表

| 模块 | 路径 | 说明 |
|------|------|------|
| 认证 | `/api/auth/login` | 用户登录 |
| 用户 | `/api/user/info` | 获取用户信息 |
| 时间 | `/api/time` | 获取虚拟时间状态 |
| 查询 | `/api/progressive/records` | 渐进式查询（MySQL/HBase） |
| 统计 | `/api/progressive/count/global` | 全局统计（Redis/MySQL） |
| 地图 | `/admin/map/checkpoints` | 获取卡口列表 |
| 实时 | `/admin/realtime/daily-stats` | 今日统计 |
| 实时 | `/admin/realtime/clone-plates` | 套牌检测（支持筛选） |
| 实时 | `/admin/realtime/violations` | 违规列表 |
| 查询 | `/admin/query/records` | 通行记录查询 |
| 查询 | `/admin/query/text2sql` | 自然语言查询 |

### 套牌检测查询参数

`GET /admin/realtime/clone-plates`

- `status`（可选）：`pending` / `confirmed` / `dismissed`
- `plateNumber`（可选）：模糊匹配
- `startTime`/`endTime`（可选）：ISO 本地时间，如 `2024-01-01T00:50:00`（按 `time_2` 过滤）
- `page`/`pageSize`：分页

## 配置说明

环境变量：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| MYSQL_HOST | localhost | MySQL 主机 |
| MYSQL_PORT | 3307 | MySQL 端口（ShardingSphere Proxy） |
| MYSQL_USER | root | MySQL 用户 |
| MYSQL_PASSWORD | root | MySQL 密码 |
| REDIS_HOST | localhost | Redis 主机 |
| REDIS_PORT | 6379 | Redis 端口 |
