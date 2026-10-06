export function incomeFilters(status) {
  return status === '' ? {} : { status: Number(status) }
}

// A malformed success response must become a recoverable load error, not a
// render exception (or a misleading empty commission list).
export function incomePage(result) {
  if (!result || !Array.isArray(result.records)
    || result.records.some(row => !row || typeof row !== 'object' || Array.isArray(row) || row.id == null)
    || result.total == null || !Number.isFinite(Number(result.total)) || Number(result.total) < 0) {
    throw new Error('收益数据暂时无法读取，请刷新重试')
  }
  return { ...result, total: Number(result.total) }
}

export function incomeAmount(value) {
  if (value == null || value === '' || !Number.isFinite(Number(value))) return '—'
  return Number(value).toFixed(3)
}

export function incomeTime(value, dateOnly = false) {
  if (typeof value !== 'string' || !value.trim()) return '时间待确认'
  return value.replace('T', ' ').slice(0, dateOnly ? 10 : 16)
}
