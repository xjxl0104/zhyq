export const SALES_PLATFORMS = ['天猫', '京东', '拼多多', '有赞', '抖音', '视频号', '其他']
export const COURIERS = ['圆通', '中通', '顺丰', '申通', '韵达', '极兔', '京东', '邮政', '其他']
export const WAREHOUSE_MODES = ['自建仓', '外包云仓', '两者都有']
export const PAIN_POINTS = ['时效慢', '错发漏发多', '大促爆仓', '成本太高', '系统对接难', '其他']
export const WEIGHT_RANGES = ['0-0.3', '0.3-0.5', '0.5-1', '1-2', '2-3', '>=3']
export const AREA_RANGES = ['0-50', '50-200', '200-1000', '1000-3000', '3000-5000', '>=5000']
export const COUNT_RANGES = ['0-10', '10-20', '20-50', '50-100', '100-500', '>=500']
export const ORDER_RANGES = ['0-200', '200-1000', '1000-3000', '3000-5000', '5000-10000', '>=10000']
export const SERVICE_TYPES = [{ value: 2, label: '一件代发' }, { value: 1, label: '仓储' }, { value: 3, label: '仓配一体' }, { value: 4, label: '园区入驻(租仓/租办公)' }]
export function rangeLabel(value, unit = '') {
  if (!value) return '未选择'
  if (value.startsWith('>=')) return `≥ ${value.slice(2)}${unit}`
  const [min, max] = value.split('-')
  return `${min}–＜${max}${unit}`
}
export function newReferralForm() {
  return { name: '', brandName: '', contact: '', phone: '', serviceType: 2, warehouseId: null, demand: '', remark: '',
    requirements: { contactPosition: '', salesPlatforms: [], salesPlatformOther: '', productWeights: [], storageArea: '', productCount: '', skuCount: '', dailyOrders: '', itemsPerParcel: '',
      packaging: '', couriers: [], courierOther: '', invoiceRequired: null,
      warehouseMode: '', currentCouriers: [], currentCourierOther: '', painPoints: [], painPointOther: '' } }
}
const text = value => String(value ?? '').trim()
export function referralPayload(form) {
  const name = text(form.name), phone = text(form.phone)
  if (!name) throw new Error('请填客户名称')
  if (name.length > 100) throw new Error('客户名称最多 100 字')
  if (phone && !/^1\d{10}$/.test(phone)) throw new Error('手机号格式不对')
  const r = form.requirements || newReferralForm().requirements
  const salesPlatforms = [...new Set(r.salesPlatforms || [])], couriers = [...new Set(r.couriers || [])]
  if (salesPlatforms.some(x => !SALES_PLATFORMS.includes(x)) || couriers.some(x => !COURIERS.includes(x))) throw new Error('请选择有效的平台和快递')
  const salesPlatformOther = salesPlatforms.includes('其他') ? text(r.salesPlatformOther) : ''
  const courierOther = couriers.includes('其他') ? text(r.courierOther) : ''
  if (salesPlatforms.includes('其他') && !salesPlatformOther) throw new Error('请填写其他销售平台')
  if (couriers.includes('其他') && !courierOther) throw new Error('请填写其他快递承运商')
  const productWeights = (r.productWeights || []).map(x => ({ range: x.range, percentage: text(x.percentage) === '' ? null : Number(x.percentage) }))
  if (productWeights.some(x => !WEIGHT_RANGES.includes(x.range)) || new Set(productWeights.map(x => x.range)).size !== productWeights.length) throw new Error('请选择有效的重量段')
  if (productWeights.some(x => x.percentage !== null)) {
    if (productWeights.some(x => x.percentage === null || !Number.isFinite(x.percentage) || x.percentage < 0 || x.percentage > 100)) throw new Error('请为每个已选重量段填写 0–100 的占比')
    if (Math.abs(productWeights.reduce((sum, x) => sum + x.percentage, 0) - 100) > 0.000001) throw new Error('重量段占比合计应为 100%')
  }
  for (const [key, ranges] of Object.entries({ storageArea: AREA_RANGES, productCount: COUNT_RANGES, skuCount: COUNT_RANGES, dailyOrders: ORDER_RANGES })) {
    if (r[key] && !ranges.includes(r[key])) throw new Error('请选择有效的需求范围')
  }
  if (![null, undefined, true, false].includes(r.invoiceRequired)) throw new Error('请选择是否开发票')
  const contactPosition = text(r.contactPosition)
  if (contactPosition.length > 50) throw new Error('对接人职位最多 50 字')
  const itemsText = text(r.itemsPerParcel), itemsPerParcel = itemsText === '' ? null : Number(itemsText)
  if (itemsPerParcel !== null && (!Number.isFinite(itemsPerParcel) || itemsPerParcel <= 0 || itemsPerParcel > 9999 || !/^\d+(\.\d{1,2})?$/.test(itemsText))) throw new Error('平均包裹产品数量请填写大于 0 的数字，最多两位小数')
  const warehouseMode = text(r.warehouseMode)
  if (warehouseMode && !WAREHOUSE_MODES.includes(warehouseMode)) throw new Error('请选择有效的仓配方式')
  const currentCouriers = [...new Set(r.currentCouriers || [])], painPoints = [...new Set(r.painPoints || [])]
  if (currentCouriers.some(x => !COURIERS.includes(x)) || painPoints.some(x => !PAIN_POINTS.includes(x))) throw new Error('请选择有效的仓配现状选项')
  const currentCourierOther = currentCouriers.includes('其他') ? text(r.currentCourierOther) : ''
  const painPointOther = painPoints.includes('其他') ? text(r.painPointOther) : ''
  if (currentCouriers.includes('其他') && !currentCourierOther) throw new Error('请填写目前合作的其他快递')
  if (painPoints.includes('其他') && !painPointOther) throw new Error('请填写其他头疼的问题')
  return { name, brandName: text(form.brandName), contact: text(form.contact), phone: phone || null,
    serviceType: form.serviceType, warehouseId: form.serviceType === 4 ? null : form.warehouseId,
    demand: text(form.demand), remark: text(form.remark), requirements: { ...r, contactPosition, salesPlatforms, salesPlatformOther,
      productWeights, itemsPerParcel, couriers, courierOther, packaging: text(r.packaging), invoiceRequired: r.invoiceRequired ?? null,
      warehouseMode, currentCouriers, currentCourierOther, painPoints, painPointOther } }
}
export function parseReferralToken(query = {}) {
  let value = query.token || query.scene || ''
  try { value = decodeURIComponent(value) } catch (_) { return '' }
  return /^[a-f0-9]{32}$/.test(value) ? value : ''
}
export function referralShare(token) {
  const valid = parseReferralToken({ token })
  return { title: '邀请您填写仓储发货需求', path: valid ? `/pages/customer-intake/index?token=${valid}` : '/pages/entry/index', imageUrl: '/static/share/app-card.png' }
}
export function newSubmissionId() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, x => {
    const n = Math.floor(Math.random() * 16)
    return (x === 'x' ? n : ((n & 3) | 8)).toString(16)
  })
}
export function requirementRows(r = {}) {
  const multi = (items, other) => (items || []).map(x => x === '其他' && other ? `其他：${other}` : x).join('、')
  return [
    ['对接人职位', r.contactPosition],
    ['销售平台', multi(r.salesPlatforms, r.salesPlatformOther)],
    ['产品重量', (r.productWeights || []).map(x => `${rangeLabel(x.range, 'kg')}${x.percentage == null ? '' : `（${x.percentage}%）`}`).join('；')],
    ['仓储面积', r.storageArea && rangeLabel(r.storageArea, '㎡')],
    ['销售产品种类数', r.productCount && rangeLabel(r.productCount, '种')],
    ['SKU 数量', r.skuCount && rangeLabel(r.skuCount, '个')],
    ['日均单量', r.dailyOrders && rangeLabel(r.dailyOrders, '单')],
    ['平均包裹产品数量', r.itemsPerParcel == null || r.itemsPerParcel === '' ? '' : `${r.itemsPerParcel} 件`],
    ['打包方式', r.packaging], ['指定快递承运商', multi(r.couriers, r.courierOther)],
    ['是否开发票', r.invoiceRequired === true ? '是' : r.invoiceRequired === false ? '否' : ''],
    ['目前仓配方式', r.warehouseMode], ['目前合作快递', multi(r.currentCouriers, r.currentCourierOther)],
    ['最头疼的问题', multi(r.painPoints, r.painPointOther)]
  ].filter(([, value]) => value).map(([label, value]) => ({ label, value }))
}
