import { whGet, whPost, whPut } from '@/utils/request'

export const warehouseAuthApi = {
  wxLogin: (jsCode, warehouseId) => whPost('/auth/wx-login', { jsCode, warehouseId }),
  bindPhone: (data) => whPost('/auth/bind-phone', data)
}
export const warehouseApi = {
  apply: () => whGet('/apply'),
  saveApply: (data) => whPut('/apply', data),
  onboarding: () => whGet('/onboarding'),
  attachments: (attachments) => whPost('/apply/attachments', { attachments }),
  dashboard: () => whGet('/dashboard'),
  customers: (params) => whGet('/customers', params),
  orders: (params) => whGet('/orders/page', params),
  erp: () => whGet('/erp'),
  issueSandbox: () => whPost('/erp/issue-sandbox'),
  testOrders: (data) => whPost('/erp/test-orders', data),
  testResults: (params) => whGet('/erp/test-results', params),
  syncLogs: (params) => whGet('/erp/sync-logs', params),
  ping: () => whGet('/erp/ping'),
  settlements: (params) => whGet('/settlement/page', params),
  confirmSettlement: (id) => whPost(`/settlement/${id}/confirm`),
  disputeSettlement: (id, reason) => whPost(`/settlement/${id}/dispute`, { reason }),
  contracts: () => whGet('/contracts'),
  createContract: (data) => whPost('/contracts', data),
  agreement: () => whGet('/agreement'),
  uploadAgreement: (file) => whPost('/agreement/upload', { file }),
  notices: (params) => whGet('/notice/page', params)
}
