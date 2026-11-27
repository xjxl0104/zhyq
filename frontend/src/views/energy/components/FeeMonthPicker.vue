<template>
  <div class="fmp">
    <div class="fmp-bar">
      <div class="fmp-title">
        <strong>{{ title }}</strong>
        <el-tag size="small" type="primary" effect="plain">{{ year }} 年</el-tag>
        <span>点击月份进入明细；按钮展示当月合计</span>
      </div>
      <div class="fmp-controls">
        <div class="year-nav">
          <el-button size="small" :icon="ArrowLeft" aria-label="上一年" @click="shift(-1)" />
          <el-date-picker
            v-model="yearStr"
            type="year"
            size="small"
            format="YYYY年"
            value-format="YYYY"
            :clearable="false"
            style="width: 96px"
          />
          <el-button size="small" :icon="ArrowRight" aria-label="下一年" @click="shift(1)" />
        </div>
        <el-date-picker
          v-model="jumpMonth"
          type="month"
          size="small"
          format="YYYY年MM月"
          value-format="YYYY-MM"
          placeholder="选择年月直达"
          clearable
          style="width: 138px"
          @change="onJumpMonth"
        />
        <el-button v-if="year !== thisYear" size="small" text type="primary" @click="year = thisYear">回到今年</el-button>
      </div>
    </div>

    <div v-loading="loading" class="months">
      <button
        v-for="m in 12"
        :key="m"
        type="button"
        class="month"
        :class="{ now: isNow(year, m), done: cell(year, m)?.status === 'CONFIRMED', empty: !cell(year, m) }"
        @click="$emit('select', key(year, m))"
      >
        <span class="m-label">
          <span>{{ m }}月</span>
          <el-tag v-if="cell(year, m)?.status === 'CONFIRMED'" size="small" type="success" effect="plain">已出账</el-tag>
          <el-tag v-else-if="cell(year, m)?.noticeStale" size="small" type="warning" effect="plain">待重算</el-tag>
        </span>
        <template v-if="cell(year, m)">
          <span class="m-line">{{ lines(cell(year, m))[0] }}</span>
          <span class="m-line fee">{{ lines(cell(year, m))[1] }}</span>
          <span class="m-doc" :class="{ ok: fileCount(cell(year, m)) > 0 }">
            {{ fileCount(cell(year, m)) > 0 ? `账单已留存 ${fileCount(cell(year, m))} 份` : '账单未上传' }}
          </span>
        </template>
        <span v-else class="m-line muted">未录入</span>
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { feePeriodApi } from '@/api/energy'
import { money, num } from '../feePeriod'

// kind: electric 电费 | water 水费 | garbage 垃圾处理费 | summary 缴费汇总
const props = defineProps({ kind: { type: String, required: true } })
const emit = defineEmits(['select', 'year'])

const now = new Date()
const thisYear = now.getFullYear()
const year = ref(thisYear)
const jumpMonth = ref('')
const loading = ref(false)
const data = ref({})

const yearStr = computed({
  get: () => String(year.value),
  set: (v) => {
    const n = Number(v)
    if (Number.isFinite(n) && n >= 2000 && n <= 2100) year.value = n
  }
})

const TITLES = { electric: '电费 · 年月表', water: '水费 · 年月表', garbage: '垃圾处理费 · 年月表', summary: '水电费、垃圾处理费 · 年月表' }
const title = computed(() => TITLES[props.kind] || '年月表')

const key = (y, m) => `${y}-${String(m).padStart(2, '0')}`
const cell = (y, m) => data.value[key(y, m)]
const isNow = (y, m) => y === now.getFullYear() && m === now.getMonth() + 1
const shift = (n) => { year.value += n }

function onJumpMonth(val) {
  if (!val) return
  const y = Number(String(val).slice(0, 4))
  if (Number.isFinite(y) && y >= 2000 && y <= 2100) year.value = y
  jumpMonth.value = ''
  emit('select', val)
}

function fileCount(c) {
  if (!c) return 0
  return { electric: c.electricFiles, water: c.waterFiles, garbage: c.garbageFiles, summary: c.electricFiles + c.waterFiles + c.garbageFiles }[props.kind] || 0
}

// 每个按钮下面两行:总量 + 费用
function lines(c) {
  switch (props.kind) {
    case 'electric': return [`总用电 ${num(c.electricUsage, 2)} 度`, `电费 ¥${money(c.electricFee)}`]
    case 'water': return [`总用水 ${num(c.waterUsage, 2)} m³`, `水费 ¥${money(c.waterFee)}`]
    case 'garbage': return [`垃圾总额 ¥${money(c.garbageTotal)}`, `已分摊 ¥${money(c.garbageFee)}`]
    default: return [c.noticeCount ? `${c.noticeCount} 张通知单` : '未生成通知单', c.totalDue == null ? '应收 -' : `应收 ¥${money(c.totalDue)}`]
  }
}

async function load() {
  loading.value = true
  try {
    const list = await feePeriodApi.overview(year.value)
    const next = {}
    ;(list || []).forEach((c) => { next[c.period] = c })
    data.value = next
  } finally {
    loading.value = false
  }
}
watch(year, load, { immediate: true })
watch(year, (y) => emit('year', y), { immediate: true })
defineExpose({ refresh: load })
</script>

<style scoped>
.fmp {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  padding: 12px 14px;
  background: var(--el-fill-color-blank);
}
.fmp-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.fmp-title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.fmp-title strong { font-size: 15px; }
.fmp-title span { font-size: 12px; color: var(--el-text-color-secondary); }
.fmp-controls {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.year-nav {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.months {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 8px;
}
.month {
  display: flex;
  flex-direction: column;
  gap: 3px;
  text-align: left;
  cursor: pointer;
  padding: 8px 10px;
  border-radius: 8px;
  border: 1px solid var(--el-border-color);
  background: var(--el-fill-color-lighter);
  color: var(--el-text-color-primary);
  font: inherit;
  min-width: 0;
  transition: border-color 0.15s, background 0.15s, transform 0.15s;
}
.month:hover { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); transform: translateY(-1px); }
.month:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 1px; }
.month.now { border-color: var(--el-color-primary); box-shadow: 0 0 0 1px var(--el-color-primary-light-5) inset; }
.month.done { background: var(--el-color-success-light-9); border-color: var(--el-color-success-light-5); }
.month.empty { background: transparent; border-style: dashed; }
.m-label { font-weight: 700; font-size: 13px; display: flex; align-items: center; justify-content: space-between; gap: 4px; }
.m-line { font-size: 12px; line-height: 1.35; font-variant-numeric: tabular-nums; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.m-line.fee { font-weight: 600; color: var(--el-color-danger); }
.m-line.muted { color: var(--el-text-color-placeholder); }
.m-doc { font-size: 11px; color: var(--el-text-color-placeholder); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.m-doc.ok { color: var(--el-color-success); }
@media (max-width: 1200px) {
  .months { grid-template-columns: repeat(4, minmax(0, 1fr)); }
}
@media (max-width: 760px) {
  .months { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
