package com.etc.agent;

import com.etc.agent.tools.CheckpointInfoTool;
import com.etc.agent.tools.ClonePlateAnalysisTool;
import com.etc.agent.tools.ForecastTool;
import com.etc.agent.tools.TrafficStatsTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ETC 智能助手 Agent 服务
 * 基于 Spring AI Alibaba 实现的交通管理智能助手
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final ChatModel chatModel;
    private final TrafficStatsTool trafficStatsTool;
    private final ForecastTool forecastTool;
    private final ClonePlateAnalysisTool clonePlateAnalysisTool;
    private final CheckpointInfoTool checkpointInfoTool;

    // 会话历史（内存存储，不持久化）
    private final Map<String, List<Message>> sessionHistory = new ConcurrentHashMap<>();

    private static final String SYSTEM_PROMPT = """
        你是"ETC智能交警助手"，一位专业的高速公路交通管理AI助手，服务于徐州市彭城交通大数据管理平台。
        
        ## 你的职责：
        1. **路况查询**：查询当前高速路况、各卡口通行状态、区域热度排名
        2. **数据统计**：查询今日车流统计、本地/外地车辆占比、各时段流量
        3. **预测分析**：查询卡口车流量预测结果，解释预测趋势和影响因素
        4. **套牌检测**：查询套牌嫌疑记录、分析套牌可能性、给出处理建议
        5. **卡口信息**：查询卡口基本信息、位置、所属区域
        
        ## 回答风格：
        - 专业、简洁、友好
        - 使用表情符号增强可读性
        - 数据用具体数字呈现
        - 给出专业建议时要有依据
        
        ## 注意事项：
        - 涉及具体数据时，调用相应工具获取实时数据
        - 如果用户问题不明确，主动询问澄清
        - 回答要结构化，便于阅读
        - 遇到无法处理的请求，礼貌说明并引导到可支持的功能
        
        ## 当前服务区域：
        徐州市及周边地区，包括铜山区、新沂市、睢宁县、丰县、邳州市、沛县等。
        """;

    /**
     * 处理用户消息（流式响应）
     */
    public Flux<String> chat(String sessionId, String userMessage) {
        log.info("[Agent] 收到消息, sessionId={}, message={}", sessionId, userMessage);

        // 获取或创建会话历史
        List<Message> history = sessionHistory.computeIfAbsent(sessionId, k -> new ArrayList<>());

        // 添加用户消息到历史
        history.add(new UserMessage(userMessage));

        // 构建消息列表
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        
        // 只保留最近10轮对话
        int historySize = history.size();
        int startIdx = Math.max(0, historySize - 20);
        messages.addAll(history.subList(startIdx, historySize));

        try {
            // 使用 ChatClient 进行流式对话
            ChatClient chatClient = ChatClient.builder(chatModel)
                    .defaultTools(
                            trafficStatsTool,
                            forecastTool,
                            clonePlateAnalysisTool,
                            checkpointInfoTool
                    )
                    .build();

            StringBuilder responseBuilder = new StringBuilder();

            return chatClient.prompt()
                    .messages(messages)
                    .stream()
                    .content()
                    .doOnNext(chunk -> {
                        responseBuilder.append(chunk);
                    })
                    .doOnComplete(() -> {
                        // 完成后将助手回复添加到历史
                        String fullResponse = responseBuilder.toString();
                        history.add(new AssistantMessage(fullResponse));
                        log.info("[Agent] 回复完成, sessionId={}, responseLength={}", sessionId, fullResponse.length());
                    })
                    .doOnError(error -> {
                        log.error("[Agent] 对话出错, sessionId={}", sessionId, error);
                    });

        } catch (Exception e) {
            log.error("[Agent] 处理消息失败", e);
            // 移除刚添加的用户消息
            history.remove(history.size() - 1);
            return Flux.just("抱歉，处理您的请求时出现错误：" + e.getMessage());
        }
    }

    /**
     * 处理用户消息（非流式响应）
     */
    public String chatSync(String sessionId, String userMessage) {
        log.info("[Agent] 收到消息(同步), sessionId={}, message={}", sessionId, userMessage);

        List<Message> history = sessionHistory.computeIfAbsent(sessionId, k -> new ArrayList<>());
        history.add(new UserMessage(userMessage));

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        
        int historySize = history.size();
        int startIdx = Math.max(0, historySize - 20);
        messages.addAll(history.subList(startIdx, historySize));

        try {
            ChatClient chatClient = ChatClient.builder(chatModel)
                    .defaultTools(
                            trafficStatsTool,
                            forecastTool,
                            clonePlateAnalysisTool,
                            checkpointInfoTool
                    )
                    .build();

            String response = chatClient.prompt()
                    .messages(messages)
                    .call()
                    .content();

            history.add(new AssistantMessage(response));
            log.info("[Agent] 回复完成(同步), sessionId={}", sessionId);
            
            return response;

        } catch (Exception e) {
            log.error("[Agent] 处理消息失败", e);
            history.remove(history.size() - 1);
            return "抱歉，处理您的请求时出现错误：" + e.getMessage();
        }
    }

    /**
     * 清除会话历史
     */
    public void clearSession(String sessionId) {
        sessionHistory.remove(sessionId);
        log.info("[Agent] 清除会话, sessionId={}", sessionId);
    }

    /**
     * 获取会话历史
     */
    public List<Map<String, String>> getSessionHistory(String sessionId) {
        List<Message> history = sessionHistory.get(sessionId);
        if (history == null || history.isEmpty()) {
            return List.of();
        }

        List<Map<String, String>> result = new ArrayList<>();
        for (Message msg : history) {
            String role = msg instanceof UserMessage ? "user" : "assistant";
            String content = msg.getText();
            result.add(Map.of("role", role, "content", content));
        }
        return result;
    }
}
