import type { DashboardData } from '@/composables/useDashboardData'

// 随机数生成器
const random = (min: number, max: number) => Math.floor(Math.random() * (max - min + 1)) + min

// 站点名称
const stationNames = ['松山湖南', '广东罗田主线站', '广东水朗D站']

// 省份数据
const provinces = [
  '广东', '湖南', '江西', '福建', '广西', '湖北', '河南', '浙江', 
  '安徽', '江苏', '四川', '云南', '贵州', '山东', '河北'
]

// 车辆型号
const vehicleTypes = [
  { name: '三型车(客)', color: '#00d4ff' },
  { name: '二型车(客)', color: '#00ff88' },
  { name: '一型车(客)', color: '#4facfe' },
  { name: '二型车(货)', color: '#ffaa00' },
  { name: '三型车(货)', color: '#ff6b35' },
  { name: '六型车(货)', color: '#8b5cf6' },
  { name: '五型车(货)', color: '#ff69b4' },
  { name: '一型车(货)', color: '#ffd700' },
]

// 生成时间序列
const generateTimeSequence = (minutes: number) => {
  const now = new Date()
  const times = []
  for (let i = minutes; i >= 0; i--) {
    const time = new Date(now.getTime() - i * 60000)
    times.push(time.toTimeString().slice(0, 5))
  }
  return times
}

// 生成 Mock 数据
export function mockDashboardData(): DashboardData {
  const totalVehicles = random(25000, 30000)
  const smallVehicles = random(10000, 12000)
  const largeVehicles = random(15000, 18000)

  // 出站点统计
  const stationStats = stationNames.map(name => {
    const value = random(3000, 3500)
    return {
      name,
      value,
      percentage: (value / 10000 * 100).toFixed(2) + '%'
    }
  })

  // 车辆型号排行
  const vehicleTypesData = vehicleTypes.map(type => ({
    ...type,
    value: random(2500, 3000)
  })).sort((a, b) => b.value - a.value)

  // 客货车情况（按车型分组）
  const vehicleTypeFlow = ['一型车', '二型车', '三型车', '四型车'].map(name => ({
    name,
    passenger: random(2000, 3000),
    freight: random(2000, 2800),
    ratio: random(80, 100)
  }))

  // 24小时车流数据
  const hours = ['0am', '4am', '8am', '12pm', '4pm', '8pm']
  const types = ['客车', '货车', '其他']
  const hourlyFlow: DashboardData['hourlyFlow'] = []
  
  hours.forEach(hour => {
    types.forEach(type => {
      hourlyFlow.push({
        hour,
        vehicleType: type,
        count: random(400, 2000)
      })
    })
  })

  // 告警趋势数据
  const times = generateTimeSequence(10)
  const alertTrend = stationNames.flatMap(station => 
    times.map(time => ({
      time,
      value: random(0, 50),
      station
    }))
  )

  // 最近告警
  const recentAlerts = stationNames.map((name, index) => ({
    id: index + 1,
    stationName: name,
    location: '深圳市',
    time: new Date().toLocaleString('zh-CN'),
    alertValue: random(15, 30).toFixed(2),
    status: (index === 2 ? 'normal' : 'warning') as 'normal' | 'warning' | 'danger',
    content: index === 2 ? '正常' : '流量告警'
  }))

  // 地图数据
  const mapData = provinces.map(name => ({
    name,
    value: random(10, 1200)
  }))

  return {
    totalVehicles,
    smallVehicles,
    largeVehicles,
    stationStats,
    vehicleTypes: vehicleTypesData,
    vehicleTypeFlow,
    hourlyFlow,
    recentAlerts,
    mapData,
    alertTrend,
  }
}

