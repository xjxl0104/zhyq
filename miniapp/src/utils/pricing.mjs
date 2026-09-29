// 单价以千分之一元运算，避免浮点误差影响金额校验。
export function amountToMills(value) {
  const text = String(value ?? '').trim()
  if (!/^\d+(?:\.\d{1,3})?$/.test(text)) return null
  const [whole, decimal = ''] = text.split('.')
  const mills = Number(whole) * 1000 + Number(decimal.padEnd(3, '0'))
  return Number.isSafeInteger(mills) ? mills : null
}

export function millsToAmount(value) {
  if (!Number.isSafeInteger(value)) return '—'
  const sign = value < 0 ? '-' : ''
  const absolute = Math.abs(value)
  return `${sign}${Math.floor(absolute / 1000)}.${String(absolute % 1000).padStart(3, '0')}`
}

export function formatPerOrder(value) {
  const mills = amountToMills(value)
  return mills === null ? '—' : millsToAmount(mills)
}

export function validatePricing(total, owner, beneficiaries) {
  const totalMills = amountToMills(total)
  const ownerMills = amountToMills(owner)
  if (totalMills === null) return '请填写总佣金单价，最多 3 位小数'
  if (ownerMills === null) return '请填写 P4 本人单价，最多 3 位小数'
  if (beneficiaries.length > 2) return '每个客户最多分配给 2 位受益人'
  const selected = new Set()
  let allocated = ownerMills
  for (let i = 0; i < beneficiaries.length; i += 1) {
    const row = beneficiaries[i]
    if (!row.promoterId) return `请选择第 ${i + 1} 位受益人`
    const key = String(row.promoterId)
    if (selected.has(key)) return '受益人不能重复，请重新选择'
    selected.add(key)
    const amount = amountToMills(row.amountPerOrder)
    if (amount === null) return `请填写第 ${i + 1} 位受益人的单价，最多 3 位小数`
    allocated += amount
  }
  if (!Number.isSafeInteger(allocated) || allocated > totalMills) return 'P4 本人金额与受益人金额之和不能超过总佣金'
  return ''
}
