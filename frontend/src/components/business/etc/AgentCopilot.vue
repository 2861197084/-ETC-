<template>
  <div class="agent-copilot">
    <!-- 悬浮球 -->
    <div class="floating-ball" :class="{ active: isOpen }" @click="toggleChat">
      <el-icon v-if="!isOpen" :size="24"><ChatDotRound /></el-icon>
      <el-icon v-else :size="24"><Close /></el-icon>
      <span v-if="unreadCount > 0" class="unread-badge">{{ unreadCount }}</span>
    </div>

    <!-- 对话窗口 -->
    <Transition name="chat-window">
      <div v-if="isOpen" class="chat-window">
        <div class="chat-header">
          <div class="header-info">
            <el-avatar :size="32" :icon="Service" />
            <div class="header-text">
              <span class="header-title">ETC 智能助手</span>
              <span class="header-status" :class="{ online: agentStatus.agent === 'available' }">
                {{ agentStatus.agent === 'available' ? '在线' : '离线' }}
              </span>
            </div>
          </div>
          <div class="header-actions">
            <el-tooltip :content="voiceEnabled ? '关闭语音' : '开启语音'">
              <el-button 
                :icon="voiceEnabled ? Microphone : Mute" 
                circle 
                size="small" 
                @click="toggleVoice"
                :type="voiceEnabled ? 'primary' : 'default'"
              />
            </el-tooltip>
            <el-tooltip content="清空对话">
              <el-button :icon="Delete" circle size="small" @click="clearMessages" />
            </el-tooltip>
          </div>
        </div>

        <div class="chat-messages" ref="messagesRef">
          <div
            v-for="message in messages"
            :key="message.id"
            class="message-item"
            :class="message.role"
          >
            <el-avatar
              v-if="message.role === 'assistant'"
              :size="32"
              :icon="Service"
              class="message-avatar"
            />
            <div class="message-content">
              <div class="message-bubble" v-html="renderMessage(message.content)"></div>
              <div class="message-footer">
                <span class="message-time">{{ message.time }}</span>
                <!-- 语音播放按钮 -->
                <el-button
                  v-if="message.role === 'assistant' && voiceEnabled && agentStatus.tts === 'available'"
                  :icon="isPlayingMessage === message.id ? VideoPause : VideoPlay"
                  size="small"
                  circle
                  class="voice-btn"
                  @click="togglePlayMessage(message)"
                  :loading="isSynthesizing === message.id"
                />
              </div>
            </div>
            <el-avatar
              v-if="message.role === 'user'"
              :size="32"
              :icon="User"
              class="message-avatar"
            />
          </div>

          <!-- 加载动画 -->
          <div v-if="isLoading" class="message-item assistant">
            <el-avatar :size="32" :icon="Service" class="message-avatar" />
            <div class="message-content">
              <div class="message-bubble loading">
                <span class="dot"></span>
                <span class="dot"></span>
                <span class="dot"></span>
              </div>
            </div>
          </div>
        </div>

        <div class="chat-input">
          <div class="quick-actions">
            <el-tag
              v-for="action in quickActions"
              :key="action.text"
              size="small"
              type="info"
              effect="plain"
              @click="sendQuickAction(action.text)"
            >
              {{ action.label }}
            </el-tag>
          </div>
          <div class="input-wrapper">
            <el-input
              v-model="inputText"
              placeholder="输入问题，按 Enter 发送..."
              @keyup.enter="sendMessage"
              :disabled="isLoading"
            >
              <template #append>
                <el-button :icon="Promotion" @click="sendMessage" :disabled="isLoading" />
              </template>
            </el-input>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted, onUnmounted, watch } from 'vue'
import {
  ChatDotRound,
  Close,
  Service,
  User,
  Delete,
  Promotion,
  Microphone,
  Mute,
  VideoPlay,
  VideoPause
} from '@element-plus/icons-vue'
import { sendMessageStream, synthesizeSpeech, getAgentStatus, clearSession } from '@/api/admin/agent'
import { getTtsPlayer } from '@/utils/tts'

defineOptions({ name: 'AgentCopilot' })

interface Message {
  id: string
  role: 'user' | 'assistant'
  content: string
  time: string
}

const isOpen = ref(false)
const isLoading = ref(false)
const inputText = ref('')
const unreadCount = ref(0)
const messagesRef = ref<HTMLDivElement>()

// 会话管理
const sessionId = ref<string>(generateId())

// Agent 状态
const agentStatus = ref<{ agent: string; tts: string }>({ agent: 'unavailable', tts: 'not_configured' })

// 语音相关
const voiceEnabled = ref(true)
const isPlayingMessage = ref<string | null>(null)
const isSynthesizing = ref<string | null>(null)
const ttsPlayer = getTtsPlayer()

