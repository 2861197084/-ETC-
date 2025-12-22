"""
TTS 服务 - 基于 DashScope CosyVoice
"""
import re
import dashscope
from dashscope.audio.tts_v2 import SpeechSynthesizer
from config import DASHSCOPE_API_KEY, TTS_MODEL, TTS_VOICE


def clean_text_for_tts(text: str) -> str:
    """清理文本，移除不适合语音播报的内容"""
    # 移除 Markdown 格式
    text = re.sub(r'\*\*(.+?)\*\*', r'\1', text)  # 粗体
    text = re.sub(r'\*(.+?)\*', r'\1', text)       # 斜体
    text = re.sub(r'`(.+?)`', r'\1', text)         # 代码
    text = re.sub(r'\[(.+?)\]\(.+?\)', r'\1', text)  # 链接
    text = re.sub(r'#{1,6}\s*', '', text)          # 标题
    text = re.sub(r'[-*+]\s+', '', text)           # 列表
    text = re.sub(r'\d+\.\s+', '', text)           # 有序列表
    
    # 移除多余空白
    text = re.sub(r'\n{2,}', '。', text)
    text = re.sub(r'\n', '，', text)
    text = re.sub(r'\s+', ' ', text)
    
    # 限制长度
    if len(text) > 500:
        text = text[:500] + "..."
    
    return text.strip()


def synthesize_speech(text: str) -> bytes:
    """合成语音，返回音频数据"""
    dashscope.api_key = DASHSCOPE_API_KEY
    
    clean_text = clean_text_for_tts(text)
    if not clean_text:
        return b""
    
    synthesizer = SpeechSynthesizer(model=TTS_MODEL, voice=TTS_VOICE)
    audio = synthesizer.call(clean_text)
    
    return audio


def is_tts_available() -> bool:
    """检查 TTS 是否可用"""
    return bool(DASHSCOPE_API_KEY)
