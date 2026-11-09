export const platformOptions = ['天猫', '京东', '拼多多', '有赞', '抖音', '视频号', '其他']
export const courierOptions = ['顺丰', '中通', '圆通', '申通', '韵达', '极兔', '其他']
export const weightRanges = ['0-0.3', '0.3-0.5', '0.5-1', '1-2', '2-3', '>=3']
export const rangeFields = [
  { key: 'storageArea', label: '仓储面积', unit: '㎡', ranges: ['0-50', '50-200', '200-1000', '1000-3000', '3000-5000', '>=5000'] },
  { key: 'productCount', label: '销售产品种类数', unit: '种', ranges: ['0-10', '10-20', '20-50', '50-100', '100-500', '>=500'] },
  { key: 'skuCount', label: 'SKU 数量', unit: '个', ranges: ['0-10', '10-20', '20-50', '50-100', '100-500', '>=500'] },
  { key: 'dailyOrders', label: '日均单量', unit: '单', ranges: ['0-200', '200-1000', '1000-3000', '3000-5000', '5000-10000', '>=10000'] }
]
export const rangeLabel = (range, unit = '') => range?.startsWith('>=') ? `≥${range.slice(2)} ${unit}` : `${range?.replace('-', '–小于 ')} ${unit}`
export const emptyRequirements = () => ({ salesPlatforms: [], salesPlatformOther: '', productWeights: [], storageArea: '', productCount: '', skuCount: '', dailyOrders: '', packaging: '', couriers: [], courierOther: '', invoiceRequired: null })
export function validateRequirements(value) {
  if (value.salesPlatforms.includes('其他') && !value.salesPlatformOther.trim()) return '请填写其他销售平台'
  if (value.couriers.includes('其他') && !value.courierOther.trim()) return '请填写其他快递承运商'
  const weights = value.productWeights
  const entered = weights.filter(w => w.percentage !== '' && w.percentage != null)
  if (entered.length) {
    if (entered.length !== weights.length || entered.some(w => !Number.isFinite(Number(w.percentage)) || Number(w.percentage) < 0 || Number(w.percentage) > 100)) return '请填写所有已选重量段的占比（0–100%），或全部留空'
    if (Math.abs(entered.reduce((sum, w) => sum + Number(w.percentage), 0) - 100) > 0.001) return '产品重量占比合计应为 100%'
  }
  return ''
}
export function requirementsRows(value = {}) {
  const multi = (items, other) => (items || []).map(item => item === '其他' && other ? `其他：${other}` : item).join('、') || '未填写'
  return [
    ['销售平台', multi(value.salesPlatforms, value.salesPlatformOther)],
    ['产品重量', (value.productWeights || []).map(w => `${rangeLabel(w.range, 'kg')}${w.percentage != null && w.percentage !== '' ? `（${w.percentage}%）` : ''}`).join('；') || '未填写'],
    ...rangeFields.map(f => [f.label, value[f.key] ? rangeLabel(value[f.key], f.unit) : '未填写']),
    ['打包方式', value.packaging || '未填写'],
    ['指定快递承运商', multi(value.couriers, value.courierOther)],
    ['是否开发票', value.invoiceRequired === true ? '是' : value.invoiceRequired === false ? '否' : '未填写']
  ]
}
