"""
ETC 智能交警助手 - FastAPI 服务
"""
import asyncio
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse, Response
from pydantic import BaseModel
from sse_starlette.sse import EventSourceResponse
from agent import agent_service
from tts import synthesize_speech, is_tts_available
from config import HOST, PORT, DASHSCOPE_API_KEY

app = FastAPI(
    title="ETC 智能交警助手",
    description="基于阿里云百炼的智能对话服务",
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


class ChatRequest(BaseModel):
    sessionId: str
    message: str


class TtsRequest(BaseModel):
    text: str


@app.get("/api/agent/status")
async def get_status():
    """获取服务状态"""
    return {
        "code": 200,
        "data": {
            "agent": "available" if DASHSCOPE_API_KEY else "unavailable",
            "tts": "available" if is_tts_available() else "not_configured"
        }
    }


@app.post("/api/agent/chat")
async def chat(request: ChatRequest):
    """同步对话"""
    try:
        response = await agent_service.chat(request.sessionId, request.message)
        return {
            "code": 200,
            "data": {"content": response}
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/agent/chat/stream")
async def chat_stream(request: ChatRequest):
    """流式对话 (SSE)"""
    async def event_generator():
        try:
            async for chunk in agent_service.chat_stream(request.sessionId, request.message):
                yield {"data": chunk}
            yield {"data": "[DONE]"}
        except Exception as e:
            yield {"data": f"[ERROR] {str(e)}"}
    
    return EventSourceResponse(event_generator())


@app.delete("/api/agent/session/{session_id}")
async def clear_session(session_id: str):
    """清除会话历史"""
    agent_service.clear_session(session_id)
    return {"code": 200, "message": "会话已清除"}


@app.post("/api/agent/tts")
async def text_to_speech(request: TtsRequest):
    """文字转语音"""
    if not is_tts_available():
        raise HTTPException(status_code=503, detail="TTS 服务未配置")
    
    try:
        audio_data = synthesize_speech(request.text)
        if not audio_data:
            raise HTTPException(status_code=400, detail="文本为空")
        
        return Response(
            content=audio_data,
            media_type="audio/mp3",
            headers={"Content-Disposition": "inline; filename=speech.mp3"}
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


if __name__ == "__main__":
    import uvicorn
    print(f"🚀 ETC 智能交警助手启动中...")
    print(f"📍 服务地址: http://{HOST}:{PORT}")
    print(f"📚 API 文档: http://{HOST}:{PORT}/docs")
    uvicorn.run(app, host=HOST, port=PORT)
