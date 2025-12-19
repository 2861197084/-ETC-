<template>
  <div class="xuzhou-map-wrapper">
    <!-- 搜索栏 -->
    <div class="map-toolbar">
      <input 
        type="text"
        v-model="searchText"
        placeholder="搜索徐州市内地点..."
        class="search-input"
        @input="onSearchInput"
        @focus="showSuggestions = true"
      />
      <button class="tool-btn" @click="locateToCUMT" title="回到矿大南湖">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
          <path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z"/>
        </svg>
      </button>
      <button 
        class="tool-btn" 
        :class="{ active: trafficOn }"
        @click="toggleTraffic" 
        title="路况开关"
      >
        <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
          <path d="M20 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zM9 18H4v-4h5v4zm0-6H4V8h5v4zm7 6h-5v-4h5v4zm0-6h-5V8h5v4zm4 6h-3v-4h3v4zm0-6h-3V8h3v4z"/>
        </svg>
      </button>
      <button 
        class="tool-btn" 
        :class="{ active: checkpointsVisible }"
        @click="toggleCheckpoints" 
        title="卡口显示"
      >
        <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
          <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/>
        </svg>
      </button>
      
      <!-- 搜索建议 -->
      <div v-if="showSuggestions && suggestions.length > 0" class="suggestions-panel">
        <div 
          v-for="(item, i) in suggestions" 
          :key="i" 
          class="suggestion-item"
          @click="goToPlace(item)"
        >
          <span class="name">{{ item.name }}</span>
          <span class="addr">{{ item.district }}</span>
        </div>
      </div>
    </div>

    <!-- 地图 -->
    <div id="xuzhou-map" ref="mapRef"></div>

    <!-- 站点信息窗体 -->
    <div ref="infoWindowRef" class="station-info-window" v-show="false">
      <div class="info-header">
        <span class="station-name">{{ selectedCheckpointInfo?.name }}</span>
        <span class="station-type" :class="selectedCheckpointInfo?.type">
          {{ selectedCheckpointInfo?.type === 'provincial' ? '省际卡口' : '市际卡口' }}
        </span>
      </div>
      <div class="info-body">
        <div class="info-row">
          <span class="label">实时车流</span>
          <span class="value">{{ selectedCheckpointInfo?.flow || 0 }} 辆/小时</span>
        </div>
        <div class="info-row">
          <span class="label">今日通行</span>
          <span class="value">{{ selectedCheckpointInfo?.todayTotal || 0 }} 辆</span>
        </div>
        <div class="info-row">
          <span class="label">状态</span>
          <span class="value status" :class="selectedCheckpointInfo?.status">
            {{ getStatusLabel(selectedCheckpointInfo?.status) }}
          </span>
        </div>
      </div>
    </div>

    <!-- 路况图例 -->
    <div class="legend" v-if="trafficOn">
      <span class="title">实时路况</span>
      <div class="items">
        <span><i style="background:#00b514"></i>畅通</span>
        <span><i style="background:#ff7324"></i>缓行</span>
        <span><i style="background:#e02222"></i>拥堵</span>
        <span><i style="background:#5e0a0b"></i>严重</span>
      </div>
    </div>

    <!-- 卡口图例 -->
    <div class="station-legend" v-if="checkpointsVisible">
      <span class="title">出市卡口 (19)</span>
      <div class="items">
        <span><i class="checkpoint"></i>正常</span>
        <span><i class="busy"></i>繁忙</span>
        <span><i class="congested"></i>拥堵</span>
      </div>
    </div>

    <!-- 城市信息 -->
    <div class="city-info">徐州市实时路况</div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import AMapLoader from '@amap/amap-jsapi-loader'
import { checkpoints as staticCheckpoints, mapCenter, defaultZoom, type Checkpoint } from '@/config/checkpoints'
import { getCheckpoints } from '@/api/admin/map'

defineOptions({ name: 'XuzhouTrafficMap' })

// 地图显示用的卡口数据（扩展实时流量信息）
export interface CheckpointDisplayData {
  id: string
  name: string
  fullName: string
  type: 'provincial' | 'municipal'
  position: [number, number]
  region: string
  road: string
  boundary: string
  flow?: number
  todayTotal?: number
  status?: 'normal' | 'busy' | 'congested'
}

// Props
const props = withDefaults(defineProps<{
  checkpointFlows?: Record<string, { flow: number; todayTotal: number; status: 'normal' | 'busy' | 'congested' }>
  showCheckpoints?: boolean
  enableRouting?: boolean
}>(), {
  checkpointFlows: () => ({}),
  showCheckpoints: true,
  enableRouting: false
})

