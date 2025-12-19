<template>
  <div class="realtime-page">
    <!-- 顶部统计卡片 -->
    <div class="stats-row">
      <el-row :gutter="16">
        <el-col :span="6">
          <div class="stat-card">
            <div class="stat-icon" style="background: linear-gradient(135deg, #667eea, #764ba2)">
              <el-icon :size="24"><Van /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.todayTotal.toLocaleString() }}</div>
              <div class="stat-label">今日总流量</div>
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card">
            <div class="stat-icon" style="background: linear-gradient(135deg, #f093fb, #f5576c)">
              <el-icon :size="24"><Money /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">¥{{ stats.todayRevenue.toLocaleString() }}</div>
              <div class="stat-label">今日总营收</div>
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card">
            <div class="stat-icon" style="background: linear-gradient(135deg, #4facfe, #00f2fe)">
              <el-icon :size="24"><Odometer /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.avgSpeed }} <small>km/h</small></div>
              <div class="stat-label">平均车速</div>
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card warning">
            <div class="stat-icon" style="background: linear-gradient(135deg, #fa709a, #fee140)">
              <el-icon :size="24"><Warning /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value alert">{{ stats.alertCount }}</div>
              <div class="stat-label">今日告警</div>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>

    <el-row :gutter="16">
      <!-- 左侧：违禁信息 -->
      <el-col :span="16">
        <!-- 套牌车检测 -->
        <div class="section-card">
          <div class="section-header">
            <div class="section-title">
              <el-icon color="#ff4d4f"><Warning /></el-icon>
              <span>套牌车检测</span>
              <el-badge :value="clonePlates.length" type="danger" />
            </div>
            <el-button type="primary" link>查看全部</el-button>
          </div>
          <div class="clone-plate-list">
            <div v-for="item in clonePlates" :key="item.id" class="clone-plate-item">
              <div class="plate-info">
                <span class="plate-number">{{ item.plateNumber }}</span>
                <el-tag type="danger" size="small">疑似套牌</el-tag>
              </div>
              <div class="conflict-info">
                <div class="location-item">
                  <el-icon><Location /></el-icon>
                  <span>{{ item.location1 }}</span>
                  <span class="time">{{ item.time1 }}</span>
                </div>
                <div class="vs">VS</div>
                <div class="location-item">
                  <el-icon><Location /></el-icon>
                  <span>{{ item.location2 }}</span>
                  <span class="time">{{ item.time2 }}</span>
                </div>
              </div>
              <div class="action-btns">
                <el-button type="primary" size="small">详情</el-button>
                <el-button type="warning" size="small">出警</el-button>
              </div>
            </div>
            <el-empty v-if="clonePlates.length === 0" description="暂无套牌车检测" />
          </div>
        </div>

        <!-- 其他违禁信息 -->
        <div class="section-card">
          <div class="section-header">
            <div class="section-title">
              <el-icon color="#fa8c16"><Bell /></el-icon>
              <span>违禁信息</span>
            </div>
            <el-radio-group v-model="violationType" size="small">
              <el-radio-button value="all">全部</el-radio-button>
              <el-radio-button value="overspeed">超速</el-radio-button>
              <el-radio-button value="overload">超载</el-radio-button>
              <el-radio-button value="illegal">违规</el-radio-button>
            </el-radio-group>
          </div>
          <el-table :data="violations" stripe max-height="300">
            <el-table-column prop="plateNumber" label="车牌号" width="120" />
            <el-table-column prop="vehicleType" label="车辆类型" width="100" />
            <el-table-column prop="violation" label="违禁行为" width="120">
              <template #default="{ row }">
                <el-tag :type="getViolationTagType(row.violationType)" size="small">
                  {{ row.violation }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="checkpoint" label="检测卡口" />
            <el-table-column prop="time" label="检测时间" width="160" />
            <el-table-column label="操作" width="100" fixed="right">
              <template #default>
                <el-button type="primary" link size="small">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>

      <!-- 右侧：站口压力预警 -->
      <el-col :span="8">
        <div class="section-card full-height">
          <div class="section-header">
            <div class="section-title">
              <el-icon color="#1890ff"><TrendCharts /></el-icon>
              <span>站口压力预警</span>
            </div>
          </div>
          <div class="pressure-list">
            <div 
              v-for="item in pressureWarnings" 
              :key="item.id" 
              class="pressure-item"
              :class="item.level"
            >
              <div class="station-info">
                <span class="station-name">{{ item.stationName }}</span>
                <el-tag :type="getPressureTagType(item.level)" size="small">
                  {{ getPressureLabel(item.level) }}
                </el-tag>
              </div>
              <div class="pressure-bar">
                <div 
                  class="pressure-fill" 
                  :style="{ width: item.pressure + '%' }"
                  :class="item.level"
                ></div>
              </div>
              <div class="pressure-detail">
                <span>当前流量: {{ item.currentFlow }} 辆/h</span>
                <span class="prediction">
                  <el-icon><TrendCharts /></el-icon>
                  预测: {{ item.predictedFlow }} 辆/h
                </span>
              </div>
              <div v-if="item.level !== 'normal'" class="ai-suggestion">
                <el-icon><MagicStick /></el-icon>
                {{ item.suggestion }}
              </div>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, watch } from 'vue'
