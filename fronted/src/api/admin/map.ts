/**
 * 管理员 - 地图相关接口
 */
import { http } from '@/utils/http'
import type { Checkpoint, CheckpointPressure, TrafficHeatmapData } from '@/types/traffic'

/**
 * 获取卡口列表
 */
export function getCheckpoints(params?: { region?: string; status?: string }) {
  return http.get<Checkpoint[]>('/admin/map/checkpoints', params)
}

/**
 * 获取卡口详情
 */
export function getCheckpointDetail(id: string) {
  return http.get<Checkpoint>(`/admin/map/checkpoints/${id}`)
}

/**
 * 获取交通热力图数据
 */
export function getTrafficHeatmap(params?: { startTime?: string; endTime?: string }) {
  return http.get<TrafficHeatmapData[]>('/admin/map/heatmap', params)
}

/**
 * 获取站口压力列表
 */
export function getPressureList() {
  return http.get<CheckpointPressure[]>('/admin/map/pressure')
}

/**
 * 获取实时事件列表
 */
export function getRealtimeEvents(params?: { type?: string; limit?: number }) {
  return http.get('/admin/map/events', params)
}
