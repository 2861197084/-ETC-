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
}
