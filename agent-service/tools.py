"""
工具函数 - 调用后端 API 获取数据
"""
import httpx
from typing import Optional
from config import BACKEND_URL


async def get_traffic_overview() -> dict:
    """获取当前路况概览"""
    async with httpx.AsyncClient(timeout=10) as client:
        try:
            resp = await client.get(f"{BACKEND_URL}/admin/realtime/vehicle-source")
            if resp.status_code == 200:
                data = resp.json().get("data", {})
                return {
                    "total": data.get("total", 0),
                    "local": data.get("local", 0),
                    "foreign": data.get("foreign", 0),
                    "localRate": data.get("localRate", 0),
                    "foreignRate": data.get("foreignRate", 0)
                }
        except Exception as e:
            return {"error": str(e)}
    return {"error": "获取路况失败"}


async def get_daily_stats() -> dict:
    """获取今日车流统计"""
    async with httpx.AsyncClient(timeout=10) as client:
        try:
            resp = await client.get(f"{BACKEND_URL}/admin/realtime/daily-stats")
            if resp.status_code == 200:
                return resp.json().get("data", {})
        except Exception as e:
            return {"error": str(e)}
    return {"error": "获取统计失败"}


async def get_clone_plates(limit: int = 10) -> list:
    """获取套牌嫌疑记录"""
    async with httpx.AsyncClient(timeout=10) as client:
        try:
            resp = await client.get(
                f"{BACKEND_URL}/admin/realtime/clone-plates",
                params={"page": 1, "pageSize": limit, "status": 0}
            )
            if resp.status_code == 200:
                data = resp.json().get("data", {})
                return data.get("records", [])
        except Exception as e:
            return [{"error": str(e)}]
    return []


async def get_clone_plate_detail(record_id: int) -> dict:
    """获取套牌记录详情"""
    async with httpx.AsyncClient(timeout=10) as client:
        try:
            resp = await client.get(f"{BACKEND_URL}/admin/realtime/clone-plates/{record_id}")
            if resp.status_code == 200:
                return resp.json().get("data", {})
        except Exception as e:
            return {"error": str(e)}
    return {"error": "获取详情失败"}


async def get_checkpoints() -> list:
    """获取所有卡口信息"""
    async with httpx.AsyncClient(timeout=10) as client:
        try:
            resp = await client.get(f"{BACKEND_URL}/admin/checkpoint")
            if resp.status_code == 200:
                return resp.json().get("data", [])
        except Exception as e:
            return [{"error": str(e)}]
    return []


async def get_checkpoint_by_id(checkpoint_id: str) -> dict:
    """根据 ID 获取卡口信息"""
    async with httpx.AsyncClient(timeout=10) as client:
        try:
            resp = await client.get(f"{BACKEND_URL}/admin/checkpoint/{checkpoint_id}")
            if resp.status_code == 200:
                return resp.json().get("data", {})
        except Exception as e:
            return {"error": str(e)}
    return {"error": "获取卡口信息失败"}


async def get_forecast(checkpoint_id: str) -> list:
    """获取卡口预测数据"""
    async with httpx.AsyncClient(timeout=10) as client:
        try:
            resp = await client.get(
                f"{BACKEND_URL}/admin/forecast/checkpoint/{checkpoint_id}",
                params={"limit": 12}
            )
            if resp.status_code == 200:
                return resp.json().get("data", [])
        except Exception as e:
            return [{"error": str(e)}]
    return []


async def get_region_heat() -> list:
    """获取区域热度数据"""
    async with httpx.AsyncClient(timeout=10) as client:
        try:
            resp = await client.get(
                f"{BACKEND_URL}/admin/realtime/region-heat",
                params={"interval": "hour"}
            )
            if resp.status_code == 200:
                return resp.json().get("data", [])
        except Exception as e:
            return [{"error": str(e)}]
    return []


async def trigger_forecast(checkpoint_id: str, fxlx: str = "1", as_of_time: Optional[str] = None) -> dict:
    """
    触发时间序列预测（写入请求队列）
    
    Args:
        checkpoint_id: 卡口ID，如 CP001
        fxlx: 方向类型，"1" 表示入口，"2" 表示出口
        as_of_time: 预测基准时间（可选），格式 yyyy-MM-dd HH:mm:ss
    """
    async with httpx.AsyncClient(timeout=15) as client:
        try:
            payload = {
                "checkpointId": checkpoint_id,
                "fxlx": fxlx
            }
            if as_of_time:
                payload["asOfTime"] = as_of_time
            
            resp = await client.post(
                f"{BACKEND_URL}/admin/analysis/forecast/refresh",
                json=payload
            )
            if resp.status_code == 200:
                data = resp.json().get("data", {})
                return {
                    "success": True,
                    "requestId": data.get("requestId"),
                    "checkpointId": data.get("checkpointId"),
                    "fxlx": data.get("fxlx"),
                    "asOfTime": data.get("asOfTime"),
                    "message": "预测请求已提交，请稍后查询结果"
                }
            else:
                return {"error": f"请求失败，状态码: {resp.status_code}"}
        except Exception as e:
            return {"error": str(e)}
    return {"error": "触发预测失败"}


