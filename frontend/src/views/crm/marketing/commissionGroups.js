// 「按合同月结」主列表：把后端“一个月 × 一个合同”的行归并为“一个合同一行”，点进合同再看每月。
const KEYS = ['total', 'frozen', 'settleable', 'settled']
const n = v => Number(v || 0)
const round3 = v => Math.round(v * 1000) / 1000

export function groupByContract(rows) {
  const map = new Map()
  for (const row of rows || []) {
    const key = row.contractId ?? `#${row.contractNo}`
    let c = map.get(key)
    if (!c) {
      c = { contractId: row.contractId, contractNo: row.contractNo, customerName: row.customerName, promoters: new Set(), tickets: 0, months: [] }
      for (const k of KEYS) c[k] = 0
      map.set(key, c)
    }
    for (const name of String(row.promoters || '').split('、').filter(Boolean)) c.promoters.add(name)
    for (const k of KEYS) c[k] = round3(c[k] + n(row[k]))
    c.tickets += n(row.tickets)
    c.months.push(row)
  }
  return [...map.values()].map(c => {
    const months = [...c.months].sort((a, b) => String(b.month).localeCompare(String(a.month)))
    return {
      ...c,
      promoters: [...c.promoters].join('、'),
      months,
      latestMonth: months[0]?.month || '',
      firstMonth: months.at(-1)?.month || '',
      settleableMonths: months.filter(m => n(m.settleable) > 0).length,
    }
  }).sort((a, b) => b.latestMonth.localeCompare(a.latestMonth) || n(b.total) - n(a.total))
}

// 结算进度条：已结算 / 可结算 / 冻结 各占佣金合计的百分比（合计为 0 时全 0）。
export function settleShares(row) {
  const total = n(row.settled) + n(row.settleable) + n(row.frozen)
  if (total <= 0) return { settled: 0, settleable: 0, frozen: 0 }
  const pct = v => Math.round(n(v) / total * 1000) / 10
  return { settled: pct(row.settled), settleable: pct(row.settleable), frozen: pct(row.frozen) }
}
