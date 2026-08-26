import request from '@/utils/request'

// 采购计划(年度/月度/临时)
export const purPlanApi = {
  page: (params) => request.get('/pur/plan/page', { params }),
  list: (planType) => request.get('/pur/plan/list', { params: { planType } }),
  get: (id) => request.get(`/pur/plan/${id}`),
  add: (data) => request.post('/pur/plan', data),
  update: (data) => request.put('/pur/plan', data),
  remove: (id) => request.delete(`/pur/plan/${id}`)
}

// 采购申请(明细 + 附件 + 审批链)
export const purRequestApi = {
  page: (params) => request.get('/pur/request/page', { params }),
  get: (id) => request.get(`/pur/request/${id}`),
  add: (data) => request.post('/pur/request', data),
  update: (data) => request.put('/pur/request', data),
  submit: (id) => request.post(`/pur/request/${id}/submit`),
  complete: (id) => request.post(`/pur/request/${id}/complete`),
  cancel: (id) => request.post(`/pur/request/${id}/cancel`),
  remove: (id) => request.delete(`/pur/request/${id}`)
}
