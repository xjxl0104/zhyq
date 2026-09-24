import request from '@/utils/request'

// 审批链流程定义与节点配置
export const wfDefinitionApi = {
  page: (params) => request.get('/workflow/definition/page', { params }),
  nodes: (definitionId) => request.get(`/workflow/definition/${definitionId}/nodes`),
  saveNodes: (definitionId, nodes) => request.put(`/workflow/definition/${definitionId}/nodes`, nodes),
  add: (data) => request.post('/workflow/definition', data),
  update: (data) => request.put('/workflow/definition', data),
  remove: (id) => request.delete(`/workflow/definition/${id}`)
}

// 审批链运行时:待办、通过/驳回、审批轨迹
export const wfTaskApi = {
  my: (assignee) => request.get('/workflow/task/my', { params: { assignee } }),
  // 不传 assignee 时由后台按当前用户及其角色返回获派待办；仅管理员可看全部。
  myPending: (assignee) => request.get('/workflow/task/my-pending', { params: { assignee } }),
  approve: (taskId, opinion) => request.post(`/workflow/task/${taskId}/approve`, { opinion }),
  reject: (taskId, opinion) => request.post(`/workflow/task/${taskId}/reject`, { opinion }),
  instancePage: (params) => request.get('/workflow/instance/page', { params }),
  instanceTasks: (instanceId) => request.get(`/workflow/instance/${instanceId}/tasks`)
}
