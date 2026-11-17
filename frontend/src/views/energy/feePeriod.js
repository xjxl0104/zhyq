import { reactive, ref, watch } from 'vue'
import { feePeriodApi } from '@/api/energy'

export const money = (v) => Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
export const num = (v, digits = 2) => (v == null || v === '' ? '-' : Number(v).toLocaleString('zh-CN', { maximumFractionDigits: digits }))

/** 供应商账单(单据留存)的三种类型,与后端 sys_file.biz_type = energy_fee_{key} 对应 */
export const BILL_KINDS = {
  electric: { key: 'electric', label: '南网电费账单' },
  water: { key: 'water', label: '自来水公司账单' },
  garbage: { key: 'garbage', label: '垃圾清理供应商账单' }
}

export function periodRange(p) {
  if (!p?.periodStart || !p?.periodEnd) return p?.period || ''
  const f = (s) => s.replace(/-0?/g, '.')
  return `${f(p.periodStart)}~${f(p.periodEnd)}`
}

/**
 * 四个页面共用:先看年月表,点某个月才打开那个月的账期。
 * month 为空 = 显示年月表;有值 = 显示该月明细。
 */
export function useFeePeriod(onOpened) {
  const month = ref(null)
  const period = ref(null)
  const loading = ref(false)
  const state = reactive({ saving: false })

  async function open() {
    if (!month.value) {
      period.value = null
      return
    }
    loading.value = true
    try {
      period.value = await feePeriodApi.open(month.value)
      if (onOpened) await onOpened(period.value)
    } finally {
      loading.value = false
    }
  }
  async function reload() {
    if (!period.value) return
    period.value = await feePeriodApi.get(period.value.id)
  }
  async function save(patch) {
    if (!period.value) return
    state.saving = true
    try {
      period.value = await feePeriodApi.update({ ...period.value, ...patch })
    } finally {
      state.saving = false
    }
  }
  function back() {
    month.value = null
  }
  watch(month, open)
  return { month, period, loading, state, open, reload, save, back }
}

export function downloadBlob(response, filename) {
  const url = URL.createObjectURL(response.data)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

/** 「2层A区」→「2层」,「室外」→「室外」;用来给每层上深浅色 */
export function floorOf(text) {
  const m = /(\d+)\s*层/.exec(text || '')
  return m ? `${Number(m[1])}层` : (String(text || '').trim() || '其他')
}
/** 一张通知单可能跨好几层(「1层B区、2层A~C区、3层A~C区」),返回它涉及的所有楼层 */
export function floorsOf(text) {
  const all = [...String(text || '').matchAll(/(\d+)\s*层/g)].map((m) => `${Number(m[1])}层`)
  return all.length ? [...new Set(all)] : [String(text || '').trim() || '其他']
}
const floorOrder = (f) => { const m = /^(\d+)层$/.exec(f); return m ? Number(m[1]) : 1000 }

/** 去重后的公司名 / 楼层(楼层按数字从小到大),给检索下拉用 */
export function filterOptions(rows, companyOf, unitOf) {
  const companies = [...new Set(rows.map(companyOf).filter(Boolean))].sort((a, b) => a.localeCompare(b, 'zh-CN'))
  const floors = [...new Set(rows.flatMap((r) => floorsOf(unitOf(r))))].sort((a, b) => floorOrder(a) - floorOrder(b) || a.localeCompare(b, 'zh-CN'))
  return { companies, floors }
}

/** 按公司、楼层筛选(通知单跨多层时,涉及该层就算命中);两个条件都为空就原样返回,保持同一个数组 */
export function applyFilter(rows, filter, companyOf, unitOf) {
  if (!filter.company && !filter.floor) return rows
  return rows.filter((r) => (!filter.company || companyOf(r) === filter.company) && (!filter.floor || floorsOf(unitOf(r)).includes(filter.floor)))
}

/** 交替深浅色:返回 el-table 的 row-class-name 用的函数。unitOf 取得相同的行算同一组,按出现顺序一组深一组浅 */
export function floorBandClass(rows, unitOf) {
  const bands = new Map()
  rows.forEach((r) => { const f = floorOf(unitOf(r)); if (!bands.has(f)) bands.set(f, bands.size % 2) })
  return (row) => `floor-band-${bands.get(floorOf(unitOf(row))) ?? 0}`
}

/** 公司全称太长,列头/图表用简称(与导出的 Excel 同一规则):去掉「有限公司」和开头的地名 */
export function shortName(name) {
  const s = String(name || '').replace(/(有限公司|股份有限公司|有限责任公司|公司)$/, '').replace(/^(广州|广东|深圳|佛山|中国)/, '').replace(/[（(].*?[)）]/g, '')
  return (s.length > 6 ? s.slice(0, 6) : s) || name
}
