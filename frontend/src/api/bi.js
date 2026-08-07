import request from '@/utils/request'

export const biApi = {
  northStar() { return request.get('/bi/admin/north-star') },
  deptRadar(periodStart) { return request.get('/bi/admin/dept-radar', { params: { periodStart } }) },
  deptDetail(deptId, periodStart) { return request.get(`/bi/admin/dept-radar/${deptId}`, { params: { periodStart } }) },
  trend(periodType) { return request.get('/bi/admin/trend', { params: { periodType } }) },
  moduleUsage() { return request.get('/bi/product/module-usage') },
  flowAnalysis() { return request.get('/bi/product/flow-analysis') },
  feedbackBoard() { return request.get('/bi/product/feedback-board') }
}
