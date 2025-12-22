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
 * 获取实时流量数据（WebSocket 连接前获取初始数据）
 */
export function getRealtimeFlow() {
  return http.get('/admin/realtime/flow')
}
