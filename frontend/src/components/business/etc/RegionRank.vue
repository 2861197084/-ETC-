<template>
  <div class="region-rank glass-card">
    <div class="rank-header">
      <span class="rank-title">
        <el-icon class="title-icon"><TrendCharts /></el-icon>
        区域热度排名
      </span>
      <div class="time-tabs">
        <span 
          class="tab-item" 
          :class="{ active: timeRange === 'hour' }"
          @click="timeRange = 'hour'"
        >1小时</span>
        <span 
          class="tab-item" 
          :class="{ active: timeRange === 'day' }"
          @click="timeRange = 'day'"
        >今日</span>
      </div>
    </div>
    <div class="rank-list-container">
      <div class="rank-list">
        <div
          v-for="(item, index) in rankData"
          :key="item.region"
          class="rank-item"
        >
          <div class="rank-info-row">
            <span class="rank-index" :class="`rank-${index + 1}`">
              {{ index + 1 }}
            </span>
            <span class="rank-region">{{ item.region }}</span>
            <div class="rank-stats">
              <span class="rank-count">{{ formatNumber(item.count) }}</span>
              <span class="rank-trend" :class="item.trend > 0 ? 'up' : 'down'">
                <el-icon v-if="item.trend > 0"><CaretTop /></el-icon>
                <el-icon v-else><CaretBottom /></el-icon>
                {{ Math.abs(item.trend) }}%
              </span>
            </div>
          </div>
          <div class="rank-bar-bg">
            <div
              class="rank-bar"
              :style="{ width: `${(item.count / maxCount) * 100}%` }"
              :class="getBarClass(index)"
            ></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { TrendCharts, CaretTop, CaretBottom } from '@element-plus/icons-vue'

defineOptions({ name: 'RegionRank' })

interface RankItem {
  region: string
  count: number
  trend: number // 正数表示上升，负数表示下降
}

interface Props {
  data?: RankItem[]
}

const props = withDefaults(defineProps<Props>(), {
  data: () => []
})

const emit = defineEmits<{
  (e: 'timeRangeChange', value: 'hour' | 'day'): void
}>()

const timeRange = ref<'hour' | 'day'>('hour')

// 监听时间范围变化，通知父组件重新加载数据
watch(timeRange, (newVal) => {
  emit('timeRangeChange', newVal)
})

const rankData = computed(() => {
  return [...props.data].sort((a, b) => b.count - a.count).slice(0, 10)
})

const maxCount = computed(() => {
  return Math.max(...rankData.value.map((item) => item.count), 1)
})

const getBarClass = (index: number) => {
  if (index === 0) return 'bar-gold'
  if (index === 1) return 'bar-silver'
  if (index === 2) return 'bar-bronze'
  return 'bar-normal'
}

const formatNumber = (num: number) => {
  if (num >= 10000) {
    return (num / 10000).toFixed(1) + 'w'
  }
  return num.toLocaleString()
}
</script>

<style lang="scss" scoped>
.glass-card {
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.6);
  border-radius: 16px;
  box-shadow: 
    0 4px 6px -1px rgba(0, 0, 0, 0.05),
    0 2px 4px -1px rgba(0, 0, 0, 0.03),
    inset 0 0 0 1px rgba(255, 255, 255, 0.5);
  display: flex;
  flex-direction: column;
  height: 100%;
}

.region-rank {
  padding: 20px;
  overflow: hidden;

  .rank-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 20px;

    .rank-title {
      display: flex;
      align-items: center;
      gap: 8px;
      color: #0f172a;
      font-weight: 600;
      font-size: 16px;
      letter-spacing: -0.01em;

      .title-icon {
        color: #3b82f6;
      }
    }

    .time-tabs {
      display: flex;
      background: #f1f5f9;
      padding: 2px;
      border-radius: 8px;
      
      .tab-item {
        padding: 4px 12px;
        font-size: 12px;
        color: #64748b;
        cursor: pointer;
        border-radius: 6px;
        transition: all 0.2s;

        &.active {
          background: #fff;
          color: #0f172a;
          box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
          font-weight: 500;
        }

        &:hover:not(.active) {
          color: #475569;
        }
      }
    }
  }

  .rank-list-container {
    flex: 1;
    overflow-y: auto;
    /* Hide scrollbar for clean look */
    &::-webkit-scrollbar {
      display: none;
    }
    -ms-overflow-style: none;
    scrollbar-width: none;
  }

  .rank-list {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .rank-item {
    display: flex;
    flex-direction: column;
    gap: 8px;

    .rank-info-row {
      display: flex;
      align-items: center;
      justify-content: space-between;

      .rank-index {
        width: 20px;
        height: 20px;
        display: flex;
        align-items: center;
        justify-content: center;
        border-radius: 6px;
        font-size: 12px;
        font-weight: 700;
        margin-right: 12px;
        
        &.rank-1 { color: #d97706; background: #fef3c7; }
        &.rank-2 { color: #475569; background: #f1f5f9; }
        &.rank-3 { color: #b45309; background: #ffedd5; }
        // Default for others
        color: #94a3b8;
        background: transparent;
      }

      .rank-region {
        flex: 1;
        color: #334155;
        font-size: 14px;
        font-weight: 500;
      }

      .rank-stats {
        display: flex;
        align-items: center;
        gap: 12px;

        .rank-count {
          color: #0f172a;
          font-weight: 600;
          font-size: 14px;
          font-feature-settings: "tnum";
        }

        .rank-trend {
          display: flex;
          align-items: center;
          font-size: 12px;
          font-weight: 500;
          min-width: 45px;
          justify-content: flex-end;

          &.up { color: #ef4444; }
          &.down { color: #22c55e; }
        }
      }
    }

    .rank-bar-bg {
      height: 6px;
      background: #f1f5f9;
      border-radius: 3px;
      overflow: hidden;
      width: 100%;

      .rank-bar {
        height: 100%;
        border-radius: 3px;
        transition: width 0.8s cubic-bezier(0.4, 0, 0.2, 1);

        &.bar-gold {
          background: linear-gradient(90deg, #fbbf24, #d97706);
        }

        &.bar-silver {
          background: linear-gradient(90deg, #94a3b8, #475569);
        }

        &.bar-bronze {
          background: linear-gradient(90deg, #fdba74, #ea580c);
        }

        &.bar-normal {
          background: linear-gradient(90deg, #38bdf8, #2563eb);
        }
      }
    }
  }
}
</style>