// Emits
const emit = defineEmits<{
  (e: 'checkpoint-click', checkpoint: CheckpointDisplayData): void
  (e: 'route-complete', result: any): void
  (e: 'map-ready', map: any): void
}>()

// 徐州市中心坐标（从配置获取）
const XUZHOU_CENTER = [mapCenter.longitude, mapCenter.latitude]
const XUZHOU_ZOOM = defaultZoom

// 中国矿业大学南湖校区坐标
const CUMT_NANHU_CENTER = [117.14509, 34.214571]
const CUMT_ZOOM = 16

const mapRef = ref<HTMLElement>()
const infoWindowRef = ref<HTMLElement>()
const searchText = ref('')
const suggestions = ref<any[]>([])
const showSuggestions = ref(false)
const trafficOn = ref(true)
const checkpointsVisible = ref(true)
const selectedCheckpointInfo = ref<CheckpointDisplayData | null>(null)

let map: any = null
let AMap: any = null
let trafficLayer: any = null
let autoComplete: any = null
let searchMarker: any = null
let checkpointMarkers: any[] = []
let infoWindow: any = null
let driving: any = null
let routePolyline: any = null

// 动态加载的卡口数据
const checkpoints = ref<Checkpoint[]>([...staticCheckpoints])

// 卡口名称映射（解决后端中文乱码问题）
const CHECKPOINT_NAME_MAP: Record<number, string> = {
  1: '苏皖界1(104省道)', 2: '苏皖界2(311国道)', 3: '苏皖界3(徐明高速)',
  4: '苏皖界4(宿新高速)', 5: '苏皖界5(徐淮高速)', 6: '苏皖界6(新扬高速)',
  7: '苏鲁界1(206国道)', 8: '苏鲁界2(104国道)', 9: '苏鲁界3(京台高速)',
  10: '苏鲁界4(枣庄连接线)', 11: '苏鲁界5(京沪高速)', 12: '苏鲁界6(沂河路)',
  13: '连云港界1(徐连高速)', 14: '连云港界2(310国道)', 15: '宿迁界1(徐宿高速)',
  16: '宿迁界2(徐宿快速)', 17: '宿迁界3(104国道)', 18: '宿迁界4(新扬高速)',
  19: '宿迁界5(徐盐高速)'
}

// 区域映射
const REGION_MAP: Record<number, string> = {
  1: '苏皖界', 2: '苏皖界', 3: '苏皖界', 4: '苏皖界', 5: '苏皖界', 6: '苏皖界',
  7: '苏鲁界', 8: '苏鲁界', 9: '苏鲁界', 10: '苏鲁界', 11: '苏鲁界', 12: '苏鲁界',
  13: '连云港界', 14: '连云港界',
  15: '宿迁界', 16: '宿迁界', 17: '宿迁界', 18: '宿迁界', 19: '宿迁界'
}

// 从后端加载卡口数据
const loadCheckpointsFromApi = async () => {
  try {
    const res = await getCheckpoints()
    if (res.code === 200 && res.data && res.data.length > 0) {
      // 将后端数据转换为 Checkpoint 格式，使用本地名称映射解决乱码
      checkpoints.value = res.data.map((cp: any) => {
        const cpId = parseInt(cp.id || cp.code)
        return {
          id: cp.id || cp.code,
          code: cp.code || cp.id,
          name: CHECKPOINT_NAME_MAP[cpId] || cp.name || `卡口${cpId}`,
          fullName: CHECKPOINT_NAME_MAP[cpId] || cp.fullName || cp.name || `卡口${cpId}`,
          longitude: cp.longitude,
          latitude: cp.latitude,
          region: REGION_MAP[cpId] || cp.region || '未知',
          type: cp.type === 'provincial' ? 'provincial' : 'municipal',
          road: cp.road || '',
          boundary: cp.boundary || 'unknown',
          status: cp.status || 'online',
          // 额外的实时数据
          currentFlow: cp.currentFlow || 0,
          maxCapacity: cp.maxCapacity || 3200
        }
      })
      console.log('✅ 从后端加载卡口数据:', checkpoints.value.length, '个')
      return true
    }
  } catch (e) {
    console.warn('⚠️ 后端卡口数据加载失败，使用静态配置:', e)
  }
  return false
}

