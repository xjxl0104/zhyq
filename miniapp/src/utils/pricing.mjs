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

// 云仓业务员（P0）固定每单佣金：0.020 元/单 = 20 毫元
export const SALESPERSON_FEE_MILLS = 20
export const SALESPERSON_FEE_TEXT = '0.020'

// 服务费以万分之一元计算：3 位总价 × 10% 必须保留 4 位，不能四舍五入挤占个人金额。
export function unitsToAmount(value) {
  if (!Number.isSafeInteger(value)) return '—'
  const sign = value < 0 ? '-' : ''
  const absolute = Math.abs(value)
  return `${sign}${Math.floor(absolute / 10000)}.${String(absolute % 10000).padStart(4, '0')}`
}
export function platformFee(total) {
  const mills = amountToMills(total)
  return mills === null ? '—' : unitsToAmount(mills)
}
export function pricingRemainder(total, owner, beneficiaries = [], salespersonMills = SALESPERSON_FEE_MILLS) {
  const mills = amountToMills(total)
  if (mills === null) return null
  let allocated = 0
  for (const value of [owner, ...beneficiaries.map(row => row.amountPerOrder)]) {
    const amount = String(value ?? '').trim() === '' ? 0 : amountToMills(value)
    if (amount === null) return null
    allocated += amount
  }
  const remaining = mills * 9 - salespersonMills * 10 - allocated * 10
  return Number.isSafeInteger(remaining) ? remaining : null
}
export function validatePricing(total, owner, beneficiaries, maxBeneficiaries = 2, salespersonMills = SALESPERSON_FEE_MILLS) {
  const totalMills = amountToMills(total)
  const ownerMills = amountToMills(owner)
  if (totalMills === null) return '请填写总佣金单价，最多 3 位小数'
  if (ownerMills === null) return '请填写 P4 本人单价，最多 3 位小数'
  if (beneficiaries.length > maxBeneficiaries) return `最多分配给 ${maxBeneficiaries} 位受益人`
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
  const maxUnits = totalMills * 9 - salespersonMills * 10
  if (maxUnits < 0) return `总佣金单价不足以覆盖 10% 平台服务费和 ${millsToAmount(salespersonMills)} 元/单业务员佣金，最低须为 0.023 元/单`
  if (!Number.isSafeInteger(allocated) || allocated * 10 > maxUnits) {
    return `平台服务费为总佣金的 10%，业务员（P0）固定佣金 ${millsToAmount(salespersonMills)} 元/单，本人和其他受益人合计最多可分配 ${millsToAmount(Math.floor(maxUnits / 10))} 元/单`
  }
  return ''
}
