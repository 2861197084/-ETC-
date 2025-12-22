<template>
  <div class="analysis-page">
    <div class="page-header">
      <div class="title-wrap">
        <h2 class="page-title">预测分析</h2>
        <p class="page-desc">基于 Time‑MoE 的卡口×方向 5 分钟粒度预测（未来 1h）</p>
      </div>

      <div class="header-actions">
        <el-switch v-model="autoRefresh" active-text="自动每分钟刷新" />
        <el-button :loading="loading" type="primary" @click="triggerOnce">立即预测</el-button>
      </div>
    </div>

    <el-card class="control-card" shadow="never">
      <div class="controls">
        <div class="control-item">
          <span class="label">卡口</span>
          <el-select v-model="checkpointId" filterable style="width: 260px" @change="triggerOnce">
            <el-option v-for="cp in checkpointOptions" :key="cp.id" :label="cp.name" :value="cp.id" />
          </el-select>
        </div>

        <div class="control-item">
          <span class="label">方向</span>
          <el-radio-group v-model="fxlx" @change="triggerOnce">
            <el-radio-button value="1">进城</el-radio-button>
            <el-radio-button value="2">出城</el-radio-button>
          </el-radio-group>
        </div>

        <div class="control-item meta">
          <el-tag v-if="pending" type="warning" effect="dark">预测中…</el-tag>
          <el-tag v-else type="success" effect="dark">已更新</el-tag>
          <span class="meta-text">start: {{ startTime || '--' }}</span>
          <span class="meta-text">updated: {{ updatedAt || '--' }}</span>
        </div>
      </div>
    </el-card>

    <div class="metrics-grid">
      <el-card class="metric-card" shadow="never">
        <div class="metric-title">未来 1 小时总量</div>
        <div class="metric-value">{{ sumValue.toLocaleString() }}</div>
        <div class="metric-sub">用于快速评估整体压力（展示用）</div>
      </el-card>

      <el-card class="metric-card" shadow="never">
        <div class="metric-title">峰值</div>
        <div class="metric-value">{{ peakValue.toLocaleString() }}</div>
        <div class="metric-sub">峰值时间：{{ peakTime || '--' }}</div>
      </el-card>

      <el-card class="metric-card" shadow="never">
        <div class="metric-title">均值</div>
        <div class="metric-value">{{ avgValue.toLocaleString() }}</div>
        <div class="metric-sub">12 点均值</div>
      </el-card>

      <el-card class="metric-card" shadow="never">
        <div class="metric-title">置信提示</div>
        <div class="metric-value">{{ healthLabel }}</div>
      </el-card>
    </div>

    <div class="charts-grid">
      <el-card class="chart-card" shadow="never">
        <div class="chart-header">
          <div class="chart-title">未来 12 点预测曲线（5min × 12）</div>
        </div>
        <div class="chart-body">
          <div ref="lineChartRef" class="chart" />
        </div>
      </el-card>

      <el-card class="chart-card" shadow="never">
        <div class="chart-header">
          <div class="chart-title">分段柱状（高亮峰值）</div>
        </div>
        <div class="chart-body">
          <div ref="barChartRef" class="chart" />
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { checkpoints } from '@/config/checkpoints'
import { refreshForecast, getLatestForecast } from '@/api/admin/analysis'
import { useChart } from '@/hooks/core/useChart'

defineOptions({ name: 'EtcAnalysis' })

const checkpointOptions = checkpoints.map((c) => ({ id: c.id, name: c.name }))
const checkpointId = ref(checkpointOptions[0]?.id ?? 'CP001')
const fxlx = ref<'1' | '2'>('1')

const autoRefresh = ref(true)
const loading = ref(false)
const pending = ref(false)

const startTime = ref('')
const updatedAt = ref('')
const times = ref<string[]>([])
const values = ref<number[]>([])

// charts
const line = useChart({ initDelay: 0 })
const bar = useChart({ initDelay: 0 })
const lineChartRef = line.chartRef
const barChartRef = bar.chartRef

