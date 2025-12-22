# ETC 后端 API 接口文档（与实现一致）

> 更新日期：2025-12-22  
> 接口返回：除少数登录失败等场景外，统一为 `{ code, msg, data }`

---

## 1. 接口概览

| 模块 | 基础路径 | 说明 |
|------|---------|------|
| 认证 | `/api/auth` | 登录/登出/刷新 Token |
| 用户 | `/api/user` | 用户信息 |
| 时间 | `/api/time` | 虚拟时间服务 |
| 渐进式查询 | `/api/progressive` | MySQL(热) + HBase(历史) |
| 统计查询 | `/api/stats` | 聚合统计（Redis缓存 + Trino联邦查询） |
| 地图 | `/admin/map` | 卡口地图与实时态势 |
| 实时 | `/admin/realtime` | 指挥舱统计/告警 |
| 预测分析 | `/admin/analysis` | Time-MoE 预测分析（未来 12×5min） |
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

## 9. 预测分析（Time-MoE）

> 预测粒度：5 分钟；预测步数：12（未来 1 小时）。\n+> 页面会每分钟触发一次刷新请求，后端写入请求队列，由 Spark 作业处理并落库结果。\n+
### POST /admin/analysis/forecast/refresh

触发新一轮预测（写入请求队列）。

请求体：

```json
{ "checkpointId": "CP016", "fxlx": "2" }
```

响应：

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "requestId": 123,
    "checkpointId": "CP016",
    "fxlx": "2",
    "asOfTime": "2025-12-23 10:01:12",
    "modelVersion": "timemoe_etc_flow_v1"
  }
}
```

### GET /admin/analysis/forecast/latest

获取最新预测结果（未来 12 点）。

参数：
- `checkpointId`（必填）
- `fxlx`（必填，`1`=进城，`2`=出城）
- `modelVersion`（可选）

响应（预测完成）：

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "checkpointId": "CP016",
    "fxlx": "2",
    "modelVersion": "timemoe_etc_flow_v1",
    "updatedAt": "2025-12-23 10:01:18",
    "pending": false,
    "startTime": "2025-12-23 10:05:00",
    "times": ["2025-12-23 10:05:00", "2025-12-23 10:10:00", "..."],
    "values": [123, 118, 97, 88,  ...]
  }
}
```

响应（仍在预测）：

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "checkpointId": "CP016",
    "fxlx": "2",
    "pending": true,
    "requestId": 123,
    "requestCreatedAt": "2025-12-23 10:01:12",
    "startTime": "",
    "times": [],
    "values": []
  }
}
```

---

## 10. 统计查询（Redis 缓存 + Trino 联邦查询）

> 提供聚合统计能力，自动选择数据源：HBase 历史统计（Redis 缓存）或 MySQL 热数据（Trino 联邦查询）。

### GET /api/stats/total

获取总记录数统计，自动选择数据源。

参数：
- `startDate`（必填）：开始日期，格式 `yyyy-MM-dd`
- `endDate`（必填）：结束日期，格式 `yyyy-MM-dd`
- `checkpointId`（可选）：卡口 ID 筛选

响应：

```json
{
  "code": 200,
  "data": {
    "hbaseCount": 438631,
    "mysqlCount": 0,
    "totalCount": 438631,
    "hbaseCached": true,
    "mysqlCached": false,
    "checkpointCounts": { "CP001": 36540, "CP002": 28910, ... },
    "source": "hbase",
    "queryTimeMs": 45
  }
}
```

### GET /api/stats/daily

获取日期范围内每天的记录数。

### GET /api/stats/by-checkpoint

获取按收费站分组的统计数据。

### GET /api/stats/hbase/warmup

预热单天 HBase 统计缓存。

参数：
- `date`（必填）：日期，格式 `yyyy-MM-dd`

### GET /api/stats/hbase/cached

获取已缓存的指定日期统计（不重新计算）。

### POST /api/stats/hbase/refresh

手动触发 HBase 统计缓存刷新。

### GET /api/stats/trino/status

检查 Trino 服务是否可用。

### POST /api/stats/trino/query

执行自定义 Trino SQL 查询（仅限开发调试）。

---

## Swagger 文档

启动后访问：`http://localhost:8080/docs`
