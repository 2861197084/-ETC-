<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps<{
  data: { hour: string; vehicleType: string; count: number }[]
}>()

const chartRef = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

const initChart = () => {
  if (!chartRef.value) return
  chart = echarts.init(chartRef.value)
  updateChart()
}

const updateChart = () => {
  if (!chart || !props.data.length) return

  // 按时间分组
  const hours = [...new Set(props.data.map(d => d.hour))]
  const types = [...new Set(props.data.map(d => d.vehicleType))]
  
  const colors = ['#00d4ff', '#00ff88', '#ffaa00']
  
  const series = types.map((type, index) => {
    const typeData = props.data.filter(d => d.vehicleType === type)
    return {
      name: type,
      type: 'bar' as const,
      barWidth: 18,
      barGap: '10%',
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: colors[index] },
          { offset: 0.5, color: colors[index] + 'cc' },
          { offset: 1, color: colors[index] + '40' }
        ]),
        borderRadius: [4, 4, 0, 0]
      },
      emphasis: {
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: colors[index] },
            { offset: 1, color: colors[index] + '80' }
          ])
        }
      },
      data: typeData.map(d => d.count)
    }
  })

  const option: echarts.EChartsOption = {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: 'rgba(6, 30, 61, 0.9)',
      borderColor: 'rgba(0, 212, 255, 0.3)',
      textStyle: { color: '#fff' }
    },
    legend: {
      data: types,
      textStyle: { color: 'rgba(255,255,255,0.7)', fontSize: 11 },
      top: 0,
      right: 20,
      itemWidth: 12,
      itemHeight: 8
    },
    grid: {
      left: 45,
      right: 20,
      top: 30,
      bottom: 30
    },
    xAxis: {
      type: 'category',
      data: hours,
      axisLine: { lineStyle: { color: 'rgba(0, 212, 255, 0.3)' } },
      axisLabel: { color: 'rgba(255,255,255,0.6)', fontSize: 10 },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      splitLine: { lineStyle: { color: 'rgba(0, 212, 255, 0.1)' } },
      axisLabel: { color: 'rgba(255,255,255,0.5)', fontSize: 10 }
    },
    series
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
  <div ref="chartRef" class="bar-3d-chart"></div>
</template>

<style scoped>
.bar-3d-chart {
  width: 100%;
  height: calc(100% - 40px);
  min-height: 150px;
}
</style>

