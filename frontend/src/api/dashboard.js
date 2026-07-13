import request from '@/utils/request'

export const dashboardApi = {
  workbench: () => request.get('/dashboard/workbench'),
  overview: () => request.get('/dashboard/overview'),
  roomStatus: () => request.get('/dashboard/room-status'),
  revenueTrend: () => request.get('/dashboard/revenue-trend'),
  workOrderCategory: () => request.get('/dashboard/workorder-category')
}
