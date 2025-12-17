<template>
  <div class="monitor-dashboard">
    <!-- 顶部标题栏 -->
    <div class="top-header">
      <div class="header-left">
        <h1 class="system-title">彭城交通 大数据管理平台</h1>
        <span class="system-subtitle">实时监控指挥舱</span>
      </div>
      <div class="header-center">
        <div class="weather-info">
          <el-icon><Sunny /></el-icon>
          <span>晴 12°C</span>
        </div>
      </div>
      <div class="header-right">
        <div class="current-time">
          <span class="time">{{ currentTime }}</span>
          <span class="date">{{ currentDate }}</span>
        </div>
      </div>
    </div>

    <!-- 主体内容区域 -->
    <div class="main-content">
      <!-- 左侧面板 -->
      <div class="left-panel">
        <BloomStats :local-count="bloomData.local" :foreign-count="bloomData.foreign" />
        <RegionRank :data="regionRankData" />
      </div>

      <!-- 中央地图区域 - 徐州实时路况 -->
      <div class="center-panel">
        <div class="map-container">
          <XuzhouTrafficMap ref="mapRef" />
        </div>

        <!-- 底部指标栏 -->
        <div class="metrics-bar">
          <div class="metric-card">
            <div class="metric-icon" style="background: linear-gradient(135deg, #667eea, #764ba2)">
              <el-icon :size="20"><Van /></el-icon>
            </div>
            <div class="metric-info">
              <span class="metric-value">{{ formatNumber(metrics.todayTotal) }}</span>
              <span class="metric-label">今日总流量</span>
            </div>
          </div>
          <div class="metric-card">
            <div class="metric-icon" style="background: linear-gradient(135deg, #f093fb, #f5576c)">
              <el-icon :size="20"><Money /></el-icon>
            </div>
            <div class="metric-info">
              <span class="metric-value">¥{{ formatNumber(metrics.todayRevenue) }}</span>
              <span class="metric-label">今日总营收</span>
            </div>
          </div>
          <div class="metric-card">
            <div class="metric-icon" style="background: linear-gradient(135deg, #4facfe, #00f2fe)">
              <el-icon :size="20"><Odometer /></el-icon>
            </div>
            <div class="metric-info">
              <span class="metric-value">{{ metrics.avgSpeed }} <small>km/h</small></span>
              <span class="metric-label">平均车速</span>
            </div>
          </div>
          <div class="metric-card">
            <div class="metric-icon" style="background: linear-gradient(135deg, #43e97b, #38f9d7)">
              <el-icon :size="20"><Connection /></el-icon>
            </div>
            <div class="metric-info">
              <span class="metric-value">{{ metrics.onlineStations }}/{{ metrics.totalStations }}</span>
              <span class="metric-label">在线站点</span>
            </div>
          </div>
          <div class="metric-card">
            <div class="metric-icon warning" style="background: linear-gradient(135deg, #fa709a, #fee140)">
              <el-icon :size="20"><Warning /></el-icon>
            </div>
            <div class="metric-info">
              <span class="metric-value alert">{{ metrics.alertCount }}</span>
              <span class="metric-label">今日告警</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧面板 -->
      <div class="right-panel">
        <AlertTicker :alerts="alertList" />
        
        <!-- 收费站详情卡片 -->
        <Transition name="slide-fade">
          <div v-if="selectedStation" class="station-detail-card">
            <div class="card-header">
              <span class="station-name">{{ selectedStation.name }}</span>
              <el-tag :type="getStatusType(selectedStation.status)" size="small">
                {{ getStatusLabel(selectedStation.status) }}
              </el-tag>
            </div>
            <div class="card-body">
              <div class="stat-row">
                <span class="stat-label">实时车流量</span>
                <span class="stat-value">{{ selectedStation.flow }} <small>辆/小时</small></span>
              </div>
              <div class="stat-row">
                <span class="stat-label">今日通行量</span>
                <span class="stat-value">{{ selectedStation.todayTotal }} <small>辆</small></span>
              </div>
              <div class="stat-row">
                <span class="stat-label">平均车速</span>
                <span class="stat-value">{{ selectedStation.avgSpeed }} <small>km/h</small></span>
              </div>
            </div>
            <div class="card-footer">
              <el-button type="primary" size="small" @click="viewStationDetail">
                查看详情
              </el-button>
              <el-button size="small" @click="selectedStation = null">关闭</el-button>
            </div>
          </div>
        </Transition>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { Sunny, Van, Money, Odometer, Connection, Warning } from '@element-plus/icons-vue'
import { XuzhouTrafficMap, BloomStats, RegionRank, AlertTicker } from '@/components/business/etc'

defineOptions({ name: 'EtcMonitor' })

// 地图引用
const mapRef = ref()

// 当前时间
const currentTime = ref('')
const currentDate = ref('')

// 选中的收费站
const selectedStation = ref<any>(null)

// 布隆过滤器统计数据
const bloomData = ref({
  local: 89120,
  foreign: 39336
})

// 区域排名数据（改为徐州区域）
const regionRankData = ref([
  { region: '云龙区', count: 23456, trend: 12 },
  { region: '鼓楼区', count: 21234, trend: -5 },
  { region: '泉山区', count: 18765, trend: 8 },
  { region: '铜山区', count: 15432, trend: 15 },
  { region: '贾汪区', count: 12345, trend: -3 },
  { region: '沛县', count: 11234, trend: 6 },
  { region: '丰县', count: 9876, trend: -8 },
  { region: '睢宁县', count: 8765, trend: 4 },
  { region: '邳州市', count: 5432, trend: 2 },
  { region: '新沂市', count: 4321, trend: -1 }
])

