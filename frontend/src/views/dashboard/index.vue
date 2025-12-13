<script setup lang="ts">
import { onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'

// 组件导入
import KPIStatCard from '@/components/dashboard/KPIStatCard.vue'
import ChinaMapChart from '@/components/charts/ChinaMapChart.vue'
import TrendLineChart from '@/components/charts/TrendLineChart.vue'
import RingPieChart from '@/components/charts/RingPieChart.vue'
import BarRankChart from '@/components/charts/BarRankChart.vue'
import Bar3DChart from '@/components/charts/Bar3DChart.vue'
import AlertTicker from '@/components/dashboard/AlertTicker.vue'
import DigitalClock from '@/components/dashboard/DigitalClock.vue'

// Mock 数据
import { useDashboardData } from '@/composables/useDashboardData'

const router = useRouter()
const { 
  dashboardData, 
  loading, 
  lastUpdateTime,
  fetchData 
} = useDashboardData()

// 30秒自动刷新
let refreshTimer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  fetchData()
  refreshTimer = setInterval(fetchData, 30000) // 30秒刷新
})

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
  }
})

// 格式化更新时间 (备用)
const _formattedUpdateTime = computed(() => {
  if (!lastUpdateTime.value) return '--'
  return lastUpdateTime.value.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    weekday: 'short',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
})
void _formattedUpdateTime // 防止未使用警告
</script>

