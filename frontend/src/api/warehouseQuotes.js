import request from '@/utils/request'

const quote = '/crm/marketing/quote'
const scoped = projectId => ({ params: { projectId } })

export const quoteApi = {
  card: (id, projectId) => request.get(`${quote}/warehouses/${id}/rate-card`, scoped(projectId)),
  save: (id, projectId, data) => request.put(`${quote}/warehouses/${id}/rate-card`, JSON.stringify(data), {
    ...scoped(projectId), headers: { 'Content-Type': 'application/json' }
  }),
  intake: (id, projectId) => request.get(`/crm/marketing/customer/${id}/quote-intake`, scoped(projectId)),
  confirmProject: (id, projectId) => request.post(`/crm/marketing/customer/${id}/quote-intake/project`, null, scoped(projectId)),
  comparison: (id, projectId) => request.get(`${quote}/customers/${id}/comparison`, scoped(projectId)),
  advice: (id, projectId) => request.get(`${quote}/customers/${id}/advice`, scoped(projectId)),
  retry: (id, projectId) => request.post(`${quote}/customers/${id}/advice/retry`, null, scoped(projectId))
}
