const DAY = 86400000
export const PAGE_SIZE = 20
export function shipmentNavigation(profile) {
  const team = ['P2', 'P3', 'P4'].includes(profile?.positionCode) && Number(profile?.status) === 1
  return {
    title: team ? '团队发货量' : '客户发货量',
    description: team ? '汇总 / 下级成员明细' : '汇总 / 客户合同明细',
    url: `/pages/shipments/index?scope=${team ? 'team' : 'own'}`
  }
}
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

/** 发货趋势柱状图数据：daily 用每日、monthly 用每月；高度按最大值缩放（rpx），非零至少 4。 */
export function trendBarsOf(trend, mode = 'daily', maxHeight = 220) {
  const rows = mode === 'monthly' ? (trend?.monthly || []) : (trend?.daily || [])
  const values = rows.map(r => Number(r.volume) || 0)
  const max = Math.max(0, ...values)
  return rows.map((r, i) => {
    const volume = values[i]
    const key = mode === 'monthly' ? r.month : r.date
    return {
      key, volume,
      hasData: mode === 'monthly' ? true : r.hasData !== false,
      label: mode === 'monthly' ? String(r.month).slice(2) : String(r.date).slice(8),
      full: mode === 'monthly' ? String(r.month) : String(r.date),
      height: max <= 0 || volume <= 0 ? 0 : Math.max(4, Math.round(volume / max * maxHeight))
    }
  })
}

/** 合计、平均（保留一位）与峰值。 */
export function trendSummary(bars) {
  const total = bars.reduce((s, b) => s + b.volume, 0)
  const peak = bars.reduce((p, b) => b.volume > p.volume ? b : p, { full: '—', volume: 0 })
  return { total, average: bars.length ? (total / bars.length).toFixed(1) : '0', peak }
}
