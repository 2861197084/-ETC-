"""
Vanna Text2SQL 服务配置
"""
import os
from dotenv import load_dotenv

load_dotenv()

# LLM 配置
# 支持: "openai", "qwen" (阿里云通义千问), "ollama"
LLM_PROVIDER = os.getenv("LLM_PROVIDER", "qwen")

# OpenAI 配置
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY", "")
OPENAI_MODEL = os.getenv("OPENAI_MODEL", "gpt-4o-mini")
OPENAI_BASE_URL = os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1")

# 阿里云通义千问配置 (推荐)
QWEN_API_KEY = os.getenv("QWEN_API_KEY", "")
QWEN_MODEL = os.getenv("QWEN_MODEL", "qwen-turbo")  # qwen-turbo / qwen-plus / qwen-max
QWEN_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"

# 本地 Ollama 配置
OLLAMA_HOST = os.getenv("OLLAMA_HOST", "http://localhost:11434")
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "qwen2.5:7b")

# MySQL 配置 (通过 ShardingSphere)
MYSQL_HOST = os.getenv("MYSQL_HOST", "shardingsphere")
MYSQL_PORT = int(os.getenv("MYSQL_PORT", "3307"))
MYSQL_USER = os.getenv("MYSQL_USER", "root")
MYSQL_PASSWORD = os.getenv("MYSQL_PASSWORD", "root")
MYSQL_DATABASE = os.getenv("MYSQL_DATABASE", "etc")

# ChromaDB 向量存储 (用于训练数据)
CHROMA_PATH = os.getenv("CHROMA_PATH", "./chroma_data")

# 服务配置
HOST = os.getenv("VANNA_HOST", "0.0.0.0")
PORT = int(os.getenv("VANNA_PORT", "8100"))
