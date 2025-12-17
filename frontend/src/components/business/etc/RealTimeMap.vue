<template>
  <div class="real-time-map-wrapper">
    <!-- 地图工具栏 -->
    <div class="map-toolbar">
      <div class="search-box">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索地点..."
          :prefix-icon="Search"
          clearable
          size="default"
        />
        <!-- 搜索建议下拉 -->
        <div v-if="searchSuggestions.length > 0" class="search-suggestions">
          <div
            v-for="(item, index) in searchSuggestions"
            :key="index"
            class="suggestion-item"
            @click="selectSuggestion(item)"
          >
            <el-icon><Location /></el-icon>
            <span>{{ item.name }}</span>
            <small>{{ item.district }}</small>
          </div>
        </div>
      </div>
      <div class="toolbar-actions">
        <el-button :icon="Aim" circle @click="getCurrentLocation" title="定位当前位置" />
        <el-button 
          :icon="trafficVisible ? View : Hide" 
          circle 
          @click="toggleTrafficLayer" 
          :type="trafficVisible ? 'primary' : 'default'"
          title="路况图层"
        />
      </div>
    </div>

    <!-- 地图容器 -->
    <div class="map-container" ref="mapContainer"></div>

    <!-- 路况图例 -->
    <div class="traffic-legend" v-if="trafficVisible">
      <span class="legend-title">路况图例</span>
      <div class="legend-items">
        <span class="legend-item"><i style="background: #00b514"></i>畅通</span>
        <span class="legend-item"><i style="background: #ff7324"></i>缓行</span>
        <span class="legend-item"><i style="background: #e02222"></i>拥堵</span>
        <span class="legend-item"><i style="background: #5e0a0b"></i>严重拥堵</span>
      </div>
    </div>

    <!-- 当前位置信息 -->
    <div class="location-info" v-if="currentLocation">
      <el-icon><Location /></el-icon>
      <span>{{ currentLocation }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { Search, Location, Aim, View, Hide } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import AMapLoader from '@amap/amap-jsapi-loader'

defineOptions({ name: 'RealTimeMap' })

interface TollStation {
  id: string
  name: string
  lng: number
  lat: number
  flow: number
  status: 'normal' | 'busy' | 'congested'
}

interface Props {
  stations?: TollStation[]
  center?: [number, number]
  zoom?: number
  mapKey?: string
  securityCode?: string
}

const props = withDefaults(defineProps<Props>(), {
  stations: () => [],
  center: () => [116.397428, 39.90923],
  zoom: 12,
  mapKey: '29add0f5bad710ee6fcb329bc2c31329',
  securityCode: 'ca1b1f8025b0dd28c73d0862f52c6ca9'
})

const emit = defineEmits<{
  (e: 'stationClick', station: TollStation): void
  (e: 'mapReady', map: any): void
  (e: 'locationChange', location: { lng: number; lat: number; address: string }): void
}>()

const mapContainer = ref<HTMLDivElement>()
const searchKeyword = ref('')
const searchSuggestions = ref<any[]>([])
const currentLocation = ref('')
const trafficVisible = ref(true)

let map: any = null
let AMap: any = null
let markers: any[] = []
let trafficLayer: any = null
let autoComplete: any = null
let geolocation: any = null

const statusColors = {
  normal: '#52c41a',
  busy: '#faad14',
  congested: '#ff4d4f'
}

// 初始化地图
const initMap = async () => {
  try {
    ;(window as any)._AMapSecurityConfig = {
      securityJsCode: props.securityCode
    }

    AMap = await AMapLoader.load({
      key: props.mapKey,
      version: '2.0',
      plugins: [
        'AMap.Scale',
        'AMap.ToolBar',
        'AMap.Geolocation',
        'AMap.AutoComplete',
        'AMap.PlaceSearch',
        'AMap.CitySearch',
        'AMap.InfoWindow'
      ]
    })

    if (!mapContainer.value) return

    map = new AMap.Map(mapContainer.value, {
      zoom: props.zoom,
      center: props.center,
      viewMode: '2D',
      mapStyle: 'amap://styles/normal'
    })

    // 实时交通图层（最重要）
    trafficLayer = new AMap.TileLayer.Traffic({
      autoRefresh: true,
      interval: 180,
      zIndex: 10
    })
    map.add(trafficLayer)

    map.addControl(new AMap.Scale({ position: 'LB' }))
    map.addControl(new AMap.ToolBar({ position: 'RB' }))

    initSearchPlugins()
    initGeolocation()
    renderStationMarkers()
    getCityInfo()

    emit('mapReady', map)
  } catch (error) {
    console.error('地图加载失败:', error)
    ElMessage.error('地图加载失败')
  }
}

let placeSearch: any = null

const initSearchPlugins = () => {
  // 输入提示
  autoComplete = new AMap.AutoComplete({
    city: '全国',
    input: '' // 不绑定DOM，手动处理
  })
  
  // POI搜索
  placeSearch = new AMap.PlaceSearch({
    city: '全国',
    pageSize: 10,
    pageIndex: 1,
    extensions: 'all'
  })
}

const initGeolocation = () => {
  geolocation = new AMap.Geolocation({
    enableHighAccuracy: true,
    timeout: 10000,
    zoomToAccuracy: true,
    buttonPosition: 'RB',
    showButton: false,
    showCircle: true,
    showMarker: true,
    markerOptions: {
      offset: new AMap.Pixel(-18, -36),
      content: '<div style="background:#409eff;padding:6px 10px;border-radius:4px;color:#fff;font-size:12px;white-space:nowrap;">我的位置</div>'
    },
    circleOptions: {
      strokeColor: '#409eff',
      strokeWeight: 2,
      fillColor: 'rgba(64,158,255,0.2)',
      fillOpacity: 0.3
    }
  })
  // 添加到地图
  map.addControl(geolocation)
}

const getCityInfo = () => {
  const citySearch = new AMap.CitySearch()
  citySearch.getLocalCity((status: string, result: any) => {
    if (status === 'complete' && result.info === 'OK') {
      currentLocation.value = result.city
    }
  })
}

watch(searchKeyword, (val) => {
  if (val && autoComplete) {
    autoComplete.search(val, (status: string, result: any) => {
      if (status === 'complete' && result.tips) {
        searchSuggestions.value = result.tips.filter((tip: any) => tip.location)
      } else {
        searchSuggestions.value = []
      }
    })
  } else {
    searchSuggestions.value = []
  }
})

const selectSuggestion = (item: any) => {
  searchSuggestions.value = []
  searchKeyword.value = item.name
  
  if (item.location && map) {
    const lnglat = [item.location.lng, item.location.lat]
    map.setZoomAndCenter(15, lnglat)
    
    // 清除之前的搜索标记
    if ((window as any)._searchMarker) {
      map.remove((window as any)._searchMarker)
    }
    
    // 创建标记
    const marker = new AMap.Marker({
      position: lnglat,
      title: item.name,
      animation: 'AMAP_ANIMATION_DROP'
    })
    map.add(marker)
    ;(window as any)._searchMarker = marker
    
    // 创建信息窗体
    const infoWindow = new AMap.InfoWindow({
      content: `
        <div style="padding:10px;min-width:200px;">
          <h4 style="margin:0 0 8px 0;color:#333;">${item.name}</h4>
          <p style="margin:0;color:#666;font-size:13px;">${item.district || ''} ${item.address || ''}</p>
        </div>
      `,
      offset: new AMap.Pixel(0, -30)
    })
    infoWindow.open(map, lnglat)
    
    ElMessage.success(`已定位到: ${item.name}`)
  }
}

const getCurrentLocation = () => {
  if (!geolocation) {
    ElMessage.warning('定位组件未初始化')
    return
  }
  
  const loading = ElMessage.info({
    message: '正在获取位置...',
    duration: 0
  })
  
  geolocation.getCurrentPosition((status: string, result: any) => {
    loading.close()
    
    if (status === 'complete' && result.position) {
      const { position, formattedAddress } = result
      map.setZoomAndCenter(14, [position.lng, position.lat])
      currentLocation.value = formattedAddress || `${position.lng.toFixed(4)}, ${position.lat.toFixed(4)}`
      emit('locationChange', { lng: position.lng, lat: position.lat, address: formattedAddress || '' })
      ElMessage.success('定位成功')
    } else {
      console.error('定位失败:', result)
      // 尝试IP定位作为备选
      const citySearch = new AMap.CitySearch()
      citySearch.getLocalCity((cityStatus: string, cityResult: any) => {
        if (cityStatus === 'complete' && cityResult.info === 'OK') {
          const bounds = cityResult.bounds
          if (bounds) {
            map.setBounds(bounds)
          }
          currentLocation.value = cityResult.city || '定位到城市'
          ElMessage.warning('精确定位失败，已定位到城市级别')
        } else {
          ElMessage.error('定位失败，请检查定位权限')
        }
      })
    }
  })
}

const toggleTrafficLayer = () => {
  trafficVisible.value = !trafficVisible.value
  if (trafficLayer) {
    trafficVisible.value ? trafficLayer.show() : trafficLayer.hide()
  }
}

const toggleTraffic = (show: boolean) => {
  trafficVisible.value = show
  if (trafficLayer) {
    show ? trafficLayer.show() : trafficLayer.hide()
  }
}

const renderStationMarkers = () => {
  if (!map || !AMap) return
  markers.forEach((marker) => map.remove(marker))
  markers = []

  props.stations.forEach((station) => {
    const markerContent = document.createElement('div')
    markerContent.className = 'station-marker'
    markerContent.innerHTML = `
      <div class="marker-icon" style="background-color: ${statusColors[station.status]}">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="white">
          <path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z"/>
        </svg>
      </div>
      <div class="marker-label">${station.name}</div>
    `
    const marker = new AMap.Marker({
      position: [station.lng, station.lat],
      content: markerContent,
      anchor: 'bottom-center'
    })
    marker.on('click', () => emit('stationClick', station))
    markers.push(marker)
    map.add(marker)
  })
}

const focusStation = (stationId: string) => {
  const station = props.stations.find((s) => s.id === stationId)
  if (station && map) {
    map.setZoomAndCenter(14, [station.lng, station.lat])
  }
}

const setCenter = (lng: number, lat: number, zoom?: number) => {
  if (map) {
    map.setZoomAndCenter(zoom || props.zoom, [lng, lat])
  }
}

watch(() => props.stations, () => renderStationMarkers(), { deep: true })

onMounted(() => initMap())
onUnmounted(() => {
  if (map) {
    map.destroy()
    map = null
  }
})

defineExpose({ focusStation, toggleTraffic, setCenter, getMap: () => map })
</script>

<style lang="scss" scoped>
.real-time-map-wrapper {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 400px;
  border-radius: 8px;
  overflow: hidden;
  background: #1a1a2e;
}

.map-toolbar {
  position: absolute;
  top: 12px;
  left: 12px;
  right: 12px;
  z-index: 100;
  display: flex;
  align-items: center;
  gap: 12px;

  .search-box {
    position: relative;
    flex: 1;
    max-width: 300px;

    :deep(.el-input__wrapper) {
      background: rgba(26, 26, 46, 0.9);
      box-shadow: 0 2px 12px rgba(0, 0, 0, 0.2);
      border: 1px solid rgba(255, 255, 255, 0.1);
      
      --el-input-text-color: #fff;
      --el-text-color-regular: #fff;
      --el-input-icon-color: #fff;

      .el-input__inner {
        color: #fff !important;
        -webkit-text-fill-color: #fff !important;
        
        &::placeholder {
          color: rgba(255, 255, 255, 0.5);
        }
      }
    }
  }

  .search-suggestions {
    position: absolute;
    top: 100%;
    left: 0;
    right: 0;
    margin-top: 4px;
    background: #fff;
    border-radius: 6px;
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
    max-height: 280px;
    overflow-y: auto;

    .suggestion-item {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 10px 12px;
      cursor: pointer;
      transition: background 0.2s;

      &:hover { background: #f5f7fa; }

      .el-icon { color: #409eff; }
      span { flex: 1; font-size: 14px; color: #303133; }
      small { font-size: 12px; color: #909399; }
    }
  }

  .toolbar-actions {
    display: flex;
    gap: 8px;

    :deep(.el-button) {
      background: rgba(255, 255, 255, 0.95);
      box-shadow: 0 2px 12px rgba(0, 0, 0, 0.15);

      &.el-button--primary {
        background: #409eff;
        border-color: #409eff;
        color: #fff;
      }
    }
  }
}

.map-container {
  width: 100%;
  height: 100%;
}

.traffic-legend {
  position: absolute;
  bottom: 12px;
  left: 12px;
  z-index: 100;
  background: rgba(0, 0, 0, 0.75);
  padding: 10px 14px;
  border-radius: 6px;
  backdrop-filter: blur(4px);

  .legend-title {
    display: block;
    font-size: 12px;
    color: rgba(255, 255, 255, 0.7);
    margin-bottom: 8px;
  }

  .legend-items { display: flex; gap: 12px; }

  .legend-item {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    color: #fff;

    i {
      display: inline-block;
      width: 20px;
      height: 4px;
      border-radius: 2px;
    }
  }
}

.location-info {
  position: absolute;
  bottom: 12px;
  right: 12px;
  z-index: 100;
  display: flex;
  align-items: center;
  gap: 6px;
  background: rgba(0, 0, 0, 0.75);
  padding: 8px 12px;
  border-radius: 6px;
  color: #fff;
  font-size: 13px;
  backdrop-filter: blur(4px);

  .el-icon { color: #409eff; }
}

:deep(.station-marker) {
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: pointer;
  transition: transform 0.2s;

  &:hover { transform: scale(1.15); }

  .marker-icon {
    width: 28px;
    height: 28px;
    border-radius: 50% 50% 50% 0;
    display: flex;
    align-items: center;
    justify-content: center;
    transform: rotate(-45deg);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.4);
    border: 2px solid rgba(255, 255, 255, 0.8);

    svg { transform: rotate(45deg); }
  }

  .marker-label {
    margin-top: 4px;
    padding: 2px 8px;
    background: rgba(0, 0, 0, 0.8);
    color: #fff;
    font-size: 11px;
    border-radius: 4px;
    white-space: nowrap;
    box-shadow: 0 2px 6px rgba(0, 0, 0, 0.3);
  }
}
</style>
