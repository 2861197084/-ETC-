/**
 * 卡口相关类型定义
 */

import { CongestionLevelEnum } from '@/enums/trafficEnum'

/**
 * 卡口信息
 */
export interface Checkpoint {
  /** 卡口ID */
  id: string
  /** 卡口名称 */
  name: string
  /** 卡口编码 */
  code: string
  /** 经度 */
  longitude: number
  /** 纬度 */
  latitude: number
  /** 所属区域 */
  region: string
  /** 卡口类型 */
  type: 'entrance' | 'exit' | 'toll'
  /** 状态 */
  status: 'online' | 'offline' | 'maintenance'
  /** 创建时间 */
  createdAt: string
  /** 更新时间 */
  updatedAt: string
}

/**
 * 站口压力信息
 */
export interface CheckpointPressure {
  /** 卡口ID */
  checkpointId: string
  /** 卡口名称 */
  checkpointName: string
  /** 当前流量 */
  currentFlow: number
  /** 最大容量 */
  maxCapacity: number
  /** 压力等级 */
  pressureLevel: CongestionLevelEnum
  /** 预测拥堵时间（分钟） */
  predictedDuration: number
  /** 建议措施 */
  suggestion?: string
  /** 更新时间 */
  updateTime: string
}

/**
 * 交通热力数据
 */
export interface TrafficHeatmapData {
  /** 经度 */
  lng: number
  /** 纬度 */
  lat: number
  /** 流量值 */
  count: number
}
