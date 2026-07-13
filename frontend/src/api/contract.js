import request from '@/utils/request'

// 合同
export const contractApi = {
  page: (params) => request.get('/contract/page', { params }),
  get: (id) => request.get(`/contract/${id}`),
  add: (data) => request.post('/contract', data),
  update: (data) => request.put('/contract', data),
  remove: (id) => request.delete(`/contract/${id}`),
  submit: (id) => request.post(`/contract/${id}/submit`),
  approve: (id) => request.post(`/contract/${id}/approve`),
  terminate: (id) => request.post(`/contract/${id}/terminate`),
  archive: (id) => request.post(`/contract/${id}/archive`)
}
