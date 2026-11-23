// 单价以千分之一元运算，避免浮点误差影响金额校验。
export const DEFAULT_PLATFORM_RATE = 0.05

function parsePlatformRate(platformRate = DEFAULT_PLATFORM_RATE) {
  const raw = typeof platformRate === 'object' && platformRate !== null
    ? platformRate.platformRate
    : platformRate
  const candidate = raw == null || raw === '' ? DEFAULT_PLATFORM_RATE : raw
  const text = String(candidate).trim()
  const num = Number(text)
  if (!Number.isFinite(num) || num < 0 || num >= 1) {
    return { num: DEFAULT_PLATFORM_RATE, rateInt: 5n, scale: 100n, platformRatePercent: '5', maxPersonalRatePercent: '95' }
  }
  const normalized = /^\d+(?:\.\d+)?$/.test(text) ? text : num.toFixed(6).replace(/0+$/, '').replace(/\.$/, '')
  const [whole, decimal = ''] = normalized.split('.')
  const scale = 10n ** BigInt(decimal.length)
  const rateInt = BigInt(whole) * scale + BigInt(decimal || '0')
  const platformRatePercent = String(Number((num * 100).toFixed(4)))
  const maxPersonalRatePercent = String(Number(((1 - num) * 100).toFixed(4)))
  return { num, rateInt, scale, platformRatePercent, maxPersonalRatePercent }
}

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

export function maxPersonalMills(totalMills, platformRate = DEFAULT_PLATFORM_RATE) {
  if (!Number.isSafeInteger(totalMills) || totalMills < 0) return null
  const { rateInt, scale } = parsePlatformRate(platformRate)
  const maxMills = Number((BigInt(totalMills) * (scale - rateInt)) / scale)
  return Number.isSafeInteger(maxMills) ? maxMills : null
}

export function maxPersonalPerOrder(total, platformRate = DEFAULT_PLATFORM_RATE) {
  const totalMills = amountToMills(total)
  const maxMills = maxPersonalMills(totalMills, platformRate)
  return maxMills === null ? '—' : millsToAmount(maxMills)
}

export function platformMinShareMills(totalMills, platformRate = DEFAULT_PLATFORM_RATE) {
  const maxMills = maxPersonalMills(totalMills, platformRate)
  return maxMills === null ? null : totalMills - maxMills
}

export function platformMinSharePerOrder(total, platformRate = DEFAULT_PLATFORM_RATE) {
  const totalMills = amountToMills(total)
  const minMills = platformMinShareMills(totalMills, platformRate)
  return minMills === null ? '—' : millsToAmount(minMills)
}

export function calcPricingBreakdown(total, owner, beneficiaries = [], platformRate = DEFAULT_PLATFORM_RATE) {
  const rateInfo = parsePlatformRate(platformRate)
  const totalMills = amountToMills(total)
  const ownerMills = amountToMills(owner)
  let beneficiariesMills = 0
  for (const row of beneficiaries || []) {
    const amount = amountToMills(row?.amountPerOrder)
    if (amount === null) {
      beneficiariesMills = null
      break
    }
    beneficiariesMills += amount
  }
  if (beneficiariesMills !== null && !Number.isSafeInteger(beneficiariesMills)) beneficiariesMills = null
  const maxMills = maxPersonalMills(totalMills, platformRate)
  const platformMinMills = maxMills === null ? null : totalMills - maxMills
  const personalMills = ownerMills !== null && beneficiariesMills !== null && Number.isSafeInteger(ownerMills + beneficiariesMills)
    ? ownerMills + beneficiariesMills
    : null
  const remainingPersonalMills = maxMills !== null && personalMills !== null ? maxMills - personalMills : null
  const companyMills = totalMills !== null && personalMills !== null ? totalMills - personalMills : null
  const overTotal = personalMills !== null && totalMills !== null && personalMills > totalMills
  const overLimit = personalMills !== null && maxMills !== null && personalMills > maxMills
  let progressPercent = 0
  if (totalMills !== null && personalMills !== null) {
    progressPercent = totalMills === 0
      ? (personalMills > 0 ? 100 : 0)
      : Math.min(100, Math.max(0, Math.round((personalMills / totalMills) * 100)))
  }
  let capUsagePercent = 0
  if (maxMills !== null && personalMills !== null) {
    capUsagePercent = maxMills === 0
      ? (personalMills > 0 ? 100 : 0)
      : Math.min(100, Math.max(0, Math.round((personalMills / maxMills) * 100)))
  }
  return {
    platformRate: rateInfo.num,
    platformRatePercent: rateInfo.platformRatePercent,
    maxPersonalRatePercent: rateInfo.maxPersonalRatePercent,
    totalMills,
    ownerMills,
    beneficiariesMills,
    personalMills,
    maxPersonalMills: maxMills,
    platformMinMills,
    remainingPersonalMills,
    companyMills,
    totalPerOrder: millsToAmount(totalMills),
    ownerPerOrder: millsToAmount(ownerMills),
    beneficiariesPerOrder: millsToAmount(beneficiariesMills),
    personalPerOrder: millsToAmount(personalMills),
    maxPersonalPerOrder: millsToAmount(maxMills),
    platformMinShare: millsToAmount(platformMinMills),
    remainingPersonalPerOrder: millsToAmount(remainingPersonalMills),
    companyPerOrder: millsToAmount(companyMills),
    progressPercent,
    capUsagePercent,
    overLimit,
    overTotal,
  }
}

export function validatePricing(total, owner, beneficiaries, platformRate = DEFAULT_PLATFORM_RATE) {
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
  const rateInfo = parsePlatformRate(platformRate)
  const maxMills = maxPersonalMills(totalMills, platformRate)
  if (maxMills !== null && allocated > maxMills) {
    return `平台保底留存总佣金的 ${rateInfo.platformRatePercent}%，个人分配合计不能超过 ${millsToAmount(maxMills)} 元/单（${rateInfo.maxPersonalRatePercent}%）`
  }
  return ''
}
