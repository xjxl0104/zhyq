export const JOIN = { 1: '待完善 / 已驳回', 2: '资质审核中', 3: '待配置订单接入', 4: '待签加盟协议', 5: '已上线', 6: '已暂停', 7: '已退出' }
export const CONTRACT = { 1: '草稿', 2: '待园区审核', 3: '待签署', 4: '已生效', 5: '履约中', 6: '变更中', 7: '已到期', 8: '已终止', 9: '已作废' }
export const ORDER = { 1: '待确认', 2: '已确认', 3: '已退款', 4: '已取消', 5: '无归属' }
export const assignmentLabel = n => ({ 1: '待确认承接', 2: '已承接', 3: '已拒绝' }[n] || '未分派')
export function parseFiles(value) { try { const x = typeof value === 'string' ? JSON.parse(value) : value; return Array.isArray(x) ? x.filter(f => f && f.id) : [] } catch { return [] } }
export const money = value => Number(value || 0).toFixed(2)
