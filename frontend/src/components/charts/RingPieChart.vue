<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, computed } from 'vue'
import * as echarts from 'echarts'

const props = defineProps<{
  data: { name: string; value: number; percentage: string }[]
}>()

const chartRef = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

const total = computed(() => {
  return props.data.reduce((sum, item) => sum + item.value, 0)
})

const initChart = () => {
  if (!chartRef.value) return
  chart = echarts.init(chartRef.value)
  updateChart()
}

const updateChart = () => {
  if (!chart || !props.data.length) return

  const colors = ['#00ff88', '#00d4ff', '#ffaa00']

  const option: echarts.EChartsOption = {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(6, 30, 61, 0.9)',
      borderColor: 'rgba(0, 212, 255, 0.3)',
      textStyle: { color: '#fff' },
      formatter: (params: any) => {
        return `${params.name}<br/>车流量: ${params.value}<br/>占比: ${params.percent}%`
      }
    },
    series: [
      // 外圈装饰
      {
        type: 'pie',
        radius: ['75%', '78%'],
        center: ['35%', '50%'],
        silent: true,
        label: { show: false },
        data: [{ value: 1, itemStyle: { color: 'rgba(0, 212, 255, 0.15)' } }]
      },
      // 主饼图
      {
        type: 'pie',
        radius: ['45%', '70%'],
        center: ['35%', '50%'],
        avoidLabelOverlap: false,
        label: { show: false },
        emphasis: {
          scale: true,
          scaleSize: 8
        },
        labelLine: { show: false },
        data: props.data.map((item, index) => ({
          value: item.value,
          name: item.name,
          itemStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 1, 1, [
              { offset: 0, color: colors[index] },
              { offset: 1, color: colors[index] + '80' }
            ])
          }
        }))
      }
    ],
    // 中心文字
    graphic: [
      {
        type: 'text',
        left: '27%',
        top: '42%',
        style: {
          text: total.value.toLocaleString(),
          fill: '#00ff88',
          fontSize: 26,
          fontWeight: 'bold' as const,
          fontFamily: 'DIN Alternate'
        }
      },
      {
        type: 'text',
        left: '30%',
        top: '55%',
        style: {
          text: '总数',
          fill: 'rgba(255,255,255,0.6)',
          fontSize: 12
        }
      }
    ]
  }

  chart.setOption(option)
}

const handleResize = () => {
  chart?.resize()
}

watch(() => props.data, updateChart, { deep: true })

onMounted(() => {
  initChart()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  chart?.dispose()
  window.removeEventListener('resize', handleResize)
})
</script>

<template>
  <div class="ring-pie-container">
    <div ref="chartRef" class="ring-pie-chart"></div>
    <div class="legend-list">
      <div 
        v-for="(item, index) in data" 
        :key="item.name"
        class="legend-item"
      >
        <span class="legend-dot" :style="{ background: ['#00ff88', '#00d4ff', '#ffaa00'][index] }"></span>
        <span class="legend-name">{{ item.name }}</span>
        <span class="legend-value">{{ item.value.toLocaleString() }}个</span>
        <span class="legend-percent">{{ item.percentage }}</span>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.ring-pie-container {
  display: flex;
  align-items: center;
  height: calc(100% - 30px);
}

.ring-pie-chart {
  width: 55%;
  height: 100%;
  min-height: 140px;
}

.legend-list {
  width: 45%;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-left: 10px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.8);
}

.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.legend-name {
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.legend-value {
  color: rgba(255, 255, 255, 0.6);
  font-family: 'DIN Alternate', sans-serif;
}

.legend-percent {
  color: #00d4ff;
  font-family: 'DIN Alternate', sans-serif;
  min-width: 45px;
  text-align: right;
}
</style>

