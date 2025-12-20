<template>
  <div class="progressive-table">
    <!-- 数据来源提示 -->
    <div v-if="showSourceBadge" class="source-badge" :class="sourceClass">
      <span v-if="source === 'mysql'" class="badge mysql">
        <i class="i-carbon-data-1"></i>
        近7天数据 (MySQL)
      </span>
      <span v-else-if="source === 'hbase'" class="badge hbase">
        <i class="i-carbon-archive"></i>
        历史数据 (HBase)
      </span>
      <span v-else class="badge mixed">
        <i class="i-carbon-data-base"></i>
        混合数据
      </span>
      <span v-if="queryTimeMs" class="query-time">
        耗时: {{ queryTimeMs }}ms
      </span>
    </div>

    <!-- 统计信息 -->
    <div v-if="showStats" class="stats-bar">
      <div class="stat-item">
        <span class="label">MySQL记录:</span>
        <span class="value">{{ formatNumber(mysqlTotal) }}</span>
      </div>
      <div class="stat-item">
        <span class="label">历史总量:</span>
        <span class="value">{{ formatNumber(totalCount) }}</span>
      </div>
      <div v-if="stats?.todayCount !== undefined" class="stat-item">
        <span class="label">今日:</span>
        <span class="value highlight">{{ formatNumber(stats.todayCount as number) }}</span>
      </div>
    </div>

    <!-- 数据表格插槽 -->
    <slot :data="data" :loading="loading" :source="source"></slot>

    <!-- 加载更多区域 -->
    <div v-if="hasMoreHistory" class="load-more-container">
      <div class="divider">
        <span class="divider-text">以上为近7天数据</span>
      </div>
      <button 
        class="load-more-btn"
        :class="{ loading: loadingMore }"
        :disabled="loadingMore"
        @click="handleLoadMore"
      >
        <template v-if="loadingMore">
          <i class="i-carbon-renew animate-spin"></i>
          加载中...
        </template>
        <template v-else>
          <i class="i-carbon-arrow-down"></i>
          展示更多历史记录
          <span v-if="historyRemaining" class="remaining">
            (约 {{ formatNumber(historyRemaining) }} 条)
          </span>
        </template>
      </button>
    </div>

    <!-- HBase 数据区域 -->
    <div v-if="hbaseData.value.length > 0" class="hbase-data-section">
      <div class="section-header">
        <i class="i-carbon-archive"></i>
        <span>历史归档数据 (HBase)</span>
        <span class="count">{{ hbaseData.value.length }} 条</span>
      </div>
      
      <slot name="hbase" :data="hbaseData.value" :loading="loadingMore"></slot>

      <!-- 继续加载更多 -->
      <button 
        v-if="canLoadMoreHBase"
        class="load-more-btn secondary"
        :disabled="loadingMore"
        @click="handleLoadMoreHBase"
      >
        <i class="i-carbon-add"></i>
        继续加载
      </button>
    </div>

    <!-- 空状态 -->
    <div v-if="!loading && data.length === 0 && hbaseData.value.length === 0" class="empty-state">
      <i class="i-carbon-document-blank"></i>
      <p>暂无数据</p>
    </div>
  </div>
</template>

<script setup lang="ts" generic="T">
import { computed, ref, watch } from 'vue'
import type { ProgressiveLoadResult } from '@/api/admin/progressive'

interface Props {
  /** 初始加载结果 */
  result?: ProgressiveLoadResult<T> | null
  /** 是否显示来源标识 */
  showSourceBadge?: boolean
  /** 是否显示统计信息 */
  showStats?: boolean
  /** 加载状态 */
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  result: null,
  showSourceBadge: true,
  showStats: true,
  loading: false
})

const emit = defineEmits<{
  (e: 'load-more', params: { source: 'hbase'; lastRowKey?: string }): void
}>()

// 状态
const loadingMore = ref(false)
const hbaseData = ref<T[]>([]) as { value: T[] }
const lastRowKey = ref<string>()

// 计算属性
const data = computed(() => props.result?.list || [])
const source = computed(() => props.result?.source || 'mysql')
const mysqlTotal = computed(() => props.result?.mysqlTotal || 0)
const totalCount = computed(() => props.result?.totalCount || 0)
const hasMoreHistory = computed(() => props.result?.hasMoreHistory || false)
const queryTimeMs = computed(() => props.result?.queryTimeMs)
const stats = computed(() => props.result?.stats)
const nextRowKey = computed(() => props.result?.nextRowKey)