// 告警列表（改为徐州车牌）
const alertList = ref([
  { id: '1', type: 'overspeed' as const, message: '检测到超速车辆，时速152km/h', plate: '苏C·88888', time: '14:32', speed: 152 },
  { id: '2', type: 'duplicate' as const, message: '发现套牌嫌疑车辆', plate: '苏C·12345', time: '14:28' },
  { id: '3', type: 'dispatch' as const, message: '已派出警力前往处置', plate: '苏C·88888', time: '14:35' },
  { id: '4', type: 'illegal' as const, message: '检测到违法占用应急车道', plate: '京C·66666', time: '14:20' },
  { id: '5', type: 'overspeed' as const, message: '检测到超速车辆，时速138km/h', plate: '冀A·11111', time: '14:15', speed: 138 }
])

// 底部指标数据
const metrics = ref({
  todayTotal: 128456,
  todayRevenue: 2856789,
  avgSpeed: 92.3,
  onlineStations: 156,
  totalStations: 162,
  alertCount: 23
})

// 格式化数字
const formatNumber = (num: number) => {
  return num.toLocaleString()
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

// 查看站点详情
const viewStationDetail = () => {
  // TODO: 跳转到详情页或打开弹窗
  console.log('查看站点详情:', selectedStation.value)
}

// 更新时间
const updateTime = () => {
  const now = new Date()
  currentTime.value = now.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
  currentDate.value = now.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', weekday: 'long' })
}

let timeTimer: number | null = null

onMounted(() => {
  updateTime()
  timeTimer = window.setInterval(updateTime, 1000)
})

onUnmounted(() => {
  if (timeTimer) {
    clearInterval(timeTimer)
  }
})
</script>

<style lang="scss" scoped>
.monitor-dashboard {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100vh;
  overflow: hidden;
  background: #f5f7fa;
}

// 顶部标题栏
.top-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  background: #fff;
  border-bottom: 1px solid #e8e8e8;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  flex-shrink: 0;

  .header-left {
    .system-title {
      margin: 0;
      font-size: 22px;
      font-weight: 700;
      color: #1890ff;
      letter-spacing: 2px;
    }

    .system-subtitle {
      font-size: 13px;
      color: #8c8c8c;
    }
  }

  .header-center {
    .weather-info {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 6px 14px;
      background: #f0f5ff;
      border-radius: 20px;
      color: #1890ff;
      font-size: 14px;
    }
  }

  .header-right {
    .current-time {
      text-align: right;

      .time {
        display: block;
        font-size: 24px;
        font-weight: 600;
        color: #1f2329;
        font-family: 'Courier New', monospace;
      }

      .date {
        font-size: 12px;
        color: #8c8c8c;
      }
    }
  }
}

// 主体内容区域
.main-content {
  display: flex;
  flex: 1;
  overflow: hidden;
  padding: 16px;
  gap: 16px;
}

// 左侧面板
.left-panel {
  width: 320px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow-y: auto;
}

// 中央面板
.center-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

// 地图容器 - 独立区域
.map-container {
  flex: 1;
  min-height: 500px;
  height: calc(100vh - 300px);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  border: 1px solid #e8e8e8;
  background: #fff;
}

// 底部指标栏
.metrics-bar {
  display: flex;
  gap: 12px;
  padding: 12px 16px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  flex-shrink: 0;
  overflow-x: auto;
}

.metric-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 12px;
  border-right: 1px solid #f0f0f0;
  white-space: nowrap;

  &:last-child {
    border-right: none;
  }

  .metric-icon {
    width: 40px;
    height: 40px;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    flex-shrink: 0;
  }

  .metric-info {
    display: flex;
    flex-direction: column;

    .metric-value {
      font-size: 18px;
      font-weight: 700;
      color: #1f2329;

      &.alert {
        color: #ff4d4f;
      }

      small {
        font-size: 11px;
        font-weight: normal;
        color: #8c8c8c;
      }
    }

    .metric-label {
      font-size: 11px;
      color: #8c8c8c;
    }
  }
}

// 右侧面板
.right-panel {
  width: 320px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow-y: auto;
}

// 收费站详情卡片
.station-detail-card {
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e8e8e8;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);

  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 16px;
    background: #fafafa;
    border-bottom: 1px solid #f0f0f0;

    .station-name {
      color: #1f2329;
      font-weight: 600;
    }
  }

  .card-body {
    padding: 16px;

    .stat-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 8px 0;
      border-bottom: 1px dashed #f0f0f0;

      &:last-child {
        border-bottom: none;
      }

      .stat-label {
        color: #8c8c8c;
        font-size: 13px;
      }

      .stat-value {
        color: #1f2329;
        font-size: 18px;
        font-weight: 600;

        small {
          font-size: 12px;
          font-weight: normal;
          color: #8c8c8c;
        }
      }
    }
  }

  .card-footer {
    display: flex;
    gap: 8px;
    padding: 12px 16px;
    background: #fafafa;
  }
}

// 动画
.slide-fade-enter-active,
.slide-fade-leave-active {
  transition: all 0.3s ease;
}

.slide-fade-enter-from,
.slide-fade-leave-to {
  opacity: 0;
  transform: translateX(20px);
}

// 响应式适配
@media (max-width: 1400px) {
  .left-panel,
  .right-panel {
    width: 280px;
  }
}

@media (max-width: 1200px) {
  .main-content {
    flex-wrap: wrap;
  }

  .left-panel,
  .right-panel {
    width: 100%;
    flex-direction: row;
    overflow-x: auto;
  }

  .center-panel {
    width: 100%;
    order: -1;
  }
}
</style>