const sumValue = computed(() => values.value.reduce((a, b) => a + (Number.isFinite(b) ? b : 0), 0))
const avgValue = computed(() => (values.value.length ? Math.round(sumValue.value / values.value.length) : 0))
const peak = computed(() => {
  if (!values.value.length) return { v: 0, i: -1 }
  let bestV = -Infinity
  let bestI = -1
  values.value.forEach((v, i) => {
    if (v > bestV) {
      bestV = v
      bestI = i
    }
  })
  return { v: Math.round(bestV), i: bestI }
})
const peakValue = computed(() => (peak.value.i >= 0 ? peak.value.v : 0))
const peakTime = computed(() => (peak.value.i >= 0 ? times.value[peak.value.i] : ''))

const healthLabel = ref('--')

function pickHealthLabel() {
  // 需求：每次预测更新前端时，在 平稳 / 较平稳 / 波动 三者中随机选；
  // 且 平稳、较平稳 概率更大。
  const r = Math.random()
  if (r < 0.45) return '平稳'
  if (r < 0.85) return '较平稳'
  return '波动'
}

function buildLineOption() {
  const x = times.value.map((t) => t.slice(11, 16))
  const y = values.value
  const { getAxisLineStyle, getSplitLineStyle, getAxisLabelStyle, getTooltipStyle, getLegendStyle, useChartOps } = line
  const { themeColor } = useChartOps()

  return {
    // 不展示任何“暂无数据”等标题占位（即使历史 option 里有 title，也通过 show=false 覆盖掉）
    title: { show: false },
    tooltip: { trigger: 'axis', ...getTooltipStyle() },
    legend: { data: ['预测'], ...getLegendStyle() },
    grid: { left: 40, right: 24, top: 40, bottom: 32, containLabel: true },
    xAxis: {
      type: 'category',
      data: x,
      axisLine: getAxisLineStyle(true),
      axisTick: { show: false },
      axisLabel: getAxisLabelStyle(true)
    },
    yAxis: {
      type: 'value',
      axisLine: getAxisLineStyle(true),
      splitLine: getSplitLineStyle(true),
      axisLabel: getAxisLabelStyle(true)
    },
    series: [
      {
        name: '预测',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { width: 2, type: 'dashed', color: themeColor },
        itemStyle: { color: themeColor },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(24, 144, 255, 0.28)' },
              { offset: 1, color: 'rgba(24, 144, 255, 0.03)' }
            ]
          }
        },
        data: y,
        markPoint: peak.value.i >= 0 ? { data: [{ type: 'max', name: '峰值' }] } : undefined
      }
    ]
  }
}

function buildBarOption() {
  const x = times.value.map((t) => t.slice(11, 16))
  const y = values.value
  const { getAxisLineStyle, getSplitLineStyle, getAxisLabelStyle, getTooltipStyle, useChartOps } = bar
  const { themeColor } = useChartOps()

  return {
    // 不展示任何“暂无数据”等标题占位
    title: { show: false },
    tooltip: { trigger: 'axis', ...getTooltipStyle() },
    grid: { left: 40, right: 24, top: 24, bottom: 32, containLabel: true },
    xAxis: {
      type: 'category',
      data: x,
      axisLine: getAxisLineStyle(true),
      axisTick: { show: false },
      axisLabel: getAxisLabelStyle(true)
    },
    yAxis: {
      type: 'value',
      axisLine: getAxisLineStyle(true),
      splitLine: getSplitLineStyle(true),
      axisLabel: getAxisLabelStyle(true)
    },
    series: [
      {
        type: 'bar',
        data: y.map((v, i) => ({
          value: v,
          itemStyle:
            peak.value.i === i
              ? { color: '#ff4d4f' }
              : {
                  color: {
                    type: 'linear',
                    x: 0,
                    y: 0,
                    x2: 0,
                    y2: 1,
                    colorStops: [
                      { offset: 0, color: themeColor },
                      { offset: 1, color: 'rgba(24, 144, 255, 0.15)' }
                    ]
                  }
                }
        })),
        barWidth: 18,
        borderRadius: [6, 6, 0, 0]
      }
    ]
  }
}

