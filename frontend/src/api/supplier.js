import request from '@/utils/request'

export const supplierApi = {
  page: (params) => request.get('/pur/supplier/page', { params }),
  stats: () => request.get('/pur/supplier/stats'),
  get: (id) => request.get(`/pur/supplier/${id}`),
  add: (data) => request.post('/pur/supplier', data),
  update: (data) => request.put('/pur/supplier', data),
  remove: (id) => request.delete(`/pur/supplier/${id}`),
  changeStatus: (id, status) => request.post(`/pur/supplier/${id}/status`, null, { params: { status } }),
  list: () => request.get('/pur/supplier/list'),
  // 导入供应商档案,formData 里带 file
  importFile: (formData) => request.post('/pur/supplier/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export const supplierContractApi = {
  page: (params) => request.get('/pur/supplier-contract/page', { params }),
  stats: () => request.get('/pur/supplier-contract/stats'),
  get: (id) => request.get(`/pur/supplier-contract/${id}`),
  add: (data) => request.post('/pur/supplier-contract', data),
  importFile: (formData) => request.post('/pur/supplier-contract/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  }),
  update: (data) => request.put('/pur/supplier-contract', data),
  remove: (id) => request.delete(`/pur/supplier-contract/${id}`),
  changeStatus: (id, status) =>
    request.post(`/pur/supplier-contract/${id}/status`, null, { params: { status } })
}
