<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import * as echarts from 'echarts'
import chinaJson from '@/assets/china.json'

const props = defineProps<{
  data: { name: string; value: number }[]
}>()

const chartRef = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

// 注册中国地图
echarts.registerMap('china', chinaJson as any)

const initChart = () => {
  if (!chartRef.value) return
  
  chart = echarts.init(chartRef.value)
  updateChart()
}

const updateChart = () => {
  if (!chart) return
  
  const option: echarts.EChartsOption = {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(6, 30, 61, 0.9)',
      borderColor: 'rgba(0, 212, 255, 0.3)',
      textStyle: {
        color: '#fff'
      },
      formatter: (params: any) => {
        return `${params.name}<br/>车辆数: ${params.value || 0}`
      }
    },
    visualMap: {
      min: 0,
      max: 1200,
      left: 20,
      bottom: 20,
      text: ['高', '低'],
      textStyle: {
        color: 'rgba(255, 255, 255, 0.7)',
        fontSize: 11
      },
      inRange: {
        color: ['#0d2b4e', '#1a4a7a', '#2d6da8', '#4091d4', '#00d4ff']
      },
      show: true,
      calculable: true
    },
    geo: {
      map: 'china',
      roam: false,
      zoom: 1.2,
      center: [105, 36],
      label: {
        show: false
      },
      itemStyle: {
        areaColor: '#0d2b4e',
        borderColor: 'rgba(0, 212, 255, 0.4)',
        borderWidth: 1
      },
      emphasis: {
        label: {
          show: true,
          color: '#fff'
        },
        itemStyle: {
          areaColor: '#2d6da8'
        }
      }
    },
    series: [
      {
        name: '车辆来源',
        type: 'map',
        map: 'china',
        geoIndex: 0,
        data: props.data
      },
      // 散点图显示热点城市
      {
        name: '热点城市',
        type: 'effectScatter',
        coordinateSystem: 'geo',
        symbolSize: (val: number[]) => Math.min(val[2] / 50, 20),
        data: [
          { name: '深圳', value: [114.05, 22.55, 800] },
          { name: '广州', value: [113.23, 23.16, 600] },
          { name: '东莞', value: [113.75, 23.05, 400] },
        ],
        showEffectOn: 'render',
        rippleEffect: {
          brushType: 'stroke',
          scale: 3,
          period: 4
        },
        label: {
          show: true,
          formatter: '{b}',
          position: 'right',
          color: '#00ff88',
          fontSize: 11
        },
        itemStyle: {
          color: '#00ff88',
          shadowBlur: 10,
          shadowColor: '#00ff88'
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
  <div ref="chartRef" class="china-map-chart"></div>
</template>

<style scoped>
.china-map-chart {
  width: 100%;
  height: calc(100% - 80px);
  min-height: 350px;
}
</style>

