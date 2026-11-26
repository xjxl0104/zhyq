// 按缴费单位定制通知单抬头的前端辅助函数（与后端 NoticeHeaderProfileService 口径一致）

function daysInMonth(year, month1Based) {
  return new Date(year, month1Based, 0).getDate()
}

function pad2(n) {
  return String(n).padStart(2, '0')
}

export function resolveProfileDateForMonth(savedDate, monthOffset, dayOfMonth, periodMonth) {
  if (!savedDate && !dayOfMonth) return null
  if (!periodMonth || !/^\d{4}-\d{2}$/.test(periodMonth)) return savedDate || null
  const [ty, tm] = periodMonth.split('-').map(Number)
  if (savedDate && monthOffset == null) {
    if (String(savedDate).startsWith(periodMonth)) return savedDate
    const d = Number(String(savedDate).split('-')[2] || 1)
    return `${ty}-${pad2(tm)}-${pad2(Math.min(d, daysInMonth(ty, tm)))}`
  }
  const offset = Number(monthOffset || 0)
  const total = ty * 12 + (tm - 1) + offset
  const ny = Math.floor(total / 12)
  const nm = (total % 12) + 1
  const rawDay = Number(dayOfMonth || String(savedDate || '').split('-')[2] || 1)
  const d = Math.min(Math.max(rawDay, 1), daysInMonth(ny, nm))
  return `${ny}-${pad2(nm)}-${pad2(d)}`
}

export function buildTenantChoices(ledgerContracts = [], noticeList = [], profiles = []) {
  const set = new Set()
  for (const c of ledgerContracts || []) {
    const n = (c?.tenantName || '').trim()
    if (n) set.add(n)
  }
  for (const n of noticeList || []) {
    const t = (n?.tenantName || '').trim()
    if (t) set.add(t)
  }
  for (const p of profiles || []) {
    const t = (p?.tenantName || '').trim()
    if (t) set.add(t)
  }
  return [...set]
}

export function mergeDepositConfigWithProfile(baseConfig, profile) {
  const base = baseConfig || {}
  if (!profile) return base
  return {
    ...base,
    parkName: profile.parkName || base.parkName,
    noticePrefix: profile.noticePrefix || base.noticePrefix,
    issueDate: profile.issueDate || base.issueDate,
    dueDate: profile.dueDate || base.dueDate,
    rentPayeeName: profile.rentPayeeName || base.rentPayeeName,
    rentContactName: profile.rentContactName || base.rentContactName,
    rentContactPhone: profile.rentContactPhone || base.rentContactPhone,
    rentPaymentAccount: profile.rentPaymentAccount || base.rentPaymentAccount,
    propertyPayeeName: profile.propertyPayeeName || base.propertyPayeeName,
    propertyContactName: profile.propertyContactName || base.propertyContactName,
    propertyContactPhone: profile.propertyContactPhone || base.propertyContactPhone,
    propertyPaymentAccount: profile.propertyPaymentAccount || base.propertyPaymentAccount
  }
}
