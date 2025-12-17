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
        :class="{ active: stationsVisible }"
        @click="toggleStations" 
        title="站点显示"
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
        <span class="station-name">{{ selectedStationInfo?.name }}</span>
        <span class="station-type" :class="selectedStationInfo?.type">
          {{ getStationTypeLabel(selectedStationInfo?.type) }}
        </span>
      </div>
      <div class="info-body">
        <div class="info-row">
          <span class="label">实时车流</span>
          <span class="value">{{ selectedStationInfo?.flow || 0 }} 辆/小时</span>
        </div>
        <div class="info-row">
          <span class="label">今日通行</span>
          <span class="value">{{ selectedStationInfo?.todayTotal || 0 }} 辆</span>
        </div>
        <div class="info-row">
          <span class="label">状态</span>
          <span class="value status" :class="selectedStationInfo?.status">
            {{ getStatusLabel(selectedStationInfo?.status) }}
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

    <!-- 站点图例 -->
    <div class="station-legend" v-if="stationsVisible">
      <span class="title">站点状态</span>
      <div class="items">
        <span><i class="normal"></i>正常</span>
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

defineOptions({ name: 'XuzhouTrafficMap' })

// 站点数据接口
export interface StationData {
  id: string
  name: string
  type: 'tollgate' | 'checkpoint' | 'service'
  position: [number, number]
  flow?: number
  todayTotal?: number
  status?: 'normal' | 'busy' | 'congested'
}

// Props
const props = withDefaults(defineProps<{
  stations?: StationData[]
  showStations?: boolean
  enableRouting?: boolean
}>(), {
  stations: () => [],
  showStations: true,
  enableRouting: false
})

// Emits
const emit = defineEmits<{
  (e: 'station-click', station: StationData): void
  (e: 'route-complete', result: any): void
  (e: 'map-ready', map: any): void
}>()

// 徐州市中心坐标
const XUZHOU_CENTER = [117.284124, 34.205768]
const XUZHOU_ZOOM = 12

// 中国矿业大学南湖校区坐标
const CUMT_NANHU_CENTER = [117.14509, 34.214571]
const CUMT_ZOOM = 16

const mapRef = ref<HTMLElement>()
const infoWindowRef = ref<HTMLElement>()
const searchText = ref('')
const suggestions = ref<any[]>([])
const showSuggestions = ref(false)
const trafficOn = ref(true)
const stationsVisible = ref(true)
const selectedStationInfo = ref<StationData | null>(null)

let map: any = null
let AMap: any = null
let trafficLayer: any = null
let autoComplete: any = null
let searchMarker: any = null
let stationMarkers: any[] = []
let infoWindow: any = null
let driving: any = null
let routePolyline: any = null

// 徐州收费站/卡口模拟数据
const defaultStations: StationData[] = [
  { id: '1', name: '徐州东收费站', type: 'tollgate', position: [117.416589, 34.207845], flow: 856, todayTotal: 12350, status: 'normal' },
  { id: '2', name: '徐州南收费站', type: 'tollgate', position: [117.285432, 34.128976], flow: 1230, todayTotal: 18420, status: 'busy' },
  { id: '3', name: '徐州西收费站', type: 'tollgate', position: [117.098765, 34.198765], flow: 520, todayTotal: 8650, status: 'normal' },
  { id: '4', name: '徐州北收费站', type: 'tollgate', position: [117.298432, 34.328976], flow: 680, todayTotal: 9870, status: 'normal' },
  { id: '5', name: '铜山收费站', type: 'tollgate', position: [117.183265, 34.178654], flow: 1560, todayTotal: 21350, status: 'congested' },
  { id: '6', name: '贾汪收费站', type: 'tollgate', position: [117.456321, 34.438765], flow: 320, todayTotal: 4560, status: 'normal' },
  { id: '7', name: '新沂收费站', type: 'tollgate', position: [118.356789, 34.368765], flow: 450, todayTotal: 6780, status: 'normal' },
  { id: '8', name: '邳州收费站', type: 'tollgate', position: [117.963254, 34.338765], flow: 380, todayTotal: 5430, status: 'normal' },
  { id: '9', name: '北三环卡口', type: 'checkpoint', position: [117.312456, 34.285432], flow: 2350, todayTotal: 35680, status: 'busy' },
  { id: '10', name: '南三环卡口', type: 'checkpoint', position: [117.278965, 34.165432], flow: 1890, todayTotal: 28950, status: 'normal' },
  { id: '11', name: '东三环卡口', type: 'checkpoint', position: [117.398765, 34.218765], flow: 2150, todayTotal: 32450, status: 'busy' },
  { id: '12', name: '西三环卡口', type: 'checkpoint', position: [117.156789, 34.205432], flow: 1650, todayTotal: 24680, status: 'normal' },
  { id: '13', name: '徐州服务区', type: 'service', position: [117.356789, 34.256789], flow: 280, todayTotal: 3560, status: 'normal' },
  { id: '14', name: '窑湾服务区', type: 'service', position: [117.856789, 34.398765], flow: 180, todayTotal: 2340, status: 'normal' },
]

