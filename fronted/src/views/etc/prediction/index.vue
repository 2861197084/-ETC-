<template>
  <div class="prediction-page">
    <div class="page-header">
      <h2 class="page-title">离线预测分析</h2>
      <p class="page-desc">基于历史数据的机器学习模型，预测未来交通流量趋势</p>
    </div>

    <!-- 模型指标概览 -->
    <div class="metrics-overview">
      <el-row :gutter="16">
        <el-col :span="6">
          <div class="metric-card">
            <div class="metric-header">
              <span class="metric-title">模型准确率</span>
              <el-tag type="success" size="small">LSTM</el-tag>
            </div>
            <div class="metric-body">
              <div class="gauge-chart" ref="accuracyChartRef"></div>
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="metric-card">
            <div class="metric-header">
              <span class="metric-title">预测偏差率</span>
              <el-tooltip content="实际值与预测值的平均偏差百分比">
                <el-icon><QuestionFilled /></el-icon>
              </el-tooltip>
            </div>
            <div class="metric-body">
              <div class="metric-value">
                <span class="value">3.2</span>
                <span class="unit">%</span>
              </div>
              <div class="metric-trend down">
                <el-icon><CaretBottom /></el-icon>
                较上周下降 0.5%
              </div>
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="metric-card">
            <div class="metric-header">
              <span class="metric-title">训练数据量</span>
            </div>
            <div class="metric-body">
              <div class="metric-value">
                <span class="value">2.8</span>
                <span class="unit">亿条</span>
              </div>
              <div class="metric-desc">最近更新: 今日 06:00</div>
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="metric-card">
            <div class="metric-header">
              <span class="metric-title">预测周期</span>
            </div>
            <div class="metric-body">
              <div class="metric-value">
                <span class="value">7</span>
                <span class="unit">天</span>
              </div>
              <div class="metric-desc">每小时粒度预测</div>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>

    <!-- 流量趋势对比 -->
    <div class="chart-section">
      <div class="section-header">
        <h3 class="section-title">流量趋势对比</h3>
        <div class="section-actions">
          <el-radio-group v-model="trendTimeRange" size="small">
            <el-radio-button value="day">今日</el-radio-button>
            <el-radio-button value="week">本周</el-radio-button>
            <el-radio-button value="month">本月</el-radio-button>
          </el-radio-group>
        </div>
      </div>
      <div class="chart-container" ref="trendChartRef"></div>
    </div>

    <!-- 拥堵预测热力图 -->
    <div class="chart-section">
      <div class="section-header">
        <h3 class="section-title">未来一周拥堵时段预测</h3>
        <div class="section-legend">
          <span class="legend-item"><span class="dot low"></span>畅通</span>
          <span class="legend-item"><span class="dot medium"></span>缓行</span>
          <span class="legend-item"><span class="dot high"></span>拥堵</span>
        </div>
      </div>
      <div class="chart-container" ref="heatmapChartRef"></div>
    </div>

    <!-- 分路段预测 -->
    <div class="chart-section">
      <div class="section-header">
        <h3 class="section-title">各路段流量预测</h3>
      </div>
      <el-table :data="roadPredictions" stripe border>
        <el-table-column prop="road_name" label="路段名称" width="200" />
        <el-table-column prop="current_flow" label="当前流量" width="120">
          <template #default="{ row }">
            <span class="flow-value">{{ row.current_flow }}</span>
            <span class="flow-unit">辆/h</span>
          </template>
        </el-table-column>
        <el-table-column prop="predicted_flow" label="预测流量(1h后)" width="150">
          <template #default="{ row }">
            <span class="flow-value">{{ row.predicted_flow }}</span>
            <span class="flow-unit">辆/h</span>
            <el-tag
              :type="row.predicted_flow > row.current_flow ? 'danger' : 'success'"
              size="small"
              style="margin-left: 8px"
            >
              {{ row.predicted_flow > row.current_flow ? '↑' : '↓' }}
              {{ Math.abs(row.predicted_flow - row.current_flow) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="confidence" label="置信度" width="150">
          <template #default="{ row }">
            <el-progress
              :percentage="row.confidence"
              :color="getConfidenceColor(row.confidence)"
              :stroke-width="10"
            />
          </template>
        </el-table-column>
        <el-table-column prop="status" label="预测状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ getStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="suggestion" label="建议措施">
          <template #default="{ row }">
            <span class="suggestion-text">{{ row.suggestion }}</span>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { QuestionFilled, CaretBottom } from '@element-plus/icons-vue'
import * as echarts from 'echarts'

defineOptions({ name: 'EtcPrediction' })

const trendTimeRange = ref('day')

// 图表引用
const accuracyChartRef = ref<HTMLDivElement>()
const trendChartRef = ref<HTMLDivElement>()
const heatmapChartRef = ref<HTMLDivElement>()

let accuracyChart: echarts.ECharts | null = null
let trendChart: echarts.ECharts | null = null
let heatmapChart: echarts.ECharts | null = null

// 路段预测数据
const roadPredictions = ref([
  { road_name: '京沪高速', current_flow: 3456, predicted_flow: 3890, confidence: 94, status: 'busy', suggestion: '建议提前疏导，增派巡逻力量' },
  { road_name: '京承高速', current_flow: 2890, predicted_flow: 2650, confidence: 91, status: 'normal', suggestion: '维持当前管控措施' },
  { road_name: '京藏高速', current_flow: 4123, predicted_flow: 4560, confidence: 88, status: 'congested', suggestion: '建议启动应急预案，开放应急车道' },
  { road_name: '京港澳高速', current_flow: 2345, predicted_flow: 2780, confidence: 92, status: 'busy', suggestion: '关注收费站车流量变化' },
  { road_name: '京哈高速', current_flow: 1890, predicted_flow: 1720, confidence: 95, status: 'normal', suggestion: '路况良好，无需特殊措施' },
  { road_name: '机场高速', current_flow: 3210, predicted_flow: 3650, confidence: 89, status: 'busy', suggestion: '航班高峰期，注意疏导' },
  { road_name: '京开高速', current_flow: 2567, predicted_flow: 2340, confidence: 93, status: 'normal', suggestion: '维持当前管控措施' },
  { road_name: '京津高速', current_flow: 3890, predicted_flow: 4120, confidence: 87, status: 'congested', suggestion: '跨省通勤高峰，建议限流' }
])

// 获取置信度颜色
const getConfidenceColor = (confidence: number) => {
  if (confidence >= 90) return '#52c41a'
  if (confidence >= 80) return '#faad14'
  return '#ff4d4f'
}

// 获取状态类型
type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'
const getStatusType = (status: string): TagType => {
  const types: Record<string, TagType> = {
    normal: 'success',
    busy: 'warning',
    congested: 'danger'
  }
  return types[status] || 'info'
}

// 获取状态标签
const getStatusLabel = (status: string) => {
  const labels: Record<string, string> = {
    normal: '畅通',
    busy: '缓行',
    congested: '拥堵'
  }
  return labels[status] || '未知'
}

// 初始化准确率仪表盘
const initAccuracyChart = () => {
  if (!accuracyChartRef.value) return

  accuracyChart = echarts.init(accuracyChartRef.value)

  const option: echarts.EChartsOption = {
    series: [
      {
        type: 'gauge',
        startAngle: 200,
        endAngle: -20,
        min: 0,
        max: 100,
        splitNumber: 10,
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: '#52c41a' },
            { offset: 0.5, color: '#faad14' },
            { offset: 1, color: '#ff4d4f' }
          ])
        },
        progress: {
          show: true,
          width: 20
        },
        pointer: {
          show: false
        },
        axisLine: {
          lineStyle: {
            width: 20,
            color: [[1, 'rgba(0,0,0,0.1)']]
          }
        },
        axisTick: {
          show: false
        },
        splitLine: {
          show: false
        },
        axisLabel: {
          show: false
        },
        anchor: {
          show: false
        },
        title: {
          show: false
        },
        detail: {
          valueAnimation: true,
          width: '60%',
          lineHeight: 40,
          borderRadius: 8,
          offsetCenter: [0, '-10%'],
          fontSize: 32,
          fontWeight: 'bold',
          formatter: '{value}%',
          color: 'inherit'
        },
        data: [{ value: 96.8 }]
      }
    ]
  }

  accuracyChart.setOption(option)
}

