import { planPointToLocal, PLAN_BUILDING } from './floorPlanData.js'
export const ACTIVE_ORDER_STATUSES = [1, 2, 3, 4, 7]
const statusLabels = { 1: '待派单', 2: '待接单', 3: '处理中', 4: '待验收', 5: '已完成', 6: '已关闭', 7: '已超时' }
export function isUrgentWorkOrder(order) { return Number(order.status) === 7 || Number(order.slaState) > 0 || Number(order.urgency) === 3 }
export function spatialWorkOrder(order) {
  const mapped = planPointToLocal(order)
  if (!mapped) return null
  return {
    id: 'workorder-' + order.id, orderId: order.id, module: 'property', floor: mapped.floor,
    localPosition: mapped.position, name: order.title || order.code, code: order.code,
    status: statusLabels[order.status] || '待处理', urgent: isUrgentWorkOrder(order),
    location: `数智云仓主楼 / ${mapped.floor === -1 ? 'B1' : mapped.floor + 'F'} / ${order.location || '图纸标注位置'}`,
    values: [['负责人', order.assignee || '待派单'], ['位置依据', '报修图纸标注'], ['状态', statusLabels[order.status] || '待处理']],
    planFileId: order.floorPlanFileId, planPoint: { x: Number(order.planX), y: Number(order.planY) },
  }
}
export function canLocateOrder(order) { return !!planPointToLocal(order) }
export { PLAN_BUILDING }
