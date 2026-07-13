import request from '@/utils/request'

// 招商线索
export const leadApi = {
  page: (params) => request.get('/crm/lead/page', { params }),
  stats: () => request.get('/crm/lead/stats'),
  get: (id) => request.get(`/crm/lead/${id}`),
  add: (data) => request.post('/crm/lead', data),
  update: (data) => request.put('/crm/lead', data),
  remove: (id) => request.delete(`/crm/lead/${id}`),
  convert: (id) => request.post(`/crm/lead/${id}/convert`)
}

// 跟进记录
export const followApi = {
  list: (leadId) => request.get('/crm/follow/list', { params: { leadId } }),
  add: (data) => request.post('/crm/follow', data)
}

// 渠道商
export const channelApi = {
  page: (params) => request.get('/crm/channel/page', { params }),
  list: () => request.get('/crm/channel/list'),
  add: (data) => request.post('/crm/channel', data),
  update: (data) => request.put('/crm/channel', data),
  remove: (id) => request.delete(`/crm/channel/${id}`)
}

// 意向客户
export const customerApi = {
  page: (params) => request.get('/crm/customer/page', { params }),
  get: (id) => request.get(`/crm/customer/${id}`),
  add: (data) => request.post('/crm/customer', data),
  update: (data) => request.put('/crm/customer', data),
  remove: (id) => request.delete(`/crm/customer/${id}`),
  fromLead: (leadId) => request.post(`/crm/customer/from-lead/${leadId}`),
  sign: (id) => request.post(`/crm/customer/${id}/sign`),
  lose: (id) => request.post(`/crm/customer/${id}/lose`)
}

// 销售计划
export const planApi = {
  page: (params) => request.get('/crm/plan/page', { params }),
  get: (id) => request.get(`/crm/plan/${id}`),
  add: (data) => request.post('/crm/plan', data),
  update: (data) => request.put('/crm/plan', data),
  remove: (id) => request.delete(`/crm/plan/${id}`),
  finish: (id) => request.post(`/crm/plan/${id}/finish`)
}

// 佣金
export const commissionApi = {
  page: (params) => request.get('/crm/commission/page', { params }),
  remove: (id) => request.delete(`/crm/commission/${id}`),
  generate: (data) => request.post('/crm/commission/generate', data),
  settle: (id) => request.post(`/crm/commission/${id}/settle`),
  void: (id) => request.post(`/crm/commission/${id}/void`),
  stats: () => request.get('/crm/commission/stats')
}

// 招商分析
export const analysisApi = {
  funnel: () => request.get('/crm/analysis/funnel'),
  trend: () => request.get('/crm/analysis/trend'),
  source: () => request.get('/crm/analysis/source'),
  summary: () => request.get('/crm/analysis/summary')
}
