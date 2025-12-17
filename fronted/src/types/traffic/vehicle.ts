/**
 * 车辆相关类型定义
 */

import { VehicleTypeEnum } from '@/enums/trafficEnum'

/**
 * 车辆信息
 */
export interface Vehicle {
  /** 车辆ID */
  id: string
  /** 车牌号 */
  plateNumber: string
  /** 车牌颜色 */
  plateColor: 'blue' | 'yellow' | 'green' | 'white' | 'black'
  /** 车辆类型 */
  vehicleType: VehicleTypeEnum
  /** 车辆品牌 */
  brand?: string
  /** 车辆型号 */
  model?: string
  /** 车辆颜色 */
  color?: string
  /** 车主ID */
  ownerId?: string
  /** 车主姓名 */
  ownerName?: string
  /** 车主电话 */
  ownerPhone?: string
  /** ETC卡号 */
  etcCardNo?: string
  /** 创建时间 */
  createdAt: string
  /** 更新时间 */
  updatedAt: string
}

/**
 * 车主绑定车辆
 */
export interface OwnerVehicle extends Vehicle {
  /** 绑定时间 */
  bindTime: string
  /** 是否默认车辆 */
  isDefault: boolean
}

/**
 * 车辆轨迹点
 */
export interface TrajectoryPoint {
  /** 经度 */
  longitude: number
  /** 纬度 */
  latitude: number
  /** 时间 */
  time: string
  /** 速度 km/h */
  speed?: number
  /** 卡口ID */
  checkpointId?: string
  /** 卡口名称 */
  checkpointName?: string
}

/**
 * 车辆轨迹
 */
export interface VehicleTrajectory {
  /** 车辆ID */
  vehicleId: string
  /** 车牌号 */
  plateNumber: string
  /** 轨迹点列表 */
  points: TrajectoryPoint[]
  /** 开始时间 */
  startTime: string
  /** 结束时间 */
  endTime: string
  /** 总里程 km */
  totalDistance: number
}
