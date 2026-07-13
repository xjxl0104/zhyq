import request from '@/utils/request'

// 项目
export const projectApi = {
  page: (params) => request.get('/building/project/page', { params }),
  list: () => request.get('/building/project/list'),
  get: (id) => request.get(`/building/project/${id}`),
  add: (data) => request.post('/building/project', data),
  update: (data) => request.put('/building/project', data),
  remove: (id) => request.delete(`/building/project/${id}`)
}

// 楼宇
export const buildingApi = {
  page: (params) => request.get('/building/building/page', { params }),
  list: (projectId) => request.get('/building/building/list', { params: { projectId } }),
  get: (id) => request.get(`/building/building/${id}`),
  add: (data) => request.post('/building/building', data),
  update: (data) => request.put('/building/building', data),
  remove: (id) => request.delete(`/building/building/${id}`)
}

// 楼层
export const floorApi = {
  list: (buildingId) => request.get('/building/floor/list', { params: { buildingId } }),
  get: (id) => request.get(`/building/floor/${id}`),
  add: (data) => request.post('/building/floor', data),
  update: (data) => request.put('/building/floor', data),
  remove: (id) => request.delete(`/building/floor/${id}`)
}

// 房源
export const roomApi = {
  page: (params) => request.get('/building/room/page', { params }),
  list: (floorId) => request.get('/building/room/list', { params: { floorId } }),
  stats: (projectId) => request.get('/building/room/stats', { params: { projectId } }),
  get: (id) => request.get(`/building/room/${id}`),
  add: (data) => request.post('/building/room', data),
  update: (data) => request.put('/building/room', data),
  remove: (id) => request.delete(`/building/room/${id}`)
}
