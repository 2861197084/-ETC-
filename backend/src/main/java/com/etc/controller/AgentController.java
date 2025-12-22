package com.etc.controller;

import com.etc.agent.AgentService;
import com.etc.agent.TtsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ETC 智能助手 Agent 控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;
    private final TtsService ttsService;

    /**
     * 发送消息（流式响应）
     * 使用 Server-Sent Events (SSE) 返回流式内容
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequest request) {
        String sessionId = request.sessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }
        
        log.info("[AgentController] 流式对话请求, sessionId={}", sessionId);
        
        return agentService.chat(sessionId, request.message())
                .map(chunk -> "data: " + chunk.replace("\n", "\\n") + "\n\n");
    }

    /**
     * 发送消息（非流式响应）
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        String sessionId = request.sessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }
        
        log.info("[AgentController] 对话请求, sessionId={}", sessionId);
        
        String response = agentService.chatSync(sessionId, request.message());
        
        return ResponseEntity.ok(new ChatResponse(sessionId, response));
    }

    /**
     * 清除会话历史
     */
    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<Void> clearSession(@PathVariable String sessionId) {
        log.info("[AgentController] 清除会话, sessionId={}", sessionId);
        agentService.clearSession(sessionId);
        return ResponseEntity.ok().build();
    }

    /**
     * 获取会话历史
     */
    @GetMapping("/session/{sessionId}/history")
    public ResponseEntity<List<Map<String, String>>> getHistory(@PathVariable String sessionId) {
        return ResponseEntity.ok(agentService.getSessionHistory(sessionId));
    }

    /**
     * 语音合成
     * 返回 MP3 格式音频
     */
    @PostMapping(value = "/tts", produces = "audio/mpeg")
    public ResponseEntity<byte[]> synthesizeSpeech(@RequestBody TtsRequest request) {
        log.info("[AgentController] TTS 请求, textLength={}", 
                request.text() != null ? request.text().length() : 0);
        
        if (!ttsService.isAvailable()) {
            log.warn("[AgentController] TTS 服务未配置");
            return ResponseEntity.status(503)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("TTS service not configured".getBytes());
        }
        
        byte[] audio = ttsService.synthesize(request.text());
        
        if (audio.length == 0) {
            return ResponseEntity.noContent().build();
        }
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "audio/mpeg")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(audio.length))
                .body(audio);
    }

    /**
     * 检查 Agent 服务状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "agent", "available",
                "tts", ttsService.isAvailable() ? "available" : "not_configured"
        ));
    }

    // Request/Response Records
    public record ChatRequest(String sessionId, String message) {}
    public record ChatResponse(String sessionId, String message) {}
    public record TtsRequest(String text) {}
}
