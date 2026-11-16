import request from '@/utils/request'

// 表计
export const meterApi = {
  page: (params) => request.get('/energy/meter/page', { params }),
  list: () => request.get('/energy/meter/list'),
  stats: () => request.get('/energy/meter/stats'),
  add: (data) => request.post('/energy/meter', data),
  update: (data) => request.put('/energy/meter', data),
  remove: (id) => request.delete(`/energy/meter/${id}`),
  // 按最近一次抄表出能源费账单;一次抄表只出一张(后端幂等键挡重复)
  createBill: (id) => request.post(`/energy/meter/${id}/bill`),
  operLogs: (id) => request.get(`/energy/meter/${id}/oper-logs`)
}

// 抄表读数
export const readingApi = {
  page: (params) => request.get('/energy/reading/page', { params }),
  add: (data) => request.post('/energy/reading', data),
  importFile: (data) => request.post('/energy/reading/import', data, {
    headers: { 'Content-Type': 'multipart/form-data' }
  }),
  update: (data) => request.put('/energy/reading', data),
  remove: (id) => request.delete(`/energy/reading/${id}`)
}

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
  confirm: (id) => request.post(`/energy/fee-period/${id}/confirm`),
  revoke: (id) => request.post(`/energy/fee-period/${id}/revoke`),
  export: (id) => request.get(`/energy/fee-period/${id}/export`, { responseType: 'blob' })
}

// 能耗统计(§14 概览/趋势/表计排行)
export const energyStatsApi = {
  overview: () => request.get('/energy/stats-api/overview'),
  trend: (months = 6) => request.get('/energy/stats-api/trend', { params: { months } }),
  meterRank: () => request.get('/energy/stats-api/meter-rank')
}
