<template>
  <div class="owner-map-page">
    <!-- 地图区域 -->
    <div class="map-container">
      <XuzhouTrafficMap 
        ref="mapRef" 
        :enable-routing="true"
        @station-click="onStationClick"
        @route-complete="onRouteComplete"
      />
      
      <!-- 地图工具栏 -->
      <div class="map-toolbar">
        <el-tooltip content="实时位置" placement="left">
          <el-button :icon="Location" circle @click="locateVehicle" />
        </el-tooltip>
      </div>
    </div>

    <!-- 右侧信息面板 -->
    <div class="info-panel">
      <!-- 车辆选择头部 -->
      <div class="panel-header">
        <el-select 
          v-model="selectedVehicleId" 
          placeholder="选择车辆"
          @change="onVehicleChange"
          class="vehicle-select"
        >
          <template #prefix>
            <el-icon :size="18" class="vehicle-icon-prefix"><Van /></el-icon>
          </template>
          <el-option
            v-for="vehicle in vehicles"
            :key="vehicle.id"
            :value="vehicle.id"
            :label="vehicle.plateNumber"
          >
            <div class="vehicle-option">
              <div class="option-left">
                <el-icon :size="20"><Van /></el-icon>
                <div class="option-info">
                  <span class="plate">{{ vehicle.plateNumber }}</span>
                  <span class="type">{{ vehicle.type }}</span>
                </div>
              </div>
              <el-tag :type="vehicle.status === 'online' ? 'success' : 'info'" size="small">
                {{ vehicle.status === 'online' ? '在线' : '离线' }}
              </el-tag>
            </div>
          </el-option>
        </el-select>
        <el-button type="primary" size="small" :icon="Plus" circle title="添加车辆" />
      </div>

      <!-- 车辆状态卡片 -->
      <div class="vehicle-status-card" v-if="selectedVehicle">
        <div class="status-row">
          <span class="label">车辆类型</span>
          <span class="value">{{ selectedVehicle.type }}</span>
        </div>
        <div class="status-row">
          <span class="label">当前状态</span>
          <el-tag :type="selectedVehicle.status === 'online' ? 'success' : 'info'" size="small">
            {{ selectedVehicle.status === 'online' ? '在线' : '离线' }}
          </el-tag>
        </div>
      </div>

      <!-- 路径规划 -->
      <div class="route-section">
        <div class="section-header" @click="showRoutePanel = !showRoutePanel">
          <h4>
            <el-icon><Guide /></el-icon>
            路径规划
          </h4>
          <el-icon class="toggle-icon" :class="{ rotated: showRoutePanel }"><ArrowDown /></el-icon>
        </div>
        <el-collapse-transition>
          <div v-show="showRoutePanel" class="route-content">
            <div class="route-input-group">
              <div class="input-item">
                <el-tooltip content="使用当前位置" placement="top">
                  <el-button 
                    :icon="Location" 
                    circle 
                    size="small" 
                    class="location-btn"
                    :loading="isLocating"
                    @click="useCurrentLocation"
                  />
                </el-tooltip>
                <el-autocomplete
                  v-model="routeForm.origin"
                  :fetch-suggestions="searchPlace"
                  placeholder="输入起点"
                  clearable
                  size="small"
                  @select="handleOriginSelect"
                >
                  <template #default="{ item }">
                    <div class="place-item">
                      <span class="name">{{ item.name }}</span>
                      <span class="address">{{ item.district }}</span>
                    </div>
                  </template>
                </el-autocomplete>
              </div>
              <div class="input-item">
                <el-tooltip content="终点" placement="top">
                  <el-button 
                    :icon="Aim" 
                    circle 
                    size="small" 
                    class="destination-btn"
                  />
                </el-tooltip>
                <el-autocomplete
                  v-model="routeForm.destination"
                  :fetch-suggestions="searchPlace"
                  placeholder="输入终点"
                  clearable
                  size="small"
                  @select="handleDestinationSelect"
                >
                  <template #default="{ item }">
                    <div class="place-item">
                      <span class="name">{{ item.name }}</span>
                      <span class="address">{{ item.district }}</span>
                    </div>
                  </template>
                </el-autocomplete>
              </div>
            </div>
            
            <div class="route-strategy">
              <el-radio-group v-model="routeStrategy" size="small">
                <el-radio-button label="fastest">最快</el-radio-button>
                <el-radio-button label="shortest">最短</el-radio-button>
                <el-radio-button label="noToll">免费</el-radio-button>
              </el-radio-group>
            </div>

            <el-button type="primary" :loading="isRouting" @click="startRoute" size="small" style="width: 100%">
              <el-icon><Position /></el-icon>
              开始导航
            </el-button>

            <!-- 路线结果 -->
            <div v-if="routeResult" class="route-result">
              <div class="result-header">
                <span class="route-title">推荐路线</span>
                <el-button text type="danger" size="small" @click="clearRouteResult">清除</el-button>
              </div>
              <div class="result-info">
                <div class="info-item">
                  <el-icon><Timer /></el-icon>
                  <span>{{ formatDuration(routeResult.duration) }}</span>
                </div>
                <div class="info-item">
                  <el-icon><Odometer /></el-icon>
                  <span>{{ formatDistance(routeResult.distance) }}</span>
                </div>
                <div class="info-item toll" v-if="routeResult.tolls > 0">
                  <el-icon><Money /></el-icon>
                  <span>¥{{ routeResult.tolls }}</span>
                </div>
              </div>
              <div class="route-steps" v-if="routeResult.steps?.length">
                <div class="step-item" v-for="(step, i) in routeResult.steps.slice(0, 3)" :key="i">
                  <span class="step-index">{{ i + 1 }}</span>
                  <span class="step-text">{{ step.instruction }}</span>
                </div>
                <div v-if="routeResult.steps.length > 3" class="more-steps">
                  还有 {{ routeResult.steps.length - 3 }} 步...
                </div>
              </div>
            </div>
          </div>
        </el-collapse-transition>
      </div>

      <!-- 本月统计 -->
      <div class="stats-section">
        <h4>本月统计</h4>
        <div class="stats-grid">
          <div class="stat-item">
            <div class="stat-value">{{ monthlyStats.trips }}</div>
            <div class="stat-label">通行次数</div>
          </div>
          <div class="stat-item">
            <div class="stat-value">¥{{ monthlyStats.fee }}</div>
            <div class="stat-label">通行费用</div>
          </div>
          <div class="stat-item">
            <div class="stat-value">{{ monthlyStats.distance }}km</div>
            <div class="stat-label">行驶里程</div>
          </div>
        </div>
      </div>

      <!-- 最近通行 -->
      <div class="recent-section">
        <h4>最近通行</h4>
        <div class="recent-list">
          <div v-for="record in recentRecords" :key="record.id" class="recent-item">
            <div class="route">
              <span class="from">{{ record.from }}</span>
              <el-icon><Right /></el-icon>
              <span class="to">{{ record.to }}</span>
            </div>
            <div class="record-detail">
              <span class="time">{{ record.time }}</span>
              <span class="fee">¥{{ record.fee }}</span>
            </div>
          </div>
        </div>
      </div>
      <!-- 轨迹回放 -->
      <div class="trajectory-section">
        <div class="section-header" @click="showTrajectoryPanel = !showTrajectoryPanel">
          <h4>
            <el-icon><VideoPlay /></el-icon>
            轨迹回放
          </h4>
          <el-icon class="toggle-icon" :class="{ rotated: showTrajectoryPanel }"><ArrowDown /></el-icon>
        </div>
        <el-collapse-transition>
          <div v-show="showTrajectoryPanel" class="trajectory-content">
            <div class="trajectory-filters">
              <el-date-picker 
                v-model="trajectoryDate" 
                type="date" 
                placeholder="选择日期"
                size="small"
                style="width: 100%; margin-bottom: 10px;"
                value-format="YYYY-MM-DD"
              />
              <el-select v-model="trajectoryVehicle" placeholder="选择车辆" size="small" style="width: 100%; margin-bottom: 10px;">
                <el-option
                  v-for="v in vehicles"
                  :key="v.id"
                  :label="v.plateNumber"
                  :value="v.id"
                />
              </el-select>
              <el-button type="primary" size="small" style="width: 100%;" @click="loadTrajectory" :loading="loadingTrajectory">
                <el-icon><Search /></el-icon>
                查询轨迹
              </el-button>
            </div>
            <div v-if="trajectoryLoaded" class="trajectory-info">
              <div class="info-row">
                <span class="label">行驶时长</span>
                <span class="value">2小时35分钟</span>
              </div>
              <div class="info-row">
                <span class="label">行驶里程</span>
                <span class="value">86.5km</span>
              </div>
              <div class="trajectory-actions">
                <el-button size="small" :icon="VideoPlay" @click="playTrajectory">播放</el-button>
                <el-button size="small" :icon="VideoPause" @click="pauseTrajectory">暂停</el-button>
              </div>
            </div>
            <div v-else class="trajectory-empty">
              <el-icon :size="32" color="#c0c4cc"><VideoPlay /></el-icon>
              <span>选择日期和车辆查询轨迹</span>
            </div>
          </div>
        </el-collapse-transition>
      </div>    </div>

    </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { 
  Van, Plus, VideoPlay, VideoPause, Location, Right, 
  Guide, ArrowDown, Position, Timer, Odometer, Money, Search, Aim 
} from '@element-plus/icons-vue'
import { XuzhouTrafficMap } from '@/components/business/etc'
import { ElMessage } from 'element-plus'
import AMapLoader from '@amap/amap-jsapi-loader'

