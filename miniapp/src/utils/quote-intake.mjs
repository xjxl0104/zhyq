import { SALES_PLATFORMS, PRODUCT_CATEGORIES, COUNT_RANGES, ORDER_RANGES, COURIERS, rangeLabel } from './referral-intake.mjs'

export const SERVICE_GROUPS = [
  { key: 'inbound', label: '入库与库存', unit: '箱、板、吨或件', values: ['收货清点上架','仓方装卸货','入库抽检','外观或效期全检','额外盘点','批次或残次品挑选'] },
  { key: 'packing', label: '组套、标签与包装', unit: '套、张或个', values: ['成品组套','称重组套','礼盒定制组装','拆套还原单品','制标贴大标','制标贴小标','增扫溯源码','折飞机盒','折异形纸盒','十字封箱','单件防护','整箱防护','整托防护'] },
  { key: 'returns', label: '售后、调拨与回收', unit: '单、箱或板', values: ['退件回仓上架','发货后拦截','残次品报废','纸箱或周转箱回收','整箱调拨或退仓','整板调拨或退仓','拼箱调拨或退仓','随货带走卡板'] }
]
const options = (values, label = value => value) => values.map(value => ({ value, label: label(value) }))
const peakRanges = [...ORDER_RANGES.slice(0, -1), '10000-30000', '30000-50000', '>=50000']
const weights = ['0-0.3','0.3-0.5','0.5-1','1-1.5','1.5-2','2-2.5','2.5-3','>3']
export const QUOTE_FIELDS = [
  { key: 'salesPlatforms', label: '销售平台', type: 'multi', options: options(SALES_PLATFORMS), other: 'salesPlatformOther' },
  { key: 'productCategories', label: '销售品类', type: 'multi', options: options(PRODUCT_CATEGORIES), other: 'productCategoryOther' },
  { key: 'palletCount', label: '仓储面积', type: 'decimal', hint: '约占多少个卡板位', unit: '个卡板位' },
  { key: 'weightRanges', label: '产品重量', hint: '按每票包裹含包装后的实际重量勾选', type: 'multi', options: options(weights, value => value === '>3' ? '＞3 kg' : value === '0-0.3' ? '≤0.3 kg' : '＞' + value.split('-')[0] + ' 至 ' + value.split('-')[1] + ' kg') },
  { key: 'skuCount', label: 'SKU 数量', type: 'single', options: options(COUNT_RANGES, n => rangeLabel(n,'个')) },
  { key: 'dailyOrders', label: '日均单量', type: 'single', options: options(ORDER_RANGES, n => rangeLabel(n,'单')) },
  { key: 'peakOrders', label: '大促单量', hint: '大促最高一天的单量', type: 'single', options: options(peakRanges, n => rangeLabel(n,'单')) },
  { key: 'materialProviders', label: '纸箱耗材提供', type: 'multi', options: options(['客户提供','云仓提供']) },
  { key: 'erpSystems', label: '系统名称', type: 'multi', options: options(['旺店通','聚水潭','管家婆','管易云','万里牛','其他']), other: 'erpOther' },
  { key: 'couriers', label: '指定快递承运商', type: 'multi', options: options(COURIERS), other: 'courierOther' },
  { key: 'services', label: '增值服务', type: 'services' },
  { key: 'invoice', label: '是否开发票', type: 'single', options: options(['不开票','开普通发票','开增值税专票','待确认']) }
]
export const SUPPLEMENT = [
  { key:'itemsPerParcel', label:'平均每包商品件数', type:'number', unit:'件' },
  { key:'batchPercent', label:'同款批量发货占比', type:'digit', unit:'%' },
  { key:'weightMix', label:'常见包裹重量占比', type:'text', unit:'' },
  { key:'packingNote', label:'包装或作业备注', type:'text', unit:'' }
]
export function newQuoteForm() {
  const form = { serviceQuantities: {}, heavyWeightKg: '' }
  for (const f of QUOTE_FIELDS) { form[f.key] = ['multi','services'].includes(f.type) ? [] : ''; if (f.other) form[f.other] = '' }
  for (const f of SUPPLEMENT) form[f.key] = ''
  return form
}
function numberInput(value, label, valid) {
  const n = Number(value)
  if (!Number.isFinite(n) || !valid(n)) throw new Error('请检查' + label)
  return n
}
export function quotePayload(form) {
  const result = {}
  for (const f of QUOTE_FIELDS) {
    const val = form[f.key]
    if (val === '' || val == null || Array.isArray(val) && !val.length) continue
    result[f.key] = f.type === 'decimal' ? numberInput(val, f.label, n => n >= 0) : Array.isArray(val) ? [...val] : val
    if (f.other && val.includes('其他')) {
      const other = (form[f.other] || '').trim()
      if (!other) throw new Error('请补充其他' + f.label)
      result[f.other] = other
    }
  }
  if (form.weightRanges.includes('>3') && form.heavyWeightKg !== '') result.heavyWeightKg = numberInput(form.heavyWeightKg, '超过 3 kg 的常见重量', n => n > 3)
  for (const g of SERVICE_GROUPS) {
    const quantity = (form.serviceQuantities[g.key] || '').trim()
    if (quantity && g.values.some(v => form.services.includes(v))) (result.serviceQuantities ||= {})[g.key] = quantity
  }
  for (const f of SUPPLEMENT) {
    const val = String(form[f.key] ?? '').trim()
    if (!val) continue
    if (f.key === 'itemsPerParcel') result[f.key] = numberInput(val, f.label, n => Number.isInteger(n) && n > 0)
    else if (f.key === 'batchPercent') result[f.key] = numberInput(val, f.label, n => n >= 0 && n <= 100)
    else result[f.key] = val
  }
  if (!Object.keys(result).length) throw new Error('请至少填写一项报价需求')
  return result
}
export function quoteRows(r = {}) {
  const rows = []
  for (const f of QUOTE_FIELDS) {
    const val = r[f.key]
    if (val == null || val === '' || Array.isArray(val) && !val.length) continue
    const values = Array.isArray(val) ? val : [val]
    const display = values.map(v => v === '其他' && f.other && r[f.other] ? '其他（' + r[f.other] + '）' : f.options?.find(o => o.value === v)?.label || v)
    rows.push({ label: f.label, value: display.join('、') + (f.unit || '') })
    if (f.key === 'weightRanges' && r.heavyWeightKg != null) rows.push({label:'超过 3 kg 的常见重量',value:r.heavyWeightKg + ' kg'})
    if (f.key === 'services') for (const g of SERVICE_GROUPS) {
      if (r.serviceQuantities?.[g.key]) rows.push({ label:g.label + '月用量',value:r.serviceQuantities[g.key] })
    }
  }
  for (const f of SUPPLEMENT) if (r[f.key] != null && r[f.key] !== '') rows.push({label:f.label,value:String(r[f.key]) + f.unit})
  return rows
}
