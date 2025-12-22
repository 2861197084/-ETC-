"""
Vanna Text2SQL 服务

使用 Vanna 的 legacy API：
- QianWenAI_Chat: 通义千问 LLM
- ChromaDB_VectorStore: 向量存储
"""
import os
import sys

# 添加本地 vanna 源码路径
VANNA_SRC = os.path.join(os.path.dirname(__file__), "vanna", "src")
sys.path.insert(0, VANNA_SRC)

from vanna.legacy.qianwen import QianWenAI_Chat
from vanna.legacy.chromadb import ChromaDB_VectorStore

import config


class ETCVanna(ChromaDB_VectorStore, QianWenAI_Chat):
    """
    ETC 平台专用的 Vanna 实例
    
    继承自:
    - ChromaDB_VectorStore: 使用 ChromaDB 存储训练数据
    - QianWenAI_Chat: 使用阿里云通义千问作为 LLM
    """
    def __init__(self, config=None):
        ChromaDB_VectorStore.__init__(self, config=config)
        QianWenAI_Chat.__init__(self, config=config)


def create_vanna():
    """创建 Vanna 实例"""
    vn = ETCVanna(config={
        # 通义千问配置
        'api_key': config.QWEN_API_KEY,
        'model': config.QWEN_MODEL,
        # ChromaDB 配置
        'path': config.CHROMA_PATH,
    })
    
    # 设置 MySQL 连接
    import pymysql
    
    def run_sql(sql: str):
        """执行 SQL 并返回 DataFrame"""
        import pandas as pd
        conn = pymysql.connect(
            host=config.MYSQL_HOST,
            port=config.MYSQL_PORT,
            user=config.MYSQL_USER,
            password=config.MYSQL_PASSWORD,
            database=config.MYSQL_DATABASE,
            charset='utf8mb4'
        )
        try:
            df = pd.read_sql(sql, conn)
            return df
        finally:
            conn.close()
    
    vn.run_sql = run_sql
    vn.run_sql_is_set = True
    
    return vn


# 全局实例
_vanna_instance = None

def get_vanna():
    """获取 Vanna 单例"""
    global _vanna_instance
    if _vanna_instance is None:
        _vanna_instance = create_vanna()
    return _vanna_instance
