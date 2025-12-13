<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps<{
  data: any[]
  type?: 'line' | 'bar' | 'area'
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

  let option: echarts.EChartsOption

  if (props.type === 'bar') {
    // 客货车情况柱状图
    const categories = props.data.map(d => d.name)
    option = {
      backgroundColor: 'transparent',
      tooltip: {
        trigger: 'axis',
        backgroundColor: 'rgba(6, 30, 61, 0.9)',
        borderColor: 'rgba(0, 212, 255, 0.3)',
        textStyle: { color: '#fff' }
      },
      legend: {
        data: ['客车', '货车', '客货比值'],
        textStyle: { color: 'rgba(255,255,255,0.7)', fontSize: 10 },
        top: 5,
        right: 10,
        itemWidth: 12,
        itemHeight: 8
      },
      grid: {
        left: 40,
        right: 40,
        top: 35,
        bottom: 25
      },
      xAxis: {
        type: 'category',
        data: categories,
        axisLine: { lineStyle: { color: 'rgba(0, 212, 255, 0.3)' } },
        axisLabel: { color: 'rgba(255,255,255,0.6)', fontSize: 10 }
      },
      yAxis: [
        {
          type: 'value',
          axisLine: { show: false },
          splitLine: { lineStyle: { color: 'rgba(0, 212, 255, 0.1)' } },
          axisLabel: { color: 'rgba(255,255,255,0.5)', fontSize: 10 }
        },
        {
          type: 'value',
          max: 120,
          axisLine: { show: false },
          splitLine: { show: false },
          axisLabel: { 
            color: 'rgba(255,255,255,0.5)', 
            fontSize: 10,
            formatter: '{value}%'
          }
        }
      ],
      series: [
        {
          name: '客车',
          type: 'bar',
          barWidth: 20,
          itemStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: '#8b5cf6' },
              { offset: 1, color: '#4c1d95' }
            ])
          },
          data: props.data.map(d => d.passenger)
        },
        {
          name: '货车',
          type: 'bar',
          barWidth: 20,
          itemStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: '#00d4ff' },
              { offset: 1, color: '#0066aa' }
            ])
          },
          data: props.data.map(d => d.freight)
        },
        {
          name: '客货比值',
          type: 'line',
          yAxisIndex: 1,
          symbol: 'circle',
          symbolSize: 6,
          lineStyle: { color: '#ff69b4', width: 2 },
          itemStyle: { color: '#ff69b4' },
          data: props.data.map(d => d.ratio)
        }
      ]
    }
  } else {
    // 趋势折线图/面积图
    const stationGroups: { [key: string]: { time: string; value: number }[] } = {}
    props.data.forEach((item: any) => {
      if (!stationGroups[item.station]) {
        stationGroups[item.station] = []
      }
      stationGroups[item.station].push({ time: item.time, value: item.value })
    })
    
    const stations = Object.keys(stationGroups)
    const times = stationGroups[stations[0]]?.map(d => d.time) || []
    const colors = ['#00d4ff', '#ff6b35', '#00ff88']
    
    option = {
      backgroundColor: 'transparent',
      tooltip: {
        trigger: 'axis',
        backgroundColor: 'rgba(6, 30, 61, 0.9)',
        borderColor: 'rgba(0, 212, 255, 0.3)',
        textStyle: { color: '#fff' }
      },
      legend: {
        data: stations,
        textStyle: { color: 'rgba(255,255,255,0.7)', fontSize: 10 },
        top: 0,
        right: 0,
        itemWidth: 15,
        itemHeight: 8
      },
      grid: {
        left: 35,
        right: 15,
        top: 30,
        bottom: 25
      },
      xAxis: {
        type: 'category',
        data: times,
        boundaryGap: false,
        axisLine: { lineStyle: { color: 'rgba(0, 212, 255, 0.3)' } },
        axisLabel: { color: 'rgba(255,255,255,0.5)', fontSize: 9 }
      },
      yAxis: {
        type: 'value',
        axisLine: { show: false },
        splitLine: { lineStyle: { color: 'rgba(0, 212, 255, 0.1)' } },
        axisLabel: { color: 'rgba(255,255,255,0.5)', fontSize: 10 }
      },
      series: stations.map((station, index) => ({
        name: station,
        type: 'line',
        smooth: true,
        symbol: 'none',
        areaStyle: props.type === 'area' ? {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: colors[index] + '40' },
            { offset: 1, color: colors[index] + '05' }
          ])
        } : undefined,
        lineStyle: {
          color: colors[index],
          width: 2
        },
        data: stationGroups[station].map(d => d.value)
      }))
    }
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
  <div ref="chartRef" class="trend-chart"></div>
</template>

<style scoped>
.trend-chart {
  width: 100%;
  height: calc(100% - 30px);
  min-height: 120px;
}
</style>

