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
      <button class="tool-btn" @click="locateToXuzhou" title="回到徐州">
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

    <!-- 城市信息 -->
    <div class="city-info">徐州市实时路况</div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import AMapLoader from '@amap/amap-jsapi-loader'

defineOptions({ name: 'XuzhouTrafficMap' })

// 徐州市中心坐标
const XUZHOU_CENTER = [117.284124, 34.205768]
const XUZHOU_ZOOM = 12

const mapRef = ref<HTMLElement>()
const searchText = ref('')
const suggestions = ref<any[]>([])
const showSuggestions = ref(false)
const trafficOn = ref(true)

let map: any = null
let AMap: any = null
let trafficLayer: any = null
let autoComplete: any = null
let searchMarker: any = null

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
      plugins: ['AMap.Scale', 'AMap.AutoComplete', 'AMap.PlaceSearch']
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

    console.log('徐州地图加载成功')
  } catch (e) {
    console.error('地图加载失败:', e)
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

// 回到徐州中心
const locateToXuzhou = () => {
  if (map) {
    map.setZoomAndCenter(XUZHOU_ZOOM, XUZHOU_CENTER)
  }
}

// 切换路况
const toggleTraffic = () => {
  trafficOn.value = !trafficOn.value
  if (trafficLayer) {
    trafficOn.value ? trafficLayer.show() : trafficLayer.hide()
  }
}

// 点击外部关闭建议
const handleClickOutside = (e: MouseEvent) => {
  const target = e.target as HTMLElement
  if (!target.closest('.map-toolbar')) {
    showSuggestions.value = false
  }
}

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
</style>
