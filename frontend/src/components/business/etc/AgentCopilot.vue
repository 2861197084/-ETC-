<template>
  <div class="agent-copilot">
    <!-- 悬浮球 -->
    <div class="floating-ball" :class="{ active: isOpen }" @click="toggleChat">
      <el-icon v-if="!isOpen" :size="30" color="#fff"><ChatDotRound /></el-icon>
      <el-icon v-else :size="30" color="#fff"><Close /></el-icon>
      <span v-if="unreadCount > 0" class="unread-badge">{{ unreadCount }}</span>
    </div>

    <!-- 对话窗口 -->
    <Transition name="chat-window">
      <div v-if="isOpen" class="chat-window">
        <!-- 微信风格头部 -->
        <div class="wechat-header">
          <div class="header-title">
            <span>ETC 智能助手</span>
            <span class="status-dot" :class="{ online: agentStatus.agent === 'available' }"></span>
          </div>
          <div class="header-actions">
             <el-dropdown trigger="click" @command="handleCommand" popper-class="agent-dropdown">
              <el-icon :size="20" class="action-icon"><MoreFilled /></el-icon>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="clear" :icon="Delete">清空对话</el-dropdown-item>
                  <el-dropdown-item command="voice" :icon="voiceEnabled ? Microphone : Mute">
                    {{ voiceEnabled ? '关闭语音' : '开启语音' }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
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
              :size="40"
              shape="square"
              :src="assistantAvatar"
              class="message-avatar"
            >
              <el-icon><Service /></el-icon>
            </el-avatar>
            
            <div class="message-content">
              <!-- 昵称/时间 (可选，微信通常不显示每条的时间，只显示间隔) -->
              <!-- <div class="message-info" v-if="shouldShowTime(message)">{{ message.time }}</div> -->
              
              <div class="bubble-wrapper">
                <div class="message-bubble">
                  <div class="markdown-body" v-html="renderMessage(message.content)"></div>
                </div>
                
                 <!-- 语音播放按钮 (悬浮或内置) -->
                <div 
                  v-if="message.role === 'assistant' && voiceEnabled && agentStatus.tts === 'available'"
                  class="voice-indicator"
                  @click.stop="togglePlayMessage(message)"
                >
                  <el-icon :class="{ 'is-playing': isPlayingMessage === message.id }">
                    <component :is="isPlayingMessage === message.id ? VideoPause : VideoPlay" />
                  </el-icon>
                </div>
              </div>
            </div>

            <el-avatar
              v-if="message.role === 'user'"
              :size="40"
              shape="square"
              :src="userAvatar"
              class="message-avatar"
            >
              <el-icon><User /></el-icon>
            </el-avatar>
          </div>

          <!-- 加载动画 -->
          <div v-if="isLoading" class="message-item assistant">
            <el-avatar :size="40" shape="square" :src="assistantAvatar" class="message-avatar">
              <el-icon><Service /></el-icon>
            </el-avatar>
            <div class="message-content">
              <div class="message-bubble loading">
                <span class="dot"></span>
                <span class="dot"></span>
                <span class="dot"></span>
              </div>
            </div>
          </div>
        </div>

        <!-- 底部区域 -->
        <div class="wechat-footer">
          <!-- 快捷操作栏 -->
          <div class="quick-actions" v-if="quickActions.length">
            <div 
              v-for="action in quickActions"
              :key="action.text"
              class="action-tag"
              @click="sendQuickAction(action.text)"
            >
              {{ action.label }}
            </div>
          </div>

          <div class="input-area">
            <div class="tool-btn" @click="toggleVoice">
              <el-icon :size="26" :color="voiceEnabled ? '#07C160' : '#7f7f7f'"><Microphone /></el-icon>
            </div>
            
            <div class="input-wrapper">
              <textarea
                v-model="inputText"
                class="chat-input-field"
                rows="1"
                @keyup.enter.prevent="sendMessage"
                placeholder=""
              ></textarea>
            </div>

            <div class="tool-btn" v-if="!inputText">
              <el-icon :size="26" color="#7f7f7f"><CirclePlus /></el-icon>
            </div>
            
            <div class="send-btn" v-else @click="sendMessage">
              <el-button type="success" size="small">发送</el-button>
            </div>
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
  VideoPause,
  MoreFilled,
  CirclePlus
} from '@element-plus/icons-vue'
import { sendMessageStream, synthesizeSpeech, getAgentStatus, clearSession } from '@/api/admin/agent'
import { getTtsPlayer } from '@/utils/tts'
import MarkdownIt from 'markdown-it'
// @ts-ignore
import assistantAvatarImg from '@/assets/images/avatar/avatar10.webp'
// @ts-ignore
import userAvatarImg from '@/assets/images/avatar/avatar5.webp'