<template>
  <div class="dashboard-page">
    <!-- 顶部标题栏 -->
    <header class="dashboard-header">
      <div class="header-left">
        <button class="back-btn" @click="router.push('/')">
          ← 返回
        </button>
      </div>
      <div class="header-center">
        <h1 class="main-title">大数据存储平台交通监控</h1>
        <div class="title-decoration">
          <span class="line"></span>
          <span class="dot"></span>
          <span class="line"></span>
        </div>
      </div>
      <div class="header-right">
        <DigitalClock />
        <span class="settings-icon">⚙️</span>
      </div>
    </header>

    <!-- 主内容区 -->
    <main class="dashboard-main">
      <!-- 左侧面板 -->
      <aside class="panel panel-left">
        <!-- 数据总览 -->
        <section class="section section-kpi">
          <div class="section-header">
            <span class="section-icon">≫</span>
            <h2>数据总览</h2>
            <span class="section-icon">≪</span>
          </div>
          <div class="kpi-grid">
            <KPIStatCard
              title="车辆总数"
              :value="dashboardData.totalVehicles"
              color="#00ff88"
              icon="🚗"
            />
            <KPIStatCard
              title="三型车及以下"
              :value="dashboardData.smallVehicles"
              color="#ffaa00"
              icon="🚙"
            />
            <KPIStatCard
              title="四型车及以上"
              :value="dashboardData.largeVehicles"
              color="#00d4ff"
              icon="🚛"
            />
          </div>
        </section>

        <!-- 出站点总览 -->
        <section class="section section-pie">
          <div class="section-header">
            <span class="section-icon">≫</span>
            <h2>出站点总览</h2>
            <span class="section-icon">≪</span>
          </div>
          <RingPieChart :data="dashboardData.stationStats" />
        </section>

        <!-- 客货车情况 -->
        <section class="section section-bar">
          <div class="section-header">
            <span class="section-icon">≫</span>
            <h2>客货车情况</h2>
            <span class="section-icon">≪</span>
          </div>
          <TrendLineChart 
            :data="dashboardData.vehicleTypeFlow" 
            type="bar"
          />
        </section>
      </aside>

      <!-- 中央地图区 -->
      <section class="panel panel-center">
        <div class="map-header">
          <span class="section-icon">≫</span>
          <h2>来深车辆来源</h2>
          <span class="section-icon">≪</span>
        </div>
        <ChinaMapChart :data="dashboardData.mapData" />
        
        <!-- 底部24小时车辆情况 -->
        <div class="hourly-section">
          <div class="section-header">
            <span class="section-icon">≫</span>
            <h2>24小时车辆情况</h2>
            <span class="section-icon">≪</span>
          </div>
          <Bar3DChart :data="dashboardData.hourlyFlow" />
        </div>
      </section>

      <!-- 右侧面板 -->
      <aside class="panel panel-right">
        <!-- 最近十分钟触发报警次数 -->
        <section class="section section-alert-trend">
          <div class="section-header">
            <span class="section-icon">≫</span>
            <h2>最近十分钟触发报警次数</h2>
            <span class="section-icon">≪</span>
          </div>
          <TrendLineChart 
            :data="dashboardData.alertTrend" 
            type="area"
          />
        </section>

        <!-- 车辆型号 -->
        <section class="section section-rank">
          <div class="section-header">
            <span class="section-icon">≫</span>
            <h2>车辆型号</h2>
            <span class="section-icon">≪</span>
          </div>
          <BarRankChart :data="dashboardData.vehicleTypes" />
        </section>

        <!-- 数据统计/告警列表 -->
        <section class="section section-alerts">
          <div class="section-header">
            <span class="section-icon">≫</span>
            <h2>数据统计图</h2>
            <span class="section-icon">≪</span>
          </div>
          <AlertTicker :alerts="dashboardData.recentAlerts" />
        </section>
      </aside>
    </main>

    <!-- 加载遮罩 -->
    <div v-if="loading" class="loading-overlay">
      <div class="loading-spinner"></div>
      <span>数据加载中...</span>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.dashboard-page {
  width: 100vw;
  height: 100vh;
  background: linear-gradient(135deg, #0a1628 0%, #0d1e36 50%, #061224 100%);
  overflow: hidden;
  position: relative;
  
  // 网格背景
  &::before {
    content: '';
    position: absolute;
    inset: 0;
    background-image: 
      linear-gradient(rgba(30, 144, 255, 0.03) 1px, transparent 1px),
      linear-gradient(90deg, rgba(30, 144, 255, 0.03) 1px, transparent 1px);
    background-size: 40px 40px;
    pointer-events: none;
  }
  
  // 装饰边框
  &::after {
    content: '';
    position: absolute;
    inset: 8px;
    border: 1px solid rgba(0, 212, 255, 0.15);
    border-radius: 4px;
    pointer-events: none;
  }
}

.dashboard-header {
  height: 70px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 30px;
  background: linear-gradient(180deg, rgba(6, 30, 61, 0.9) 0%, transparent 100%);
  position: relative;
  z-index: 10;
  
  &::after {
    content: '';
    position: absolute;
    bottom: 0;
    left: 5%;
    right: 5%;
    height: 1px;
    background: linear-gradient(90deg, transparent, rgba(0, 212, 255, 0.5), transparent);
  }
}

.header-left {
  flex: 1;
  
  .back-btn {
    background: rgba(0, 212, 255, 0.1);
    border: 1px solid rgba(0, 212, 255, 0.3);
    color: #00d4ff;
    padding: 8px 16px;
    border-radius: 4px;
    cursor: pointer;
    font-size: 13px;
    transition: all 0.3s;
    
    &:hover {
      background: rgba(0, 212, 255, 0.2);
    }
  }
}

.header-center {
  flex: 2;
  text-align: center;
}

.main-title {
  font-size: 32px;
  font-weight: 700;
  background: linear-gradient(90deg, #00d4ff, #00ff88, #00d4ff);
  background-size: 200% 100%;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  animation: gradientMove 3s ease infinite;
  letter-spacing: 6px;
}

@keyframes gradientMove {
  0%, 100% { background-position: 0% 50%; }
  50% { background-position: 100% 50%; }
}

.title-decoration {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 8px;
  
  .line {
    width: 100px;
    height: 2px;
    background: linear-gradient(90deg, transparent, #00d4ff);
    
    &:last-child {
      background: linear-gradient(90deg, #00d4ff, transparent);
    }
  }
  
  .dot {
    width: 8px;
    height: 8px;
    background: #00d4ff;
    border-radius: 50%;
    animation: pulse 2s ease-in-out infinite;
  }
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(0.8); }
}

.header-right {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 20px;
  
  .settings-icon {
    font-size: 20px;
    cursor: pointer;
    opacity: 0.7;
    transition: opacity 0.3s;
    
    &:hover {
      opacity: 1;
    }
  }
}

.dashboard-main {
  display: grid;
  grid-template-columns: 380px 1fr 380px;
  gap: 15px;
  height: calc(100vh - 85px);
  padding: 0 20px 15px;
  position: relative;
  z-index: 1;
}

.panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow: hidden;
}

.section {
  background: rgba(6, 30, 61, 0.7);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(0, 212, 255, 0.2);
  border-radius: 8px;
  padding: 12px 15px;
  position: relative;
  overflow: hidden;
  
  // 角落装饰
  &::before, &::after {
    content: '';
    position: absolute;
    width: 15px;
    height: 15px;
    border-color: rgba(0, 212, 255, 0.5);
    border-style: solid;
  }
  
  &::before {
    top: 0;
    left: 0;
    border-width: 2px 0 0 2px;
  }
  
  &::after {
    bottom: 0;
    right: 0;
    border-width: 0 2px 2px 0;
  }
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-bottom: 12px;
  
  h2 {
    font-size: 14px;
    font-weight: 600;
    color: #00d4ff;
    letter-spacing: 3px;
  }
  
  .section-icon {
    color: rgba(0, 212, 255, 0.6);
    font-size: 12px;
  }
}

.panel-left {
  .section-kpi {
    flex-shrink: 0;
  }
  
  .section-pie {
    flex: 1;
    min-height: 200px;
  }
  
  .section-bar {
    flex: 1;
    min-height: 180px;
  }
}

.kpi-grid {
  display: flex;
  gap: 12px;
}

.panel-center {
  display: flex;
  flex-direction: column;
  
  .map-header {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 10px;
    padding: 12px 0;
    
    h2 {
      font-size: 14px;
      font-weight: 600;
      color: #00d4ff;
      letter-spacing: 3px;
    }
    
    .section-icon {
      color: rgba(0, 212, 255, 0.6);
      font-size: 12px;
    }
  }
}

.hourly-section {
  margin-top: auto;
  background: rgba(6, 30, 61, 0.7);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(0, 212, 255, 0.2);
  border-radius: 8px;
  padding: 12px 15px;
  height: 220px;
}

.panel-right {
  .section-alert-trend {
    flex-shrink: 0;
    height: 180px;
  }
  
  .section-rank {
    flex: 1;
    min-height: 200px;
  }
  
  .section-alerts {
    flex: 1;
    min-height: 180px;
  }
}

.loading-overlay {
  position: fixed;
  inset: 0;
  background: rgba(10, 22, 40, 0.9);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 20px;
  z-index: 1000;
  
  span {
    color: #00d4ff;
    font-size: 16px;
    letter-spacing: 2px;
  }
}

.loading-spinner {
  width: 50px;
  height: 50px;
  border: 3px solid rgba(0, 212, 255, 0.2);
  border-top-color: #00d4ff;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>