// 将配置数据转换为地图显示数据
const getCheckpointDisplayData = (): CheckpointDisplayData[] => {
  return checkpoints.value.map(cp => {
    // 从后端数据获取实时流量
    const apiFlow = (cp as any).currentFlow
    const apiCapacity = (cp as any).maxCapacity || 3200
    
    // 计算状态
    let status: 'normal' | 'busy' | 'congested' = 'normal'
    if (apiFlow) {
      const ratio = apiFlow / apiCapacity
      if (ratio > 0.8) status = 'congested'
      else if (ratio > 0.5) status = 'busy'
    }
    
    return {
      id: cp.id,
      name: cp.name,
      fullName: cp.fullName,
      type: cp.type,
      position: [cp.longitude, cp.latitude] as [number, number],
      region: cp.region,
      road: cp.road,
      boundary: cp.boundary,
      // 优先使用后端数据，其次使用 props 传入的数据
      flow: apiFlow || props.checkpointFlows[cp.id]?.flow || 0,
      todayTotal: props.checkpointFlows[cp.id]?.todayTotal || (apiFlow ? apiFlow * 24 : 0),
      status: props.checkpointFlows[cp.id]?.status || status
    }
  })
}

// 卡口统一配置
const checkpointConfig = {
  label: '出市卡口',
  color: '#409EFF',  // 统一蓝色
  icon: '📍'
}

// 获取卡口类型标签
const getCheckpointTypeLabel = (type?: string) => {
  return checkpointTypeConfig[type as keyof typeof checkpointTypeConfig]?.label || '未知'
}

// 获取状态标签
const getStatusLabel = (status?: string) => {
  const labels: Record<string, string> = {
    normal: '正常',
    busy: '繁忙',
    congested: '拥堵'
  }
  return labels[status || ''] || '未知'
}

// 创建卡口标记图标（统一样式）
const createCheckpointMarkerContent = (checkpoint: CheckpointDisplayData) => {
  // 根据状态设置颜色
  const statusColor = checkpoint.status === 'congested' ? '#F56C6C' : 
                     checkpoint.status === 'busy' ? '#E6A23C' : 
                     checkpointConfig.color
  
  return `
    <div class="checkpoint-marker" style="
      width: 14px;
      height: 14px;
      background: ${statusColor};
      border-radius: 50%;
      border: 2px solid #fff;
      box-shadow: 0 2px 6px rgba(0,0,0,0.3);
      cursor: pointer;
    "></div>
  `
}

// 初始化地图
const initMap = async () => {
  try {
    // 安全配置 - 必须在加载前设置
    ;(window as any)._AMapSecurityConfig = {
      securityJsCode: '7205847ef1f61e487df1d74607a26422'
    }

    console.log('开始加载高德地图...')

    // 加载高德地图
    AMap = await AMapLoader.load({
      key: 'cdc27498b38c08c8950410d6697bcd0b',
      version: '2.0',
      plugins: ['AMap.Scale', 'AMap.AutoComplete', 'AMap.PlaceSearch', 'AMap.Driving']
    })

    console.log('AMap对象加载成功:', AMap)

    // 确保容器存在
    const container = document.getElementById('xuzhou-map')
    if (!container) {
      console.error('地图容器不存在!')
      return
    }
    
    console.log('容器尺寸:', container.offsetWidth, container.offsetHeight)

    // 创建地图实例 - 直接定位徐州
    map = new AMap.Map('xuzhou-map', {
      zoom: XUZHOU_ZOOM,
      center: XUZHOU_CENTER,
      viewMode: '2D',
      mapStyle: 'amap://styles/normal',
      resizeEnable: true
    })

    console.log('地图实例创建成功')

    // 地图加载完成事件
    map.on('complete', () => {
      console.log('地图渲染完成')
      emit('map-ready', map)
    })

    // 添加实时路况图层
    trafficLayer = new AMap.TileLayer.Traffic({
      autoRefresh: true,
      interval: 180
    })
    map.add(trafficLayer)

    // 添加比例尺
    const scale = new AMap.Scale({ position: 'LB' })
    map.addControl(scale)

    // 初始化搜索（限定徐州市）
    autoComplete = new AMap.AutoComplete({
      city: '徐州市',
      citylimit: true
    })

    // 初始化信息窗体
    infoWindow = new AMap.InfoWindow({
      isCustom: true,
      autoMove: true,
      offset: new AMap.Pixel(0, -20)
    })

    // 点击地图关闭信息窗口
    map.on('click', () => {
      if (infoWindow) {
        infoWindow.close()
      }
    })

    // 初始化驾车路径规划
    driving = new AMap.Driving({
      map: map,
      panel: null,
      policy: AMap.DrivingPolicy.LEAST_TIME // 最快路线
    })

    // 添加卡口标记
    if (props.showCheckpoints) {
      addCheckpointMarkers()
    }

    console.log('徐州地图加载成功，已加载19个出市卡口')
  } catch (e) {
    console.error('地图加载失败:', e)
  }
}

