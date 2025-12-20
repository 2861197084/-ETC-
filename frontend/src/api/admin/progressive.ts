/**
 * 渐进式加载 API
 * 
 * 支持 MySQL (7天热数据) + HBase (历史全量) 的两阶段查询策略
 */
import { http } from '@/utils/http'

/**
 * 渐进式加载响应类型
 */
export interface ProgressiveLoadResult<T> {
  /** 数据来源: mysql | hbase | mixed */
  source: 'mysql' | 'hbase' | 'mixed'
  /** 当前页数据 */
  list: T[]
  /** MySQL 中的记录总数（7天内） */
  mysqlTotal?: number
  /** Redis 缓存的全量计数 */
  totalCount: number
  /** 是否还有更多历史记录 */
  hasMoreHistory: boolean
  /** 当前页 */
  current: number
  /** 每页大小 */
  size: number
  /** HBase 查询的起始 RowKey（用于下次加载更多） */
  nextRowKey?: string
  /** 加载耗时（毫秒） */
  queryTimeMs?: number
  /** 额外统计信息 */
  stats?: Record<string, unknown>
}

/**
 * 通行记录类型
 */
export interface PassRecordItem {
  id?: number
  rowKey?: string
  plateNumber: string
  checkpointId: string | number
  checkpointName?: string
  passTime: string
  direction?: string
  speed?: number
  laneNo?: string
  vehicleType?: string
  etcDeduction?: number
  imageUrl?: string
  /** 数据来源标识 */
  source?: 'mysql' | 'hbase'
}

/**
 * 计数统计类型
 */
export interface CountStats {
  plateNumber?: string
  checkpointId?: string
  totalCount: number
  todayCount: number
  checkpointName?: string
}

/**
 * 按车牌查询通行记录（渐进式加载）
 */
export function queryByPlate(params: {
  plateNumber: string
  source?: 'mysql' | 'hbase'
  lastRowKey?: string
  page?: number
  size?: number
}) {
  return http.get<ProgressiveLoadResult<PassRecordItem>>('/api/progressive/records/by-plate', {
    plateNumber: params.plateNumber,
    source: params.source || 'mysql',
    lastRowKey: params.lastRowKey,
    page: params.page || 1,
    size: params.size || 20
  })
}

/**
 * 按卡口查询通行记录（渐进式加载）
 */
export function queryByCheckpoint(params: {
  checkpointId: string
  source?: 'mysql' | 'hbase'
  lastRowKey?: string
  page?: number
  size?: number
}) {
  return http.get<ProgressiveLoadResult<PassRecordItem>>('/api/progressive/records/by-checkpoint', {
    checkpointId: params.checkpointId,
    source: params.source || 'mysql',
    lastRowKey: params.lastRowKey,
    page: params.page || 1,
    size: params.size || 20
  })
}

/**
 * 综合查询通行记录（渐进式加载）
 */
export function queryRecords(params: {
  plateNumber?: string
  checkpointId?: string
  startTime?: string
  endTime?: string
  source?: 'mysql' | 'hbase'
  lastRowKey?: string
  page?: number
  size?: number
}) {
  return http.get<ProgressiveLoadResult<PassRecordItem>>('/api/progressive/records', params)
}

/**
 * 获取车牌通行统计
 */
export function getPlateCount(plateNumber: string) {
  return http.get<CountStats>(`/api/progressive/count/plate/${plateNumber}`)
}

/**
 * 获取卡口通行统计
 */
export function getCheckpointCount(checkpointId: string) {
  return http.get<CountStats>(`/api/progressive/count/checkpoint/${checkpointId}`)
}

/**
 * 获取全局通行统计
 */
export function getGlobalCount() {
  return http.get<{
    todayCount: number
    totalCount: number
    checkpointCounts: Record<string, number>
  }>('/api/progressive/count/global')
}
