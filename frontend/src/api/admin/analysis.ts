/**
 * 管理员 - 预测分析接口（Time-MoE）
 */
import { http } from '@/utils/http'

export type ForecastRefreshReq = {
  checkpointId: string
  fxlx: '1' | '2'
  asOfTime?: string
  modelVersion?: string
}

export type ForecastRefreshResp = {
  requestId: number
  checkpointId: string
  fxlx: '1' | '2'
  asOfTime: string
  modelVersion: string
}

export type ForecastLatestResp = {
  checkpointId: string
  fxlx: '1' | '2'
  modelVersion: string
  updatedAt?: string
  pending: boolean
  requestId?: number
  requestCreatedAt?: string
  startTime: string
  times: string[]
  values: number[]
}

export function refreshForecast(data: ForecastRefreshReq) {
  return http.post<ForecastRefreshResp>('/admin/analysis/forecast/refresh', data)
}

export function getLatestForecast(params: { checkpointId: string; fxlx: '1' | '2'; modelVersion?: string }) {
  return http.get<ForecastLatestResp>('/admin/analysis/forecast/latest', params)
}