const historyRemaining = computed(() => {
  if (totalCount.value > mysqlTotal.value) {
    return totalCount.value - mysqlTotal.value
  }
  return 0
})

const canLoadMoreHBase = computed(() => {
  return lastRowKey.value && lastRowKey.value !== ''
})

const sourceClass = computed(() => ({
  'source-mysql': source.value === 'mysql',
  'source-hbase': source.value === 'hbase',
  'source-mixed': source.value === 'mixed'
}))

// 方法
function formatNumber(num: number): string {
  if (num >= 10000) {
    return (num / 10000).toFixed(1) + '万'
  }
  return num.toLocaleString()
}

function handleLoadMore() {
  loadingMore.value = true
  emit('load-more', { source: 'hbase', lastRowKey: undefined })
}

function handleLoadMoreHBase() {
  loadingMore.value = true
  emit('load-more', { source: 'hbase', lastRowKey: lastRowKey.value })
}

// 公开方法供父组件调用
function appendHBaseData(newData: T[], newLastRowKey?: string) {
  hbaseData.value.push(...newData)
  lastRowKey.value = newLastRowKey
  loadingMore.value = false
}

function resetHBaseData() {
  hbaseData.value = []
  lastRowKey.value = undefined
}

function setLoadingMore(val: boolean) {
  loadingMore.value = val
}

// 监听 result 变化重置 HBase 数据
watch(() => props.result, () => {
  resetHBaseData()
})

defineExpose({
  appendHBaseData,
  resetHBaseData,
  setLoadingMore,
  hbaseData
})
</script>

<style scoped lang="scss">
.progressive-table {
  width: 100%;
}

.source-badge {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 13px;

  &.source-mysql {
    background: linear-gradient(135deg, #e6f7ff 0%, #bae7ff 100%);
    border: 1px solid #91d5ff;
  }

  &.source-hbase {
    background: linear-gradient(135deg, #fff7e6 0%, #ffd591 100%);
    border: 1px solid #ffc069;
  }

  &.source-mixed {
    background: linear-gradient(135deg, #f6ffed 0%, #b7eb8f 100%);
    border: 1px solid #95de64;
  }

  .badge {
    display: flex;
    align-items: center;
    gap: 4px;
    font-weight: 500;

    i {
      font-size: 16px;
    }
  }

  .query-time {
    color: #666;
    font-size: 12px;
    margin-left: auto;
  }
}

.stats-bar {
  display: flex;
  gap: 24px;
  margin-bottom: 12px;
  padding: 10px 16px;
  background: #fafafa;
  border-radius: 6px;

  .stat-item {
    display: flex;
    align-items: center;
    gap: 6px;

    .label {
      color: #666;
      font-size: 13px;
    }

    .value {
      font-weight: 600;
      color: #333;

      &.highlight {
        color: #1890ff;
      }
    }
  }
}

.load-more-container {
  margin-top: 24px;

  .divider {
    position: relative;
    text-align: center;
    margin-bottom: 16px;

    &::before {
      content: '';
      position: absolute;
      left: 0;
      top: 50%;
      width: 100%;
      height: 1px;
      background: #e8e8e8;
    }

    .divider-text {
      position: relative;
      padding: 0 16px;
      background: #fff;
      color: #999;
      font-size: 12px;
    }
  }
}

.load-more-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  padding: 12px 24px;
  border: 1px dashed #d9d9d9;
  border-radius: 8px;
  background: #fff;
  color: #1890ff;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s;

  &:hover:not(:disabled) {
    border-color: #1890ff;
    background: #e6f7ff;
  }

  &:disabled {
    cursor: not-allowed;
    opacity: 0.6;
  }

  &.loading {
    color: #999;
  }

  &.secondary {
    margin-top: 12px;
    border-style: solid;
    background: #fafafa;
  }

  .remaining {
    font-size: 12px;
    color: #999;
  }

  i {
    font-size: 16px;
  }
}

.hbase-data-section {
  margin-top: 24px;
  padding: 16px;
  background: #fffbe6;
  border: 1px solid #ffe58f;
  border-radius: 8px;

  .section-header {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 12px;
    font-weight: 500;
    color: #d48806;

    i {
      font-size: 18px;
    }

    .count {
      margin-left: auto;
      font-size: 12px;
      color: #999;
    }
  }
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px;
  color: #999;

  i {
    font-size: 48px;
    margin-bottom: 12px;
  }

  p {
    font-size: 14px;
  }
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.animate-spin {
  animation: spin 1s linear infinite;
}
</style>
