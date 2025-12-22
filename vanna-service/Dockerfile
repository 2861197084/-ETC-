FROM python:3.11-slim

WORKDIR /app

# 安装依赖
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 复制代码
COPY . .

# 创建数据目录
RUN mkdir -p /app/chroma_data

# 环境变量
ENV PYTHONUNBUFFERED=1
ENV CHROMA_PATH=/app/chroma_data

# 暴露端口
EXPOSE 8100

# 启动命令
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8100"]