defineOptions({ name: 'OwnerMap' })

// 车辆列表
const vehicles = ref([
  { id: 1, plateNumber: '苏C·12345', type: '小型轿车', status: 'online' },
  { id: 2, plateNumber: '苏C·67890', type: 'SUV', status: 'offline' }
])

const selectedVehicleId = ref(vehicles.value[0]?.id)
const selectedVehicle = computed(() => 
  vehicles.value.find(v => v.id === selectedVehicleId.value)
)
const mapRef = ref()
const trajectoryDate = ref('')

// 轨迹回放相关
const showTrajectoryPanel = ref(false)
const trajectoryVehicle = ref(vehicles.value[0]?.id)
const loadingTrajectory = ref(false)
const trajectoryLoaded = ref(false)

// 切换车辆
const onVehicleChange = (id: number) => {
  selectedVehicleId.value = id
}

// 路径规划相关
const showRoutePanel = ref(true)
const isRouting = ref(false)
const routeStrategy = ref('fastest')
const routeForm = reactive({
  origin: '',
  destination: '',
  originLngLat: null as [number, number] | null,
  destinationLngLat: null as [number, number] | null
})
const routeResult = ref<any>(null)

let autoComplete: any = null
let geocoder: any = null
let AMapInstance: any = null  // 保存 AMap 对象
const isLocating = ref(false)