// 生成信息窗体HTML内容
const generateInfoWindowContent = (checkpoint: CheckpointDisplayData) => {
  const statusLabels: Record<string, string> = {
    normal: '正常',
    busy: '繁忙',
    congested: '拥堵'
  }
  const statusClass = checkpoint.status || 'normal'
  
  return `
    <div class="checkpoint-info-window" style="
      background: #fff;
      border-radius: 8px;
      box-shadow: 0 4px 16px rgba(0,0,0,0.15);
      min-width: 220px;
      overflow: hidden;
      border: 1px solid #e4e7ed;
    ">
      <div style="
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 12px 14px;
        background: linear-gradient(135deg, ${checkpointConfig.color}22, ${checkpointConfig.color}11);
        border-bottom: 1px solid #e4e7ed;
      ">
        <span style="font-size: 14px; font-weight: 600; color: #303133;">${checkpoint.name}</span>
        <span style="
          font-size: 10px;
          padding: 2px 8px;
          border-radius: 10px;
          background: ${checkpointConfig.color}22;
          color: ${checkpointConfig.color};
          font-weight: 500;
        ">出市卡口</span>
      </div>
      <div style="padding: 12px 14px;">
        <div style="font-size: 11px; color: #909399; margin-bottom: 8px; line-height: 1.4;">
          📍 ${checkpoint.region} · ${checkpoint.road} · ${checkpoint.boundary}
        </div>
        <div style="display: flex; justify-content: space-between; padding: 6px 0; border-bottom: 1px solid #f0f0f0;">
          <span style="font-size: 12px; color: #909399;">实时车流</span>
          <span style="font-size: 13px; color: #303133; font-weight: 600;">${checkpoint.flow || 0} <small style="font-weight:normal;color:#909399">辆/时</small></span>
        </div>
        <div style="display: flex; justify-content: space-between; padding: 6px 0; border-bottom: 1px solid #f0f0f0;">
          <span style="font-size: 12px; color: #909399;">今日通行</span>
          <span style="font-size: 13px; color: #303133; font-weight: 600;">${(checkpoint.todayTotal || 0).toLocaleString()} <small style="font-weight:normal;color:#909399">辆</small></span>
        </div>
        <div style="display: flex; justify-content: space-between; padding: 6px 0;">
          <span style="font-size: 12px; color: #909399;">通行状态</span>
          <span style="
            padding: 2px 8px;
            border-radius: 10px;
            font-size: 11px;
            font-weight: 500;
            background: ${statusClass === 'congested' ? '#fef0f0' : statusClass === 'busy' ? '#fdf6ec' : '#f0f9eb'};
            color: ${statusClass === 'congested' ? '#F56C6C' : statusClass === 'busy' ? '#E6A23C' : '#67C23A'};
          ">${statusLabels[checkpoint.status || ''] || '正常'}</span>
        </div>
      </div>
    </div>
  `
}

// 添加19个出市卡口标记
const addCheckpointMarkers = () => {
  const checkpointData = getCheckpointDisplayData()
  
  checkpointData.forEach(checkpoint => {
    const marker = new AMap.Marker({
      position: checkpoint.position,
      content: createCheckpointMarkerContent(checkpoint),
      offset: new AMap.Pixel(-7, -7),
      extData: checkpoint,
      title: checkpoint.name
    })

    // 点击事件
    marker.on('click', (e: any) => {
      e.stopPropagation && e.stopPropagation()
      
      const cpData = marker.getExtData() as CheckpointDisplayData
      selectedCheckpointInfo.value = cpData
      emit('checkpoint-click', cpData)
      
      if (infoWindow) {
        const content = generateInfoWindowContent(cpData)
        infoWindow.setContent(content)
        infoWindow.open(map, marker.getPosition())
      }
    })

    // 鼠标悬停效果
    marker.on('mouseover', () => {
      marker.setOffset(new AMap.Pixel(-7, -10))
    })
    marker.on('mouseout', () => {
      marker.setOffset(new AMap.Pixel(-7, -7))
    })

    checkpointMarkers.push(marker)
    map.add(marker)
  })
}