async def get_forecast_result(checkpoint_id: str, fxlx: str = "1", wait_for_result: bool = True, max_wait_seconds: int = 30) -> dict:
    """
    获取时间序列预测结果
    
    Args:
        checkpoint_id: 卡口ID，如 CP001
        fxlx: 方向类型，"1" 表示入口，"2" 表示出口
        wait_for_result: 是否等待预测完成（默认 True）
        max_wait_seconds: 最大等待时间（秒），默认30秒
    """
    import asyncio
    
    async with httpx.AsyncClient(timeout=15) as client:
        start_time = asyncio.get_event_loop().time()
        poll_interval = 2  # 每2秒轮询一次
        
        while True:
            try:
                resp = await client.get(
                    f"{BACKEND_URL}/admin/analysis/forecast/latest",
                    params={
                        "checkpointId": checkpoint_id,
                        "fxlx": fxlx
                    }
                )
                
                if resp.status_code != 200:
                    return {"error": f"获取预测结果失败，状态码: {resp.status_code}"}
                
                data = resp.json().get("data", {})
                is_pending = data.get("pending", False)
                
                # 如果不需要等待，或者已经完成，直接返回
                if not wait_for_result or not is_pending:
                    times = data.get("times", [])
                    values = data.get("values", [])
                    
                    if not times or not values:
                        return {
                            "pending": is_pending,
                            "message": "暂无预测数据，请先触发预测" if not is_pending else "预测正在进行中...",
                            "checkpointId": checkpoint_id,
                            "fxlx": fxlx
                        }
                    
                    # 构建预测结果摘要
                    forecast_items = []
                    for t, v in zip(times, values):
                        forecast_items.append({"time": t, "flow": round(v, 1)})
                    
                    return {
                        "pending": False,
                        "checkpointId": checkpoint_id,
                        "fxlx": "入口" if fxlx == "1" else "出口",
                        "startTime": data.get("startTime", ""),
                        "updatedAt": data.get("updatedAt", ""),
                        "forecastCount": len(forecast_items),
                        "forecasts": forecast_items,
                        "summary": f"未来 {len(forecast_items) * 5} 分钟车流预测"
                    }
                
                # 检查是否超时
                elapsed = asyncio.get_event_loop().time() - start_time
                if elapsed >= max_wait_seconds:
                    return {
                        "pending": True,
                        "message": f"预测仍在进行中，已等待 {int(elapsed)} 秒",
                        "checkpointId": checkpoint_id,
                        "fxlx": fxlx,
                        "requestId": data.get("requestId")
                    }
                
                # 继续等待
                await asyncio.sleep(poll_interval)
                
            except Exception as e:
                return {"error": str(e)}
    
    return {"error": "获取预测结果失败"}


async def run_full_forecast(checkpoint_id: str, fxlx: str = "1", as_of_time: Optional[str] = None) -> dict:
    """
    完整的预测流程：触发预测并等待结果
    
    Args:
        checkpoint_id: 卡口ID，如 CP001
        fxlx: 方向类型，"1" 表示入口，"2" 表示出口
        as_of_time: 预测基准时间（可选），格式 yyyy-MM-dd HH:mm:ss
    """
    # 1. 触发预测
    trigger_result = await trigger_forecast(checkpoint_id, fxlx, as_of_time)
    if "error" in trigger_result:
        return trigger_result
    
    # 2. 等待并获取结果
    result = await get_forecast_result(checkpoint_id, fxlx, wait_for_result=True, max_wait_seconds=60)
    return result


