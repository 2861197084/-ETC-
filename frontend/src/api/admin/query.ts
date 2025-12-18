/**
 * 管理员 - 数据查询接口
 */
import { http } from '@/utils/http'
import type { PassRecord, PassRecordQueryParams } from '@/types/traffic'

/**
 * 筛选查询通行记录
 */
export function searchRecords(params: PassRecordQueryParams) {
  return http.get<{
    list: PassRecord[]
    total: number
  }>('/admin/query/records', params)
}

/**
 * 获取通行记录详情
 */
export function getRecordDetail(id: string) {
  return http.get<PassRecord>(`/admin/query/records/${id}`)
}

/**
 * Text2SQL - 自然语言转 SQL
 */
export function text2sql(data: { query: string; context?: string }) {
  return http.post<{
    sql: string
    explanation: string
    confidence: number
  }>('/admin/query/text2sql', data)
}

/**
 * 执行 SQL 查询
 */
export function executeQuery(data: { sql: string }) {
  return http.post<{
    columns: string[]
    data: Record<string, unknown>[]
    total: number
  }>('/admin/query/execute', data)
}

/**
 * 获取查询历史
 */
export function getQueryHistory(params?: { page?: number; pageSize?: number }) {
  return http.get<{
    list: {
      id: string
      query: string
      sql: string
      createdAt: string
    }[]
    total: number
  }>('/admin/query/history', params)
}

/**
 * 导出查询结果
 */
export function exportQueryResult(data: { sql: string; format: 'excel' | 'csv' }) {
  return http.post('/admin/query/export', data, {
    responseType: 'blob'
  })
}

/**
 * 获取卡口下拉选项
 */
export function getCheckpointOptions() {
  return http.get<{ label: string; value: string }[]>('/admin/query/options/checkpoints')
}

/**
 * 获取车辆类型下拉选项
 */
export function getVehicleTypeOptions() {
  return http.get<{ label: string; value: string }[]>('/admin/query/options/vehicle-types')
}
