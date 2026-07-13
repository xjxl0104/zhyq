import request from '@/utils/request'

// 租客
export const tenantApi = {
  page: (params) => request.get('/tenant/info/page', { params }),
  stats: () => request.get('/tenant/info/stats'),
  list: () => request.get('/tenant/info/list'),
  get: (id) => request.get(`/tenant/info/${id}`),
  add: (data) => request.post('/tenant/info', data),
  update: (data) => request.put('/tenant/info', data),
  remove: (id) => request.delete(`/tenant/info/${id}`),
  archive: (id) => request.post(`/tenant/info/${id}/archive`)
}

// 租客站内信
export const messageApi = {
  page: (params) => request.get('/tenant/message/page', { params }),
  get: (id) => request.get(`/tenant/message/${id}`),
  add: (data) => request.post('/tenant/message', data),
  update: (data) => request.put('/tenant/message', data),
  remove: (id) => request.delete(`/tenant/message/${id}`),
  send: (id) => request.post(`/tenant/message/${id}/send`)
}

// 租客员工
export const staffApi = {
  page: (params) => request.get('/tenant/staff/page', { params }),
  get: (id) => request.get(`/tenant/staff/${id}`),
  add: (data) => request.post('/tenant/staff', data),
  update: (data) => request.put('/tenant/staff', data),
  remove: (id) => request.delete(`/tenant/staff/${id}`)
}
