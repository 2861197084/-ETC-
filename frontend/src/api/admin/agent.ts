/**
 * 智能助手 Agent API
 */
import { http } from '@/utils/http'

/**
 * 发送消息（非流式）
 */
export function sendMessage(sessionId: string | null, message: string) {
  return http.post<{
    sessionId: string
    message: string
  }>('/api/agent/chat', {
    sessionId,
    message
  })
}

/**
 * 发送消息（流式 SSE）
 * 返回 EventSource 或 ReadableStream
 */
export async function sendMessageStream(
  sessionId: string | null,
  message: string,
  onChunk: (chunk: string) => void,
  onComplete: () => void,
  onError: (error: Error) => void
): Promise<() => void> {
  // 使用相对路径，通过 Vite 代理转发
  const url = '/api/agent/chat/stream'
  
  const controller = new AbortController()
  
  try {
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream'
      },
      body: JSON.stringify({ sessionId, message }),
      signal: controller.signal
    })

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    const reader = response.body?.getReader()
    if (!reader) {
      throw new Error('No response body')
    }

    const decoder = new TextDecoder()
    let buffer = ''

    const processStream = async () => {
      while (true) {
        const { done, value } = await reader.read()
        
        if (done) {
          onComplete()
          break
        }

        buffer += decoder.decode(value, { stream: true })
        
        // 处理 SSE 数据
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''
        
        for (const line of lines) {
          if (line.startsWith('data: ')) {
            const data = line.slice(6)
            // 还原换行符
            const content = data.replace(/\\n/g, '\n')
            onChunk(content)
          }
        }
      }
    }

    processStream().catch(onError)

    // 返回取消函数
    return () => {
      controller.abort()
    }
  } catch (error) {
    onError(error as Error)
    return () => {}
  }
}

/**
 * 清除会话
 */
export function clearSession(sessionId: string) {
  return http.delete(`/api/agent/session/${sessionId}`)
}

/**
 * 获取会话历史
 */
export function getSessionHistory(sessionId: string) {
  return http.get<Array<{ role: string; content: string }>>(`/api/agent/session/${sessionId}/history`)
}

/**
 * 语音合成
 * 返回音频 Blob
 */
export async function synthesizeSpeech(text: string): Promise<Blob | null> {
  // 使用相对路径，通过 Vite 代理转发
  const url = '/api/agent/tts'
  
  try {
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ text })
    })

    if (!response.ok) {
      if (response.status === 503) {
        console.warn('[TTS] 服务未配置')
        return null
      }
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    if (response.status === 204) {
      return null
    }

    return await response.blob()
  } catch (error) {
    console.error('[TTS] 请求失败:', error)
    return null
  }
}

/**
 * 获取 Agent 服务状态
 */
export function getAgentStatus() {
  return http.get<{
    agent: 'available' | 'unavailable'
    tts: 'available' | 'not_configured'
  }>('/api/agent/status')
}
