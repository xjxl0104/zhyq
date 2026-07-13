import request from '@/utils/request'

// 统一待办
export const todoApi = {
  page: (params) => request.get('/todo/page', { params }),
  list: () => request.get('/todo/list'),
  stats: () => request.get('/todo/stats'),
  add: (data) => request.post('/todo', data),
  update: (data) => request.put('/todo', data),
  done: (id) => request.post(`/todo/${id}/done`),
  read: (id) => request.post(`/todo/${id}/read`),
  remove: (id) => request.delete(`/todo/${id}`)
}
