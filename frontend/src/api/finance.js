import request from '@/utils/request'

// 账单
export const billApi = {
  page: (params) => request.get('/finance/bill/page', { params }),
  get: (id) => request.get(`/finance/bill/${id}`),
  stats: () => request.get('/finance/bill/stats'),
  overdue: (params) => request.get('/finance/bill/overdue', { params }),
  calcLateFee: () => request.post('/finance/bill/calcLateFee'),
  add: (data) => request.post('/finance/bill', data),
  update: (data) => request.put('/finance/bill', data),
  remove: (id) => request.delete(`/finance/bill/${id}`)
}

// 收款
export const paymentApi = {
  pay: (data) => request.post('/finance/payment', data),
  list: (billId) => request.get('/finance/payment/list', { params: { billId } })
}

// 发票
export const invoiceApi = {
  page: (params) => request.get('/finance/invoice/page', { params }),
  get: (id) => request.get(`/finance/invoice/${id}`),
  add: (data) => request.post('/finance/invoice', data),
  update: (data) => request.put('/finance/invoice', data),
  remove: (id) => request.delete(`/finance/invoice/${id}`)
}

// 收支流水
export const flowApi = {
  page: (params) => request.get('/finance/flow/page', { params })
}

// 财务报表
export const reportApi = {
  summary: () => request.get('/finance/report/summary')
}

// 收据
export const receiptApi = {
  page: (params) => request.get('/finance/receipt/page', { params }),
  get: (id) => request.get(`/finance/receipt/${id}`),
  add: (data) => request.post('/finance/receipt', data),
  update: (data) => request.put('/finance/receipt', data),
  remove: (id) => request.delete(`/finance/receipt/${id}`),
  print: (id) => request.post(`/finance/receipt/${id}/print`),
  logs: (id) => request.get(`/finance/receipt/${id}/logs`)
}

// 收款通知
export const payNoticeApi = {
  page: (params) => request.get('/finance/notice/page', { params }),
  remove: (id) => request.delete(`/finance/notice/${id}`),
  generate: () => request.post('/finance/notice/generate'),
  send: (id, data) => request.post(`/finance/notice/${id}/send`, data || {})
}

// 退房报表
export const checkoutApi = {
  page: (params) => request.get('/finance/checkout-report/page', { params }),
  stats: () => request.get('/finance/checkout-report/stats')
}

// 业务配置(财务设置)
export const settingApi = {
  list: (module) => request.get('/finance/setting/list', { params: { module } }),
  batch: (data) => request.put('/finance/setting/batch', data),
  add: (data) => request.post('/finance/setting', data),
  remove: (id) => request.delete(`/finance/setting/${id}`)
}