// 高德 API Key
const AMAP_KEY = 'cdc27498b38c08c8950410d6697bcd0b'

// 初始化搜索服务
const initAutoComplete = async () => {
  try {
    ;(window as any)._AMapSecurityConfig = {
      securityJsCode: '7205847ef1f61e487df1d74607a26422'
    }
    AMapInstance = await AMapLoader.load({
      key: AMAP_KEY,
      version: '2.0',
      plugins: ['AMap.AutoComplete', 'AMap.Geocoder', 'AMap.Geolocation']
    })
    autoComplete = new AMapInstance.AutoComplete({
      city: '徐州市',
      citylimit: true
    })
    
    // 初始化逆地理编码
    geocoder = new AMapInstance.Geocoder({
      city: '徐州市',
      radius: 1000
    })
  } catch (e) {
    console.error('初始化搜索服务失败:', e)
  }
}
initAutoComplete()

// 获取高精度定位（不使用城市级 IP 回落）
const getHighPrecisionPosition = async (): Promise<{ lng: number; lat: number; address: string }> => {
  try {
    if (!AMapInstance) {
      await initAutoComplete()
    }

    if (!AMapInstance?.Geolocation) {
      throw new Error('定位插件未加载')
    }

    return await new Promise((resolve, reject) => {
      const geolocation = new AMapInstance.Geolocation({
        enableHighAccuracy: true,
        timeout: 10000,
        GeoLocationFirst: true,
        noIpLocate: 3, // 禁用城市级 IP 回落
        noGeoLocation: 0,
        getCityWhenFail: false,
        showButton: false,
        zoomToAccuracy: true
      })

      geolocation.getCurrentPosition((status: string, result: any) => {
        if (status === 'complete' && result?.position) {
          resolve({
            lng: result.position.lng,
            lat: result.position.lat,
            address: result.formattedAddress || '当前位置'
          })
        } else {
          reject(new Error(result?.message || '定位失败'))
        }
      })
    })
  } catch (e) {
    console.error('高精度定位失败:', e)
    throw e
  }
}

