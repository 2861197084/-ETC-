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
              <span class="header-status">在线</span>
            </div>
          </div>
          <div class="header-actions">
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
              <span class="message-time">{{ message.time }}</span>
              <!-- 卡片类型消息 -->
              <div v-if="message.card" class="message-card">
                <component :is="message.card.component" v-bind="message.card.props" />
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
import { ref, nextTick, markRaw } from 'vue'
import {
  ChatDotRound,
  Close,
  Service,
  User,
  Delete,
  Promotion
} from '@element-plus/icons-vue'

defineOptions({ name: 'AgentCopilot' })

interface Message {
  id: string
  role: 'user' | 'assistant'
  content: string
  time: string
  card?: {
    component: any
    props: Record<string, any>
  }
}

const isOpen = ref(false)
const isLoading = ref(false)
const inputText = ref('')
const unreadCount = ref(0)
const messagesRef = ref<HTMLDivElement>()

const messages = ref<Message[]>([
  {
    id: '1',
    role: 'assistant',
    content: '您好！我是 ETC 智能助手，可以帮您查询路况、分析数据、规划路径。请问有什么可以帮您？',
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

// 模拟 AI 回复
const mockResponses: Record<string, string> = {
  '查询当前路况': `当前高速路况概况：\n\n🟢 **畅通路段**：京哈高速、京承高速\n🟡 **缓行路段**：京沪高速（大羊坊-马驹桥段）\n🔴 **拥堵路段**：京藏高速（北沙滩-回龙观段）\n\n建议避开拥堵路段，选择京承高速出行。`,
  '查询今日车流统计': `📊 **今日车流统计**（截至当前）\n\n- 总通行量：**128,456** 辆\n- 本地车辆：**89,120** 辆（69.4%）\n- 外地车辆：**39,336** 辆（30.6%）\n- 高峰时段：08:00-09:00\n- 平均车速：**92.3** km/h`,
  '查询今日异常告警': `⚠️ **今日异常告警汇总**\n\n- 超速告警：**23** 起\n- 套牌车辆：**5** 起\n- 逃费嫌疑：**12** 起\n- 已出警处理：**18** 起\n\n最近一条：京A·88888 于 14:32 在京沪高速超速（152km/h）`
}

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

function clearMessages() {
  messages.value = [
    {
      id: generateId(),
      role: 'assistant',
      content: '对话已清空，请问有什么可以帮您？',
      time: formatTime(new Date())
    }
  ]
}

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

  // 模拟 AI 思考
  isLoading.value = true
  await new Promise((resolve) => setTimeout(resolve, 1000 + Math.random() * 1000))
  isLoading.value = false

  // 生成回复
  let response = mockResponses[query]
  if (!response) {
    // 默认回复
    if (query.includes('路线') || query.includes('规划')) {
      response = `🗺️ **路径规划结果**\n\n为您规划的最优路线：\n\n1. 从起点出发，沿京沪高速行驶\n2. 途经廊坊收费站\n3. 预计行程时间：**1小时25分钟**\n4. 预计过路费：**￥85**\n\n当前路况良好，建议立即出发。`
    } else if (query.includes('收费') || query.includes('费用')) {
      response = `💰 **收费查询**\n\n根据您的行程：\n- 小型车（1类）：￥85\n- 中型车（2类）：￥120\n- 大型车（3类）：￥180\n\n支持 ETC 快捷缴费，享受95折优惠。`
    } else {
      response = `好的，我理解您的问题是关于"${query}"。\n\n正在为您查询相关信息，请稍候...\n\n如果您需要更具体的帮助，可以尝试以下方式提问：\n- 查询某条高速的实时路况\n- 规划从A地到B地的路线\n- 查询今日的车流统计数据`
    }
  }

  const assistantMessage: Message = {
    id: generateId(),
    role: 'assistant',
    content: response,
    time: formatTime(new Date())
  }

  messages.value.push(assistantMessage)

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
  height: 560px;
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
      }
    }
  }

  .header-actions {
    :deep(.el-button) {
      background: rgba(255, 255, 255, 0.2);
      border-color: transparent;
      color: #fff;

      &:hover {
        background: rgba(255, 255, 255, 0.3);
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

    .message-time {
      text-align: right;
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

.message-time {
  display: block;
  margin-top: 4px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.message-card {
  margin-top: 8px;
  border-radius: 8px;
  overflow: hidden;
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
