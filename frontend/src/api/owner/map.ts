/**
 * 车主 - 地图相关接口
 */
import { http } from '@/utils/http'
import type { OwnerVehicle, VehicleTrajectory } from '@/types/traffic'

/**
 * 获取绑定车辆列表
 */
export function getVehicles() {
  return http.get<OwnerVehicle[]>('/owner/map/vehicles')
}

/**
 * 获取车辆轨迹
 */
export function getTrajectory(params: {
  vehicleId: string
  startTime: string
  endTime: string
}) {
  return http.get<VehicleTrajectory>('/owner/map/trajectory', params)
}

/**
 * 获取个人统计
 */
export function getPersonalStats(params?: { vehicleId?: string; month?: string }) {
  return http.get<{
    totalTrips: number
    totalFee: number
    totalDistance: number
    avgSpeed: number
  }>('/owner/map/stats', params)
}