// 初始化趋势对比图
const initTrendChart = () => {
  if (!trendChartRef.value) return

  trendChart = echarts.init(trendChartRef.value)

  const hours = Array.from({ length: 24 }, (_, i) => `${String(i).padStart(2, '0')}:00`)
  const actualData = [1200, 800, 600, 500, 600, 1000, 2500, 4500, 5200, 4800, 4200, 3800, 3500, 3200, 3000, 3500, 4200, 5000, 4500, 3800, 3200, 2800, 2200, 1600]
  const predictedData = [1180, 820, 580, 520, 620, 980, 2480, 4550, 5180, 4850, 4180, 3750, 3520, 3180, 2980, 3520, 4250, 5050, 4480, 3820, 3180, 2780, 2180, 1580]

  const option: echarts.EChartsOption = {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'cross'
      }
    },
    legend: {
      data: ['实际流量', '预测流量'],
      top: 10
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: hours
    },
    yAxis: {
      type: 'value',
      name: '车流量 (辆/h)',
      axisLabel: {
        formatter: (value: number) => {
          if (value >= 1000) return (value / 1000).toFixed(1) + 'k'
          return value.toString()
        }
      }
    },
    series: [
      {
        name: '实际流量',
        type: 'line',
        smooth: true,
        lineStyle: {
          width: 3,
          color: '#1890ff'
        },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(24, 144, 255, 0.3)' },
            { offset: 1, color: 'rgba(24, 144, 255, 0.05)' }
          ])
        },
        data: actualData
      },
      {
        name: '预测流量',
        type: 'line',
        smooth: true,
        lineStyle: {
          width: 3,
          color: '#52c41a',
          type: 'dashed'
        },
        data: predictedData
      }
    ]
  }

  trendChart.setOption(option)
}

