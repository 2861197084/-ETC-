# Vanna Text2SQL 服务

基于 [Vanna.AI](https://vanna.ai/) 的自然语言查询服务，支持用自然语言查询 ETC 高速公路数据。

## 功能特性

- 🗣️ **自然语言转 SQL** - 输入中文问题，自动生成并执行 SQL
- 📊 **智能理解** - 理解业务术语（本地车/外地车、进城/出城等）
- 🧠 **持续学习** - 支持添加新的训练数据
- 🔌 **多 LLM 支持** - OpenAI / Ollama (本地)

## 快速开始

### 1. 配置环境变量

```bash
cp .env.example .env
# 编辑 .env，填入 OpenAI API Key 或配置 Ollama
```

### 2. 安装依赖

```bash
pip install -r requirements.txt
```

### 3. 启动服务

```bash
# 开发模式
python main.py

# 或使用 uvicorn
uvicorn main:app --host 0.0.0.0 --port 8100 --reload
```

### 4. Docker 部署

```bash
docker build -t vanna-service .
docker run -p 8100:8100 --env-file .env vanna-service
```

## API 接口

### 查询接口

```bash
POST /api/v1/ask
Content-Type: application/json

{
    "question": "今天各卡口的车流量排名",
    "execute": true
}
```

响应:
```json
{
    "question": "今天各卡口的车流量排名",
    "sql": "SELECT checkpoint_id, COUNT(*) as count FROM pass_record WHERE DATE(gcsj) = CURDATE() GROUP BY checkpoint_id ORDER BY count DESC",
    "columns": ["checkpoint_id", "count"],
    "data": [
        ["CP001", 1523],
        ["CP002", 1456],
        ...
    ]
}
```

### 仅生成 SQL

```bash
POST /api/v1/generate-sql
{
    "question": "本地车辆占比是多少"
}
```

### 添加训练数据

```bash
POST /api/v1/train
{
    "question": "查询早高峰车流",
    "sql": "SELECT * FROM pass_record WHERE HOUR(gcsj) BETWEEN 7 AND 9"
}
```

## 示例问题

- 今天的总车流量是多少
- 查询各卡口的通行量排名
- 最近7天每天的车流量趋势
- 本地车辆和外地车辆的占比
- 查询苏C12345这辆车的通行记录
- 今天哪个时段车流量最大
- 铜山区今天的进城车辆数
- 查询套牌嫌疑车辆

## 使用本地 Ollama

如果不想使用 OpenAI API，可以使用本地 Ollama:

```bash
# 1. 安装 Ollama (https://ollama.com)
# 2. 拉取中文模型
ollama pull qwen2.5:7b

# 3. 修改 .env
LLM_PROVIDER=ollama
OLLAMA_HOST=http://localhost:11434
OLLAMA_MODEL=qwen2.5:7b
```

## 架构说明

```
┌─────────────────┐     ┌─────────────────┐
│   前端请求       │────▶│  Vanna Agent    │
│ "今天车流量？"   │     │                 │
└─────────────────┘     │  ┌───────────┐  │
                        │  │ LLM       │  │
                        │  │ (GPT/Ollama)│ │
                        │  └───────────┘  │
                        │  ┌───────────┐  │
                        │  │ ChromaDB  │  │  训练数据
                        │  │ (向量存储) │  │  DDL/文档/问答
                        │  └───────────┘  │
                        │  ┌───────────┐  │
                        │  │ MySQL     │  │  执行 SQL
                        │  │ Runner    │  │
                        │  └───────────┘  │
                        └────────┬────────┘
                                 │
                                 ▼
                        ┌─────────────────┐
                        │ ShardingSphere  │
                        │   (3307)        │
                        └─────────────────┘
```

## 文件结构

```
vanna-service/
├── main.py          # FastAPI 服务入口
├── agent.py         # Vanna Agent 初始化和训练
├── config.py        # 配置管理
├── train_data.py    # 训练数据 (DDL/文档/问答)
├── requirements.txt # Python 依赖
├── Dockerfile       # Docker 构建文件
├── .env.example     # 环境变量模板
└── chroma_data/     # 向量存储数据 (自动生成)
```
