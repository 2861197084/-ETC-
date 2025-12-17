/**
 * 车主 - 异常提醒接口
 */
import { http } from '@/utils/http'
import type { Alert, Appeal } from '@/types/traffic'

/**
 * 获取异常提醒列表
 */
export function getAlerts(params?: {
  type?: string
  isRead?: boolean
  page?: number
  pageSize?: number
}) {
  return http.get<{
    list: Alert[]
    total: number
    unreadCount: number
  }>('/owner/alerts', params)
}

/**
 * 获取提醒详情
 */
export function getAlertDetail(id: string) {
  return http.get<Alert>(`/owner/alerts/${id}`)
}

/**
 * 标记提醒已读
 */
export function markAlertRead(id: string) {
  return http.put(`/owner/alerts/${id}/read`)
}

/**
 * 批量标记已读
 */
export function markAllAlertsRead() {
  return http.put('/owner/alerts/read-all')
}

/**
 * 忽略提醒
 */
export function dismissAlert(id: string) {
  return http.put(`/owner/alerts/${id}/dismiss`)
}

/**
 * 提交申诉
 */
export function submitAppeal(data: {
  alertId: string
  appealType: string
  reason: string
  proofImages?: string[]
}) {
  return http.post<Appeal>('/owner/alerts/appeal', data)
}

/**
 * 获取申诉列表
 */
export function getAppeals(params?: { status?: string; page?: number; pageSize?: number }) {
  return http.get<{
    list: Appeal[]
    total: number
  }>('/owner/alerts/appeals', params)
}

/**
 * 获取申诉详情
 */
export function getAppealDetail(id: string) {
  return http.get<Appeal>(`/owner/alerts/appeals/${id}`)
}
