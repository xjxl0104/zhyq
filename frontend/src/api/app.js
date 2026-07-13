import request from '@/utils/request'

// 应用中心
export const appApi = {
  list: (params) => request.get('/app/list', { params }),
  categories: () => request.get('/app/categories'),
  page: (params) => request.get('/app/page', { params }),
  get: (id) => request.get(`/app/${id}`),
  add: (data) => request.post('/app', data),
  update: (data) => request.put('/app', data),
  remove: (id) => request.delete(`/app/${id}`),
  favoriteList: () => request.get('/app/favorite/list'),
  addFavorite: (appId) => request.post(`/app/favorite/${appId}`),
  removeFavorite: (appId) => request.delete(`/app/favorite/${appId}`)
}