// 搜索地点
const searchPlace = (queryString: string, cb: (results: any[]) => void) => {
  if (!queryString || !autoComplete) {
    cb([])
    return
  }

  autoComplete.search(queryString, (status: string, result: any) => {
    if (status === 'complete' && result.tips) {
      const places = result.tips.filter((t: any) => t.location).map((t: any) => ({
        value: t.name,
        name: t.name,
        district: t.district,
        location: t.location
      }))
      cb(places)
    } else {
      cb([])
    }
  })
}

// 选择起点
const handleOriginSelect = (item: any) => {
  if (item.location) {
    routeForm.originLngLat = [item.location.lng, item.location.lat]
  }
}

// 选择终点
const handleDestinationSelect = (item: any) => {
  if (item.location) {
    routeForm.destinationLngLat = [item.location.lng, item.location.lat]
  }
}

// 开始路径规划
const startRoute = async () => {
  if (!routeForm.originLngLat || !routeForm.destinationLngLat) {
    ElMessage.warning('请选择有效的起点和终点')
    return
  }

  isRouting.value = true
  try {
    const result = await mapRef.value?.planRoute(
      routeForm.originLngLat,
      routeForm.destinationLngLat
    )
    
    if (result?.routes?.[0]) {
      const route = result.routes[0]
      const path = route.paths?.[0]
      if (path) {
        routeResult.value = {
          distance: path.distance,
          duration: path.duration,
          tolls: path.tolls || 0,
          steps: path.steps?.map((s: any) => ({
            instruction: s.instruction,
            road: s.road,
            distance: s.distance
          }))
        }
      }
    }
  } catch (e) {
    ElMessage.error('路径规划失败，请重试')
  } finally {
    isRouting.value = false
  }
}

// 清除路线结果
const clearRouteResult = () => {
  routeResult.value = null
  mapRef.value?.clearRoute()
}

// 格式化时长
const formatDuration = (seconds: number) => {
  if (!seconds) return '0分钟'
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.ceil((seconds % 3600) / 60)
  if (hours > 0) {
    return `${hours}小时${minutes}分钟`
  }
  return `${minutes}分钟`
}

// 格式化距离
const formatDistance = (meters: number) => {
  if (!meters) return '0公里'
  if (meters >= 1000) {
    return `${(meters / 1000).toFixed(1)}公里`
  }
  return `${meters}米`
}

// 本月统计
const monthlyStats = reactive({
  trips: 28,
  fee: 356.50,
  distance: 1280
})

// 最近通行记录
const recentRecords = ref([
  { id: 1, from: '徐州东站', to: '铜山收费站', time: '今天 14:32', fee: 25 },
  { id: 2, from: '铜山收费站', to: '徐州东站', time: '今天 09:15', fee: 25 },
  { id: 3, from: '北站卡口', to: '南站卡口', time: '昨天 18:45', fee: 15 }
])

// 当前位置标记
let currentLocationMarker: any = null

