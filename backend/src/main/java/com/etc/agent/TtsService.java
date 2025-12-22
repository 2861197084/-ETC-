package com.etc.agent;

import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;

/**
 * 语音合成服务 - 基于 DashScope CosyVoice
 */
@Slf4j
@Service
public class TtsService {

    @Value("${dashscope.api-key:}")
    private String apiKey;

    @Value("${dashscope.tts.model:cosyvoice-v1}")
    private String model;

    @Value("${dashscope.tts.voice:longxiaochun}")
    private String voice;

    /**
     * 合成语音
     * @param text 要合成的文本
     * @return 音频数据（MP3格式）
     */
    public byte[] synthesize(String text) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[TTS] API Key 未配置，跳过语音合成");
            return new byte[0];
        }

        if (text == null || text.isBlank()) {
            return new byte[0];
        }

        // 清理文本中的 Markdown 格式
        String cleanText = cleanMarkdown(text);
        
        // 限制文本长度
        if (cleanText.length() > 500) {
            cleanText = cleanText.substring(0, 500) + "。后续内容请查看文字显示。";
        }

        log.info("[TTS] 开始合成语音, text={}", cleanText.substring(0, Math.min(50, cleanText.length())));

        try {
            SpeechSynthesisParam param = SpeechSynthesisParam.builder()
                    .apiKey(apiKey)
                    .model(model)
                    .voice(voice)
                    .build();

            SpeechSynthesizer synthesizer = new SpeechSynthesizer(param, null);
            ByteBuffer audio = synthesizer.call(cleanText);
            
            byte[] audioBytes = new byte[audio.remaining()];
            audio.get(audioBytes);
            
            log.info("[TTS] 语音合成完成, audioSize={} bytes", audioBytes.length);
            return audioBytes;

        } catch (Exception e) {
            log.error("[TTS] 语音合成失败", e);
            return new byte[0];
        }
    }

    /**
     * 清理 Markdown 格式，使文本更适合语音播报
     */
    private String cleanMarkdown(String text) {
        return text
                // 移除 Markdown 标题
                .replaceAll("#{1,6}\\s*", "")
                // 移除粗体
                .replaceAll("\\*\\*(.*?)\\*\\*", "$1")
                // 移除斜体
                .replaceAll("\\*(.*?)\\*", "$1")
                // 移除代码块
                .replaceAll("`{1,3}[^`]*`{1,3}", "")
                // 移除链接
                .replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1")
                // 移除表情符号（保留一部分常用的）
                .replaceAll("[📊📍🚗⚠️✅❌💡📋🔖📛🏢🏷️📝💰🗺️⏱️📏🚀⚖️🔴🟠🟡🟢⚪📈📉➡️⏰═]", "")
                // 移除多余空白
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * 检查 TTS 服务是否可用
     */
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }
}
