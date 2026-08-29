import request from '@/utils/request'

// 预算(年度/月度)。附件走统一附件接口,bizType 固定 'budget'
export const budgetApi = {
  page: (params) => request.get('/budget/page', { params }),
  get: (id) => request.get(`/budget/${id}`),
  add: (data) => request.post('/budget', data),
  update: (data) => request.put('/budget', data),
  submit: (id) => request.post(`/budget/${id}/submit`),
  archive: (id) => request.post(`/budget/${id}/archive`),
  cancel: (id) => request.post(`/budget/${id}/cancel`),
  remove: (id) => request.delete(`/budget/${id}`)
}
