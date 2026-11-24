export const platformOptions = ['天猫', '京东', '拼多多', '有赞', '抖音', '视频号', '其他']
export const productCategoryOptions = ['食品饮料', '美妆护肤', '日化清洁', '服装鞋帽', '家居家纺', '母婴用品', '宠物用品', '数码家电', '文体办公', '箱包配饰', '五金汽配', '其他']
export const itemsPerParcelRanges = ['1', '2', '3', '4', '5', '>=6']
const legacyItemsPerParcelRanges = ['0-2', '2-5', '5-10', '10-20', '>=20']
export const itemsPerParcelLabel = value => ({ '1': '1 件', '2': '2 件', '3': '3 件', '4': '4 件', '5': '5 件', '>=6': '6 件及以上', '0-2': '少于 2 件', '2-5': '2–小于 5 件', '5-10': '5–小于 10 件', '10-20': '10–小于 20 件', '>=20': '≥20 件' })[value] || ''
export const courierOptions = ['圆通', '中通', '顺丰', '申通', '韵达', '极兔', '京东', '邮政', '其他', '无']
// “无”与其他快递互斥
export function exclusiveNone(previous = [], next = []) {
  if (next.includes('无') && !previous.includes('无')) return ['无']
  return next.length > 1 ? next.filter(x => x !== '无') : next
}
export const warehouseModeOptions = ['自建仓', '外包云仓', '两者都有']
export const painPointOptions = ['时效慢', '错发漏发多', '大促爆仓', '成本太高', '系统对接难', '其他']
export const weightRanges = ['0-0.3', '0.3-0.5', '0.5-1', '1-2', '2-3', '>=3']
export const rangeFields = [
  { key: 'skuCount', label: 'SKU 数量', unit: '个', ranges: ['0-10', '10-20', '20-50', '50-100', '100-500', '>=500'] },
  { key: 'dailyOrders', label: '日均单量', unit: '单', ranges: ['0-200', '200-1000', '1000-3000', '3000-5000', '5000-10000', '>=10000'] }
]
export const rangeLabel = (range, unit = '') => range?.startsWith('>=') ? `≥${range.slice(2)} ${unit}` : `${range?.replace('-', '–小于 ')} ${unit}`
export const emptyRequirements = () => ({ contactPosition: '', salesPlatforms: [], salesPlatformOther: '', productWeights: [], productCategories: [], productCategoryOther: '', skuCount: '', dailyOrders: '', itemsPerParcelRange: '', itemsPerParcel: null,
  packaging: '', couriers: [], courierOther: '', invoiceRequired: null, warehouseMode: '', currentCouriers: [], currentCourierOther: '', painPoints: [], painPointOther: '' })
export function validateRequirements(value) {
  if ((value.productCategories || []).some(category => !productCategoryOptions.includes(category))) return '销售品类选项不正确'
  if ((value.productCategories || []).includes('其他') && !(value.productCategoryOther || '').trim()) return '请填写其他销售品类'
  if (value.itemsPerParcelRange && !itemsPerParcelRanges.includes(value.itemsPerParcelRange) && !legacyItemsPerParcelRanges.includes(value.itemsPerParcelRange)) return '平均包裹产品数量选项不正确'
  if (value.salesPlatforms.includes('其他') && !value.salesPlatformOther.trim()) return '请填写其他销售平台'
  if (value.couriers.includes('其他') && !value.courierOther.trim()) return '请填写其他快递承运商'
  if ((value.currentCouriers || []).includes('其他') && !(value.currentCourierOther || '').trim()) return '请填写目前合作的其他快递'
  if ((value.painPoints || []).includes('其他') && !(value.painPointOther || '').trim()) return '请填写其他头疼的问题'
  const weights = value.productWeights
  const entered = weights.filter(w => w.percentage !== '' && w.percentage != null)
  if (entered.length) {
    if (entered.length !== weights.length || entered.some(w => !Number.isFinite(Number(w.percentage)) || Number(w.percentage) < 0 || Number(w.percentage) > 100)) return '请填写所有已选重量段的占比（0–100%），或全部留空'
    if (Math.abs(entered.reduce((sum, w) => sum + Number(w.percentage), 0) - 100) > 0.001) return '产品重量占比合计应为 100%'
  }
  return ''
}
const quoteWeightLabel = v => v === '0-0.3' ? '≤0.3 kg' : v === '>3' ? '＞3 kg' : `＞${String(v).replace('-', ' 至 ')} kg`

