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
  approve: (taskId, opinion) => request.post(`/workflow/task/${taskId}/approve`, { opinion }),
  reject: (taskId, opinion) => request.post(`/workflow/task/${taskId}/reject`, { opinion }),
  instancePage: (params) => request.get('/workflow/instance/page', { params }),
  instanceTasks: (instanceId) => request.get(`/workflow/instance/${instanceId}/tasks`)
}