const locateVehicle = async () => {
  if (!mapRef.value) {
    ElMessage.warning('地图未初始化')
    return
  }

  ElMessage.info('正在获取实时位置...')

  try {
    const { lng, lat, address } = await getHighPrecisionPosition()

    // 移动地图到当前位置
    mapRef.value.setCenter([lng, lat], 16)
    
    const AMap = mapRef.value.getAMap()
    const map = mapRef.value.getMap()
    
    if (AMap && map) {
      if (currentLocationMarker) {
        map.remove(currentLocationMarker)
      }
      
      currentLocationMarker = new AMap.Marker({
        position: [lng, lat],
        content: `
          <div class="current-location-marker">
            <div class="pulse"></div>
            <div class="dot"></div>
          </div>
        `,
        offset: new AMap.Pixel(-15, -15),
        zIndex: 200
      })
      map.add(currentLocationMarker)
    }
    
    ElMessage.success(`当前位置: ${address}`)
  } catch (e) {
    ElMessage.error('定位失败，已定位到默认位置')
    mapRef.value.setCenter([117.14509, 34.214571], 16)
  }
}

// 使用当前位置作为起点
const useCurrentLocation = async () => {
  isLocating.value = true
  ElMessage.info('正在获取当前位置...')
  
  try {
    const { lng, lat, address } = await getHighPrecisionPosition()
    routeForm.originLngLat = [lng, lat]
    routeForm.origin = address
    ElMessage.success('定位成功')
  } catch (e) {
    // 定位失败，使用默认位置
    routeForm.originLngLat = [117.14509, 34.214571]
    routeForm.origin = '矿大南湖校区'
    ElMessage.warning('定位失败，已使用默认位置')
  } finally {
    isLocating.value = false
  }
}

// 加载轨迹
const loadTrajectory = () => {
  if (!trajectoryDate.value || !trajectoryVehicle.value) {
    ElMessage.warning('请选择日期和车辆')
    return
  }
  loadingTrajectory.value = true
  // 模拟加载
  setTimeout(() => {
    loadingTrajectory.value = false
    trajectoryLoaded.value = true
    ElMessage.success('轨迹加载成功')
  }, 1000)
}

// 播放轨迹
const playTrajectory = () => {
  ElMessage.info('开始播放轨迹')
}

// 暂停轨迹
const pauseTrajectory = () => {
  ElMessage.info('轨迹播放已暂停')
}

// 站点点击事件
const onStationClick = (station: any) => {
  console.log('点击站点:', station)
}

// 路线规划完成
const onRouteComplete = (result: any) => {
  console.log('路线规划完成:', result)
}
</script>

<style lang="scss" scoped>
.owner-map-page {
  display: flex;
  height: calc(100vh - 120px);
  background: #f5f7fa;
}

.map-container {
  flex: 1;
  position: relative;

  .map-toolbar {
    position: absolute;
    right: 16px;
    top: 50%;
    transform: translateY(-50%);
    display: flex;
    flex-direction: column;
    gap: 8px;
    z-index: 10;
  }

  /* 当前位置标记样式 */
  :deep(.current-location-marker) {
    position: relative;
    width: 30px;
    height: 30px;

    .dot {
      position: absolute;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      width: 14px;
      height: 14px;
      background: #409EFF;
      border-radius: 50%;
      border: 3px solid #fff;
      box-shadow: 0 2px 8px rgba(64, 158, 255, 0.5);
      z-index: 2;
    }

    .pulse {
      position: absolute;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      width: 30px;
      height: 30px;
      background: rgba(64, 158, 255, 0.3);
      border-radius: 50%;
      animation: pulse-animation 2s ease-out infinite;
      z-index: 1;
    }
  }

  @keyframes pulse-animation {
    0% {
      transform: translate(-50%, -50%) scale(0.5);
      opacity: 1;
    }
    100% {
      transform: translate(-50%, -50%) scale(2);
      opacity: 0;
    }
  }
}

.place-item {
  .name {
    display: block;
    font-size: 14px;
    color: #333;
  }
  .address {
    display: block;
    font-size: 12px;
    color: #999;
    margin-top: 2px;
  }
}

