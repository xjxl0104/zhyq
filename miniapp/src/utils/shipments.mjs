const DAY = 86400000
export const PAGE_SIZE = 20
export function chinaToday(now = new Date()) {
  return new Date(now.getTime() + 8 * 3600000).toISOString().slice(0, 10)
}
function dayNumber(value) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value || '')) return null
  const date = new Date(value + 'T00:00:00Z')
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value ? date.getTime() / DAY : null
}
export function validateRange(startDate, endDate) {
  const start = dayNumber(startDate), end = dayNumber(endDate)
  if (start == null || end == null) return '请选择正确的开始和结束日期'
  if (start > end) return '开始日期不能晚于结束日期'
  if (end - start + 1 > 366) return '单次最多查看 366 天，请缩小日期范围'
  return ''
}
export function rangePreset(kind, now = new Date()) {
  const today = chinaToday(now)
  const end = dayNumber(today)
  const date = days => new Date(days * DAY).toISOString().slice(0, 10)
  if (kind === 'yesterday') return { startDate: date(end - 1), endDate: date(end - 1) }
  if (kind === 'week') return { startDate: date(end - 6), endDate: today }
  if (kind === 'lastMonth') {
    const last = dayNumber(today.slice(0, 8) + '01') - 1
    return { startDate: date(last).slice(0, 8) + '01', endDate: date(last) }
  }
  return { startDate: today.slice(0, 8) + '01', endDate: today }
}
/** Volumes arrive as decimal strings; formatting must not round large shipment totals. */
export function formatVolume(value) {
  const match = String(value ?? '0').match(/^(-?)(\d+)(?:\.(\d+))?$/)
  if (!match) return '—'
  const whole = match[2].replace(/^0+(?=\d)/, '').replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  const decimal = (match[3] || '').replace(/0+$/, '')
  return match[1] + whole + (decimal ? '.' + decimal : '')
}
