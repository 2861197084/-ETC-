<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps<{
  data: { name: string; value: number; color: string }[]
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

  const sortedData = [...props.data].sort((a, b) => a.value - b.value)
  const maxValue = Math.max(...sortedData.map(d => d.value))

  const option: echarts.EChartsOption = {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: 'rgba(6, 30, 61, 0.9)',
      borderColor: 'rgba(0, 212, 255, 0.3)',
      textStyle: { color: '#fff' }
    },
    grid: {
      left: 90,
      right: 50,
      top: 5,
      bottom: 5
    },
    xAxis: {
      type: 'value',
      max: maxValue * 1.1,
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { show: false },
      axisLabel: { show: false }
    },
    yAxis: {
      type: 'category',
      data: sortedData.map(d => d.name),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: {
        color: 'rgba(255, 255, 255, 0.8)',
        fontSize: 11,
        margin: 10
      }
    },
    series: [
      // 背景条
      {
        type: 'bar',
        barWidth: 12,
        barGap: '-100%',
        silent: true,
        itemStyle: {
          color: 'rgba(0, 212, 255, 0.1)',
          borderRadius: 6
        },
        data: sortedData.map(() => maxValue * 1.05)
      },
      // 数据条
      {
        type: 'bar',
        barWidth: 12,
        itemStyle: {
          borderRadius: 6
        },
        label: {
          show: true,
          position: 'right',
          color: 'rgba(255, 255, 255, 0.7)',
          fontSize: 11,
          fontFamily: 'DIN Alternate',
          formatter: (params: any) => params.value.toLocaleString()
        },
        data: sortedData.map((item) => ({
          value: item.value,
          itemStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
              { offset: 0, color: item.color + '40' },
              { offset: 1, color: item.color }
            ])
          }
        }))
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
  <div ref="chartRef" class="bar-rank-chart"></div>
</template>

<style scoped>
.bar-rank-chart {
  width: 100%;
  height: calc(100% - 30px);
  min-height: 180px;
}
</style>