// 站点图标配置
const stationIcons: Record<string, { icon: string; color: string }> = {
  tollgate: { 
    icon: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z',
    color: '#409EFF'
  },
  checkpoint: { 
    icon: 'M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z',
    color: '#E6A23C'
  },
  service: { 
    icon: 'M18.92 6.01C18.72 5.42 18.16 5 17.5 5h-11c-.66 0-1.21.42-1.42 1.01L3 12v8c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-1h12v1c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-8l-2.08-5.99zM6.5 16c-.83 0-1.5-.67-1.5-1.5S5.67 13 6.5 13s1.5.67 1.5 1.5S7.33 16 6.5 16zm11 0c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zM5 11l1.5-4.5h11L19 11H5z',
    color: '#67C23A'
  }
}

// 获取站点类型标签
const getStationTypeLabel = (type?: string) => {
  const labels: Record<string, string> = {
    tollgate: '收费站',
    checkpoint: '卡口',
    service: '服务区'
  }
  return labels[type || ''] || '未知'
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

// 创建统一的标记图标（简单圆点样式）
const createMarkerContent = (station: StationData) => {
  // 根据状态设置颜色
  const statusColor = station.status === 'congested' ? '#F56C6C' : 
                     station.status === 'busy' ? '#E6A23C' : '#409EFF'
  
  return `
    <div class="station-marker" style="
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

    // 添加站点标记
    if (props.showStations) {
      addStationMarkers()
    }

    console.log('徐州地图加载成功')
  } catch (e) {
    console.error('地图加载失败:', e)
  }
}

// 生成信息窗体HTML内容
const generateInfoWindowContent = (station: StationData) => {
  const typeLabels: Record<string, string> = {
    tollgate: '收费站',
    checkpoint: '卡口',
    service: '服务区'
  }
  const statusLabels: Record<string, string> = {
    normal: '正常',
    busy: '繁忙',
    congested: '拥堵'
  }
  const statusClass = station.status || 'normal'
  
  return `
    <div class="station-info-window" style="
      background: #fff;
      border-radius: 6px;
      box-shadow: 0 2px 12px rgba(0,0,0,0.12);
      min-width: 180px;
      overflow: hidden;
      border: 1px solid #e4e7ed;
    ">
      <div style="
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 10px 12px;
        background: #f5f7fa;
        border-bottom: 1px solid #e4e7ed;
      ">
        <span style="font-size: 13px; font-weight: 600; color: #303133;">${station.name}</span>
        <span style="
          font-size: 10px;
          padding: 2px 6px;
          border-radius: 3px;
          background: #ecf5ff;
          color: #409EFF;
        ">${typeLabels[station.type] || '未知'}</span>
      </div>
      <div style="padding: 10px 12px;">
        <div style="display: flex; justify-content: space-between; padding: 5px 0; border-bottom: 1px solid #f0f0f0;">
          <span style="font-size: 12px; color: #909399;">实时车流</span>
          <span style="font-size: 12px; color: #303133; font-weight: 500;">${station.flow || 0} 辆/小时</span>
        </div>
        <div style="display: flex; justify-content: space-between; padding: 5px 0; border-bottom: 1px solid #f0f0f0;">
          <span style="font-size: 12px; color: #909399;">今日通行</span>
          <span style="font-size: 12px; color: #303133; font-weight: 500;">${station.todayTotal || 0} 辆</span>
        </div>
        <div style="display: flex; justify-content: space-between; padding: 5px 0;">
          <span style="font-size: 12px; color: #909399;">状态</span>
          <span style="
            padding: 2px 6px;
            border-radius: 3px;
            font-size: 10px;
            background: ${statusClass === 'congested' ? '#fef0f0' : statusClass === 'busy' ? '#fdf6ec' : '#f0f9eb'};
            color: ${statusClass === 'congested' ? '#F56C6C' : statusClass === 'busy' ? '#E6A23C' : '#67C23A'};
          ">${statusLabels[station.status || ''] || '正常'}</span>
        </div>
      </div>
    </div>
  `
}

// 添加站点标记
const addStationMarkers = () => {
  const stationsData = props.stations.length > 0 ? props.stations : defaultStations
  
  stationsData.forEach(station => {
    const marker = new AMap.Marker({
      position: station.position,
      content: createMarkerContent(station),
      offset: new AMap.Pixel(-7, -7),
      extData: station
    })

    // 点击事件 - 使用闭包确保station数据正确
    marker.on('click', (e: any) => {
      e.stopPropagation && e.stopPropagation()
      
      // 从 marker 的 extData 获取站点数据，确保数据准确
      const stationData = marker.getExtData() as StationData
      selectedStationInfo.value = stationData
      emit('station-click', stationData)
      
      // 直接生成HTML内容显示信息窗体，避免Vue响应式延迟
      if (infoWindow) {
        const content = generateInfoWindowContent(stationData)
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

    stationMarkers.push(marker)
  })

  map.add(stationMarkers)
}

// 移除站点标记
const removeStationMarkers = () => {
  if (stationMarkers.length > 0) {
    map.remove(stationMarkers)
    stationMarkers = []
  }
}

// 切换站点显示
const toggleStations = () => {
  stationsVisible.value = !stationsVisible.value
  if (stationsVisible.value) {
    addStationMarkers()
  } else {
    removeStationMarkers()
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
  addStationMarkers,
  removeStationMarkers
})

onMounted(() => {
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

.station-legend .items i.normal {
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
