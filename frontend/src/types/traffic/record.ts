/**
 * 通行记录相关类型定义
 */

import { PassStatusEnum, VehicleTypeEnum } from '@/enums/trafficEnum'

/**
 * 通行记录
 */
export interface PassRecord {
  /** 记录ID */
  id: string
  /** 车辆ID */
  vehicleId: string
  /** 车牌号 */
  plateNumber: string
  /** 车辆类型 */
  vehicleType: VehicleTypeEnum
  /** 入口卡口ID */
  entryCheckpointId: string
  /** 入口卡口名称 */
  entryCheckpointName: string
  /** 入口时间 */
  entryTime: string
  /** 出口卡口ID */
  exitCheckpointId?: string
  /** 出口卡口名称 */
  exitCheckpointName?: string
  /** 出口时间 */
  exitTime?: string
  /** 行驶里程 km */
  distance?: number
  /** 平均速度 km/h */
  avgSpeed?: number
  /** 通行费用 */
  fee?: number
  /** 支付状态 */
  paymentStatus: 'paid' | 'unpaid' | 'pending'
  /** 支付方式 */
  paymentMethod?: 'etc' | 'cash' | 'mobile'
  /** 通行状态 */
  status: PassStatusEnum
  /** 备注 */
  remark?: string
  /** 创建时间 */
  createdAt: string
}

/**
 * 通行记录查询参数
 */
export interface PassRecordQueryParams {
  /** 车牌号 */
  plateNumber?: string
  /** 入口卡口ID */
  entryCheckpointId?: string
  /** 出口卡口ID */
  exitCheckpointId?: string
  /** 开始时间 */
  startTime?: string
  /** 结束时间 */
  endTime?: string
  /** 车辆类型 */
  vehicleType?: VehicleTypeEnum
  /** 通行状态 */
  status?: PassStatusEnum
  /** 费用范围-最小值 */
  minFee?: number
  /** 费用范围-最大值 */
  maxFee?: number
  /** 页码 */
  page?: number
  /** 每页数量 */
  pageSize?: number
}

/**
 * 通行账单汇总
 */
export interface BillSummary {
  /** 月份 */
  month: string
  /** 总通行次数 */
  totalCount: number
  /** 总费用 */
  totalFee: number
  /** 总里程 */
  totalDistance: number
  /** 车辆统计 */
  vehicleStats: {
    vehicleId: string
    plateNumber: string
    count: number
    fee: number
  }[]
}

/**
 * 今日统计数据
 */
export interface DailyStats {
  /** 今日总流量 */
  totalFlow: number
  /** 今日总营收 */
  totalRevenue: number
  /** 平均车速 */
  avgSpeed: number
  /** 在线设备数 */
  onlineDevices: number
  /** 异常事件数 */
  abnormalEvents: number
  /** 同比增长率 */
  flowGrowthRate?: number
  /** 营收同比增长率 */
  revenueGrowthRate?: number
}
