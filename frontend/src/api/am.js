import request from '@/utils/request'

// 资产管理(台账 + 签出签入/报废/维修/盘点,#20)
export const assetApi = {
  page: (params) => request.get('/am/asset/page', { params }),
  get: (id) => request.get(`/am/asset/${id}`),
  add: (data) => request.post('/am/asset', data),
  update: (data) => request.put('/am/asset', data),
  remove: (id) => request.delete(`/am/asset/${id}`),
  checkout: (id, holder) => request.post(`/am/asset/${id}/checkout`, null, { params: { holder } }),
  checkin: (id) => request.post(`/am/asset/${id}/checkin`),
  scrap: (id) => request.post(`/am/asset/${id}/scrap`),
  repair: (id) => request.post(`/am/asset/${id}/repair`),
  repairDone: (id) => request.post(`/am/asset/${id}/repairDone`),
  inventory: (id, spaceId) => request.post(`/am/asset/${id}/inventory`, null, { params: { spaceId } })
}
