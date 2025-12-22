<template>
  <div class="bloom-stats glass-card">
    <div class="stats-header">
      <span class="stats-title">车辆来源统计</span>
    </div>
    <div class="chart-container">
      <div class="stats-chart" ref="chartRef"></div>
      
      <!-- Custom Legend -->
      <div class="stats-legend">
        <div class="legend-item">
          <div class="legend-info">
            <span class="legend-dot local"></span>
            <span class="legend-label">本地车辆</span>
          </div>
          <div class="legend-data">
            <span class="legend-value">{{ localCount }}</span>
            <span class="legend-percent">{{ localPercent }}%</span>
          </div>
        </div>
        <div class="legend-item">
          <div class="legend-info">
            <span class="legend-dot foreign"></span>
            <span class="legend-label">外地车辆</span>
          </div>
          <div class="legend-data">
            <span class="legend-value">{{ foreignCount }}</span>
            <span class="legend-percent">{{ foreignPercent }}%</span>
          </div>
        </div>
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
      formatter: '{b}: {c} ({d}%)',
      backgroundColor: 'rgba(255, 255, 255, 0.9)',
      borderColor: '#e2e8f0',
      textStyle: {
        color: '#1e293b'
      },
      padding: [8, 12]
    },
    series: [
      {
        type: 'pie',
        radius: ['65%', '80%'],
        center: ['50%', '50%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 20,
          borderColor: '#ffffff',
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
              fontSize: 24,
              fontWeight: '700',
              color: '#0f172a',
              lineHeight: 32,
              fontFamily: 'Inter, system-ui, sans-serif'
            },
            label: {
              fontSize: 12,
              color: '#64748b',
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
              color: new echarts.graphic.LinearGradient(0, 0, 1, 1, [
                { offset: 0, color: '#38bdf8' }, // Sky 400
                { offset: 1, color: '#0284c7' }  // Sky 600
              ])
            }
          },
          {
            value: props.foreignCount,
            name: '外地车辆',
            itemStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 1, 1, [
                { offset: 0, color: '#2dd4bf' }, // Teal 400
                { offset: 1, color: '#0d9488' }  // Teal 600
              ])
            }
          }
        ],
        animationType: 'scale',
        animationEasing: 'cubicOut',
        animationDuration: 1000
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
.glass-card {
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.6);
  border-radius: 16px;
  box-shadow: 
    0 4px 6px -1px rgba(0, 0, 0, 0.05),
    0 2px 4px -1px rgba(0, 0, 0, 0.03),
    inset 0 0 0 1px rgba(255, 255, 255, 0.5);
}

.bloom-stats {
  padding: 20px;
  height: 100%;
  display: flex;
  flex-direction: column;

  .stats-header {
    margin-bottom: 8px;

    .stats-title {
      display: block;
      color: #0f172a;
      font-size: 16px;
      font-weight: 600;
      letter-spacing: -0.01em;
    }
  }

  .chart-container {
    flex: 1;
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    gap: 20px;
  }

  .stats-chart {
    width: 100%;
    height: 160px;
    margin-bottom: 0;
  }

  .stats-legend {
    width: 100%;
    display: flex;
    justify-content: space-around;
    gap: 16px;
    padding-top: 0;
    border-top: none;

    .legend-item {
      display: flex;
      flex-direction: column;
      gap: 4px;
      
      .legend-info {
        display: flex;
        align-items: center;
        gap: 6px;

        .legend-dot {
          width: 8px;
          height: 8px;
          border-radius: 4px;
          
          &.local {
            background: linear-gradient(135deg, #38bdf8, #0284c7);
            box-shadow: 0 2px 4px rgba(2, 132, 199, 0.2);
          }
          
          &.foreign {
            background: linear-gradient(135deg, #2dd4bf, #0d9488);
            box-shadow: 0 2px 4px rgba(13, 148, 136, 0.2);
          }
        }

        .legend-label {
          color: #64748b;
          font-size: 12px;
        }
      }

      .legend-data {
        display: flex;
        align-items: baseline;
        gap: 4px;
        padding-left: 14px;

        .legend-value {
          color: #0f172a;
          font-weight: 600;
          font-size: 16px;
          font-feature-settings: "tnum";
        }

        .legend-percent {
          color: #94a3b8;
          font-size: 12px;
        }
      }
    }
  }
}
</style>
