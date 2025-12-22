# ETC 智能交警助手 - Python Agent 服务

基于阿里云百炼 (DashScope) 实现的智能对话服务。

## 功能

- 🤖 智能对话：基于通义千问，支持多轮对话
- 🔧 工具调用：自动调用后端 API 获取实时数据
- 🎤 语音合成：CosyVoice TTS 语音播报
- 📡 流式输出：SSE 实时推送回复

## 快速开始

### 1. 安装依赖

```bash
cd agent-service
pip install -r requirements.txt
```

### 2. 配置环境变量（可选）

默认已内置 API Key，如需修改可创建 `.env` 文件：

```env
DASHSCOPE_API_KEY=sk-xxxxx
BACKEND_URL=http://localhost:8080
PORT=8090
```

### 3. 启动服务

```bash
python main.py
```

服务将在 `http://localhost:8090` 启动。

## API 接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/agent/status` | GET | 获取服务状态 |
| `/api/agent/chat` | POST | 同步对话 |
| `/api/agent/chat/stream` | POST | 流式对话 (SSE) |
| `/api/agent/session/{id}` | DELETE | 清除会话 |
| `/api/agent/tts` | POST | 文字转语音 |

### 请求示例

```bash
# 对话
curl -X POST http://localhost:8090/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"sessionId": "test", "message": "查询当前路况"}'

# TTS
curl -X POST http://localhost:8090/api/agent/tts \
  -H "Content-Type: application/json" \
  -d '{"text": "您好，欢迎使用智能交警助手"}' \
  --output speech.mp3
```

## 支持的工具

| 工具 | 说明 |
|------|------|
| `get_traffic_overview` | 获取路况概览 |
| `get_daily_stats` | 获取今日统计 |
| `get_clone_plates` | 获取套牌记录 |
| `get_clone_plate_detail` | 获取套牌详情 |
| `get_checkpoints` | 获取卡口列表 |
| `get_checkpoint_by_id` | 获取卡口信息 |
| `get_forecast` | 获取预测数据 |
| `get_region_heat` | 获取区域热度 |

## Docker 运行

```bash
docker compose up -d agent-service
```
