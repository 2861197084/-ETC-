import { ref, reactive } from 'vue'
import { mockDashboardData } from '@/mock/dashboard'

export interface StationStat {
  name: string
  value: number
  percentage: string
}

export interface VehicleTypeStat {
  name: string
  value: number
  color: string
}

export interface HourlyData {
  hour: string
  vehicleType: string
  count: number
}

export interface Alert {
  id: number
  stationName: string
  location: string
  time: string
  alertValue: string
  status: 'normal' | 'warning' | 'danger'
  content: string
}

export interface MapRegionData {
  name: string
  value: number
}

export interface AlertTrendData {
  time: string
  value: number
  station: string
}

export interface DashboardData {
  totalVehicles: number
  smallVehicles: number
  largeVehicles: number
  stationStats: StationStat[]
  vehicleTypes: VehicleTypeStat[]
  vehicleTypeFlow: { name: string; passenger: number; freight: number; ratio: number }[]
  hourlyFlow: HourlyData[]
  recentAlerts: Alert[]
  mapData: MapRegionData[]
  alertTrend: AlertTrendData[]
}

export function useDashboardData() {
  const loading = ref(false)
  const lastUpdateTime = ref<Date | null>(null)
  
  const dashboardData = reactive<DashboardData>({
    totalVehicles: 0,
    smallVehicles: 0,
    largeVehicles: 0,
    stationStats: [],
    vehicleTypes: [],
    vehicleTypeFlow: [],
    hourlyFlow: [],
    recentAlerts: [],
    mapData: [],
    alertTrend: [],
  })

  const fetchData = async () => {
    loading.value = true
    
    try {
      // 模拟网络延迟
      await new Promise(resolve => setTimeout(resolve, 500))
      
      // 获取 mock 数据
      const data = mockDashboardData()
      
      // 更新响应式数据
      Object.assign(dashboardData, data)
      lastUpdateTime.value = new Date()
      
    } catch (error) {
      console.error('Failed to fetch dashboard data:', error)
    } finally {
      loading.value = false
    }
  }

  return {
    dashboardData,
    loading,
    lastUpdateTime,
    fetchData,
  }
}

