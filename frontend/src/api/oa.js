import request from '@/utils/request'

// 任务
export const taskApi = {
  page: (params) => request.get('/oa/task/page', { params }),
  add: (data) => request.post('/oa/task', data),
  update: (data) => request.put('/oa/task', data),
  remove: (id) => request.delete(`/oa/task/${id}`),
  done: (id) => request.post(`/oa/task/${id}/done`)
}

// 公告
export const noticeApi = {
  page: (params) => request.get('/oa/notice/page', { params }),
  add: (data) => request.post('/oa/notice', data),
  update: (data) => request.put('/oa/notice', data),
  remove: (id) => request.delete(`/oa/notice/${id}`)
}

// 审批中心
export const approvalApi = {
  page: (params) => request.get('/oa/approval/page', { params }),
  stats: () => request.get('/oa/approval/stats'),
  add: (data) => request.post('/oa/approval', data),
  remove: (id) => request.delete(`/oa/approval/${id}`),
  approve: (id, data) => request.post(`/oa/approval/${id}/approve`, data),
  reject: (id, data) => request.post(`/oa/approval/${id}/reject`, data)
}

// 日程
export const scheduleApi = {
  page: (params) => request.get('/oa/schedule/page', { params }),
  week: () => request.get('/oa/schedule/week'),
  add: (data) => request.post('/oa/schedule', data),
  update: (data) => request.put('/oa/schedule', data),
  remove: (id) => request.delete(`/oa/schedule/${id}`)
}

// 人才招聘
export const recruitApi = {
  page: (params) => request.get('/oa/recruit/page', { params }),
  add: (data) => request.post('/oa/recruit', data),
  update: (data) => request.put('/oa/recruit', data),
  remove: (id) => request.delete(`/oa/recruit/${id}`),
  close: (id) => request.post(`/oa/recruit/${id}/close`),
  apply: (id) => request.post(`/oa/recruit/${id}/apply`)
}

// 文章管理
export const articleApi = {
  page: (params) => request.get('/oa/article/page', { params }),
  add: (data) => request.post('/oa/article', data),
  update: (data) => request.put('/oa/article', data),
  remove: (id) => request.delete(`/oa/article/${id}`),
  publish: (id) => request.post(`/oa/article/${id}/publish`),
  view: (id) => request.post(`/oa/article/${id}/view`)
}

// 考勤
export const attendanceApi = {
  page: (params) => request.get('/oa/attendance/page', { params }),
  stats: (attDate) => request.get('/oa/attendance/stats', { params: { attDate } }),
  add: (data) => request.post('/oa/attendance', data),
  update: (data) => request.put('/oa/attendance', data),
  remove: (id) => request.delete(`/oa/attendance/${id}`)
}

// 公文
export const documentApi = {
  page: (params) => request.get('/oa/document/page', { params }),
  add: (data) => request.post('/oa/document', data),
  update: (data) => request.put('/oa/document', data),
  remove: (id) => request.delete(`/oa/document/${id}`),
  review: (id) => request.post(`/oa/document/${id}/review`),
  sign: (id) => request.post(`/oa/document/${id}/sign`),
  archive: (id) => request.post(`/oa/document/${id}/archive`)
}

// 流程管理
export const flowApi = {
  page: (params) => request.get('/oa/flow/page', { params }),
  add: (data) => request.post('/oa/flow', data),
  update: (data) => request.put('/oa/flow', data),
  remove: (id) => request.delete(`/oa/flow/${id}`),
  toggle: (id) => request.post(`/oa/flow/${id}/toggle`)
}
