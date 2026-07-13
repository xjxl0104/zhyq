import request from '@/utils/request'

// 访客预约
export const visitorApi = {
  page: (params) => request.get('/service/visitor/page', { params }),
  add: (data) => request.post('/service/visitor', data),
  update: (data) => request.put('/service/visitor', data),
  remove: (id) => request.delete(`/service/visitor/${id}`),
  approve: (id) => request.post(`/service/visitor/${id}/approve`)
}

// 商城商品
export const productApi = {
  page: (params) => request.get('/service/product/page', { params }),
  add: (data) => request.post('/service/product', data),
  update: (data) => request.put('/service/product', data),
  remove: (id) => request.delete(`/service/product/${id}`)
}

// 园区论坛(含内容审核)
export const forumApi = {
  page: (params) => request.get('/service/forum/page', { params }),
  get: (id) => request.get(`/service/forum/${id}`),
  add: (data) => request.post('/service/forum', data),
  update: (data) => request.put('/service/forum', data),
  remove: (id) => request.delete(`/service/forum/${id}`),
  approve: (id) => request.post(`/service/forum/${id}/approve`),
  offline: (id, reason) => request.post(`/service/forum/${id}/offline`, { reason }),
  reply: (id, data) => request.post(`/service/forum/${id}/reply`, data),
  like: (id) => request.post(`/service/forum/${id}/like`),
  stats: () => request.get('/service/forum/stats')
}

// 申报服务
export const declareApi = {
  page: (params) => request.get('/service/declare/page', { params }),
  get: (id) => request.get(`/service/declare/${id}`),
  add: (data) => request.post('/service/declare', data),
  update: (data) => request.put('/service/declare', data),
  remove: (id) => request.delete(`/service/declare/${id}`),
  submit: (id) => request.post(`/service/declare/${id}/submit`),
  pass: (id) => request.post(`/service/declare/${id}/pass`),
  fail: (id) => request.post(`/service/declare/${id}/fail`)
}

// 知产服务
export const ipApi = {
  page: (params) => request.get('/service/ip/page', { params }),
  get: (id) => request.get(`/service/ip/${id}`),
  add: (data) => request.post('/service/ip', data),
  update: (data) => request.put('/service/ip', data),
  remove: (id) => request.delete(`/service/ip/${id}`),
  stats: () => request.get('/service/ip/stats')
}

// 政策服务
export const policyApi = {
  page: (params) => request.get('/service/policy/page', { params }),
  get: (id) => request.get(`/service/policy/${id}`),
  add: (data) => request.post('/service/policy', data),
  update: (data) => request.put('/service/policy', data),
  remove: (id) => request.delete(`/service/policy/${id}`),
  match: (tenantRefId) => request.get('/service/policy/match', { params: { tenantRefId } })
}

// 物品放行
export const passApi = {
  page: (params) => request.get('/service/pass/page', { params }),
  get: (id) => request.get(`/service/pass/${id}`),
  add: (data) => request.post('/service/pass', data),
  update: (data) => request.put('/service/pass', data),
  remove: (id) => request.delete(`/service/pass/${id}`),
  verify: (id) => request.post(`/service/pass/${id}/verify`),
  expireCheck: () => request.post('/service/pass/expire-check')
}

// 场地预约
export const siteApi = {
  page: (params) => request.get('/service/site/page', { params }),
  get: (id) => request.get(`/service/site/${id}`),
  add: (data) => request.post('/service/site', data),
  update: (data) => request.put('/service/site', data),
  remove: (id) => request.delete(`/service/site/${id}`),
  approve: (id) => request.post(`/service/site/${id}/approve`),
  cancel: (id) => request.post(`/service/site/${id}/cancel`)
}

// 装修申请
export const decorationApi = {
  page: (params) => request.get('/service/decoration/page', { params }),
  get: (id) => request.get(`/service/decoration/${id}`),
  add: (data) => request.post('/service/decoration', data),
  update: (data) => request.put('/service/decoration', data),
  remove: (id) => request.delete(`/service/decoration/${id}`),
  approve: (id) => request.post(`/service/decoration/${id}/approve`),
  reject: (id) => request.post(`/service/decoration/${id}/reject`),
  finish: (id) => request.post(`/service/decoration/${id}/finish`)
}

// 出入证管理
export const passCardApi = {
  page: (params) => request.get('/service/passcard/page', { params }),
  get: (id) => request.get(`/service/passcard/${id}`),
  add: (data) => request.post('/service/passcard', data),
  update: (data) => request.put('/service/passcard', data),
  remove: (id) => request.delete(`/service/passcard/${id}`),
  loss: (id) => request.post(`/service/passcard/${id}/loss`),
  cancel: (id) => request.post(`/service/passcard/${id}/cancel`)
}
