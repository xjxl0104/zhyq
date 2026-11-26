function amount(raw) {
  if (raw == null || String(raw).trim() === '') return undefined
  const n = Number(raw)
  if (!Number.isFinite(n) || n < 0) throw new Error('单价须为非负数字，未报价可以留空')
  return n
}
function prices(values) {
  const out = {}
  for (const [key,value] of Object.entries(values)) {
    const n = amount(value)
    if (n !== undefined) out[key] = n
  }
  return out
}
export function prepareRateCard(card) {
  if (!card.scopeNote.trim()) throw new Error('请填写报价范围、有效期及附加费说明')
  const names = new Set()
  const carriers = card.carriers.map(row => {
    const name = row.name.trim()
    if (!name || name === '其他' || name === '无') throw new Error('请填写具体快递公司名称')
    if (names.has(name)) throw new Error('快递公司重复：' + name)
    names.add(name)
    const heavyRule = row.heavyRule === 'firstStep' ? 'firstStep' : 'labelKg'
    const result = {name,heavyRule,rates:prices(row.rates),scopeNote:(row.scopeNote || '').trim()}
    const keys = heavyRule === 'firstStep' ? ['heavyFirstKg','heavyFirstFee','heavyStepKg','heavyStepFee','heavyMaxKg'] : ['heavyLabelFee','heavyPerKg','heavyMaxKg']
    for (const key of keys) {
      const n = amount(row[key])
      if (n !== undefined) result[key] = n
    }
    return result
  })
  return {scopeNote:card.scopeNote.trim(),fields:prices(card.fields),services:prices(card.services),carriers,...(card.tariff ? {tariff:card.tariff} : {})}
}
