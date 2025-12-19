/**
 * 卡口相关类型定义
 */

import { CongestionLevelEnum } from '@/enums/trafficEnum'

/**
 * 出市卡口信息（19个固定卡口）
 */
export interface Checkpoint {
  /** 卡口ID */
  id: string
  /** 卡口编码 */
  code: string
  /** 卡口简称（地图标注用） */
  name: string
  /** 完整名称 */
  fullName: string
  /** 经度 */
  longitude: number
  /** 纬度 */
  latitude: number
  /** 所属区县 */
  region: string
  /** 卡口类型: provincial-省际, municipal-市际 */
  type: 'provincial' | 'municipal'
  /** 所属道路 */
  road: string
  /** 交界方向 */
  boundary: string
  /** 状态 */
  status: 'online' | 'offline' | 'maintenance'
}

/**
 * 市内监测站点（用于路径规划）
 */
export interface Station {
  /** 站点ID */
  id: string
  /** 站点编码 */
  code: string
  /** 站点名称 */
  name: string
  /** 经度 */
  longitude: number
  /** 纬度 */
  latitude: number
  /** 所属区县 */
  region: string
  /** 所在道路 */
  roadName?: string
  /** 道路等级 */
  roadLevel?: 'highway' | 'main' | 'secondary' | 'branch'
  /** 监测方向 */
  direction?: 'both' | 'inbound' | 'outbound'
  /** 状态 */
  status: 'online' | 'offline' | 'maintenance'
}

/**
 * 卡口/站点实时流量信息
 */
export interface CheckpointFlow {
  /** 卡口/站点ID */
  checkpointId: string
  /** 名称 */
  name: string
  /** 实时车流（辆/小时） */
  currentFlow: number
  /** 今日累计通行 */
  todayTotal: number
  /** 压力状态 */
  pressureStatus: 'normal' | 'busy' | 'congested'
  /** 更新时间 */
  updateTime: string
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