defineOptions({ name: 'AgentCopilot' })

// 图片资源
const assistantAvatar = assistantAvatarImg
const userAvatar = userAvatarImg

// Markdown 配置
const md = new MarkdownIt({
  html: true,
  linkify: true,
  breaks: false, // 关闭软换行转 <br>，避免文本异常断行
  typographer: true
})

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
  { label: '⚠️ 异常告警', text: '查询今日异常告警' },
  { label: '🗺️ 路径规划', text: '帮我规划从北京到天津的路线' }
]

function formatTime(date: Date): string {
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

function generateId(): string {
  return Math.random().toString(36).substring(2, 9)
}

function renderMessage(content: string): string {
  if (!content) return ''
  // 1. 移除 [DONE] 标记
  let cleanContent = content.replace(/\[DONE\]/g, '').trim()
  
  // 2. 修复 Markdown 粗体语法 (移除 ** 内部的空格，解决无法渲染粗体的问题)
  cleanContent = cleanContent.replace(/\*\*\s+(.*?)\s+\*\*/g, '**$1**')
  cleanContent = cleanContent.replace(/\*\*\s+(.*?)\*\*/g, '**$1**')
  cleanContent = cleanContent.replace(/\*\*(.*?)\s+\*\*/g, '**$1**')

  // 3. 尝试合并被错误截断的行 (简单的启发式：如果一行结尾不是标点符号，则可能是不正常的换行)
  // 注意：这可能会误伤，但在 Agent 输出不规范时很有用
  cleanContent = cleanContent.replace(/([^\n。！？：；…])\n+([^\n])/g, '$1 $2')

  return md.render(cleanContent)
}

function toggleChat() {
  isOpen.value = !isOpen.value
  if (isOpen.value) {
    unreadCount.value = 0
    scrollToBottom()
  }
}

function handleCommand(command: string) {
  if (command === 'clear') {
    clearMessages()
  } else if (command === 'voice') {
    toggleVoice()
  }
}

function toggleVoice() {
  voiceEnabled.value = !voiceEnabled.value
  if (!voiceEnabled.value) {
    stopPlaying()
  }
}

async function clearMessages() {
  try {
    await clearSession(sessionId.value)
  } catch (e) {
    // 忽略
  }
  
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
    cancelStream = await sendMessageStream(
      sessionId.value,
      query,
      (chunk: string) => {
        assistantMessage.content += chunk
        scrollToBottom()
      },
      async () => {
        isLoading.value = false
        cancelStream = null
        if (voiceEnabled.value && agentStatus.value.tts === 'available') {
          await playMessageVoice(assistantMessage)
        }
      },
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

async function playMessageVoice(message: Message) {
  if (isSynthesizing.value || isPlayingMessage.value) return
  isSynthesizing.value = message.id
  try {
    const audioBlob = await synthesizeSpeech(message.content)
    if (!audioBlob) return
    isPlayingMessage.value = message.id
    ttsPlayer.setOnPlayStateChange((playing) => {
      if (!playing) isPlayingMessage.value = null
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

async function fetchAgentStatus() {
  try {
    const res = await getAgentStatus()
    agentStatus.value = res.data
  } catch (error) {
    console.warn('[Agent] 获取状态失败')
  }
}

watch(isOpen, (newVal) => {
  if (newVal) {
    fetchAgentStatus()
  }
})

onMounted(() => {
  fetchAgentStatus()
  ttsPlayer.setOnPlayStateChange((playing) => {
    if (!playing) isPlayingMessage.value = null
  })
})

onUnmounted(() => {
  if (cancelStream) cancelStream()
  ttsPlayer.stop()
})
</script>

<style lang="scss">
.agent-dropdown {
  z-index: 10002 !important;
}

/* Markdown Styles */
.markdown-body {
  font-size: 15px;
  line-height: 1.6;
  color: inherit;
  
  p {
    margin: 8px 0;
    white-space: normal; /* 恢复正常换行 */
    &:first-child { margin-top: 0; }
    &:last-child { margin-bottom: 0; }
  }

  ul, ol {
    padding-left: 20px;
    margin: 4px 0;
  }
  
  li {
    margin: 2px 0;
  }

  h1, h2, h3, h4, h5, h6 {
    margin: 8px 0 4px;
    font-weight: 600;
    line-height: 1.4;
  }
  
  h1 { font-size: 1.4em; }
  h2 { font-size: 1.25em; }
  h3 { font-size: 1.1em; }

  code {
    background: rgba(0,0,0,0.06);
    padding: 2px 4px;
    border-radius: 3px;
    font-family: monospace;
    font-size: 0.9em;
  }
  
  pre {
    background: #f0f0f0;
    padding: 8px;
    border-radius: 6px;
    overflow-x: auto;
    margin: 8px 0;
    
    code {
      background: none;
      padding: 0;
    }
  }

  blockquote {
    margin: 8px 0;
    padding-left: 10px;
    border-left: 3px solid #ddd;
    color: #666;
  }
  
  a {
    color: #409eff;
    text-decoration: none;
    &:hover { text-decoration: underline; }
  }
  
  table {
    border-collapse: collapse;
    width: 100%;
    margin: 8px 0;
    
    th, td {
      border: 1px solid #ddd;
      padding: 6px;
      text-align: left;
    }
    
    th {
      background: #f5f5f5;
      font-weight: 600;
    }
  }
  
  img {
    max-width: 100%;
    border-radius: 4px;
  }
}
</style>

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
  background: #07C160; /* 微信绿 */
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(7, 193, 96, 0.4);
  transition: all 0.3s ease;
  position: relative;

  &:hover {
    transform: scale(1.05);
    box-shadow: 0 6px 16px rgba(7, 193, 96, 0.5);
  }

  &.active {
    background: #dedede;
    color: #333;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  }

  .unread-badge {
    position: absolute;
    top: -2px;
    right: -2px;
    min-width: 18px;
    height: 18px;
    padding: 0 5px;
    background: #fa5151;
    border-radius: 9px;
    font-size: 11px;
    font-weight: 600;
    color: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    border: 2px solid #fff;
  }
}

.chat-window {
  position: absolute;
  right: 0;
  bottom: 72px;
  width: 420px; /* 增加宽度 */
  height: 640px; /* 增加高度 */
  background: #f5f5f5; /* 微信背景灰 */
  border-radius: 12px;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid rgba(0,0,0,0.05);
}

.wechat-header {
  height: 50px;
  background: #ededed;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  border-bottom: 1px solid rgba(0,0,0,0.05);
  flex-shrink: 0;

  .header-title {
    font-size: 16px;
    font-weight: 500;
    color: #000;
    display: flex;
    align-items: center;
    gap: 6px;

    .status-dot {
      width: 6px;
      height: 6px;
      background: #ccc;
      border-radius: 50%;
      
      &.online {
        background: #07C160;
      }
    }
  }

  .action-icon {
    cursor: pointer;
    color: #333;
    &:hover {
      color: #000;
    }
  }
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  background: #f5f5f5;

  &::-webkit-scrollbar {
    width: 6px;
  }
  &::-webkit-scrollbar-thumb {
    background: rgba(0,0,0,0.1);
    border-radius: 3px;
  }
}

.message-item {
  display: flex;
  gap: 10px;
  align-items: flex-start;

  &.user {
    flex-direction: row-reverse;

    .message-bubble {
      background: #95EC69; /* 微信气泡绿 */
      color: #000;
      border-radius: 6px;
      position: relative;

      &::after {
        content: '';
        position: absolute;
        right: -6px;
        top: 14px;
        width: 0;
        height: 0;
        border-style: solid;
        border-width: 6px 0 6px 6px;
        border-color: transparent transparent transparent #95EC69;
      }
    }
  }

  &.assistant {
    .message-bubble {
      background: #fff;
      color: #000;
      border-radius: 6px;
      position: relative;

      &::after {
        content: '';
        position: absolute;
        left: -6px;
        top: 14px;
        width: 0;
        height: 0;
        border-style: solid;
        border-width: 6px 6px 6px 0;
        border-color: transparent #fff transparent transparent;
      }
    }
  }
  
  .message-avatar {
    flex-shrink: 0;
    background: #fff;
  }
}

.message-content {
  max-width: 85%;
  display: flex;
  flex-direction: column;
}

.bubble-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
}

.voice-indicator {
  cursor: pointer;
  color: #999;
  &:hover {
    color: #07C160;
  }
  .is-playing {
    color: #07C160;
    animation: pulse 1s infinite;
  }
}

@keyframes pulse {
  0% { opacity: 1; }
  50% { opacity: 0.5; }
  100% { opacity: 1; }
}

.message-bubble {
  padding: 10px 14px;
  font-size: 15px;
  line-height: 1.6;
  text-align: left; /* 回归左对齐 */
  overflow-wrap: break-word;
  word-wrap: break-word;
  box-shadow: 0 1px 2px rgba(0,0,0,0.05);
  min-height: 20px;
  max-width: 100%;
  
  &.loading {
    display: flex;
    gap: 4px;
    padding: 14px;

    .dot {
      width: 6px;
      height: 6px;
      background: #999;
      border-radius: 50%;
      animation: loading-bounce 1.4s infinite ease-in-out both;

      &:nth-child(1) { animation-delay: -0.32s; }
      &:nth-child(2) { animation-delay: -0.16s; }
    }
  }
}

.wechat-footer {
  background: #f7f7f7;
  border-top: 1px solid rgba(0,0,0,0.1);
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.quick-actions {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
  padding: 8px 12px;
  
  .action-tag {
    display: flex;
    align-items: center;
    justify-content: center;
    text-align: center;
    width: 100%;
    box-sizing: border-box;
    font-size: 13px;
    color: #555;
    background: #fff;
    padding: 8px 4px;
    border-radius: 6px;
    cursor: pointer;
    border: 1px solid #e0e0e0;
    transition: all 0.2s;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    
    &:hover {
      background: #f0f0f0;
      border-color: #d0d0d0;
    }
  }
}

.input-area {
  display: flex;
  align-items: flex-end; /* Align to bottom for multiline */
  gap: 10px;
  
  .tool-btn {
    width: 30px;
    height: 36px;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    flex-shrink: 0;
    
    &:hover {
      opacity: 0.8;
    }
  }
  
  .input-wrapper {
    flex: 1;
    background: #fff;
    border-radius: 4px;
    padding: 8px 10px;
    min-height: 36px;
    
    .chat-input-field {
      width: 100%;
      border: none;
      outline: none;
      resize: none;
      font-size: 15px;
      font-family: inherit;
      padding: 0;
      margin: 0;
      line-height: 20px;
      max-height: 80px;
      display: block;
    }
  }
  
  .send-btn {
    height: 36px;
    display: flex;
    align-items: center;
  }
}

/* 动画 */
.chat-window-enter-active,
.chat-window-leave-active {
  transition: all 0.3s cubic-bezier(0.19, 1, 0.22, 1);
}

.chat-window-enter-from,
.chat-window-leave-to {
  opacity: 0;
  transform: translateY(20px) scale(0.95);
}

@keyframes loading-bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}
</style>
