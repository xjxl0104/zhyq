import request from '@/utils/request'

export const suggestionApi = {
  submit(data) {
    return request.post('/suggestion', data)
  },
  mine(params) {
    return request.get('/suggestion/mine', { params })
  },
  mineDetail(id) {
    return request.get(`/suggestion/mine/${id}`)
  },
  manageList(params) {
    return request.get('/suggestion/manage', { params })
  },
  manageDetail(id) {
    return request.get(`/suggestion/manage/${id}`)
  },
  changeStatus(id, data) {
    return request.put(`/suggestion/manage/${id}/status`, data)
  },
  assign(id, data) {
    return request.put(`/suggestion/manage/${id}/assign`, data)
  }
}
