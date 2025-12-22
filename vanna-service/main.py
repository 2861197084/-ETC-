"""
Vanna Text2SQL FastAPI 服务

API:
- POST /api/v1/ask - 自然语言查询
- POST /api/v1/generate-sql - 仅生成 SQL
- POST /api/v1/train - 添加训练数据
- GET /health - 健康检查
"""
import os
import sys

# 添加本地 vanna 源码路径
VANNA_SRC = os.path.join(os.path.dirname(__file__), "vanna", "src")
sys.path.insert(0, VANNA_SRC)

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Optional, List, Any
import logging
import traceback

import config
from vanna_instance import get_vanna
from train import train

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# 创建 FastAPI 应用
app = FastAPI(
    title="ETC Text2SQL API",
    description="基于 Vanna + 通义千问的自然语言查询接口",
    version="1.0.0"
)

# CORS 配置
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# ==================== 请求/响应模型 ====================

class AskRequest(BaseModel):
    question: str
    execute: bool = True


class AskResponse(BaseModel):
    question: str
    sql: Optional[str] = None
    columns: Optional[List[str]] = None
    data: Optional[List[Any]] = None
    error: Optional[str] = None


class TrainRequest(BaseModel):
    ddl: Optional[str] = None
    documentation: Optional[str] = None
    question: Optional[str] = None
    sql: Optional[str] = None


# ==================== 启动事件 ====================

@app.on_event("startup")
async def startup():
    """启动时初始化 Vanna 并训练"""
    logger.info("🚀 启动 Vanna Text2SQL 服务...")
    
    # 初始化训练数据
    try:
        train(force=False)
        logger.info("✅ Vanna 初始化完成")
    except Exception as e:
        logger.error(f"❌ Vanna 初始化失败: {e}")
        traceback.print_exc()


# ==================== API 端点 ====================

@app.get("/health")
async def health():
    """健康检查"""
    return {"status": "ok", "service": "vanna-text2sql", "llm": "qwen"}


@app.post("/api/v1/ask", response_model=AskResponse)
async def ask(request: AskRequest):
    """
    自然语言查询
    
    - 输入自然语言问题
    - 返回生成的 SQL 和查询结果
    """
    try:
        logger.info(f"📝 收到查询: {request.question}")
        vn = get_vanna()
        
        # 生成 SQL
        sql = vn.generate_sql(request.question)
        
        if not sql:
            return AskResponse(
                question=request.question,
                error="无法生成有效的 SQL"
            )
        
        logger.info(f"🔧 生成 SQL: {sql}")
        
        response = AskResponse(
            question=request.question,
            sql=sql
        )
        
        # 执行 SQL
        if request.execute:
            try:
                df = vn.run_sql(sql)
                if df is not None and not df.empty:
                    response.columns = df.columns.tolist()
                    response.data = df.values.tolist()
                    logger.info(f"📊 查询结果: {len(df)} 行")
            except Exception as e:
                logger.error(f"❌ SQL 执行错误: {e}")
                response.error = f"SQL 执行错误: {str(e)}"
        
        return response
        
    except Exception as e:
        logger.error(f"❌ 查询失败: {e}")
        traceback.print_exc()
        return AskResponse(
            question=request.question,
            error=str(e)
        )


@app.post("/api/v1/generate-sql")
async def generate_sql(request: AskRequest):
    """仅生成 SQL，不执行"""
    try:
        vn = get_vanna()
        sql = vn.generate_sql(request.question)
        return {
            "question": request.question,
            "sql": sql
        }
    except Exception as e:
        logger.error(f"❌ 生成 SQL 失败: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/v1/train")
async def train_endpoint(request: TrainRequest):
    """添加训练数据"""
    try:
        vn = get_vanna()
        trained = []
        
        if request.ddl:
            vn.add_ddl(request.ddl)
            trained.append("ddl")
        
        if request.documentation:
            vn.add_documentation(request.documentation)
            trained.append("documentation")
        
        if request.question and request.sql:
            vn.add_question_sql(request.question, request.sql)
            trained.append("question-sql")
        
        return {
            "success": True,
            "trained": trained,
            "message": f"成功添加 {len(trained)} 项训练数据"
        }
    except Exception as e:
        logger.error(f"❌ 训练失败: {e}")
        raise HTTPException(status_code=500, detail=str(e))


class ExecuteSqlRequest(BaseModel):
    sql: str


@app.post("/api/v1/execute")
async def execute_sql(request: ExecuteSqlRequest):
    """
    直接执行 SQL 查询
    
    - 输入 SQL 语句
    - 返回查询结果
    """
    try:
        logger.info(f"📝 执行 SQL: {request.sql[:100]}...")
        vn = get_vanna()
        
        df = vn.run_sql(request.sql)
        
        if df is not None and not df.empty:
            logger.info(f"📊 查询结果: {len(df)} 行")
            return {
                "success": True,
                "columns": df.columns.tolist(),
                "data": df.values.tolist(),
                "total": len(df)
            }
        else:
            return {
                "success": True,
                "columns": [],
                "data": [],
                "total": 0
            }
    except Exception as e:
        logger.error(f"❌ SQL 执行错误: {e}")
        return {
            "success": False,
            "error": str(e),
            "columns": [],
            "data": [],
            "total": 0
        }


@app.get("/api/v1/training-data")
async def get_training_data():
    """获取已有的训练数据"""
    try:
        vn = get_vanna()
        data = vn.get_training_data()
        return {"data": data.to_dict('records') if hasattr(data, 'to_dict') else []}
    except Exception as e:
        return {"data": [], "error": str(e)}


# ==================== 启动入口 ====================

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "main:app",
        host=config.HOST,
        port=config.PORT,
        reload=True
    )