import { Van, Money, Odometer, Warning, Location, Bell, TrendCharts, MagicStick } from '@element-plus/icons-vue'
import { getDailyStats, getViolations, getClonePlates, getPressureWarnings } from '@/api/admin/realtime'
import { getCheckpoints } from '@/api/admin/map'

defineOptions({ name: 'EtcRealtime' })

// 统计数据
const stats = reactive({
  todayTotal: 0,
  todayRevenue: 0,
  avgSpeed: 0,
  alertCount: 0
})

// 套牌车检测
const clonePlates = ref<any[]>([])

// 违禁类型筛选
const violationType = ref('all')

// 违禁信息列表
const violations = ref<any[]>([])

// 站口压力预警
const pressureWarnings = ref<any[]>([])

// 加载统计数据
const loadStats = async () => {
  try {
    const res = await getDailyStats()
    if (res.code === 200 && res.data) {
      const data = res.data as any
      stats.todayTotal = data.totalFlow || 0
      stats.todayRevenue = data.totalRevenue || 0
      stats.avgSpeed = data.avgSpeed || 85
      stats.alertCount = data.alertCount || 0
    }
  } catch (e) {
    console.error('加载统计失败:', e)
  }
}

// 加载套牌车检测
const loadClonePlates = async () => {
  try {
    const res = await getClonePlates({ pageSize: 5 })
    if (res.code === 200 && res.data?.list) {
      clonePlates.value = res.data.list.map((item: any) => ({
        id: item.id,
        plateNumber: item.plateNumber,
        location1: item.checkpoint1 || '卡口A',
        time1: item.time1 ? new Date(item.time1).toLocaleTimeString('zh-CN') : '--',
        location2: item.checkpoint2 || '卡口B',
        time2: item.time2 ? new Date(item.time2).toLocaleTimeString('zh-CN') : '--'
      }))
    }
  } catch (e) {
    console.error('加载套牌检测失败:', e)
  }
}

// 加载违禁信息
const loadViolations = async () => {
  try {
    // 传递违禁类型过滤器
    const params: Record<string, any> = { pageSize: 10 }
    if (violationType.value !== 'all') {
      params.type = violationType.value
    }
    const res = await getViolations(params)
    if (res.code === 200 && res.data?.list) {
      violations.value = res.data.list.map((item: any) => ({
        plateNumber: item.plateNumber,
        vehicleType: item.vehicleType || '小型车',
        violation: item.description || item.type,
        violationType: item.type || 'illegal',
        checkpoint: item.checkpointName || item.checkpoint || '未知卡口',
        time: item.detectTime || item.time ? new Date(item.detectTime || item.time).toLocaleString('zh-CN') : '--'
      }))
    }
  } catch (e) {
    console.error('加载违禁信息失败:', e)
  }
}

// 加载站口压力预警 - 显示全部19个卡口
const loadPressureWarnings = async () => {
  try {
    // 使用专门的压力预警接口
    const res = await getPressureWarnings()
    if (res.code === 200 && res.data) {
      // 后端已返回全部19个卡口，不再slice
      pressureWarnings.value = res.data.map((item: any) => ({
        id: item.id,
        stationName: item.stationName,
        level: item.level || 'normal',
        pressure: item.pressure || 0,
        currentFlow: item.currentFlow || 0,
        predictedFlow: item.predictedFlow || 0,
        suggestion: item.suggestion || ''
      }))
    }
  } catch (e) {
    console.error('加载压力预警失败:', e)
  }
}

// 监听违禁类型变化，重新加载数据
watch(violationType, () => {
  loadViolations()
})

