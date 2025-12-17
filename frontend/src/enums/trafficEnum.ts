/**
 * 交通业务枚举
 *
 * 定义彭城交通系统的业务相关枚举
 *
 * @module enums/trafficEnum
 */

/**
 * 车辆类型
 */
export enum VehicleTypeEnum {
  /** 小型车 */
  SMALL = 'small',
  /** 中型车 */
  MEDIUM = 'medium',
  /** 大型车 */
  LARGE = 'large',
  /** 特大型车 */
  EXTRA_LARGE = 'extra_large',
  /** 摩托车 */
  MOTORCYCLE = 'motorcycle'
}

/**
 * 车辆类型名称映射
 */
export const VehicleTypeNameMap: Record<VehicleTypeEnum, string> = {
  [VehicleTypeEnum.SMALL]: '小型车',
  [VehicleTypeEnum.MEDIUM]: '中型车',
  [VehicleTypeEnum.LARGE]: '大型车',
  [VehicleTypeEnum.EXTRA_LARGE]: '特大型车',
  [VehicleTypeEnum.MOTORCYCLE]: '摩托车'
}

/**
 * 通行状态
 */
export enum PassStatusEnum {
  /** 正常 */
  NORMAL = 'normal',
  /** 异常 */
  ABNORMAL = 'abnormal',
  /** 待处理 */
  PENDING = 'pending'
}

/**
 * 通行状态名称映射
 */
export const PassStatusNameMap: Record<PassStatusEnum, string> = {
  [PassStatusEnum.NORMAL]: '正常',
  [PassStatusEnum.ABNORMAL]: '异常',
  [PassStatusEnum.PENDING]: '待处理'
}

/**
 * 违规类型
 */
export enum ViolationTypeEnum {
  /** 套牌车 */
  CLONE_PLATE = 'clone_plate',
  /** 超速 */
  SPEEDING = 'speeding',
  /** 逆行 */
  WRONG_WAY = 'wrong_way',
  /** 闯红灯 */
  RED_LIGHT = 'red_light',
  /** 未系安全带 */
  NO_SEATBELT = 'no_seatbelt',
  /** 其他 */
  OTHER = 'other'
}

/**
 * 违规类型名称映射
 */
export const ViolationTypeNameMap: Record<ViolationTypeEnum, string> = {
  [ViolationTypeEnum.CLONE_PLATE]: '套牌车',
  [ViolationTypeEnum.SPEEDING]: '超速',
  [ViolationTypeEnum.WRONG_WAY]: '逆行',
  [ViolationTypeEnum.RED_LIGHT]: '闯红灯',
  [ViolationTypeEnum.NO_SEATBELT]: '未系安全带',
  [ViolationTypeEnum.OTHER]: '其他'
}

/**
 * 拥堵等级
 */
export enum CongestionLevelEnum {
  /** 畅通 */
  SMOOTH = 'smooth',
  /** 轻度拥堵 */
  LOW = 'low',
  /** 中度拥堵 */
  MEDIUM = 'medium',
  /** 严重拥堵 */
  HIGH = 'high'
}

/**
 * 拥堵等级名称映射
 */
export const CongestionLevelNameMap: Record<CongestionLevelEnum, string> = {
  [CongestionLevelEnum.SMOOTH]: '畅通',
  [CongestionLevelEnum.LOW]: '轻度拥堵',
  [CongestionLevelEnum.MEDIUM]: '中度拥堵',
  [CongestionLevelEnum.HIGH]: '严重拥堵'
}

/**
 * 拥堵等级颜色映射
 */
export const CongestionLevelColorMap: Record<CongestionLevelEnum, string> = {
  [CongestionLevelEnum.SMOOTH]: '#52c41a',
  [CongestionLevelEnum.LOW]: '#fadb14',
  [CongestionLevelEnum.MEDIUM]: '#fa8c16',
  [CongestionLevelEnum.HIGH]: '#ff4d4f'
}

/**
 * 提醒类型
 */
export enum AlertTypeEnum {
  /** 疑似套牌 */
  SUSPECTED_CLONE = 'suspected_clone',
  /** 违章风险 */
  VIOLATION_RISK = 'violation_risk',
  /** 系统通知 */
  SYSTEM_NOTICE = 'system_notice',
  /** 账单提醒 */
  BILL_NOTICE = 'bill_notice'
}

/**
 * 提醒类型名称映射
 */
export const AlertTypeNameMap: Record<AlertTypeEnum, string> = {
  [AlertTypeEnum.SUSPECTED_CLONE]: '疑似套牌提醒',
  [AlertTypeEnum.VIOLATION_RISK]: '违章风险提示',
  [AlertTypeEnum.SYSTEM_NOTICE]: '系统通知',
  [AlertTypeEnum.BILL_NOTICE]: '账单提醒'
}
