import request from '@/utils/request'

// 合同
export const contractApi = {
  page: (params) => request.get('/contract/page', { params }),
  // 新增合同的默认值(编号/租期/保证金月数),全部来自「合同设置」——改设置这里立刻变
  defaults: (startDate) => request.get('/contract/defaults', { params: { startDate } }),
  get: (id) => request.get(`/contract/${id}`),
  add: (data) => request.post('/contract', data),
  update: (data) => request.put('/contract', data),
  remove: (id) => request.delete(`/contract/${id}`),
  // 真重置:作废未产生实收的全部合同(有收款的整份保留),清演示/试录数据用
  reset: () => request.post('/contract/reset'),
  submit: (id) => request.post(`/contract/${id}/submit`),
  approve: (id) => request.post(`/contract/${id}/approve`),
  terminate: (id) => request.post(`/contract/${id}/terminate`),
  archive: (id) => request.post(`/contract/${id}/archive`)
}
