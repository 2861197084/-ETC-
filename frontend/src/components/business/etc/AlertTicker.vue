<template>
  <div class="alert-ticker">
    <div class="ticker-header">
      <span class="ticker-title">
        <el-icon><Warning /></el-icon>
        实时告警
      </span>
      <div class="header-actions">
        <el-badge :value="pendingCount" type="danger" :hidden="pendingCount === 0" />
        <el-button type="primary" link size="small" @click="showAllDialog = true">
          查看全部 ({{ alerts.length }})
        </el-button>
      </div>
    </div>
    <div class="ticker-content" ref="tickerRef">
      <TransitionGroup name="alert-list" tag="ul" class="alert-list">
        <li
          v-for="alert in displayAlerts"
          :key="alert.id"
          class="alert-item"
          :class="[`alert-${alert.type}`]"
        >
          <span class="alert-time">{{ alert.time }}</span>
          <span class="alert-type-tag" :class="alert.type">
            {{ alertTypeLabels[alert.type] }}
          </span>
          <span class="alert-message" v-html="highlightKeywords(alert.message)"></span>
          <span class="alert-plate" v-if="alert.plate && alert.type !== 'pressure'">{{ alert.plate }}</span>
          <el-button 
            type="success" 
            size="small" 
            link
            class="handle-btn"
            @click.stop="handleAlert(alert)"
          >
            已处理
          </el-button>
        </li>
      </TransitionGroup>
      <div v-if="displayAlerts.length === 0" class="empty-tip">
        暂无告警信息
      </div>
    </div>

    <!-- 查看全部弹窗 -->
    <el-dialog 
      v-model="showAllDialog" 
      title="告警历史" 
      width="700px"
      :close-on-click-modal="false"
    >
      <div class="dialog-filter">
        <el-radio-group v-model="dialogFilter" size="small">
          <el-radio-button value="all">全部</el-radio-button>
          <el-radio-button value="pending">待处理</el-radio-button>
          <el-radio-button value="handled">已处理</el-radio-button>
        </el-radio-group>
        <span class="filter-count">共 {{ filteredDialogAlerts.length }} 条</span>
      </div>
      <el-scrollbar max-height="400px">
        <ul class="dialog-alert-list">
          <li 
            v-for="alert in filteredDialogAlerts" 
            :key="alert.id" 
            class="dialog-alert-item"
            :class="{ handled: handledIds.has(alert.id) }"
          >
            <span class="alert-time">{{ alert.time }}</span>
            <span class="alert-type-tag" :class="alert.type">
              {{ alertTypeLabels[alert.type] }}
            </span>
            <span class="alert-message">{{ alert.message }}</span>
            <span class="alert-plate" v-if="alert.plate && alert.type !== 'pressure'">{{ alert.plate }}</span>
            <el-tag v-if="handledIds.has(alert.id)" type="success" size="small">已处理</el-tag>
            <el-button 
              v-else
              type="success" 
              size="small"
              @click="handleAlert(alert)"
            >
              标记已处理
            </el-button>
          </li>
          <li v-if="filteredDialogAlerts.length === 0" class="empty-item">
            暂无{{ dialogFilter === 'pending' ? '待处理' : dialogFilter === 'handled' ? '已处理' : '' }}告警
          </li>
        </ul>
      </el-scrollbar>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { Warning } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

defineOptions({ name: 'AlertTicker' })

interface AlertItem {
  id: string
  type: 'overspeed' | 'duplicate' | 'illegal' | 'dispatch' | 'pressure'
  message: string
  plate?: string
  time: string
  speed?: number
}

interface Props {
  alerts?: AlertItem[]
  maxDisplay?: number
  autoScroll?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  alerts: () => [],
  maxDisplay: 10,
  autoScroll: true
})

const emit = defineEmits<{
  (e: 'handle', alert: AlertItem): void
}>()

const tickerRef = ref<HTMLDivElement>()

// 已处理的告警ID集合（本地存储）
const handledIds = ref<Set<string>>(new Set())

// 弹窗控制
const showAllDialog = ref(false)
const dialogFilter = ref<'all' | 'pending' | 'handled'>('all')

// 告警类型标签
const alertTypeLabels: Record<string, string> = {
  overspeed: '超速',
  duplicate: '套牌',
  illegal: '违法',
  dispatch: '出警',
  pressure: '拥堵'
}

// 关键词高亮
const highlightKeywords = (text: string) => {
  const keywords = ['超速', '套牌', '违法', '出警', '重复', '异常', '车流量', '高峰', '拥堵', '分流']
  let result = text
  keywords.forEach((keyword) => {
    const regex = new RegExp(keyword, 'g')
    result = result.replace(regex, `<span class="highlight">${keyword}</span>`)
  })
  return result
}

// 显示的告警列表（排除已处理的，显示全部未处理）
const displayAlerts = computed(() => {
  return props.alerts.filter(a => !handledIds.value.has(a.id))
})

// 未处理告警总数
const pendingCount = computed(() => {
  return displayAlerts.value.length
})

// 弹窗中的过滤列表
const filteredDialogAlerts = computed(() => {
  if (dialogFilter.value === 'pending') {
    return props.alerts.filter(a => !handledIds.value.has(a.id))
  } else if (dialogFilter.value === 'handled') {
    return props.alerts.filter(a => handledIds.value.has(a.id))
  }
  return props.alerts
})

// 处理告警
const handleAlert = (alert: AlertItem) => {
  handledIds.value.add(alert.id)
  // 保存到本地存储
  saveHandledIds()
  ElMessage.success(`已处理: ${alert.type === 'pressure' ? alert.plate : alert.message}`)
  emit('handle', alert)
}

