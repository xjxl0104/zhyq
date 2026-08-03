import request from '@/utils/request'

const endpoints = {
  MACHINE: 'machines',
  SALE: 'sales',
  RESTOCK: 'restocks',
  FAULT: 'faults',
  RECONCILIATION: 'reconciliations'
}

export const vendingApi = {
  capabilities: () => request.get('/vending/capabilities'),
  config: () => request.get('/vending/config'),
  configurationStatus: () => request.get('/vending/config/status'),
  openAudit: () => request.post('/vending/open-audit'),
  stats: () => request.get('/vending/stats'),
  page: (type, params) => request.get(`/vending/${endpoints[type]}`, { params }),
  template: (type) => request.get('/vending/template', { params: { type }, responseType: 'blob' }),
  preview: (type, file) => {
    const body = new FormData()
    body.append('file', file)
    return request.post('/vending/import/preview', body, { params: { type } })
  },
  exclude: (batchId, data) => request.put(`/vending/import/${batchId}/exclude`, data),
  confirm: (batchId) => request.post(`/vending/import/${batchId}/confirm`),
  rollback: (batchId) => request.post(`/vending/import/${batchId}/rollback`),
  batches: () => request.get('/vending/import/batches')
}
