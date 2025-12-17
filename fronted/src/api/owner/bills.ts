/**
 * 车主 - 账单接口
 */
import { http } from '@/utils/http'
import type { BillSummary, PassRecord } from '@/types/traffic'

/**
 * 获取账单汇总
 */
export function getBillSummary(params: { month: string; vehicleId?: string }) {
  return http.get<BillSummary>('/owner/bills/summary', params)
}

/**
 * 获取账单明细
 */
export function getBillDetails(params: {
  month: string
  vehicleId?: string
  page?: number
  pageSize?: number
}) {
  return http.get<{
    list: PassRecord[]
    total: number
  }>('/owner/bills/details', params)
}

/**
 * 导出账单
 */
export function exportBill(params: { month: string; vehicleId?: string; format: 'pdf' | 'excel' }) {
  return http.get('/owner/bills/export', params, {
    responseType: 'blob'
  })
}

/**
 * 申请发票
 */
export function applyInvoice(data: {
  month: string
  vehicleId?: string
  invoiceType: 'personal' | 'company'
  invoiceTitle: string
  taxNumber?: string
  email: string
}) {
  return http.post('/owner/bills/invoice', data)
}

/**
 * 获取发票申请记录
 */
export function getInvoiceRecords(params?: { page?: number; pageSize?: number }) {
  return http.get<{
    list: {
      id: string
      month: string
      amount: number
      status: 'pending' | 'processing' | 'completed'
      invoiceUrl?: string
      createdAt: string
    }[]
    total: number
  }>('/owner/bills/invoices', params)
}