export function mergeRequirementsWithQuote(value = {}, quote = null) {
  const base = value || {}
  const q = quote || {}
  const pickList = (a, b) => Array.isArray(a) && a.length ? a : (Array.isArray(b) && b.length ? b : [])
  const pickText = (a, b) => (a != null && String(a).trim() !== '') ? a : (b ?? '')
  return {
    ...base,
    salesPlatforms: pickList(base.salesPlatforms, q.salesPlatforms),
    salesPlatformOther: pickText(base.salesPlatformOther, q.salesPlatformOther),
    productCategories: pickList(base.productCategories, q.productCategories),
    productCategoryOther: pickText(base.productCategoryOther, q.productCategoryOther),
    skuCount: pickText(base.skuCount, q.skuCount),
    dailyOrders: pickText(base.dailyOrders, q.dailyOrders),
    itemsPerParcel: base.itemsPerParcel != null && base.itemsPerParcel !== '' ? base.itemsPerParcel : (q.itemsPerParcel ?? null),
    couriers: pickList(base.couriers, q.couriers),
    courierOther: pickText(base.courierOther, q.courierOther),
    quoteWeightRanges: Array.isArray(q.weightRanges) ? q.weightRanges : (Array.isArray(base.quoteWeightRanges) ? base.quoteWeightRanges : []),
    heavyWeightKg: base.heavyWeightKg ?? q.heavyWeightKg ?? null,
    weightMix: pickText(base.weightMix, q.weightMix)
  }
}

export function requirementsRows(value = {}, quote = null) {
  const merged = quote ? mergeRequirementsWithQuote(value, quote) : (value || {})
  const multi = (items, other) => (items || []).map(item => item === '其他' && other ? `其他：${other}` : item).join('、') || '未填写'
  const weightText = (() => {
    if (Array.isArray(merged.productWeights) && merged.productWeights.length) {
      return merged.productWeights.map(w => `${rangeLabel(w.range, 'kg')}${w.percentage != null && w.percentage !== '' ? `（${w.percentage}%）` : ''}`).join('；')
    }
    if (Array.isArray(merged.quoteWeightRanges) && merged.quoteWeightRanges.length) {
      const parts = [merged.quoteWeightRanges.map(quoteWeightLabel).join('、')]
      if (merged.heavyWeightKg != null && merged.heavyWeightKg !== '') parts.push(`典型重件 ${merged.heavyWeightKg} kg`)
      if (merged.weightMix) parts.push(merged.weightMix)
      return parts.join('；')
    }
    return '未填写'
  })()
  const rows = [
    ...(merged.contactPosition ? [['对接人职位', merged.contactPosition]] : []),
    ['销售平台', multi(merged.salesPlatforms, merged.salesPlatformOther)],
    ['销售品类', multi(merged.productCategories, merged.productCategoryOther)],
    ['产品重量', weightText],
    ...rangeFields.map(f => [f.label, merged[f.key] ? rangeLabel(merged[f.key], f.unit) : '未填写']),
    ['平均包裹产品数量', itemsPerParcelLabel(merged.itemsPerParcelRange) || (merged.itemsPerParcel != null && merged.itemsPerParcel !== '' ? `${merged.itemsPerParcel} 件` : '未填写')],
    ['指定快递承运商', multi(merged.couriers, merged.courierOther)],
    ['目前仓配方式', merged.warehouseMode || '未填写'],
    ['目前合作快递', multi(merged.currentCouriers, merged.currentCourierOther)],
    ['最头疼的问题', multi(merged.painPoints, merged.painPointOther)]
  ]
  if (merged.productCount) rows.push(['销售产品种类数（历史）', rangeLabel(merged.productCount, '种')])
  // 表单已不再收集仓储面积、打包方式和开票，只在历史数据有值时显示
  if (merged.storageArea) rows.push(['仓储面积', rangeLabel(merged.storageArea, '㎡')])
  if (merged.packaging) rows.push(['打包方式', merged.packaging])
  if (merged.invoiceRequired === true || merged.invoiceRequired === false) rows.push(['是否开发票', merged.invoiceRequired ? '是' : '否'])
  return rows
}