// 加载所有数据
const loadAllData = async () => {
  await Promise.all([
    loadStats(),
    loadClonePlates(),
    loadViolations(),
    loadPressureWarnings()
  ])
}

let refreshTimer: number | null = null

onMounted(() => {
  loadAllData()
  // 每30秒刷新
  refreshTimer = window.setInterval(loadAllData, 30000)
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
})

const getViolationTagType = (type: string) => {
  const types: Record<string, string> = {
    overspeed: 'danger',
    overload: 'warning',
    illegal: 'danger'
  }
  return types[type] || 'info'
}

const getPressureTagType = (level: string) => {
  const types: Record<string, string> = {
    danger: 'danger',
    warning: 'warning',
    normal: 'success'
  }
  return types[level] || 'info'
}

const getPressureLabel = (level: string) => {
  const labels: Record<string, string> = {
    danger: '拥堵',
    warning: '缓行',
    normal: '畅通'
  }
  return labels[level] || '未知'
}
</script>

<style lang="scss" scoped>
.realtime-page {
  padding: 20px;
  background: #f5f7fa;
  min-height: calc(100vh - 120px);
}

.stats-row {
  margin-bottom: 16px;

  .stat-card {
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 20px;
    background: #fff;
    border-radius: 8px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);

    .stat-icon {
      width: 56px;
      height: 56px;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      color: #fff;
    }

    .stat-info {
      .stat-value {
        font-size: 24px;
        font-weight: 700;
        color: #1f2329;

        small {
          font-size: 14px;
          font-weight: normal;
          color: #8c8c8c;
        }

        &.alert {
          color: #ff4d4f;
        }
      }

      .stat-label {
        font-size: 13px;
        color: #8c8c8c;
        margin-top: 4px;
      }
    }
  }
}

.section-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);

  &.full-height {
    height: calc(100% - 16px);
  }

  .section-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 16px;
    padding-bottom: 12px;
    border-bottom: 1px solid #f0f0f0;

    .section-title {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 15px;
      font-weight: 600;
      color: #1f2329;
    }
  }
}

// 套牌车列表
.clone-plate-list {
  .clone-plate-item {
    padding: 12px;
    border: 1px solid #ffccc7;
    border-radius: 8px;
    background: #fff2f0;
    margin-bottom: 12px;

    .plate-info {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 8px;

      .plate-number {
        font-size: 16px;
        font-weight: 600;
        color: #1f2329;
      }
    }

    .conflict-info {
      display: flex;
      align-items: center;
      gap: 12px;
      font-size: 13px;
      color: #595959;

      .location-item {
        display: flex;
        align-items: center;
        gap: 4px;

        .time {
          color: #8c8c8c;
          margin-left: 8px;
        }
      }

      .vs {
        color: #ff4d4f;
        font-weight: 600;
      }
    }

    .action-btns {
      margin-top: 12px;
      display: flex;
      gap: 8px;
    }
  }
}

// 压力预警列表
.pressure-list {
  .pressure-item {
    padding: 12px;
    border-radius: 8px;
    margin-bottom: 12px;
    border: 1px solid #f0f0f0;

    &.danger {
      border-color: #ffccc7;
      background: #fff2f0;
    }

    &.warning {
      border-color: #ffe58f;
      background: #fffbe6;
    }

    .station-info {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 8px;

      .station-name {
        font-weight: 600;
        color: #1f2329;
      }
    }

    .pressure-bar {
      height: 8px;
      background: #f0f0f0;
      border-radius: 4px;
      overflow: hidden;
      margin-bottom: 8px;

      .pressure-fill {
        height: 100%;
        border-radius: 4px;
        transition: width 0.3s;

        &.danger {
          background: linear-gradient(90deg, #ff4d4f, #ff7875);
        }

        &.warning {
          background: linear-gradient(90deg, #faad14, #ffc53d);
        }

        &.normal {
          background: linear-gradient(90deg, #52c41a, #73d13d);
        }
      }
    }

    .pressure-detail {
      display: flex;
      justify-content: space-between;
      font-size: 12px;
      color: #8c8c8c;

      .prediction {
        display: flex;
        align-items: center;
        gap: 4px;
        color: #1890ff;
      }
    }

    .ai-suggestion {
      margin-top: 8px;
      padding: 8px;
      background: rgba(24, 144, 255, 0.1);
      border-radius: 4px;
      font-size: 12px;
      color: #1890ff;
      display: flex;
      align-items: center;
      gap: 4px;
    }
  }
}
</style>
