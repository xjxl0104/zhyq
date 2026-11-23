export const WAREHOUSE_STAGES = ['待业务员承接', '业务员承接', '价格谈判', '签约', '货品入仓', '正式发货']
export const isWarehouseOwner = profile => profile?.warehouseRole === 'owner' && profile?.isOwner === true
export const warehouseStageLabel = value => WAREHOUSE_STAGES[Number(value)] || WAREHOUSE_STAGES[0]
export function staffPayload(form) {
  const name = String(form.name || '').trim()
  const phone = String(form.phone || '').trim()
  const status = Number(form.status)
  if (!name || name.length > 50) throw new Error('请填写业务员真实姓名（不超过 50 字）')
  if (!/^1\d{10}$/.test(phone)) throw new Error('请填写正确的 11 位手机号')
  if (status !== 0 && status !== 1) throw new Error('请选择启用或停用')
  return { name, phone, status }
}
export function stagePayload(stage, summary) {
  const value = Number(stage)
  if (!Number.isInteger(value) || value < 1 || value > 5) throw new Error('请选择实际服务进度')
  const text = String(summary || '').trim()
  if (text.length > 450) throw new Error('跟进说明不能超过 450 字')
  return { stage: value, summary: text }
}
