<template>
  <div class="monitor-dashboard">
    <!-- 套牌车检测弹窗 -->
    <ClonePlateAlert />
    
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
import { XuzhouTrafficMap, BloomStats, RegionRank, AlertTicker, ClonePlateAlert } from '@/components/business/etc'
import { getDailyStats, getViolations, getClonePlates } from '@/api/admin/realtime'
import { getCheckpoints } from '@/api/admin/map'
import { checkpoints as localCheckpoints } from '@/config/checkpoints'
import { useSimulatedClock } from '@/hooks/core/useSimulatedClock'

defineOptions({ name: 'EtcMonitor' })

// 地图引用
const mapRef = ref()

// 当前时间
const { timeText: currentTime, dateText: currentDate } = useSimulatedClock({ autoStart: true })

// 选中的收费站
const selectedStation = ref<any>(null)

// 布隆过滤器统计数据（本地/外地车辆）
const bloomData = ref({
  local: 0,
  foreign: 0
})

// 区域排名数据
const regionRankData = ref<{ region: string; count: number; trend: number }[]>([])

// 告警列表
const alertList = ref<{ id: string; type: 'overspeed' | 'duplicate' | 'dispatch' | 'illegal'; message: string; plate: string; time: string; speed?: number }[]>([])

// 底部指标数据
const metrics = ref({
  todayTotal: 0,
  todayRevenue: 0,
  avgSpeed: 0,
  onlineStations: 0,
  totalStations: 0,
  alertCount: 0
})

// 加载统计数据
const loadDailyStats = async () => {
  try {
    console.log('🔄 开始加载日统计数据...')
    const res = await getDailyStats()
    console.log('📊 日统计响应:', res)
    if (res.code === 200 && res.data) {
      const data = res.data as any
      metrics.value = {
        todayTotal: data.totalFlow || 0,
        todayRevenue: data.totalRevenue || 0,
        avgSpeed: data.avgSpeed || 85.6,
        onlineStations: data.onlineCount || 0,
        totalStations: data.checkpointCount || 0,
        alertCount: data.alertCount || 0
      }
    }
  } catch (e) {
    console.error('加载日统计失败:', e)
  }
}

// 加载区域排名
const loadRegionRank = async () => {
  try {
    console.log('🔄 开始加载区域排名...')
    const res = await getCheckpoints()
    console.log('🗺️ 卡口数据响应:', res)
    if (res.code === 200 && res.data) {
      // 卡口ID到区域名称的映射（解决后端中文乱码）
      const regionByCheckpointId: Record<number, string> = {
        1: '睢宁县', 2: '铜山区', 3: '铜山区', 4: '睢宁县', 5: '沛县', 6: '新沂市',
        7: '沛县', 8: '邳州市', 9: '贾汪区', 10: '邳州市', 11: '邳州市', 12: '新沂市',
        13: '邳州市', 14: '邳州市', 15: '铜山区', 16: '铜山区', 17: '睢宁县',
        18: '睢宁县', 19: '睢宁县'
      }
      
      // 按区域分组统计流量
      const regionMap = new Map<string, number>()
      res.data.forEach((cp: any) => {
        const region = regionByCheckpointId[cp.id] || '其他'
        regionMap.set(region, (regionMap.get(region) || 0) + (cp.currentFlow || 0))
      })
      // 转为数组并排序
      regionRankData.value = Array.from(regionMap.entries())
        .map(([region, count]) => ({
          region,
          count,
          trend: Math.floor(Math.random() * 20) - 10 // 暂用随机趋势
        }))
        .sort((a, b) => b.count - a.count)
        .slice(0, 10)
      console.log('📊 区域排名:', regionRankData.value)
      // 计算本地/外地车辆（本地按70%估算）
      const total = res.data.reduce((sum: number, cp: any) => sum + (cp.currentFlow || 0), 0)
      bloomData.value = {
        local: Math.floor(total * 0.7),
        foreign: Math.floor(total * 0.3)
      }
    }
  } catch (e) {
    console.error('加载区域排名失败:', e)
  }
}

// 加载告警数据
const loadAlerts = async () => {
  try {
    // 获取违规信息
    const [violationsRes, clonePlatesRes] = await Promise.all([
      getViolations({ pageSize: 5 }),
      getClonePlates({ pageSize: 3 })
    ])
    
    const alerts: typeof alertList.value = []
    
    // 处理违规信息
    if (violationsRes.code === 200 && violationsRes.data?.list) {
      violationsRes.data.list.forEach((v: any) => {
        alerts.push({
          id: v.id,
          type: v.type === 'overspeed' ? 'overspeed' : 'illegal',
          message: v.description || `检测到违规车辆`,
          plate: v.plateNumber || '未知',
          time: v.detectTime ? new Date(v.detectTime).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) : '--:--',
          speed: v.speed
        })
      })
    }
    
    // 处理套牌信息
    if (clonePlatesRes.code === 200 && clonePlatesRes.data?.list) {
      clonePlatesRes.data.list.forEach((c: any) => {
        alerts.push({
          id: c.id,
          type: 'duplicate',
          message: '发现套牌嫌疑车辆',
          plate: c.plateNumber || '未知',
          time: c.detectTime ? new Date(c.detectTime).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) : '--:--'
        })
      })
    }
    
    alertList.value = alerts.slice(0, 8)
  } catch (e) {
    console.error('加载告警失败:', e)
  }
}

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
  console.log('查看站点详情:', selectedStation.value)
}

let dataTimer: number | null = null

// 加载所有数据
const loadAllData = async () => {
  await Promise.all([
    loadDailyStats(),
    loadRegionRank(),
    loadAlerts()
  ])
}

onMounted(() => {
  loadAllData()
  // 每30秒刷新数据
  dataTimer = window.setInterval(loadAllData, 30000)
})

onUnmounted(() => {
  if (dataTimer) clearInterval(dataTimer)
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
