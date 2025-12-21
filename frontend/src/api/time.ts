import { http } from '@/utils/http'

/**
 * 时间模拟 API
 */

export interface TimeStatus {
  simulatedTime: string
  simulatedTimestamp: number
  realTime: string
  timeScale: number
  isRunning: boolean
  windowStart: string
  windowEnd: string
}

/**
 * 获取当前时间状态
 */
export function getTimeStatus() {
  return http.get<TimeStatus>('/api/time')
}

/**
 * 启动时间模拟
 */
export function startSimulation() {
  return http.post<TimeStatus>('/api/time/start')
}

/**
 * 暂停时间模拟
 */
export function pauseSimulation() {
  return http.post<TimeStatus>('/api/time/pause')
}

/**
 * 重置时间到 2024-01-01 00:00
 */
export function resetSimulation() {
  return http.post<TimeStatus>('/api/time/reset')
}
