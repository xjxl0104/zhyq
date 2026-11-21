const withdrawalStatuses = { 1: '审批中', 2: '审批通过·待打款', 3: '已打款', 4: '已驳回' }
const taskStatuses = { 1: '待审批', 2: '已通过', 3: '已驳回' }
export function withdrawalStage(record = {}) {
  const base = withdrawalStatuses[record.status] || '状态待确认'
  if (Number(record.status) !== 1) return base
  const name = record.approval?.assigneeName
  return name ? `待${name}审批` : base
}
export function approvalHistoryStatus(value) { return taskStatuses[value] || '状态待确认' }
export function withdrawalAmount(value) {
  if (value == null || String(value).trim() === '' || !Number.isFinite(Number(value))) return '—'
  return Number(value).toFixed(2)
}
export function withdrawalTime(value) { return typeof value === 'string' ? value.replace('T', ' ').slice(0, 16) : '—' }
export function withdrawalAccount(record = {}) {
  const type = { 1: '微信', 2: '银行卡', 3: '支付宝' }[record.accountType] || '收款账户'
  return record.accountTail ? `${type} · 尾号 ${record.accountTail}` : `${type} · 尾号未记录`
}
// Ability comes from the server's bound approver and the current task, never a partner title/name.
export function canDecideWithdrawal(record, approved) {
  return Number(record?.status) === 1 && record?.[approved ? 'canApprove' : 'canReject'] === true
}
export function approvalOpinionError(value) {
  const text = String(value ?? '').trim()
  if (!text) return '请填写审批意见，说明通过或驳回原因'
  if (text.length > 500) return '审批意见不能超过 500 字'
  return ''
}
