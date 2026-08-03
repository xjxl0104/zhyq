import request from '@/utils/request'

export const receivableApi = {
  page: (params) => request.get('/finance/receivable/page', { params }),
  get: (id) => request.get(`/finance/receivable/${id}`),
  add: (data) => request.post('/finance/receivable', data),
  update: (data) => request.put('/finance/receivable', data),
  remove: (id) => request.delete(`/finance/receivable/${id}`),
  preview: (file) => {
    const body = new FormData()
    body.append('file', file)
    return request.post('/finance/receivable/import/preview', body)
  },
  bind: (batchId, rowId, data) => request.put(
    `/finance/receivable/import/${batchId}/rows/${rowId}/binding`, data),
  confirm: (batchId) => request.post(`/finance/receivable/import/${batchId}/confirm`),
  rollback: (batchId) => request.post(`/finance/receivable/import/${batchId}/rollback`),
  generate: (id) => request.post(`/finance/receivable/${id}/generate`),
  revealAccount: (id) => request.get(`/finance/receivable/accounts/${id}/reveal`),
  export: () => request.get('/finance/receivable/export', { responseType: 'blob' })
}
