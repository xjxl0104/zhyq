<template>
  <el-select
    :model-value="normalizedValue"
    filterable
    remote
    reserve-keyword
    :clearable="clearable"
    :disabled="disabled"
    :placeholder="placeholder"
    :remote-method="searchPromoters"
    :loading="loading"
    class="promoter-select"
    @focus="ensureInitialLoaded"
    @update:model-value="onUpdate"
    @change="onChange"
  >
    <el-option
      v-for="p in displayOptions"
      :key="p.id"
      :value="Number(p.id)"
      :label="formatPromoterLabel(p)"
    >
      <div class="promoter-option">
        <span class="promoter-main">{{ p.name || '未命名伙伴' }} · {{ maskPhone(p.phone) }}</span>
        <span class="promoter-meta">{{ p.positionCode || 'P1' }} · {{ p.inviteCode || '无邀请码' }}</span>
      </div>
    </el-option>
  </el-select>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { mktPromoterApi } from '@/api/marketing'

const props = defineProps({
  modelValue: { type: [Number, String], default: null },
  placeholder: { type: String, default: '搜索姓名、手机号或邀请码' },
  clearable: { type: Boolean, default: true },
  disabled: { type: Boolean, default: false },
  excludeId: { type: [Number, String], default: null }
})

const emit = defineEmits(['update:modelValue', 'change'])

const options = ref([])
const selectedCache = ref(null)
const loading = ref(false)
let initialized = false
let searchSeq = 0
let detailSeq = 0
let disposed = false

const normalizedValue = computed(() => {
  if (props.modelValue === null || props.modelValue === undefined || props.modelValue === '') return null
  const num = Number(props.modelValue)
  return Number.isFinite(num) && num > 0 ? num : null
})

const maskPhone = (phone) => {
  if (!phone) return '未绑手机'
  const text = String(phone)
  return text.length === 11 ? `${text.slice(0, 3)}****${text.slice(7)}` : text
}

function formatPromoterLabel(p) {
  if (!p) return ''
  return `${p.name || '未命名伙伴'} · ${maskPhone(p.phone)} · ${p.positionCode || 'P1'} · ${p.inviteCode || '无邀请码'}`
}

const displayOptions = computed(() => {
  const excluded = props.excludeId != null && props.excludeId !== '' ? Number(props.excludeId) : null
  const merged = [...options.value]
  if (selectedCache.value && !merged.some(item => Number(item.id) === Number(selectedCache.value.id))) {
    merged.unshift(selectedCache.value)
  }
  return excluded == null ? merged : merged.filter(item => Number(item.id) !== excluded)
})

async function searchPromoters(keyword = '') {
  const current = ++searchSeq
  loading.value = true
  try {
    const res = await mktPromoterApi.page({
      pageNo: 1,
      pageSize: 20,
      keyword: String(keyword || '').trim() || undefined
    })
    if (!disposed && current === searchSeq) {
      initialized = true
      options.value = Array.isArray(res?.records) ? res.records : []
    }
  } catch {
    if (!disposed && current === searchSeq) options.value = []
  } finally {
    if (!disposed && current === searchSeq) loading.value = false
  }
}

function ensureInitialLoaded() {
  if (!initialized && !loading.value) {
    searchPromoters('')
  }
}

async function ensureSelectedOption(id) {
  if (!id) {
    selectedCache.value = null
    return
  }
  const hit = options.value.find(item => Number(item.id) === Number(id))
  if (hit) {
    selectedCache.value = hit
    return
  }
  const current = ++detailSeq
  try {
    const detail = await mktPromoterApi.get(id)
    if (!disposed && current === detailSeq && detail) {
      selectedCache.value = detail
    }
  } catch {
    if (!disposed && current === detailSeq) {
      selectedCache.value = { id, name: `伙伴 #${id}`, phone: '', positionCode: '—', inviteCode: '—' }
    }
  }
}

function onUpdate(val) {
  const next = val === null || val === undefined || val === '' ? null : Number(val)
  if (next) {
    const found = displayOptions.value.find(item => Number(item.id) === next)
    if (found) selectedCache.value = found
  } else {
    selectedCache.value = null
  }
  emit('update:modelValue', next)
}

function onChange(val) {
  const next = val === null || val === undefined || val === '' ? null : Number(val)
  emit('change', next)
}

watch(normalizedValue, (id) => {
  ensureSelectedOption(id)
}, { immediate: true })

onBeforeUnmount(() => {
  disposed = true
  searchSeq++
  detailSeq++
})

defineExpose({ searchPromoters, formatPromoterLabel })
</script>

<style scoped>
.promoter-select { width: 100%; }
.promoter-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
}
.promoter-main {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.promoter-meta {
  flex: none;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
