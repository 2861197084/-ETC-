/**
 * 管理员 - 数据查询接口
 */
import { http } from '@/utils/http'
import type { PassRecord, PassRecordQueryParams } from '@/types/traffic'

// Vanna Text2SQL 服务地址
const VANNA_API_URL = import.meta.env.VITE_VANNA_API_URL || 'http://localhost:8100'

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
 * Text2SQL - 自然语言转 SQL (使用 Vanna 服务)
 */
export async function text2sql(data: { query: string; execute?: boolean }) {
  try {
    const response = await fetch(`${VANNA_API_URL}/api/v1/ask`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        question: data.query,
        execute: data.execute ?? false  // 默认只生成 SQL
      })
    })

    if (!response.ok) {
      throw new Error(`Vanna 服务请求失败: ${response.status}`)
    }

    const result = await response.json()
    
    return {
      code: result.error ? 500 : 200,
      msg: result.error || 'success',
      data: {
        sql: result.sql || '',
        explanation: `由 Vanna AI 生成`,
        confidence: result.sql ? 0.9 : 0,
        // 如果执行了查询，返回结果
        columns: result.columns,
        result: result.data
      }
    }
  } catch (error) {
    console.error('Vanna Text2SQL 失败:', error)
    // 降级到后端 API
    return http.post<{
      sql: string
      explanation: string
      confidence: number
    }>('/admin/query/text2sql', data)
  }
}

/**
 * 使用 Vanna 执行 SQL 查询
 */
export async function executeQueryVanna(sql: string) {
  try {
    // 使用 Vanna 的直接执行 SQL 接口
    const response = await fetch(`${VANNA_API_URL}/api/v1/execute`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ sql })
    })

    if (!response.ok) {
      throw new Error(`执行失败: ${response.status}`)
    }

    const result = await response.json()
    
    if (!result.success && result.error) {
      return {
        code: 500,
        msg: result.error,
        data: { columns: [], data: [], total: 0 }
      }
    }
    
    return {
      code: 200,
      msg: 'success',
      data: {
        columns: result.columns || [],
        data: result.data ? result.data.map((row: any[]) => {
          const obj: Record<string, unknown> = {}
          result.columns?.forEach((col: string, i: number) => {
            obj[col] = row[i]
          })
          return obj
        }) : [],
        total: result.total || 0
      }
    }
  } catch (error) {
    console.error('Vanna 执行查询失败:', error)
    // 降级到后端
    return executeQuery({ sql })
  }
}

/**
 * 执行 SQL 查询 (后端 API)
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
