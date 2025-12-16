<template>
  <div class="bloom-stats">
    <div class="stats-header">
      <span class="stats-title">车辆来源统计</span>
      <span class="stats-subtitle">布隆过滤器实时分析</span>
    </div>
    <div class="stats-chart" ref="chartRef"></div>
    <div class="stats-legend">
      <div class="legend-item">
        <span class="legend-dot local"></span>
        <span class="legend-label">本地车辆</span>
        <span class="legend-value">{{ localCount }}</span>
        <span class="legend-percent">({{ localPercent }}%)</span>
      </div>
      <div class="legend-item">
        <span class="legend-dot foreign"></span>
        <span class="legend-label">外地车辆</span>
        <span class="legend-value">{{ foreignCount }}</span>
        <span class="legend-percent">({{ foreignPercent }}%)</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch, onUnmounted } from 'vue'
import * as echarts from 'echarts'

defineOptions({ name: 'BloomStats' })

interface Props {
  localCount?: number
  foreignCount?: number
}

const props = withDefaults(defineProps<Props>(), {
  localCount: 0,
  foreignCount: 0
})

const chartRef = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null

const total = computed(() => props.localCount + props.foreignCount)
const localPercent = computed(() =>
  total.value > 0 ? ((props.localCount / total.value) * 100).toFixed(1) : '0'
)
const foreignPercent = computed(() =>
  total.value > 0 ? ((props.foreignCount / total.value) * 100).toFixed(1) : '0'
)

const initChart = () => {
  if (!chartRef.value) return

  chart = echarts.init(chartRef.value)
  updateChart()
}

const updateChart = () => {
  if (!chart) return

  const option: echarts.EChartsOption = {
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)'
    },
    series: [
      {
        type: 'pie',
        radius: ['55%', '80%'],
        center: ['50%', '50%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 6,
          borderColor: 'rgba(0,0,0,0.3)',
          borderWidth: 2
        },
        label: {
          show: true,
          position: 'center',
          formatter: () => {
            return `{total|${total.value}}\n{label|总车辆}`
          },
          rich: {
            total: {
              fontSize: 28,
              fontWeight: 'bold',
              color: '#fff',
              lineHeight: 36
            },
            label: {
              fontSize: 12,
              color: 'rgba(255,255,255,0.6)',
              lineHeight: 20
            }
          }
        },
        labelLine: {
          show: false
        },
        data: [
          {
            value: props.localCount,
            name: '本地车辆',
            itemStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: '#52c41a' },
                { offset: 1, color: '#237804' }
              ])
            }
          },
          {
            value: props.foreignCount,
            name: '外地车辆',
            itemStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: '#1890ff' },
                { offset: 1, color: '#0050b3' }
              ])
            }
          }
        ],
        animationType: 'scale',
        animationEasing: 'elasticOut'
      }
    ]
  }

  chart.setOption(option)
}

// 监听数据变化
watch(
  () => [props.localCount, props.foreignCount],
  () => {
    updateChart()
  }
)

// 处理窗口大小变化
const handleResize = () => {
  chart?.resize()
}

onMounted(() => {
  initChart()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
})
</script>

<style lang="scss" scoped>
.bloom-stats {
  background: rgba(0, 0, 0, 0.6);
  border-radius: 8px;
  padding: 16px;

  .stats-header {
    margin-bottom: 12px;

    .stats-title {
      display: block;
      color: #fff;
      font-size: 14px;
      font-weight: 600;
    }

    .stats-subtitle {
      display: block;
      color: rgba(255, 255, 255, 0.5);
      font-size: 12px;
      margin-top: 4px;
    }
  }

  .stats-chart {
    width: 100%;
    height: 180px;
  }

  .stats-legend {
    display: flex;
    flex-direction: column;
    gap: 8px;
    margin-top: 12px;

    .legend-item {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 13px;

      .legend-dot {
        width: 10px;
        height: 10px;
        border-radius: 50%;

        &.local {
          background: #52c41a;
        }

        &.foreign {
          background: #1890ff;
        }
      }

      .legend-label {
        color: rgba(255, 255, 255, 0.8);
        min-width: 60px;
      }

      .legend-value {
        color: #fff;
        font-weight: 600;
        min-width: 50px;
      }

      .legend-percent {
        color: rgba(255, 255, 255, 0.5);
      }
    }
  }
}
</style>
