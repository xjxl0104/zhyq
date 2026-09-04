import request from '@/utils/request'

export const receivableApi = {
  capabilities: () => request.get('/finance/receivable/capabilities'),
  page: (params) => request.get('/finance/receivable/page', { params }),
  monthlySummary: (params) => request.get('/finance/receivable/monthly-summary', { params }),
  get: (id) => request.get(`/finance/receivable/${id}`),
  add: (data) => request.post('/finance/receivable', data),
  update: (data) => request.put('/finance/receivable', data),
  // 单行确认(草稿→已确认)。手工新增写死草稿,而生成账单只认已确认/已生效,
  // 原先只有导入批次能确认,手工新增的行就永远推不出账单
  confirmRow: (id) => request.put(`/finance/receivable/${id}/confirm`),
  remove: (id) => request.delete(`/finance/receivable/${id}`),
  preview: (file) => {
    const body = new FormData()
    body.append('file', file)
    return request.post('/finance/receivable/import/preview', body)
  },
  bind: (batchId, rowId, data) => request.put(
    `/finance/receivable/import/${batchId}/rows/${rowId}/binding`, data),
  confirm: (batchId) => request.post(`/finance/receivable/import/${batchId}/confirm`),
  provisionPreview: (batchId) => request.get(`/finance/receivable/import/${batchId}/provision/preview`),
  provision: (batchId, body) => request.post(`/finance/receivable/import/${batchId}/provision`, body),
  rollback: (batchId) => request.post(`/finance/receivable/import/${batchId}/rollback`),
  batches: () => request.get('/finance/receivable/import/batches'),
  generate: (id) => request.post(`/finance/receivable/${id}/generate`),
  updateLateFeeStart: (id, lateFeeStartDate) => request.put(
    `/finance/receivable/${id}/late-fee-start`, { lateFeeStartDate }),
  revealAccount: (id) => request.get(`/finance/receivable/accounts/${id}/reveal`),
  export: () => request.get('/finance/receivable/export', { responseType: 'blob' })
}