// 消息列表
const messages = ref<Message[]>([
  {
    id: '1',
    role: 'assistant',
    content: '您好！我是 ETC 智能交警助手，可以帮您查询路况、分析数据、规划路径。请问有什么可以帮您？',
    time: formatTime(new Date())
  }
])

// 快捷操作
const quickActions = [
  { label: '🚗 当前路况', text: '查询当前路况' },
  { label: '📊 今日统计', text: '查询今日车流统计' },
  { label: '🗺️ 路径规划', text: '帮我规划从北京到天津的路线' },
  { label: '⚠️ 异常告警', text: '查询今日异常告警' }
]

function formatTime(date: Date): string {
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

function generateId(): string {
  return Math.random().toString(36).substring(2, 9)
}

function renderMessage(content: string): string {
  // 简单的 Markdown 渲染
  return content
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\n/g, '<br>')
}

function toggleChat() {
  isOpen.value = !isOpen.value
  if (isOpen.value) {
    unreadCount.value = 0
  }
}

function toggleVoice() {
  voiceEnabled.value = !voiceEnabled.value
  if (!voiceEnabled.value) {
    stopPlaying()
  }
}

async function clearMessages() {
  // 清除后端会话
  try {
    await clearSession(sessionId.value)
  } catch (e) {
    // 忽略
  }
  
  // 重置会话
  sessionId.value = generateId()
  messages.value = [
    {
      id: generateId(),
      role: 'assistant',
      content: '对话已清空，请问有什么可以帮您？',
      time: formatTime(new Date())
    }
  ]
}

// 流式对话取消函数
let cancelStream: (() => void) | null = null

async function sendMessage() {
  if (!inputText.value.trim() || isLoading.value) return

  const userMessage: Message = {
    id: generateId(),
    role: 'user',
    content: inputText.value,
    time: formatTime(new Date())
  }

  messages.value.push(userMessage)
  const query = inputText.value
  inputText.value = ''

  await scrollToBottom()

  // 开始加载
  isLoading.value = true

  // 创建助手消息占位
  const assistantMessage: Message = {
    id: generateId(),
    role: 'assistant',
    content: '',
    time: formatTime(new Date())
  }
  messages.value.push(assistantMessage)

  try {
    // 使用流式 API
    cancelStream = await sendMessageStream(
      sessionId.value,
      query,
      // onChunk
      (chunk: string) => {
        assistantMessage.content += chunk
        scrollToBottom()
      },
      // onComplete
      async () => {
        isLoading.value = false
        cancelStream = null
        
        // 自动播放语音
        if (voiceEnabled.value && agentStatus.value.tts === 'available') {
          await playMessageVoice(assistantMessage)
        }
      },
      // onError
      (error: Error) => {
        console.error('[Agent] 对话失败:', error)
        assistantMessage.content = '抱歉，处理您的请求时出现错误，请稍后重试。'
        isLoading.value = false
        cancelStream = null
      }
    )
  } catch (error) {
    console.error('[Agent] 发送消息失败:', error)
    assistantMessage.content = '抱歉，连接服务失败，请检查网络后重试。'
    isLoading.value = false
  }

  if (!isOpen.value) {
    unreadCount.value++
  }

  await scrollToBottom()
}

function sendQuickAction(text: string) {
  inputText.value = text
  sendMessage()
}