# 工具定义 - 供 Agent 调用
TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "get_traffic_overview",
            "description": "获取当前路况概览，包括总车流量、本地车辆数、外地车辆数及占比",
            "parameters": {"type": "object", "properties": {}, "required": []}
        }
    },
    {
        "type": "function",
        "function": {
            "name": "get_daily_stats",
            "description": "获取今日车流统计数据，包括各时段车流量",
            "parameters": {"type": "object", "properties": {}, "required": []}
        }
    },
    {
        "type": "function",
        "function": {
            "name": "get_clone_plates",
            "description": "获取套牌嫌疑车辆记录列表",
            "parameters": {
                "type": "object",
                "properties": {
                    "limit": {
                        "type": "integer",
                        "description": "返回记录数量，默认10条"
                    }
                },
                "required": []
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "get_clone_plate_detail",
            "description": "根据记录ID获取套牌嫌疑详情，包括两次通行的时间、地点等",
            "parameters": {
                "type": "object",
                "properties": {
                    "record_id": {
                        "type": "integer",
                        "description": "套牌记录的ID"
                    }
                },
                "required": ["record_id"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "get_checkpoints",
            "description": "获取所有卡口（收费站）信息列表",
            "parameters": {"type": "object", "properties": {}, "required": []}
        }
    },
    {
        "type": "function",
        "function": {
            "name": "get_checkpoint_by_id",
            "description": "根据卡口ID获取卡口详细信息",
            "parameters": {
                "type": "object",
                "properties": {
                    "checkpoint_id": {
                        "type": "string",
                        "description": "卡口ID，如 CP001"
                    }
                },
                "required": ["checkpoint_id"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "get_forecast",
            "description": "获取指定卡口的车流预测数据",
            "parameters": {
                "type": "object",
                "properties": {
                    "checkpoint_id": {
                        "type": "string",
                        "description": "卡口ID，如 CP001"
                    }
                },
                "required": ["checkpoint_id"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "get_region_heat",
            "description": "获取各区域的车流热度数据",
            "parameters": {"type": "object", "properties": {}, "required": []}
        }
    },
    {
        "type": "function",
        "function": {
            "name": "trigger_forecast",
            "description": "触发时间序列预测（Time-MoE模型），将预测请求写入队列，由Spark作业处理",
            "parameters": {
                "type": "object",
                "properties": {
                    "checkpoint_id": {
                        "type": "string",
                        "description": "卡口ID，如 CP001"
                    },
                    "fxlx": {
                        "type": "string",
                        "description": "方向类型：'1' 表示入口，'2' 表示出口",
                        "enum": ["1", "2"]
                    },
                    "as_of_time": {
                        "type": "string",
                        "description": "预测基准时间（可选），格式 yyyy-MM-dd HH:mm:ss"
                    }
                },
                "required": ["checkpoint_id", "fxlx"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "get_forecast_result",
            "description": "获取时间序列预测结果，可以选择是否等待预测完成",
            "parameters": {
                "type": "object",
                "properties": {
                    "checkpoint_id": {
                        "type": "string",
                        "description": "卡口ID，如 CP001"
                    },
                    "fxlx": {
                        "type": "string",
                        "description": "方向类型：'1' 表示入口，'2' 表示出口",
                        "enum": ["1", "2"]
                    },
                    "wait_for_result": {
                        "type": "boolean",
                        "description": "是否等待预测完成，默认为 true"
                    },
                    "max_wait_seconds": {
                        "type": "integer",
                        "description": "最大等待时间（秒），默认30秒"
                    }
                },
                "required": ["checkpoint_id", "fxlx"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "run_full_forecast",
            "description": "执行完整的车流预测流程：触发Time-MoE模型预测并等待返回未来12个时间点（每5分钟一个）的车流量预测结果。这是推荐使用的预测方法。",
            "parameters": {
                "type": "object",
                "properties": {
                    "checkpoint_id": {
                        "type": "string",
                        "description": "卡口ID，如 CP001"
                    },
                    "fxlx": {
                        "type": "string",
                        "description": "方向类型：'1' 表示入口，'2' 表示出口",
                        "enum": ["1", "2"]
                    },
                    "as_of_time": {
                        "type": "string",
                        "description": "预测基准时间（可选），格式 yyyy-MM-dd HH:mm:ss"
                    }
                },
                "required": ["checkpoint_id", "fxlx"]
            }
        }
    }
]

# 工具函数映射
TOOL_FUNCTIONS = {
    "get_traffic_overview": get_traffic_overview,
    "get_daily_stats": get_daily_stats,
    "get_clone_plates": get_clone_plates,
    "get_clone_plate_detail": get_clone_plate_detail,
    "get_checkpoints": get_checkpoints,
    "get_checkpoint_by_id": get_checkpoint_by_id,
    "get_forecast": get_forecast,
    "get_region_heat": get_region_heat,
    "trigger_forecast": trigger_forecast,
    "get_forecast_result": get_forecast_result,
    "run_full_forecast": run_full_forecast,
}
