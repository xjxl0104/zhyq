import request from '@/utils/request'

// 资源目录(会议室/场地/工位统一目录,#23)
export const resourceApi = {
  page: (params) => request.get('/rsv/resource/page', { params }),
  get: (id) => request.get(`/rsv/resource/${id}`),
  add: (data) => request.post('/rsv/resource', data),
  update: (data) => request.put('/rsv/resource', data),
  remove: (id) => request.delete(`/rsv/resource/${id}`)
}

// 统一预订(#23)
export const bookingApi = {
  page: (params) => request.get('/rsv/booking/page', { params }),
  get: (id) => request.get(`/rsv/booking/${id}`),
  book: (data) => request.post('/rsv/booking', data),
  cancel: (id) => request.post(`/rsv/booking/${id}/cancel`),
  finish: (id) => request.post(`/rsv/booking/${id}/finish`),
  slots: (resourceId, from, to) => request.get('/rsv/booking/slots', { params: { resourceId, from, to } })
}