async function scrollToBottom() {
  await nextTick()
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

// 语音播放
async function playMessageVoice(message: Message) {
  if (isSynthesizing.value || isPlayingMessage.value) return
  
  isSynthesizing.value = message.id
  
  try {
    const audioBlob = await synthesizeSpeech(message.content)
    if (!audioBlob) {
      console.warn('[TTS] 无音频数据')
      return
    }

    isPlayingMessage.value = message.id
    
    ttsPlayer.setOnPlayStateChange((playing) => {
      if (!playing) {
        isPlayingMessage.value = null
      }
    })

    await ttsPlayer.play(audioBlob)
  } catch (error) {
    console.error('[TTS] 播放失败:', error)
  } finally {
    isSynthesizing.value = null
  }
}

function togglePlayMessage(message: Message) {
  if (isPlayingMessage.value === message.id) {
    stopPlaying()
  } else {
    playMessageVoice(message)
  }
}

function stopPlaying() {
  ttsPlayer.stop()
  isPlayingMessage.value = null
}

// 获取 Agent 状态
async function fetchAgentStatus() {
  try {
    const res = await getAgentStatus()
    agentStatus.value = res.data
  } catch (error) {
    console.warn('[Agent] 获取状态失败')
  }
}

// 监听窗口打开
watch(isOpen, (newVal) => {
  if (newVal) {
    fetchAgentStatus()
  }
})

onMounted(() => {
  fetchAgentStatus()
  
  // 设置 TTS 播放状态回调
  ttsPlayer.setOnPlayStateChange((playing) => {
    if (!playing) {
      isPlayingMessage.value = null
    }
  })
})

onUnmounted(() => {
  // 清理资源
  if (cancelStream) {
    cancelStream()
  }
  ttsPlayer.stop()
})
</script>

<style lang="scss" scoped>
.agent-copilot {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 9999;
}

.floating-ball {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  cursor: pointer;
  box-shadow: 0 4px 20px rgba(102, 126, 234, 0.4);
  transition: all 0.3s ease;
  position: relative;

  &:hover {
    transform: scale(1.1);
    box-shadow: 0 6px 25px rgba(102, 126, 234, 0.5);
  }

  &.active {
    background: linear-gradient(135deg, #ff6b6b 0%, #ee5a5a 100%);
    box-shadow: 0 4px 20px rgba(255, 107, 107, 0.4);
  }

  .unread-badge {
    position: absolute;
    top: -4px;
    right: -4px;
    min-width: 20px;
    height: 20px;
    padding: 0 6px;
    background: #ff4d4f;
    border-radius: 10px;
    font-size: 12px;
    font-weight: 600;
    display: flex;
    align-items: center;
    justify-content: center;
  }
}

.chat-window {
  position: absolute;
  right: 0;
  bottom: 72px;
  width: 400px;
  height: 680px;
  background: var(--el-bg-color);
  border-radius: 16px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--el-border-color-light);
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;

  .header-info {
    display: flex;
    align-items: center;
    gap: 12px;

    .header-text {
      display: flex;
      flex-direction: column;

      .header-title {
        font-weight: 600;
        font-size: 15px;
      }

      .header-status {
        font-size: 12px;
        opacity: 0.8;
        
        &.online {
          color: #67c23a;
        }
      }
    }
  }

  .header-actions {
    display: flex;
    gap: 8px;
    
    :deep(.el-button) {
      background: rgba(255, 255, 255, 0.2);
      border-color: transparent;
      color: #fff;

      &:hover {
        background: rgba(255, 255, 255, 0.3);
      }
      
      &.el-button--primary {
        background: rgba(103, 194, 58, 0.6);
      }
    }
  }
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.message-item {
  display: flex;
  gap: 8px;

  &.user {
    flex-direction: row-reverse;

    .message-bubble {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: #fff;
      border-radius: 16px 16px 4px 16px;
    }

    .message-footer {
      justify-content: flex-end;
    }
  }

  &.assistant {
    .message-bubble {
      background: var(--el-fill-color-light);
      color: var(--el-text-color-primary);
      border-radius: 16px 16px 16px 4px;
    }
  }
}

.message-content {
  max-width: 75%;
}

.message-bubble {
  padding: 12px 16px;
  font-size: 14px;
  line-height: 1.6;

  &.loading {
    display: flex;
    gap: 4px;
    padding: 16px;

    .dot {
      width: 8px;
      height: 8px;
      background: var(--el-text-color-secondary);
      border-radius: 50%;
      animation: loading-bounce 1.4s infinite ease-in-out both;

      &:nth-child(1) {
        animation-delay: -0.32s;
      }
      &:nth-child(2) {
        animation-delay: -0.16s;
      }
    }
  }
}

@keyframes loading-bounce {
  0%,
  80%,
  100% {
    transform: scale(0);
  }
  40% {
    transform: scale(1);
  }
}

.message-footer {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
}

.message-time {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.voice-btn {
  padding: 4px;
  height: 20px;
  width: 20px;
  
  :deep(.el-icon) {
    font-size: 12px;
  }
}

.chat-input {
  padding: 12px 16px;
  border-top: 1px solid var(--el-border-color-light);
  background: var(--el-fill-color-lighter);

  .quick-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-bottom: 12px;

    :deep(.el-tag) {
      cursor: pointer;
      transition: all 0.3s;

      &:hover {
        background: var(--el-color-primary-light-9);
        border-color: var(--el-color-primary);
        color: var(--el-color-primary);
      }
    }
  }

  .input-wrapper {
    :deep(.el-input-group__append) {
      padding: 0;

      .el-button {
        border: none;
        background: var(--el-color-primary);
        color: #fff;
        border-radius: 0;
        padding: 0 16px;

        &:hover {
          background: var(--el-color-primary-dark-2);
        }
      }
    }
  }
}

// 窗口动画
.chat-window-enter-active,
.chat-window-leave-active {
  transition: all 0.3s ease;
}

.chat-window-enter-from,
.chat-window-leave-to {
  opacity: 0;
  transform: translateY(20px) scale(0.95);
}
</style>
