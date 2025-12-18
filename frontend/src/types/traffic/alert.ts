/**
 * 提醒和违规相关类型定义
 */

import { AlertTypeEnum, ViolationTypeEnum } from '@/enums/trafficEnum'

/**
 * 违规信息
 */
export interface Violation {
  /** 违规ID */
  id: string
  /** 车辆ID */
  vehicleId: string
  /** 车牌号 */
  plateNumber: string
  /** 违规类型 */
  violationType: ViolationTypeEnum
  /** 发现卡口ID */
  checkpointId: string
  /** 发现卡口名称 */
  checkpointName: string
  /** 发现时间 */
  detectTime: string
  /** 违规描述 */
  description?: string
  /** 证据图片 */
  evidenceImages?: string[]
  /** 处理状态 */
  status: 'pending' | 'processing' | 'resolved' | 'dismissed'
  /** 处理人 */
  handler?: string
  /** 处理时间 */
  handleTime?: string
  /** 处理备注 */
  handleRemark?: string
  /** 创建时间 */
  createdAt: string
}

/**
 * 套牌车检测信息
 */
export interface ClonePlateDetection {
  /** 检测ID */
  id: string
  /** 车牌号 */
  plateNumber: string
  /** 置信度 0-100 */
  confidence: number
  /** 冲突记录 */
  conflictRecords: {
    /** 卡口ID */
    checkpointId: string
    /** 卡口名称 */
    checkpointName: string
    /** 检测时间 */
    detectTime: string
    /** 经度 */
    longitude: number
    /** 纬度 */
    latitude: number
  }[]
  /** 最后出现位置 */
  lastLocation: {
    checkpointId: string
    checkpointName: string
    time: string
  }
  /** 处理状态 */
  status: 'pending' | 'confirmed' | 'dismissed'
  /** 创建时间 */
  createdAt: string
}

/**
 * 异常提醒
 */
export interface Alert {
  /** 提醒ID */
  id: string
  /** 用户ID */
  userId: string
  /** 车辆ID */
  vehicleId?: string
  /** 车牌号 */
  plateNumber?: string
  /** 提醒类型 */
  alertType: AlertTypeEnum
  /** 提醒标题 */
  title: string
  /** 提醒内容 */
  content: string
  /** 是否已读 */
  isRead: boolean
  /** 是否需要处理 */
  needAction: boolean
  /** 关联数据ID（如违规ID） */
  relatedId?: string
  /** 创建时间 */
  createdAt: string
  /** 过期时间 */
  expireAt?: string
}

/**
 * 申诉信息
 */
export interface Appeal {
  /** 申诉ID */
  id: string
  /** 用户ID */
  userId: string
  /** 关联提醒ID */
  alertId: string
  /** 申诉类型 */
  appealType: 'clone_plate' | 'violation' | 'fee' | 'other'
  /** 申诉原因 */
  reason: string
  /** 证明材料 */
  proofImages?: string[]
  /** 处理状态 */
  status: 'pending' | 'approved' | 'rejected'
  /** 处理结果 */
  result?: string
  /** 处理时间 */
  handleTime?: string
  /** 创建时间 */
  createdAt: string
}