function renderCharts() {
  if (!times.value.length || !values.value.length) return
  if (!line.isChartInitialized()) line.initChart(buildLineOption())
  else line.updateChart(buildLineOption())

  if (!bar.isChartInitialized()) bar.initChart(buildBarOption())
  else bar.updateChart(buildBarOption())
}

function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

let refreshSeq = 0
let minuteTimer: number | null = null

async function triggerOnce() {
  const seq = ++refreshSeq
  loading.value = true
  pending.value = true
  try {
    // 1) enqueue refresh request
    const r1 = await refreshForecast({ checkpointId: checkpointId.value, fxlx: fxlx.value })
    if (r1.code !== 200) throw new Error(r1.msg || 'refresh failed')

    // 2) poll latest until ready
    const deadline = Date.now() + 55_000
    while (Date.now() < deadline) {
      if (seq !== refreshSeq) return
      const r2 = await getLatestForecast({ checkpointId: checkpointId.value, fxlx: fxlx.value })
      if (r2.code === 200 && r2.data) {
        const d = r2.data
        pending.value = !!d.pending
        // 有历史预测也先展示，避免一直“暂无数据”；pending=true 时继续轮询直到新一轮预测完成
        if (d.values?.length) {
          startTime.value = d.startTime || ''
          updatedAt.value = d.updatedAt || ''
          times.value = d.times || []
          values.value = (d.values || []).map((v) => Math.max(0, Math.round(v)))
          healthLabel.value = pickHealthLabel()
          renderCharts()
          if (!d.pending) return
        }
      }
      await sleep(2000)
    }
    // 只有在“连历史预测都没有”的情况下才提示，避免每分钟弹窗骚扰
    if (!values.value.length) {
      ElMessage.warning('预测生成中，请稍后…（Spark 侧可能尚未处理请求）')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '预测失败')
  } finally {
    if (seq === refreshSeq) loading.value = false
  }
}

onMounted(() => {
  triggerOnce()
  minuteTimer = window.setInterval(() => {
    if (!autoRefresh.value) return
    triggerOnce()
  }, 60_000)
})

onUnmounted(() => {
  if (minuteTimer) window.clearInterval(minuteTimer)
  minuteTimer = null
})
</script>

<style scoped lang="scss">
.analysis-page {
  padding: 18px;
}

.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 14px;

  .page-title {
    margin: 0;
    font-size: 22px;
    font-weight: 700;
    color: var(--el-text-color-primary);
    letter-spacing: 1px;
  }

  .page-desc {
    margin: 6px 0 0;
    color: var(--el-text-color-secondary);
    font-size: 13px;
  }

  .header-actions {
    display: flex;
    align-items: center;
    gap: 12px;
  }
}

.control-card {
  margin-bottom: 14px;
}

.controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;

  .control-item {
    display: flex;
    align-items: center;
    gap: 10px;
    .label {
      width: 40px;
      color: var(--el-text-color-secondary);
    }
  }

  .meta {
    margin-left: auto;
    gap: 10px;
    .meta-text {
      color: var(--el-text-color-secondary);
      font-size: 12px;
    }
  }
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 14px;

  @media (max-width: 1200px) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  @media (max-width: 720px) {
    grid-template-columns: 1fr;
  }
}

.metric-card {
  border-radius: 10px;
  background: linear-gradient(135deg, rgba(24, 144, 255, 0.10), rgba(24, 144, 255, 0.02));

  .metric-title {
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }
  .metric-value {
    margin-top: 6px;
    font-size: 26px;
    font-weight: 800;
    color: var(--el-text-color-primary);
    font-variant-numeric: tabular-nums;
  }
  .metric-sub {
    margin-top: 6px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}

.charts-grid {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 14px;

  @media (max-width: 1200px) {
    grid-template-columns: 1fr;
  }
}

.chart-card {
  border-radius: 10px;
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 6px;

  .chart-title {
    font-size: 14px;
    font-weight: 700;
    color: var(--el-text-color-primary);
  }

  .chart-sub {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}

.chart-body {
  height: 340px;
}

.chart {
  width: 100%;
  height: 100%;
}
</style>


