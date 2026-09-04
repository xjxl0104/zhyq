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
  update: (data) => request.put('/energy/reading', data),
  remove: (id) => request.delete(`/energy/reading/${id}`)
}

// 能耗统计(§14 概览/趋势/表计排行)
export const energyStatsApi = {
  overview: () => request.get('/energy/stats-api/overview'),
  trend: (months = 6) => request.get('/energy/stats-api/trend', { params: { months } }),
  meterRank: () => request.get('/energy/stats-api/meter-rank')
}
