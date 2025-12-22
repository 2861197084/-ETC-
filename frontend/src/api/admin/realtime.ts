/**
 * 管理员 - 实时数据接口
 */
import { http } from '@/utils/http'
import type { ClonePlateDetection, Violation, DailyStats } from '@/types/traffic'

/**
 * 获取套牌车检测数据
 */
export function getClonePlates(params?: {
  status?: string
  plateNumber?: string
  startTime?: string
  endTime?: string
  page?: number
  pageSize?: number
}) {
  return http.get<{
    list: ClonePlateDetection[]
    total: number
  }>('/admin/realtime/clone-plates', params)
}

/**
 * 处理套牌车检测
 */
export function handleClonePlate(id: string, data: { status: 'confirmed' | 'dismissed'; remark?: string }) {
  return http.put(`/admin/realtime/clone-plates/${id}`, data)
}

/**
 * 获取违禁信息列表
 */
export function getViolations(params?: {
  type?: string
  status?: string
  startTime?: string
  endTime?: string
  page?: number
  pageSize?: number
}) {
  return http.get<{
    list: Violation[]
    total: number
  }>('/admin/realtime/violations', params)
}

/**
 * 处理违禁信息
 */
export function handleViolation(id: string, data: { status: string; remark?: string }) {
  return http.put(`/admin/realtime/violations/${id}`, data)
}

/**
 * 获取站口压力预警
 */
export function getPressureWarnings() {
  return http.get('/admin/realtime/pressure-warnings')
}

/**
 * 获取今日统计数据
 */
export function getDailyStats() {
  return http.get<DailyStats>('/admin/realtime/daily-stats')
}

/**
 * 获取车辆来源统计（本地/外地）
 */
export function getVehicleSourceStats() {
  return http.get<{ local: number; foreign: number; total: number; localRate: number; foreignRate: number }>('/admin/realtime/vehicle-source')
}

/**
 * 获取区域热度排名统计
 * @param timeRange 'hour' 最近1小时，'day' 今日累计
 */
export function getRegionHeatStats(timeRange: 'hour' | 'day' = 'hour') {
  return http.get<{ region: string; count: number; trend: number }[]>('/admin/realtime/region-heat', { timeRange })
}

/**
 * 获取单个卡口的实时统计数据
 * @param checkpointId 卡口ID或Code
 */
export function getCheckpointStats(checkpointId: string) {
  return http.get<{
    checkpointId: string
    todayTotal: number
    hourlyFlow: number
    localCount: number
    foreignCount: number
    localRate: number
    foreignRate: number
    status: 'normal' | 'busy' | 'congested'
  }>(`/admin/realtime/checkpoint/${checkpointId}/stats`)
}

/**
 * 车流量高峰检测 - 返回超过阈值的卡口告警
 * @param threshold 阈值比例（0-1），默认0.7表示超过最大容量70%时触发告警
 */
export function getFlowAlerts(threshold = 0.7) {
  return http.get<{
    hasAlerts: boolean
    alertCount: number
    alerts: Array<{
      id: string
      checkpointId: string
      checkpointName: string
      type: 'pressure'
      level: 'warning' | 'danger' | 'critical'
      currentFlow: number
      maxCapacity: number
      ratio: number
      message: string
      time: string
    }>
    checkTime: string
  }>('/admin/realtime/flow-alerts', { threshold })
}

/**
 * 获取实时流量数据（WebSocket 连接前获取初始数据）
 */
export function getRealtimeFlow() {
  return http.get('/admin/realtime/flow')
}
