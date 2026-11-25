import request from '@/utils/request'

// 合同
export const contractApi = {
  page: (params) => request.get('/contract/page', { params }),
  export: (params) => request.get('/contract/export', { params, responseType: 'blob' }),
  expiryAlerts: () => request.get('/contract/expiry-alerts'),
  // 新增合同的默认值(编号/租期/保证金月数),全部来自「合同设置」——改设置这里立刻变
  defaults: (startDate) => request.get('/contract/defaults', { params: { startDate } }),
  get: (id) => request.get(`/contract/${id}`),
  add: (data) => request.post('/contract', data),
  importFile: (formData) => request.post('/contract/import', formData, { headers: { 'Content-Type': 'multipart/form-data' } }),
  update: (data) => request.put('/contract', data),
  remove: (id) => request.delete(`/contract/${id}`),
  // 真重置:作废未产生实收的全部合同(有收款的整份保留),清演示/试录数据用
  reset: () => request.post('/contract/reset'),
  submit: (id) => request.post(`/contract/${id}/submit`),
  approve: (id) => request.post(`/contract/${id}/approve`),
  terminate: (id) => request.post(`/contract/${id}/terminate`),
  archive: (id) => request.post(`/contract/${id}/archive`)
}

// 简易合同(注册公司用,按楼层 + 自编号房号 X01/X02)
export const simpleContractApi = {
  list: (params) => request.get('/contract/simple/list', { params }),
  nextRoom: (floorNo) => request.get('/contract/simple/next-room', { params: { floorNo } }),
  add: (data) => request.post('/contract/simple', data),
  update: (data) => request.put('/contract/simple', data),
  remove: (id) => request.delete(`/contract/simple/${id}`)
}

// 合同模板库(租赁合同 / 物业合同 / 简易合同 / 补充协议等)
export const contractTemplateApi = {
  list: (params) => request.get('/contract/template/list', { params }),
  add: (data) => request.post('/contract/template', data),
  update: (data) => request.put('/contract/template', data),
  remove: (id) => request.delete(`/contract/template/${id}`)
}
