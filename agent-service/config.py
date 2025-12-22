"""
ETC 智能交警助手 - Python Agent 服务
基于阿里云百炼 (DashScope) 实现
"""
import os
from dotenv import load_dotenv

load_dotenv()

# DashScope 配置
DASHSCOPE_API_KEY = os.getenv("DASHSCOPE_API_KEY", "sk-ec2fa8d0e5f342f5b15dd638de8a7f49")
DASHSCOPE_MODEL = os.getenv("DASHSCOPE_MODEL", "qwen-plus")

# 后端 API 地址
BACKEND_URL = os.getenv("BACKEND_URL", "http://localhost:8080")

# TTS 配置 - 使用 sambert 模型
TTS_MODEL = os.getenv("TTS_MODEL", "sambert-zhichu-v1")
TTS_VOICE = os.getenv("TTS_VOICE", "zhichu")

# 服务配置
HOST = os.getenv("HOST", "0.0.0.0")
PORT = int(os.getenv("PORT", "8091"))