// 初始化热力图
const initHeatmapChart = () => {
  if (!heatmapChartRef.value) return

  heatmapChart = echarts.init(heatmapChartRef.value)

  const days = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
  const hours = Array.from({ length: 24 }, (_, i) => `${String(i).padStart(2, '0')}:00`)

  // 生成热力图数据
  const data: number[][] = []
  for (let i = 0; i < 7; i++) {
    for (let j = 0; j < 24; j++) {
      let value = Math.random() * 100
      // 模拟早晚高峰
      if ((j >= 7 && j <= 9) || (j >= 17 && j <= 19)) {
        value = 60 + Math.random() * 40
      }
      // 周末略低
      if (i >= 5) {
        value *= 0.7
      }
      data.push([j, i, Math.round(value)])
    }
  }

  const option: echarts.EChartsOption = {
    tooltip: {
      position: 'top',
      formatter: (params: any) => {
        const value = params.value[2]
        let status = '畅通'
        if (value > 70) status = '拥堵'
        else if (value > 40) status = '缓行'
        return `${days[params.value[1]]} ${hours[params.value[0]]}<br/>拥堵指数: ${value}<br/>预测状态: ${status}`
      }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '10%',
      top: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: hours,
      splitArea: {
        show: true
      }
    },
    yAxis: {
      type: 'category',
      data: days,
      splitArea: {
        show: true
      }
    },
    visualMap: {
      min: 0,
      max: 100,
      calculable: true,
      orient: 'horizontal',
      left: 'center',
      bottom: '0%',
      inRange: {
        color: ['#52c41a', '#faad14', '#ff4d4f']
      }
    },
    series: [
      {
        type: 'heatmap',
        data: data,
        label: {
          show: false
        },
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowColor: 'rgba(0, 0, 0, 0.5)'
          }
        }
      }
    ]
  }

  heatmapChart.setOption(option)
}

// 处理窗口大小变化
const handleResize = () => {
  accuracyChart?.resize()
  trendChart?.resize()
  heatmapChart?.resize()
}

// 监听时间范围变化
watch(trendTimeRange, () => {
  // TODO: 根据时间范围更新数据
  initTrendChart()
})

onMounted(() => {
  initAccuracyChart()
  initTrendChart()
  initHeatmapChart()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  accuracyChart?.dispose()
  trendChart?.dispose()
  heatmapChart?.dispose()
})
</script>

<style lang="scss" scoped>
.prediction-page {
  padding: 24px;

  .page-header {
    margin-bottom: 24px;

    .page-title {
      margin: 0 0 8px;
      font-size: 22px;
      font-weight: 600;
      color: var(--el-text-color-primary);
    }

    .page-desc {
      margin: 0;
      font-size: 14px;
      color: var(--el-text-color-secondary);
    }
  }
}

// 指标概览
.metrics-overview {
  margin-bottom: 24px;

  .metric-card {
    background: var(--el-bg-color);
    border-radius: 8px;
    padding: 16px;
    border: 1px solid var(--el-border-color-light);
    height: 160px;

    .metric-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 12px;

      .metric-title {
        font-size: 14px;
        color: var(--el-text-color-secondary);
      }

      .el-icon {
        color: var(--el-text-color-placeholder);
        cursor: help;
      }
    }

    .metric-body {
      .gauge-chart {
        width: 100%;
        height: 100px;
      }

      .metric-value {
        .value {
          font-size: 36px;
          font-weight: 700;
          color: var(--el-text-color-primary);
        }

        .unit {
          font-size: 14px;
          color: var(--el-text-color-secondary);
          margin-left: 4px;
        }
      }

      .metric-trend {
        display: flex;
        align-items: center;
        gap: 4px;
        font-size: 13px;
        margin-top: 8px;

        &.down {
          color: #52c41a;
        }

        &.up {
          color: #ff4d4f;
        }
      }

      .metric-desc {
        font-size: 12px;
        color: var(--el-text-color-placeholder);
        margin-top: 8px;
      }
    }
  }
}

// 图表区域
.chart-section {
  background: var(--el-bg-color);
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 24px;
  border: 1px solid var(--el-border-color-light);

  .section-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 16px;

    .section-title {
      margin: 0;
      font-size: 16px;
      font-weight: 600;
      color: var(--el-text-color-primary);
    }

    .section-legend {
      display: flex;
      gap: 16px;

      .legend-item {
        display: flex;
        align-items: center;
        gap: 6px;
        font-size: 13px;
        color: var(--el-text-color-secondary);

        .dot {
          width: 12px;
          height: 12px;
          border-radius: 2px;

          &.low {
            background: #52c41a;
          }

          &.medium {
            background: #faad14;
          }

          &.high {
            background: #ff4d4f;
          }
        }
      }
    }
  }

  .chart-container {
    width: 100%;
    height: 350px;
  }
}

// 表格样式
.flow-value {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.flow-unit {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-left: 4px;
}

.suggestion-text {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>
