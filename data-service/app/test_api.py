"""
测试数据注入 API 服务
提供 HTTP 接口供前端测试页面调用
"""
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
import subprocess
import sys
import os
import signal

app = FastAPI(title="ETC 测试数据服务", version="1.0")

# 允许跨域
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 实时模拟器进程
simulator_process = None


class FlowPeakRequest(BaseModel):
    checkpoint_id: str = "CP001"
    count: int = 50


class ClonePlateRequest(BaseModel):
    plate_number: str = "苏C12345"
    checkpoint1: str = "CP001"
    checkpoint2: str = "CP011"


@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/simulator/status")
def simulator_status():
    """获取实时模拟器状态"""
    global simulator_process
    running = simulator_process is not None and simulator_process.poll() is None
    return {"running": running}


@app.post("/simulator/start")
def simulator_start():
    """启动实时数据生成器"""
    global simulator_process
    
    # 检查是否已在运行
    if simulator_process is not None and simulator_process.poll() is None:
        return {"success": False, "message": "生成器已在运行中"}
    
    try:
        simulator_process = subprocess.Popen(
            [sys.executable, "-m", "scripts.realtime_generator"],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE
        )
        return {"success": True, "message": "实时数据生成器已启动", "pid": simulator_process.pid}
    except Exception as e:
        return {"success": False, "error": str(e)}


@app.post("/simulator/stop")
def simulator_stop():
    """停止实时数据生成器"""
    global simulator_process
    
    if simulator_process is None or simulator_process.poll() is not None:
        simulator_process = None
        return {"success": False, "message": "生成器未在运行"}
    
    try:
        simulator_process.terminate()
        simulator_process.wait(timeout=5)
        simulator_process = None
        return {"success": True, "message": "实时数据生成器已停止"}
    except Exception as e:
        # 强制杀死
        if simulator_process:
            simulator_process.kill()
            simulator_process = None
        return {"success": True, "message": f"生成器已强制停止: {e}"}


@app.post("/inject/flow-peak")
def inject_flow_peak(req: FlowPeakRequest):
    """注入车流量高峰数据"""
    try:
        result = subprocess.run(
            [sys.executable, "-m", "scripts.inject_flow_peak", 
             "--checkpoint", req.checkpoint_id, 
             "--count", str(req.count)],
            capture_output=True,
            text=True,
            timeout=60
        )
        return {
            "success": result.returncode == 0,
            "message": f"注入 {req.count} 条记录到 {req.checkpoint_id}",
            "stdout": result.stdout,
            "stderr": result.stderr
        }
    except Exception as e:
        return {"success": False, "error": str(e)}


@app.post("/inject/clone-plate")
def inject_clone_plate(req: ClonePlateRequest):
    """注入套牌车检测数据"""
    try:
        result = subprocess.run(
            [sys.executable, "-m", "scripts.inject_clone_plate",
             "--plate", req.plate_number,
             "--cp1", req.checkpoint1,
             "--cp2", req.checkpoint2,
             "--auto-time",
             "--verify"],
            capture_output=True,
            text=True,
            timeout=60
        )
        return {
            "success": result.returncode == 0,
            "message": f"注入套牌 {req.plate_number} 在 {req.checkpoint1} 和 {req.checkpoint2}",
            "stdout": result.stdout,
            "stderr": result.stderr
        }
    except Exception as e:
        return {"success": False, "error": str(e)}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8001)