// 保存/加载已处理ID
const STORAGE_KEY = 'etc_handled_alerts'
const saveHandledIds = () => {
  const arr = Array.from(handledIds.value)
  // 只保留最近100条
  const recent = arr.slice(-100)
  localStorage.setItem(STORAGE_KEY, JSON.stringify(recent))
}
const loadHandledIds = () => {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved) {
      handledIds.value = new Set(JSON.parse(saved))
    }
  } catch {
    handledIds.value = new Set()
  }
}

// 自动滚动
let scrollTimer: number | null = null

const startAutoScroll = () => {
  if (!props.autoScroll || !tickerRef.value) return

  scrollTimer = window.setInterval(() => {
    if (tickerRef.value) {
      const { scrollTop, scrollHeight, clientHeight } = tickerRef.value
      if (scrollTop + clientHeight >= scrollHeight) {
        tickerRef.value.scrollTop = 0
      } else {
        tickerRef.value.scrollTop += 1
      }
    }
  }, 50)
}

const stopAutoScroll = () => {
  if (scrollTimer) {
    clearInterval(scrollTimer)
    scrollTimer = null
  }
}

onMounted(() => {
  loadHandledIds()
  startAutoScroll()
})

onUnmounted(() => {
  stopAutoScroll()
})
</script>

<style lang="scss" scoped>
.alert-ticker {
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  border: 1px solid #e8e8e8;

  .ticker-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 16px;
    background: #fff1f0;
    border-bottom: 1px solid #ffccc7;

    .ticker-title {
      display: flex;
      align-items: center;
      gap: 8px;
      color: #ff4d4f;
      font-weight: 600;
      font-size: 14px;
    }

    .header-actions {
      display: flex;
      align-items: center;
      gap: 12px;
    }
  }

  .ticker-content {
    max-height: 300px;
    overflow-y: auto;
    scrollbar-width: thin;
    scrollbar-color: #d9d9d9 transparent;

    &::-webkit-scrollbar {
      width: 4px;
    }

    &::-webkit-scrollbar-thumb {
      background: #d9d9d9;
      border-radius: 2px;
    }
  }

  .alert-list {
    list-style: none;
    margin: 0;
    padding: 0;
  }

  .alert-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 16px;
    border-bottom: 1px solid #f0f0f0;
    font-size: 13px;
    color: #1f2329;
    transition: background 0.3s;

    &:hover {
      background: #fafafa;
    }

    .alert-time {
      color: #8c8c8c;
      font-size: 12px;
      min-width: 50px;
    }

    .alert-type-tag {
      padding: 2px 8px;
      border-radius: 4px;
      font-size: 12px;
      font-weight: 500;

      &.overspeed {
        background: #fffbe6;
        color: #d48806;
      }

      &.duplicate {
        background: #fff1f0;
        color: #ff4d4f;
      }

      &.illegal {
        background: #fff1f0;
        color: #ff4d4f;
      }

      &.dispatch {
        background: #e6f7ff;
        color: #1890ff;
      }

      &.pressure {
        background: #fff2e8;
        color: #fa541c;
      }
    }

    .alert-message {
      flex: 1;

      :deep(.highlight) {
        color: #ff4d4f;
        font-weight: 600;
      }
    }

    .alert-plate {
      padding: 2px 8px;
      background: #f5f5f5;
      border-radius: 4px;
      font-family: 'Courier New', monospace;
      font-weight: 600;
      color: #1f2329;
    }

    .handle-btn {
      opacity: 0;
      transition: opacity 0.2s;
    }

    &:hover .handle-btn {
      opacity: 1;
    }
  }

  .empty-tip {
    padding: 24px;
    text-align: center;
    color: #8c8c8c;
    font-size: 13px;
  }
}

// 弹窗样式
.dialog-filter {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;

  .filter-count {
    color: #8c8c8c;
    font-size: 13px;
  }
}

.dialog-alert-list {
  list-style: none;
  margin: 0;
  padding: 0;

  .dialog-alert-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 12px;
    border-bottom: 1px solid #f0f0f0;
    font-size: 13px;

    &.handled {
      opacity: 0.6;
      background: #fafafa;
    }

    .alert-time {
      color: #8c8c8c;
      font-size: 12px;
      min-width: 50px;
    }

    .alert-type-tag {
      padding: 2px 8px;
      border-radius: 4px;
      font-size: 12px;
      font-weight: 500;

      &.overspeed { background: #fffbe6; color: #d48806; }
      &.duplicate { background: #fff1f0; color: #ff4d4f; }
      &.illegal { background: #fff1f0; color: #ff4d4f; }
      &.dispatch { background: #e6f7ff; color: #1890ff; }
      &.pressure { background: #fff2e8; color: #fa541c; }
    }

    .alert-message {
      flex: 1;
    }

    .alert-plate {
      padding: 2px 8px;
      background: #f5f5f5;
      border-radius: 4px;
      font-family: 'Courier New', monospace;
      font-weight: 600;
    }
  }

  .empty-item {
    padding: 24px;
    text-align: center;
    color: #8c8c8c;
  }
}

// 列表过渡动画
.alert-list-enter-active,
.alert-list-leave-active {
  transition: all 0.3s ease;
}

.alert-list-enter-from {
  opacity: 0;
  transform: translateX(-30px);
}

.alert-list-leave-to {
  opacity: 0;
  transform: translateX(30px);
}
</style>
