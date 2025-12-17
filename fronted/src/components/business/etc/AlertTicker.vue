<template>
  <div class="alert-ticker">
    <div class="ticker-header">
      <span class="ticker-title">
        <el-icon><Warning /></el-icon>
        实时告警
      </span>
      <el-badge :value="alerts.length" type="danger" />
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
          <span class="alert-plate" v-if="alert.plate">{{ alert.plate }}</span>
        </li>
      </TransitionGroup>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { Warning } from '@element-plus/icons-vue'

defineOptions({ name: 'AlertTicker' })

interface AlertItem {
  id: string
  type: 'overspeed' | 'duplicate' | 'illegal' | 'dispatch'
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

const tickerRef = ref<HTMLDivElement>()

// 告警类型标签
const alertTypeLabels: Record<string, string> = {
  overspeed: '超速',
  duplicate: '套牌',
  illegal: '违法',
  dispatch: '出警'
}

// 关键词高亮
const highlightKeywords = (text: string) => {
  const keywords = ['超速', '套牌', '违法', '出警', '重复', '异常']
  let result = text
  keywords.forEach((keyword) => {
    const regex = new RegExp(keyword, 'g')
    result = result.replace(regex, `<span class="highlight">${keyword}</span>`)
  })
  return result
}

// 显示的告警列表（限制数量）
const displayAlerts = computed(() => {
  return props.alerts.slice(0, props.maxDisplay)
})

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
