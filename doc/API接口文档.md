# ETC 后端 API 接口文档（与实现一致）

> 更新日期：2025-12-21  
> 接口返回：除少数登录失败等场景外，统一为 `{ code, msg, data }`

---

## 1. 接口概览

| 模块 | 基础路径 | 说明 |
|------|---------|------|
| 认证 | `/api/auth` | 登录/登出/刷新 Token |
| 用户 | `/api/user` | 用户信息 |
| 时间 | `/api/time` | 虚拟时间服务 |
| 渐进式查询 | `/api/progressive` | MySQL(热) + HBase(历史) |
| 地图 | `/admin/map` | 卡口地图与实时态势 |
| 实时 | `/admin/realtime` | 指挥舱统计/告警 |
| 查询 | `/admin/query` | 查询中心（基础查询 + Text2SQL） |

---

## 2. 通用响应

```json
{ "code": 200, "msg": "success", "data": {} }
```

业务错误示例：

```json
{ "code": 404, "msg": "record not found", "data": null }
```

---

## 3. 认证与用户

### POST /api/auth/login

请求体：

```json
{ "username": "admin", "password": "admin123" }
```

响应：

```json
{ "code": 200, "msg": "success", "data": { "token": "...", "refreshToken": "...", "expiresIn": 86400 } }
```

### POST /api/auth/logout

### POST /api/auth/refresh

### GET /api/user/info

---

## 4. 虚拟时间

### GET /api/time

返回 `simulatedTime`、`simulatedTimestamp`、`timeScale`、`isRunning`、`windowStart/windowEnd` 等字段。

### POST /api/time/start

### POST /api/time/pause

### POST /api/time/reset

---

## 5. 渐进式查询（MySQL + HBase）

> `source=mysql|hbase`，用于“先返回热数据，再加载历史数据”的交互。

### GET /api/progressive/records

参数（常用）：
- `plateNumber`、`checkpointId`、`startTime`、`endTime`
- `source`（默认 `mysql`）
- `page`（默认 `1`）、`size`（默认 `20`）
- `lastRowKey`（HBase 分页用）

### GET /api/progressive/records/by-plate

### GET /api/progressive/records/by-checkpoint

### GET /api/progressive/count/global

返回 `todayCount`、`totalCount`、`checkpointCounts`（统计汇总来自 Redis，热数据由后端定时刷新）。

### GET /api/progressive/count/plate/{plateNumber}

### GET /api/progressive/count/checkpoint/{checkpointId}

---

## 6. 地图（指挥舱）

### GET /admin/map/checkpoints

### GET /admin/map/checkpoints/{id}

### GET /admin/map/heatmap

### GET /admin/map/pressure

### GET /admin/map/events

参数：
- `type`（可选）
- `limit`（默认 `20`）

---

## 7. 实时（告警与统计）

### GET /admin/realtime/daily-stats

### GET /admin/realtime/clone-plates

### PUT /admin/realtime/clone-plates/{id}

### GET /admin/realtime/violations

### PUT /admin/realtime/violations/{id}

### GET /admin/realtime/pressure-warnings

### GET /admin/realtime/flow

---

## 8. 查询中心

### GET /admin/query/records

### GET /admin/query/records/{id}

### POST /admin/query/text2sql

### POST /admin/query/execute

### GET /admin/query/options/checkpoints

### GET /admin/query/options/vehicle-types

---

## Swagger 文档

启动后访问：`http://localhost:8080/docs`
