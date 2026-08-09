import request from '@/utils/request'

// 用户
export const userApi = {
  page: (params) => request.get('/system/user/page', { params }),
  list: () => request.get('/system/user/list'),
  get: (id) => request.get(`/system/user/${id}`),
  add: (data) => request.post('/system/user', data),
  update: (data) => request.put('/system/user', data),
  remove: (id) => request.delete(`/system/user/${id}`)
}

// 角色
export const roleApi = {
  page: (params) => request.get('/system/role/page', { params }),
  list: () => request.get('/system/role/list'),
  menuIds: (id) => request.get(`/system/role/${id}/menu-ids`),
  saveMenuIds: (id, menuIds) => request.put(`/system/role/${id}/menu-ids`, { menuIds }),
  add: (data) => request.post('/system/role', data),
  update: (data) => request.put('/system/role', data),
  remove: (id) => request.delete(`/system/role/${id}`)
}

// 部门
export const deptApi = {
  list: () => request.get('/system/dept/list'),
  add: (data) => request.post('/system/dept', data),
  update: (data) => request.put('/system/dept', data),
  remove: (id) => request.delete(`/system/dept/${id}`)
}

// 岗位
export const postApi = {
  page: (params) => request.get('/system/post/page', { params }),
  list: () => request.get('/system/post/list'),
  add: (data) => request.post('/system/post', data),
  update: (data) => request.put('/system/post', data),
  remove: (id) => request.delete(`/system/post/${id}`)
}

// 菜单
export const menuApi = {
  list: () => request.get('/system/menu/list'),
  get: (id) => request.get(`/system/menu/${id}`),
  add: (data) => request.post('/system/menu', data),
  update: (data) => request.put('/system/menu', data),
  remove: (id) => request.delete(`/system/menu/${id}`)
}

// 字典
export const dictApi = {
  typePage: (params) => request.get('/system/dict/type/page', { params }),
  addType: (data) => request.post('/system/dict/type', data),
  updateType: (data) => request.put('/system/dict/type', data),
  removeType: (id) => request.delete(`/system/dict/type/${id}`),
  dataByType: (type) => request.get(`/system/dict/data/${type}`),
  addData: (data) => request.post('/system/dict/data', data),
  updateData: (data) => request.put('/system/dict/data', data),
  removeData: (id) => request.delete(`/system/dict/data/${id}`)
}

// 运营资源
export const resourceApi = {
  page: (params) => request.get('/system/resource/page', { params }),
  get: (id) => request.get(`/system/resource/${id}`),
  add: (data) => request.post('/system/resource', data),
  update: (data) => request.put('/system/resource', data),
  remove: (id) => request.delete(`/system/resource/${id}`),
  toggle: (id) => request.post(`/system/resource/${id}/toggle`)
}

// 消息中心
export const msgApi = {
  templatePage: (params) => request.get('/system/message/template/page', { params }),
  templateAdd: (data) => request.post('/system/message/template', data),
  templateUpdate: (data) => request.put('/system/message/template', data),
  templateRemove: (id) => request.delete(`/system/message/template/${id}`),
  recordPage: (params) => request.get('/system/message/record/page', { params }),
  recordRemove: (id) => request.delete(`/system/message/record/${id}`),
  send: (data) => request.post('/system/message/send', data)
}