.info-panel {
  width: 340px;
  background: #fff;
  border-left: 1px solid #e8e8e8;
  overflow-y: auto;
  display: flex;
  flex-direction: column;

  .panel-header {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 12px 16px;
    border-bottom: 1px solid #e8e8e8;
    flex-shrink: 0;
    background: #fff;

    .vehicle-select {
      flex: 1;

      :deep(.el-input__wrapper) {
        background: #f5f7fa;
        border-radius: 6px;
        box-shadow: none;
        border: 1px solid #e4e7ed;
        
        .el-input__inner {
          font-weight: 500;
          font-size: 14px;
          color: #303133;
        }
      }

      .vehicle-icon-prefix {
        color: #409EFF;
      }
    }

    :deep(.el-button) {
      background: #f5f7fa;
      border-color: #dcdfe6;
      color: #606266;

      &:hover {
        background: #ecf5ff;
        border-color: #409EFF;
        color: #409EFF;
      }
    }
  }

  /* 车辆状态卡片 */
  .vehicle-status-card {
    padding: 12px 16px;
    background: #fafafa;
    border-bottom: 1px solid #f0f0f0;
    display: flex;
    gap: 20px;

    .status-row {
      display: flex;
      align-items: center;
      gap: 8px;

      .label {
        font-size: 12px;
        color: #8c8c8c;
      }

      .value {
        font-size: 13px;
        color: #1f2329;
        font-weight: 500;
      }
    }
  }

  /* 路径规划区域 */
  .route-section {
    border-bottom: 1px solid #f0f0f0;
    flex-shrink: 0;

    .section-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 12px 16px;
      cursor: pointer;
      user-select: none;
      background: #fafafa;

      &:hover {
        background: #f5f5f5;
      }

      h4 {
        margin: 0;
        font-size: 14px;
        color: #1f2329;
        display: flex;
        align-items: center;
        gap: 6px;

        .el-icon {
          color: #409EFF;
        }
      }

      .toggle-icon {
        transition: transform 0.3s;
        color: #999;
        &.rotated {
          transform: rotate(180deg);
        }
      }
    }

    .route-content {
      padding: 16px;
    }

    .route-input-group {
      display: flex;
      flex-direction: column;
      gap: 12px;
      margin-bottom: 14px;

      .input-item {
        display: flex;
        align-items: center;
        gap: 8px;

        .location-btn {
          flex-shrink: 0;
          width: 28px;
          height: 28px;
          padding: 0;
          background: #f0f9eb;
          border-color: #c2e7b0;
          color: #67C23A;
          
          &:hover {
            background: #e1f3d8;
            border-color: #67C23A;
          }
          
          :deep(.el-icon) {
            font-size: 14px;
          }
        }

        .destination-btn {
          flex-shrink: 0;
          width: 28px;
          height: 28px;
          padding: 0;
          background: #fef0f0;
          border-color: #fab6b6;
          color: #F56C6C;
          cursor: default;
          
          &:hover {
            background: #fef0f0;
            border-color: #fab6b6;
            color: #F56C6C;
          }
          
          :deep(.el-icon) {
            font-size: 14px;
          }
        }

        :deep(.el-autocomplete) {
          flex: 1;

          .el-input__wrapper {
            padding: 6px 12px;
          }

          .el-input__inner {
            font-size: 14px;
          }
        }
      }
    }

    .route-strategy {
      margin-bottom: 14px;
      
      :deep(.el-radio-group) {
        width: 100%;
        display: flex;

        .el-radio-button {
          flex: 1;

          .el-radio-button__inner {
            width: 100%;
            padding: 8px 0;
            font-size: 13px;
          }
        }
      }
    }

    .route-result {
      margin-top: 12px;
      padding-top: 12px;
      border-top: 1px solid #f0f0f0;

      .result-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 8px;

        .route-title {
          font-size: 13px;
          font-weight: 600;
          color: #1f2329;
        }
      }

      .result-info {
        display: flex;
        gap: 12px;
        padding: 10px;
        background: #f5f7fa;
        border-radius: 6px;
        margin-bottom: 10px;

        .info-item {
          display: flex;
          align-items: center;
          gap: 4px;
          font-size: 12px;
          color: #666;

          .el-icon {
            color: #409EFF;
            font-size: 14px;
          }

          &.toll {
            color: #E6A23C;
            .el-icon {
              color: #E6A23C;
            }
          }
        }
      }

      .route-steps {
        max-height: 120px;
        overflow-y: auto;

        .step-item {
          display: flex;
          gap: 8px;
          padding: 6px 0;
          border-bottom: 1px dashed #f0f0f0;

          .step-index {
            width: 18px;
            height: 18px;
            background: #e6f7ff;
            color: #1890ff;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 10px;
            flex-shrink: 0;
          }

          .step-text {
            font-size: 11px;
            color: #666;
            line-height: 18px;
          }
        }

        .more-steps {
          text-align: center;
          padding: 6px;
          font-size: 11px;
          color: #999;
        }
      }
    }
  }

  .stats-section {
    padding: 16px;
    border-bottom: 1px solid #f0f0f0;
    flex-shrink: 0;

    h4 {
      margin: 0 0 12px;
      font-size: 14px;
      color: #8c8c8c;
    }
  }

  .recent-section {
    padding: 16px;
    flex-shrink: 0;

    h4 {
      margin: 0 0 12px;
      font-size: 14px;
      color: #8c8c8c;
    }
  }

  /* 轨迹回放区域 */
  .trajectory-section {
    border-top: 1px solid #f0f0f0;
    flex: 1;
    display: flex;
    flex-direction: column;
    min-height: 0;

    .section-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 12px 16px;
      cursor: pointer;
      user-select: none;
      background: #fafafa;
      flex-shrink: 0;

      &:hover {
        background: #f5f5f5;
      }

      h4 {
        margin: 0;
        font-size: 14px;
        color: #1f2329;
        display: flex;
        align-items: center;
        gap: 6px;

        .el-icon {
          color: #409EFF;
        }
      }

      .toggle-icon {
        transition: transform 0.3s;
        color: #999;
        &.rotated {
          transform: rotate(180deg);
        }
      }
    }

    .trajectory-content {
      padding: 16px;
      flex: 1;
      display: flex;
      flex-direction: column;
    }

    .trajectory-filters {
      flex-shrink: 0;
    }

    .trajectory-info {
      margin-top: 12px;
      padding: 12px;
      background: #f5f7fa;
      border-radius: 6px;

      .info-row {
        display: flex;
        justify-content: space-between;
        padding: 6px 0;

        .label {
          font-size: 12px;
          color: #909399;
        }

        .value {
          font-size: 13px;
          color: #303133;
          font-weight: 500;
        }
      }

      .trajectory-actions {
        display: flex;
        gap: 8px;
        margin-top: 10px;
        padding-top: 10px;
        border-top: 1px solid #e4e7ed;
      }
    }

    .trajectory-empty {
      flex: 1;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 8px;
      color: #c0c4cc;
      font-size: 12px;
      padding: 20px 0;
    }
  }

  .stats-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 12px;

    .stat-item {
      text-align: center;
      padding: 12px;
      background: #f5f7fa;
      border-radius: 8px;

      .stat-value {
        font-size: 18px;
        font-weight: 600;
        color: #1890ff;
      }

      .stat-label {
        font-size: 12px;
        color: #8c8c8c;
        margin-top: 4px;
      }
    }
  }

  .recent-list {
    flex: 1;
    overflow-y: auto;

    .recent-item {
      padding: 10px 0;
      border-bottom: 1px dashed #f0f0f0;

      &:last-child {
        border-bottom: none;
      }

      .route {
        display: flex;
        align-items: center;
        gap: 8px;
        font-size: 14px;
        color: #1f2329;

        .el-icon {
          color: #bfbfbf;
        }
      }

      .record-detail {
        display: flex;
        justify-content: space-between;
        margin-top: 4px;
        font-size: 12px;

        .time {
          color: #8c8c8c;
        }

        .fee {
          color: #52c41a;
          font-weight: 500;
        }
      }
    }
  }
}

/* 车辆下拉选项样式 */
.vehicle-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 0;

  .option-left {
    display: flex;
    align-items: center;
    gap: 10px;

    .el-icon {
      color: #667eea;
    }

    .option-info {
      display: flex;
      flex-direction: column;

      .plate {
        font-size: 14px;
        font-weight: 600;
        color: #1f2329;
      }

      .type {
        font-size: 11px;
        color: #8c8c8c;
      }
    }
  }
}
</style>
