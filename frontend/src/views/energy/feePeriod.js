import { onMounted, reactive, ref, watch } from 'vue'
import { feePeriodApi } from '@/api/energy'

export const money = (v) => Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
export const num = (v, digits = 2) => (v == null || v === '' ? '-' : Number(v).toLocaleString('zh-CN', { maximumFractionDigits: digits }))

export function currentMonth() {
  const d = new Date()
  const m = d.getMonth() === 0 ? 12 : d.getMonth()
  const y = d.getMonth() === 0 ? d.getFullYear() - 1 : d.getFullYear()
  return `${y}-${String(m).padStart(2, '0')}`
}

export function periodRange(p) {
  if (!p?.periodStart || !p?.periodEnd) return p?.period || ''
  const f = (s) => s.replace(/-0?/g, '.')
  return `${f(p.periodStart)}~${f(p.periodEnd)}`
}

/** 四个页面共用:按月份打开账期,月份记在 localStorage 里,切页面不丢 */
export function useFeePeriod(onOpened) {
  const KEY = 'zhyq_fee_period_month'
  const month = ref(localStorage.getItem(KEY) || currentMonth())
  const period = ref(null)
  const loading = ref(false)
  const state = reactive({ saving: false })

  async function open() {
    if (!month.value) return
    loading.value = true
    try {
      period.value = await feePeriodApi.open(month.value)
      localStorage.setItem(KEY, month.value)
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
  watch(month, open)
  onMounted(open)
  return { month, period, loading, state, open, reload, save }
}

export function downloadBlob(response, filename) {
  const url = URL.createObjectURL(response.data)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}
