import request from '@/utils/request'

// 月度缴费账期:电费 / 水费 / 垃圾处理费 / 缴费汇总 四个页面共用
// 流程:open 选月 → update 填总度数/单价/垃圾总额 → saveReadings 填各表读数 → generate 出通知单 → confirm 出账
export const feePeriodApi = {
  page: (params) => request.get('/energy/fee-period/page', { params }),
  // 年月表:某一年每个月的总用电量/电费、总用水量/水费、垃圾费、应收合计、已留存单据数
  overview: (year) => request.get('/energy/fee-period/overview', { params: { year } }),
  open: (period) => request.post('/energy/fee-period/open', { period }),
  get: (id) => request.get(`/energy/fee-period/${id}`),
  update: (data) => request.put('/energy/fee-period', data),
  remove: (id) => request.delete(`/energy/fee-period/${id}`),
  sheet: (id, energyType) => request.get(`/energy/fee-period/${id}/sheet`, { params: { energyType } }),
  saveReadings: (id, rows) => request.post(`/energy/fee-period/${id}/readings`, rows),
  meterFlags: (data) => request.put('/energy/fee-period/meter-flags', data),
  garbageSheet: (id) => request.get(`/energy/fee-period/${id}/garbage-sheet`),
  // 增减区域/用户:一块租户分表 = 一个区域下的一个用户;带 id 即修改
  saveMeter: (data) => request.post('/energy/fee-period/meter', data),
  deleteMeters: (ids) => request.post('/energy/fee-period/meter/delete', { ids }),
  // 删除通知单:单张/多选传 ids,一键全部用 deleteAllNotices
  deleteNotices: (id, ids) => request.post(`/energy/fee-period/${id}/notices/delete`, { ids }),
  deleteAllNotices: (id) => request.post(`/energy/fee-period/${id}/notices/delete-all`),
  generate: (id) => request.post(`/energy/fee-period/${id}/generate`),
  notices: (id) => request.get(`/energy/fee-period/${id}/notices`),
  updateNotice: (data) => request.put('/energy/fee-period/notice', data),
  updateNoticePayment: (id, data) => request.put(`/energy/fee-period/notice/${id}/payment`, data),
  updateNoticeInvoice: (id, data) => request.put(`/energy/fee-period/notice/${id}/invoice`, data),
  editNotice: (data) => request.post('/energy/fee-period/notice/edit', data),
  confirm: (id) => request.post(`/energy/fee-period/${id}/confirm`),
  revoke: (id) => request.post(`/energy/fee-period/${id}/revoke`),
  export: (id) => request.get(`/energy/fee-period/${id}/export`, { responseType: 'blob' })
}

// 租金与物业管理费月度账期(与水电费同模式,租金与物业费收取单位独立)
export const leaseFeeApi = {
  contracts: (includeDisabled = true) => request.get('/energy/lease-fee/contracts', { params: { includeDisabled } }),
  syncFormalContracts: () => request.post('/energy/lease-fee/contracts/sync-formal'),
  saveContract: (data) => request.post('/energy/lease-fee/contracts', data),
  deleteContract: (id) => request.delete(`/energy/lease-fee/contracts/${id}`),
  deposits: (includeDisabled = true) => request.get('/energy/lease-fee/deposits', { params: { includeDisabled } }),
  updateDeposit: (id, data) => request.put(`/energy/lease-fee/deposits/${id}`, data),
  depositConfig: () => request.get('/energy/lease-fee/deposit-config'),
  saveDepositConfig: (data) => request.put('/energy/lease-fee/deposit-config', data),
  calibrateDeposits: () => request.post('/energy/lease-fee/deposits/calibrate'),
  overview: (year) => request.get('/energy/lease-fee/overview', { params: { year } }),
  open: (period) => request.post('/energy/lease-fee/open', { period }),
  get: (id) => request.get(`/energy/lease-fee/${id}`),
  update: (data) => request.put('/energy/lease-fee', data),
  sheet: (id, feeType = 'RENT') => request.get(`/energy/lease-fee/${id}/sheet`, { params: { feeType } }),
  saveEntries: (id, rows) => request.post(`/energy/lease-fee/${id}/entries`, rows),
  generate: (id) => request.post(`/energy/lease-fee/${id}/generate`),
  notices: (id) => request.get(`/energy/lease-fee/${id}/notices`),
  editNotice: (data) => request.post('/energy/lease-fee/notice/edit', data),
  updateNoticePayment: (id, data) => request.put(`/energy/lease-fee/notice/${id}/payment`, data),
  updateNoticeInvoice: (id, data) => request.put(`/energy/lease-fee/notice/${id}/invoice`, data),
  deleteNotices: (id, ids) => request.post(`/energy/lease-fee/${id}/notices/delete`, { ids }),
  deleteAllNotices: (id) => request.post(`/energy/lease-fee/${id}/notices/delete-all`),
  confirm: (id) => request.post(`/energy/lease-fee/${id}/confirm`),
  revoke: (id) => request.post(`/energy/lease-fee/${id}/revoke`),
  export: (id) => request.get(`/energy/lease-fee/${id}/export`, { responseType: 'blob' })
}

// 缴费通知单抬头-按缴费单位定制配置(LEASE | UTILITY | DEPOSIT)
export const noticeHeaderProfileApi = {
  list: (scopeType) => request.get('/energy/notice-header-profile', { params: { scopeType } }),
  save: (data) => request.post('/energy/notice-header-profile', data),
  remove: (scopeType, tenantName, periodId) =>
    request.delete('/energy/notice-header-profile', { params: { scopeType, tenantName, periodId } })
}

// 缴费汇总-AI自动识别核准租户缴费回执与发票
export const noticeAiApi = {
  verifyUpload: (formData) =>
    request.post('/energy/notice-ai/verify-upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120000
    })
}
