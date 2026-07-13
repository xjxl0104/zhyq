import request from '@/utils/request'

// 设备
export const deviceApi = {
  page: (params) => request.get('/iot/device/page', { params }),
  list: () => request.get('/iot/device/list'),
  stats: () => request.get('/iot/device/stats'),
  add: (data) => request.post('/iot/device', data),
  update: (data) => request.put('/iot/device', data),
  remove: (id) => request.delete(`/iot/device/${id}`)
}

// 告警
export const alarmApi = {
  page: (params) => request.get('/iot/alarm/page', { params }),
  add: (data) => request.post('/iot/alarm', data),
  update: (data) => request.put('/iot/alarm', data),
  remove: (id) => request.delete(`/iot/alarm/${id}`),
  confirm: (id) => request.post(`/iot/alarm/${id}/confirm`),
  close: (id) => request.post(`/iot/alarm/${id}/close`)
}

// 点位
export const pointApi = {
  page: (params) => request.get('/iot/point/page', { params }),
  add: (data) => request.post('/iot/point', data),
  update: (data) => request.put('/iot/point', data),
  remove: (id) => request.delete(`/iot/point/${id}`)
}

// 通道
export const channelApi = {
  page: (params) => request.get('/iot/channel/page', { params }),
  list: (deviceId) => request.get('/iot/channel/list', { params: { deviceId } }),
  add: (data) => request.post('/iot/channel', data),
  update: (data) => request.put('/iot/channel', data),
  remove: (id) => request.delete(`/iot/channel/${id}`)
}

// 厂商配置
export const vendorApi = {
  page: (params) => request.get('/iot/vendor/page', { params }),
  add: (data) => request.post('/iot/vendor', data),
  update: (data) => request.put('/iot/vendor', data),
  remove: (id) => request.delete(`/iot/vendor/${id}`),
  test: (id) => request.post(`/iot/vendor/${id}/test`)
}
