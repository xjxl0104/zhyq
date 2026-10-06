export const rectificationOptions = [3, 7, 15, 30].map(days => ({
  label: `${days}天`, value: days * 24 * 60
}))

function asDate(value) {
  if (!value) return null
  const date = new Date(String(value).replace(' ', 'T'))
  return Number.isNaN(date.getTime()) ? null : date
}

export function rectificationDeadline(order) {
  const created = asDate(order?.createTime)
  const minutes = Number(order?.slaResolveMin)
  if (!created || !Number.isFinite(minutes) || minutes <= 0) return null
  return new Date(created.getTime() + minutes * 60_000)
}

export function rectificationOverdue(order, now = new Date()) {
  const deadline = rectificationDeadline(order)
  if (!deadline) return false
  const completed = asDate(order?.finishTime)
  if (completed) return completed > deadline
  return [1, 2, 3, 4].includes(Number(order?.status)) && now > deadline
}

export function formatRectificationDeadline(order) {
  const date = rectificationDeadline(order)
  if (!date) return '-'
  const pad = number => String(number).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}
