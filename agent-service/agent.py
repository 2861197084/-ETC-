"""
Agent 服务 - 基于 DashScope 的对话服务
"""
import json
from typing import AsyncGenerator, Optional
from dashscope import Generation
from dashscope.api_entities.dashscope_response import Role
from config import DASHSCOPE_API_KEY, DASHSCOPE_MODEL
from tools import TOOLS, TOOL_FUNCTIONS

# 系统提示词
SYSTEM_PROMPT = """你是 ETC 智能交警助手，一个专业的高速公路交通管理 AI 助手。

你的职责：
1. 帮助用户查询实时路况和车流统计
2. 分析套牌嫌疑车辆信息
3. 提供卡口（收费站）信息查询
4. 执行和解读车流预测数据（基于 Time-MoE 时间序列模型）
5. 回答交通管理相关问题

回答要求：
- 使用简洁专业的语言
- 数据要准确，必要时调用工具获取实时数据
- 对于套牌分析，要给出专业的风险评估
- 对于车流预测，解读预测趋势并给出交通管理建议
- 严禁在回答中使用任何 Emoji 符号

你可以调用以下工具获取数据：
- get_traffic_overview: 获取路况概览
- get_daily_stats: 获取今日统计
- get_clone_plates: 获取套牌记录
- get_clone_plate_detail: 获取套牌详情
- get_checkpoints: 获取卡口列表
- get_checkpoint_by_id: 获取卡口信息
- get_forecast: 获取卡口预测数据（简单查询）
- get_region_heat: 获取区域热度

时间序列预测工具（基于 Time-MoE 模型，预测未来60分钟的车流量）：
- run_full_forecast: 【推荐】完整预测流程，触发预测并等待返回结果（未来12个时间点，每5分钟一个）
- trigger_forecast: 仅触发预测请求，不等待结果
- get_forecast_result: 获取预测结果，可选择是否轮询等待

当用户询问"预测"、"预估"、"未来车流"等相关问题时，优先使用 run_full_forecast 工具。
卡口ID格式为 CP001-CP010，方向类型 fxlx 为 "1"（入口）或 "2"（出口）。
"""


class AgentService:
    def __init__(self):
        self.sessions: dict[str, list] = {}
    
    def get_or_create_session(self, session_id: str) -> list:
        """获取或创建会话历史"""
        if session_id not in self.sessions:
            self.sessions[session_id] = [
                {"role": "system", "content": SYSTEM_PROMPT}
            ]
        return self.sessions[session_id]
    
    def clear_session(self, session_id: str):
        """清除会话历史"""
        if session_id in self.sessions:
            del self.sessions[session_id]
    
    async def _execute_tool(self, tool_name: str, arguments: dict) -> str:
        """执行工具函数"""
        if tool_name in TOOL_FUNCTIONS:
            func = TOOL_FUNCTIONS[tool_name]
            try:
                result = await func(**arguments)
                return json.dumps(result, ensure_ascii=False)
            except Exception as e:
                return json.dumps({"error": str(e)}, ensure_ascii=False)
        return json.dumps({"error": f"未知工具: {tool_name}"}, ensure_ascii=False)
    
    async def chat(self, session_id: str, message: str) -> str:
        """同步对话"""
        messages = self.get_or_create_session(session_id)
        messages.append({"role": "user", "content": message})
        
        # 调用 DashScope
        response = Generation.call(
            api_key=DASHSCOPE_API_KEY,
            model=DASHSCOPE_MODEL,
            messages=messages,
            tools=TOOLS,
            result_format="message"
        )
        
        if response.status_code != 200:
            return f"抱歉，服务出现错误: {response.message}"
        
        assistant_message = response.output.choices[0].message
        
        # 处理工具调用
        while assistant_message.get("tool_calls"):
            messages.append(assistant_message)
            
            for tool_call in assistant_message["tool_calls"]:
                func_name = tool_call["function"]["name"]
                func_args = json.loads(tool_call["function"].get("arguments", "{}"))
                
                # 执行工具
                tool_result = await self._execute_tool(func_name, func_args)
                
                messages.append({
                    "role": "tool",
                    "content": tool_result,
                    "tool_call_id": tool_call["id"]
                })
            
            # 继续对话
            response = Generation.call(
                api_key=DASHSCOPE_API_KEY,
                model=DASHSCOPE_MODEL,
                messages=messages,
                tools=TOOLS,
                result_format="message"
            )
            
            if response.status_code != 200:
                return f"抱歉，服务出现错误: {response.message}"
            
            assistant_message = response.output.choices[0].message
        
        # 保存助手回复
        content = assistant_message.get("content", "")
        messages.append({"role": "assistant", "content": content})
        
        return content
    
    async def chat_stream(self, session_id: str, message: str) -> AsyncGenerator[str, None]:
        """流式对话"""
        messages = self.get_or_create_session(session_id)
        messages.append({"role": "user", "content": message})
        
        # 第一次调用，检查是否需要工具
        response = Generation.call(
            api_key=DASHSCOPE_API_KEY,
            model=DASHSCOPE_MODEL,
            messages=messages,
            tools=TOOLS,
            result_format="message"
        )
        
        if response.status_code != 200:
            yield f"抱歉，服务出现错误: {response.message}"
            return
        
        assistant_message = response.output.choices[0].message
        
        # 处理工具调用（非流式）
        while assistant_message.get("tool_calls"):
            messages.append(assistant_message)
            
            for tool_call in assistant_message["tool_calls"]:
                func_name = tool_call["function"]["name"]
                func_args = json.loads(tool_call["function"].get("arguments", "{}"))
                
                yield f"[正在查询 {func_name}...]\n"
                
                tool_result = await self._execute_tool(func_name, func_args)
                
                messages.append({
                    "role": "tool",
                    "content": tool_result,
                    "tool_call_id": tool_call["id"]
                })
            
            response = Generation.call(
                api_key=DASHSCOPE_API_KEY,
                model=DASHSCOPE_MODEL,
                messages=messages,
                tools=TOOLS,
                result_format="message"
            )
            
            if response.status_code != 200:
                yield f"抱歉，服务出现错误: {response.message}"
                return
            
            assistant_message = response.output.choices[0].message
        
        # 最后用流式输出最终回复
        full_content = ""
        responses = Generation.call(
            api_key=DASHSCOPE_API_KEY,
            model=DASHSCOPE_MODEL,
            messages=messages,
            result_format="message",
            stream=True,
            incremental_output=True
        )
        
        for resp in responses:
            if resp.status_code == 200:
                delta = resp.output.choices[0].message.get("content", "")
                if delta:
                    full_content += delta
                    yield delta
        
        # 保存完整回复
        if full_content:
            messages.append({"role": "assistant", "content": full_content})


# 单例
agent_service = AgentService()
