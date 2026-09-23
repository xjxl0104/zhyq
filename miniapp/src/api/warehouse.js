import { whGet, whPost, whPut } from '@/utils/request'

export const warehouseAuthApi = {
  wxLogin: (jsCode, warehouseId, appId) => whPost('/auth/wx-login', { jsCode, warehouseId, appId }),
  passwordLogin: (data) => whPost('/auth/password-login', data),
  passwordRegister: (data) => whPost('/auth/password-register', data),
  passwordSetup: (data) => whPost('/auth/password-setup', data),
  passwordStatus: () => whGet('/auth/password-status'),
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
  notices: (params) => whGet('/notice/page', params),
  noticeUnread: () => whGet('/notice/unread-count'),
  noticeRead: (id) => whPost(`/notice/${id}/read`)
}
