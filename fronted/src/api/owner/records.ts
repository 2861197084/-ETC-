/**
 * 车主 - 通行记录接口
 */
import { http } from '@/utils/http'
import type { PassRecord, PassRecordQueryParams } from '@/types/traffic'

/**
 * 获取通行记录列表
 */
export function getPassRecords(params?: PassRecordQueryParams) {
  return http.get<{
    list: PassRecord[]
    total: number
  }>('/owner/records/pass', params)
}

/**
 * 获取通行记录详情
 */
export function getPassRecordDetail(id: string) {
  return http.get<PassRecord>(`/owner/records/pass/${id}`)
}

/**
 * 搜索通行记录
 */
export function searchPassRecords(params: PassRecordQueryParams) {
  return http.get<{
    list: PassRecord[]
    total: number
  }>('/owner/records/search', params)
}

/**
 * Text2SQL - 自然语言查询
 */
export function ownerText2sql(data: { query: string }) {
  return http.post<{
    sql: string
    explanation: string
  }>('/owner/records/text2sql', data)
}

/**
 * 执行查询
 */
export function executeOwnerQuery(data: { sql: string }) {
  return http.post<{
    columns: string[]
    data: Record<string, unknown>[]
    total: number
  }>('/owner/records/execute', data)
}
