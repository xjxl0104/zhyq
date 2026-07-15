import request from '@/utils/request'

// 便捷通行-门禁通行记录(只增不改的流水,#21)
export const accessApi = {
  page: (params) => request.get('/acc/access/page', { params }),
  record: (data) => request.post('/acc/access/record', data)
}

// 便捷通行-访客预约/到访(状态机:1已预约 2已到访 3已离场 4已取消,#21)
export const visitorApi = {
  page: (params) => request.get('/acc/visitor/page', { params }),
  get: (id) => request.get(`/acc/visitor/${id}`),
  reserve: (data) => request.post('/acc/visitor/reserve', data),
  checkin: (id) => request.post(`/acc/visitor/${id}/checkin`),
  leave: (id) => request.post(`/acc/visitor/${id}/leave`),
  cancel: (id) => request.post(`/acc/visitor/${id}/cancel`)
}

// 便捷通行-停车(状态机:1在场 2已离场,出场试算费用只存不入账,#21)
export const parkingApi = {
  page: (params) => request.get('/acc/parking/page', { params }),
  get: (id) => request.get(`/acc/parking/${id}`),
  enter: (plateNo, ownerType) => request.post('/acc/parking/enter', null, { params: { plateNo, ownerType } }),
  leave: (id) => request.post(`/acc/parking/${id}/leave`)
}