// 移除卡口标记
const removeCheckpointMarkers = () => {
  checkpointMarkers.forEach(marker => {
    map.remove(marker)
  })
  checkpointMarkers = []
}

// 切换卡口显示
const toggleCheckpoints = () => {
  checkpointsVisible.value = !checkpointsVisible.value
  if (checkpointsVisible.value) {
    addCheckpointMarkers()
  } else {
    removeCheckpointMarkers()
    if (infoWindow) {
      infoWindow.close()
    }
  }
}

// 路径规划
const planRoute = (origin: [number, number], destination: [number, number], waypoints?: [number, number][]) => {
  if (!driving) {
    console.error('驾车路径规划未初始化')
    return Promise.reject('Driving not initialized')
  }

  return new Promise((resolve, reject) => {
    // 清除之前的路线
    driving.clear()
    
    const options: any = {}
    if (waypoints && waypoints.length > 0) {
      options.waypoints = waypoints.map(p => new AMap.LngLat(p[0], p[1]))
    }

    driving.search(
      new AMap.LngLat(origin[0], origin[1]),
      new AMap.LngLat(destination[0], destination[1]),
      options,
      (status: string, result: any) => {
        if (status === 'complete') {
          console.log('路径规划成功:', result)
          emit('route-complete', result)
          resolve(result)
        } else {
          console.error('路径规划失败:', result)
          reject(result)
        }
      }
    )
  })
}

// 清除路径
const clearRoute = () => {
  if (driving) {
    driving.clear()
  }
  if (routePolyline) {
    map.remove(routePolyline)
    routePolyline = null
  }
}

// 搜索输入
const onSearchInput = () => {
  const keyword = searchText.value.trim()
  if (!keyword || !autoComplete) {
    suggestions.value = []
    return
  }

  autoComplete.search(keyword, (status: string, result: any) => {
    if (status === 'complete' && result.tips) {
      suggestions.value = result.tips.filter((t: any) => t.location)
    } else {
      suggestions.value = []
    }
  })
}

// 选择地点
const goToPlace = (item: any) => {
  showSuggestions.value = false
  searchText.value = item.name
  suggestions.value = []

  if (item.location && map) {
    // 移除旧标记
    if (searchMarker) {
      map.remove(searchMarker)
    }

    // 跳转到位置
    map.setZoomAndCenter(15, [item.location.lng, item.location.lat])

    // 添加标记
    searchMarker = new AMap.Marker({
      position: [item.location.lng, item.location.lat],
      title: item.name
    })
    map.add(searchMarker)
  }
}

// 回到中国矿业大学南湖校区
const locateToCUMT = () => {
  if (map) {
    map.setZoomAndCenter(CUMT_ZOOM, CUMT_NANHU_CENTER)
  }
}

// 切换路况
const toggleTraffic = () => {
  trafficOn.value = !trafficOn.value
  if (trafficLayer) {
    trafficOn.value ? trafficLayer.show() : trafficLayer.hide()
  }
}

// 获取地图实例
const getMap = () => map
const getAMap = () => AMap

// 定位到指定位置
const setCenter = (position: [number, number], zoom?: number) => {
  if (map) {
    map.setZoomAndCenter(zoom || map.getZoom(), position)
  }
}

// 点击外部关闭建议
const handleClickOutside = (e: MouseEvent) => {
  const target = e.target as HTMLElement
  if (!target.closest('.map-toolbar')) {
    showSuggestions.value = false
  }
}

// 暴露方法给父组件
defineExpose({
  getMap,
  getAMap,
  planRoute,
  clearRoute,
  setCenter,
  addCheckpointMarkers,
  removeCheckpointMarkers
})

onMounted(async () => {
  // 先从后端加载卡口数据
  await loadCheckpointsFromApi()
  // 然后初始化地图
  initMap()
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
  if (map) {
    map.destroy()
  }
})
</script>

<style scoped>
.xuzhou-map-wrapper {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 500px;
  border-radius: 8px;
  overflow: hidden;
  background: #e4e4e4;
}

#xuzhou-map {
  width: 100% !important;
  height: 100% !important;
  min-height: 500px;
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
}

.map-toolbar {
  position: absolute;
  top: 10px;
  left: 10px;
  z-index: 100;
  display: flex;
  gap: 8px;
  align-items: center;
}

