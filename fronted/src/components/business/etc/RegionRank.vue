<template>
  <div class="region-rank">
    <div class="rank-header">
      <span class="rank-title">
        <el-icon><TrendCharts /></el-icon>
        区域热度排名
      </span>
      <el-radio-group v-model="timeRange" size="small">
        <el-radio-button value="hour">1小时</el-radio-button>
        <el-radio-button value="day">今日</el-radio-button>
      </el-radio-group>
    </div>
    <div class="rank-list">
      <div
        v-for="(item, index) in rankData"
        :key="item.region"
        class="rank-item"
        :class="{ 'top-three': index < 3 }"
      >
        <span class="rank-index" :class="`rank-${index + 1}`">
          {{ index + 1 }}
        </span>
        <span class="rank-region">{{ item.region }}</span>
        <div class="rank-bar-wrapper">
          <div
            class="rank-bar"
            :style="{ width: `${(item.count / maxCount) * 100}%` }"
            :class="getBarClass(index)"
          ></div>
        </div>
        <span class="rank-count">{{ formatNumber(item.count) }}</span>
        <span class="rank-trend" :class="item.trend > 0 ? 'up' : 'down'">
          <el-icon v-if="item.trend > 0"><CaretTop /></el-icon>
          <el-icon v-else><CaretBottom /></el-icon>
          {{ Math.abs(item.trend) }}%
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
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

const timeRange = ref<'hour' | 'day'>('hour')

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
.region-rank {
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  border: 1px solid #e8e8e8;

  .rank-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 16px;
    border-bottom: 1px solid #f0f0f0;

    .rank-title {
      display: flex;
      align-items: center;
      gap: 8px;
      color: #1f2329;
      font-weight: 600;
      font-size: 14px;
    }

    :deep(.el-radio-group) {
      .el-radio-button__inner {
        background: #fff;
        border-color: #d9d9d9;
        color: #595959;
      }

      .el-radio-button__original-radio:checked + .el-radio-button__inner {
        background: #e6f7ff;
        border-color: #1890ff;
        color: #1890ff;
      }
    }
  }

  .rank-list {
    padding: 8px 0;
  }

  .rank-item {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 8px 16px;
    transition: background 0.3s;

    &:hover {
      background: #fafafa;
    }

    &.top-three {
      .rank-region {
        font-weight: 600;
      }
    }

    .rank-index {
      width: 24px;
      height: 24px;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 4px;
      font-size: 12px;
      font-weight: 600;
      background: #f5f5f5;
      color: #8c8c8c;

      &.rank-1 {
        background: linear-gradient(135deg, #ffd700, #ffa500);
        color: #000;
      }

      &.rank-2 {
        background: linear-gradient(135deg, #c0c0c0, #a0a0a0);
        color: #000;
      }

      &.rank-3 {
        background: linear-gradient(135deg, #cd7f32, #8b4513);
        color: #fff;
      }
    }

    .rank-region {
      min-width: 80px;
      color: #1f2329;
      font-size: 13px;
    }

    .rank-bar-wrapper {
      flex: 1;
      height: 8px;
      background: #f0f0f0;
      border-radius: 4px;
      overflow: hidden;

      .rank-bar {
        height: 100%;
        border-radius: 4px;
        transition: width 0.5s ease;

        &.bar-gold {
          background: linear-gradient(90deg, #ffd700, #ffa500);
        }

        &.bar-silver {
          background: linear-gradient(90deg, #c0c0c0, #a0a0a0);
        }

        &.bar-bronze {
          background: linear-gradient(90deg, #cd7f32, #8b4513);
        }

        &.bar-normal {
          background: linear-gradient(90deg, #1890ff, #69c0ff);
        }
      }
    }

    .rank-count {
      min-width: 50px;
      text-align: right;
      color: #1f2329;
      font-size: 13px;
      font-weight: 600;
    }

    .rank-trend {
      display: flex;
      align-items: center;
      min-width: 50px;
      font-size: 12px;

      &.up {
        color: #ff4d4f;
      }

      &.down {
        color: #52c41a;
      }
    }
  }
}
</style>
