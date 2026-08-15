import request from '@/utils/request'

// 报修工单
export const workOrderApi = {
  page: (params) => request.get('/property/workorder/page', { params }),
  get: (id) => request.get(`/property/workorder/${id}`),
  stats: (params) => request.get('/property/workorder/stats', { params }),
  // 汇总总览:按状态/来源/分类/紧急度聚合 + SLA 达成率 + 近 N 天趋势
  summary: (params) => request.get('/property/workorder/summary', { params }),
  add: (data) => request.post('/property/workorder', data),
  update: (data) => request.put('/property/workorder', data),
  remove: (id) => request.delete(`/property/workorder/${id}`),
  dispatch: (id, data) => request.post(`/property/workorder/${id}/dispatch`, data),
  accept: (id, data) => request.post(`/property/workorder/${id}/accept`, data || {}),
  arrive: (id, data) => request.post(`/property/workorder/${id}/arrive`, data || {}),
  finish: (id, data) => request.post(`/property/workorder/${id}/finish`, data || {}),
  verify: (id, data) => request.post(`/property/workorder/${id}/verify`, data || {}),
  close: (id, data) => request.post(`/property/workorder/${id}/close`, data || {})
}

// 责任单位主数据(工单/报修的承接方)
export const responsibleUnitApi = {
  page: (params) => request.get('/property/responsible-unit/page', { params }),
  options: (params) => request.get('/property/responsible-unit/options', { params }),
  add: (data) => request.post('/property/responsible-unit', data),
  update: (id, data) => request.put(`/property/responsible-unit/${id}`, data),
  remove: (id) => request.delete(`/property/responsible-unit/${id}`),
  // 导入前只解析不落库,先让用户看错误
  preview: (formData, params) => request.post('/property/responsible-unit/import/preview', formData, {
    params,
    headers: { 'Content-Type': 'multipart/form-data' }
  }),
  import: (formData, params) => request.post('/property/responsible-unit/import', formData, {
    params,
    headers: { 'Content-Type': 'multipart/form-data' }
  }),
  template: () => request.get('/property/responsible-unit/template', { responseType: 'blob' })
}

// 会议室
export const meetingRoomApi = {
  page: (params) => request.get('/property/meeting/room/page', { params }),
  list: () => request.get('/property/meeting/room/list'),
  get: (id) => request.get(`/property/meeting/room/${id}`),
  add: (data) => request.post('/property/meeting/room', data),
  update: (data) => request.put('/property/meeting/room', data),
  remove: (id) => request.delete(`/property/meeting/room/${id}`)
}

// 会议室预约
export const bookingApi = {
  page: (params) => request.get('/property/meeting/booking/page', { params }),
  get: (id) => request.get(`/property/meeting/booking/${id}`),
  add: (data) => request.post('/property/meeting/booking', data),
  update: (data) => request.put('/property/meeting/booking', data),
  remove: (id) => request.delete(`/property/meeting/booking/${id}`)
}

// 资产管理
export const assetApi = {
  page: (params) => request.get('/property/asset/page', { params }),
  get: (id) => request.get(`/property/asset/${id}`),
  stats: () => request.get('/property/asset/stats'),
  add: (data) => request.post('/property/asset', data),
  update: (data) => request.put('/property/asset', data),
  remove: (id) => request.delete(`/property/asset/${id}`),
  scrap: (id) => request.post(`/property/asset/${id}/scrap`)
}

// 巡检计划
export const inspectionApi = {
  page: (params) => request.get('/property/inspection/page', { params }),
  get: (id) => request.get(`/property/inspection/${id}`),
  add: (data) => request.post('/property/inspection', data),
  update: (data) => request.put('/property/inspection', data),
  remove: (id) => request.delete(`/property/inspection/${id}`),
  generate: (id) => request.post(`/property/inspection/${id}/generate`)
}

// 安防巡更
export const patrolApi = {
  page: (params) => request.get('/property/patrol/page', { params }),
  get: (id) => request.get(`/property/patrol/${id}`),
  stats: () => request.get('/property/patrol/stats'),
  add: (data) => request.post('/property/patrol', data),
  update: (data) => request.put('/property/patrol', data),
  remove: (id) => request.delete(`/property/patrol/${id}`),
  toWorkOrder: (id) => request.post(`/property/patrol/${id}/toWorkOrder`)
}

// 三检(保洁/绿化/品质)
export const checkApi = {
  page: (params) => request.get('/property/check/page', { params }),
  get: (id) => request.get(`/property/check/${id}`),
  add: (data) => request.post('/property/check', data),
  update: (data) => request.put('/property/check', data),
  remove: (id) => request.delete(`/property/check/${id}`),
  rectify: (id) => request.post(`/property/check/${id}/rectify`)
}

// 投诉反馈
export const feedbackApi = {
  page: (params) => request.get('/property/feedback/page', { params }),
  get: (id) => request.get(`/property/feedback/${id}`),
  add: (data) => request.post('/property/feedback', data),
  update: (data) => request.put('/property/feedback', data),
  remove: (id) => request.delete(`/property/feedback/${id}`),
  processing: (id) => request.post(`/property/feedback/${id}/processing`),
  handle: (id, data) => request.post(`/property/feedback/${id}/handle`, data)
}

// 投票问卷
export const surveyApi = {
  page: (params) => request.get('/property/survey/page', { params }),
  get: (id) => request.get(`/property/survey/${id}`),
  add: (data) => request.post('/property/survey', data),
  update: (data) => request.put('/property/survey', data),
  remove: (id) => request.delete(`/property/survey/${id}`),
  close: (id) => request.post(`/property/survey/${id}/close`)
}

// 物业活动
export const activityApi = {
  page: (params) => request.get('/property/activity/page', { params }),
  get: (id) => request.get(`/property/activity/${id}`),
  add: (data) => request.post('/property/activity', data),
  update: (data) => request.put('/property/activity', data),
  remove: (id) => request.delete(`/property/activity/${id}`),
  enroll: (id) => request.post(`/property/activity/${id}/enroll`)
}