.search-input {
  width: 220px;
  height: 36px;
  padding: 0 12px;
  border: none;
  border-radius: 4px;
  font-size: 14px;
  background: #fff;
  box-shadow: 0 2px 6px rgba(0,0,0,0.15);
  outline: none;
}

.search-input:focus {
  box-shadow: 0 2px 8px rgba(64,158,255,0.4);
}

.tool-btn {
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 4px;
  background: #fff;
  box-shadow: 0 2px 6px rgba(0,0,0,0.15);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #666;
  transition: all 0.2s;
}

.tool-btn:hover {
  background: #f5f5f5;
  color: #409eff;
}

.tool-btn.active {
  background: #409eff;
  color: #fff;
}

.suggestions-panel {
  position: absolute;
  top: 44px;
  left: 0;
  width: 280px;
  max-height: 300px;
  overflow-y: auto;
  background: #fff;
  border-radius: 4px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
}

.suggestion-item {
  padding: 10px 12px;
  cursor: pointer;
  border-bottom: 1px solid #f0f0f0;
}

.suggestion-item:hover {
  background: #f5f7fa;
}

.suggestion-item:last-child {
  border-bottom: none;
}

.suggestion-item .name {
  display: block;
  font-size: 14px;
  color: #333;
}

.suggestion-item .addr {
  display: block;
  font-size: 12px;
  color: #999;
  margin-top: 2px;
}

.legend {
  position: absolute;
  bottom: 10px;
  left: 10px;
  z-index: 100;
  background: rgba(255,255,255,0.95);
  padding: 8px 12px;
  border-radius: 4px;
  box-shadow: 0 2px 6px rgba(0,0,0,0.15);
}

.legend .title {
  display: block;
  font-size: 12px;
  color: #666;
  margin-bottom: 6px;
}

.legend .items {
  display: flex;
  gap: 10px;
}

.legend .items span {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #333;
}

.legend .items i {
  display: inline-block;
  width: 16px;
  height: 4px;
  border-radius: 2px;
}

.city-info {
  position: absolute;
  bottom: 10px;
  right: 10px;
  z-index: 100;
  background: rgba(255,255,255,0.95);
  padding: 6px 12px;
  border-radius: 4px;
  font-size: 13px;
  color: #333;
  box-shadow: 0 2px 6px rgba(0,0,0,0.15);
}

/* 站点图例 */
.station-legend {
  position: absolute;
  bottom: 50px;
  left: 10px;
  z-index: 100;
  background: rgba(255,255,255,0.95);
  padding: 8px 12px;
  border-radius: 4px;
  box-shadow: 0 2px 6px rgba(0,0,0,0.15);
}

.station-legend .title {
  display: block;
  font-size: 12px;
  color: #666;
  margin-bottom: 6px;
}

.station-legend .items {
  display: flex;
  gap: 10px;
}

.station-legend .items span {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #333;
}

.station-legend .items i {
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: 50%;
}

.station-legend .items i.checkpoint {
  background: #409EFF;
}

.station-legend .items i.busy {
  background: #E6A23C;
}

.station-legend .items i.congested {
  background: #F56C6C;
}

/* 站点信息窗体 */
.station-info-window {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.15);
  min-width: 200px;
  overflow: hidden;
}

.station-info-window .info-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
}

.station-info-window .station-name {
  font-size: 14px;
  font-weight: 600;
}

.station-info-window .station-type {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  background: rgba(255,255,255,0.2);
}

.station-info-window .station-type.tollgate {
  background: rgba(64,158,255,0.3);
}

.station-info-window .station-type.checkpoint {
  background: rgba(230,162,60,0.3);
}

.station-info-window .station-type.service {
  background: rgba(103,194,58,0.3);
}

.station-info-window .info-body {
  padding: 12px 14px;
}

.station-info-window .info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 0;
  border-bottom: 1px solid #f0f0f0;
}

.station-info-window .info-row:last-child {
  border-bottom: none;
}

.station-info-window .label {
  font-size: 12px;
  color: #666;
}

.station-info-window .value {
  font-size: 13px;
  color: #333;
  font-weight: 500;
}

.station-info-window .value.status {
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 11px;
}

.station-info-window .value.status.normal {
  background: #f0f9eb;
  color: #67C23A;
}

.station-info-window .value.status.busy {
  background: #fdf6ec;
  color: #E6A23C;
}

.station-info-window .value.status.congested {
  background: #fef0f0;
  color: #F56C6C;
}
</style>
