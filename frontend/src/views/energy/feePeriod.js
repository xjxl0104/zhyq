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
