import request from '@/utils/request'

const base = (warehouseId) => `/crm/marketing/erp/warehouse/${warehouseId}/reconciliation`
export const erpReconciliationApi = {
  preview: (warehouseId, params) => request.get(base(warehouseId), { params }),
  submit: (warehouseId, data) => request.post(base(warehouseId), data),
  confirm: (warehouseId, snapshotId, reason) => request.post(`${base(warehouseId)}/${snapshotId}/confirm`, { reason }),
  refresh: (warehouseId, data) => request.post(`${base(warehouseId)}/refresh`, data)
}
