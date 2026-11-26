<template>
  <div class="fmp">
    <div class="fmp-bar">
      <el-button circle :icon="ArrowLeft" aria-label="上一年" @click="shift(-1)" />
      <div class="fmp-title">
        <strong>{{ title }}</strong>
        <span>{{ subtitle }}</span>
      </div>
      <slot name="actions" />
      <el-button v-if="center !== thisYear" text type="primary" @click="center = thisYear">回到 {{ thisYear }} 年</el-button>
      <el-button circle :icon="ArrowRight" aria-label="下一年" @click="shift(1)" />
    </div>

    <div v-loading="loading" class="fmp-years">
      <section v-for="y in years" :key="y" class="year" :class="{ center: y === center }">
        <header>{{ y }} 年</header>
        <div class="months">
          <button v-for="m in 12" :key="m" type="button" class="month"
                  :class="{ now: isNow(y, m), done: cell(y, m)?.status === 'CONFIRMED', empty: !cell(y, m) }"
                  @click="$emit('select', key(y, m))">
            <span class="m-label">
              {{ m }}月
              <el-tag v-if="cell(y, m)?.status === 'CONFIRMED'" size="small" type="success" effect="plain">已出账</el-tag>
              <el-tag v-else-if="cell(y, m)?.noticeStale" size="small" type="warning" effect="plain">待重算</el-tag>
            </span>
            <template v-if="cell(y, m)">
              <span class="m-line">{{ lines(cell(y, m))[0] }}</span>
              <span class="m-line fee">{{ lines(cell(y, m))[1] }}</span>
              <span v-if="lines(cell(y, m))[2]" class="m-sub">{{ lines(cell(y, m))[2] }}</span>
              <span class="m-doc" :class="{ ok: fileCount(cell(y, m)) > 0 }">
                {{ fileCount(cell(y, m)) > 0 ? `单据已留存 ${fileCount(cell(y, m))} 份` : `${cell(y, m).contractCount || 0} 份在租合同` }}
              </span>
            </template>
            <span v-else class="m-line muted">无在租合同</span>
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { leaseFeeApi } from '@/api/energy'
import { money } from '../feePeriod'

// kind: rent 租金 | property 物业费 | summary 租金与物业管理费汇总
const props = defineProps({ kind: { type: String, required: true } })
const emit = defineEmits(['select', 'year'])

const now = new Date()
const thisYear = now.getFullYear()
const center = ref(thisYear)
const years = computed(() => [center.value - 1, center.value, center.value + 1])
const loading = ref(false)
const data = ref({})

const TITLES = {
  rent: '租金 · 年月表',
  property: '物业费 · 年月表',
  summary: '租金、物业管理费 · 年月表'
}
const SUBTITLES = {
  rent: '点击月份查看当月各租户租金明细与减免调整；按合同起租日/递增/免租规则自动推算',
  property: '点击月份查看当月各租户物业管理费明细（统一口径 2元/㎡/月 起，随合同周期递增）',
  summary: '租金与物业管理费收取单位独立，点击月份查看分租户汇总并分别生成/打印通知单'
}
const title = computed(() => TITLES[props.kind] || '租金与物业管理费 · 年月表')
const subtitle = computed(() => SUBTITLES[props.kind] || '')

const key = (y, m) => `${y}-${String(m).padStart(2, '0')}`
const cell = (y, m) => data.value[key(y, m)]
const isNow = (y, m) => y === now.getFullYear() && m === now.getMonth() + 1
const shift = (n) => { center.value += n }

function fileCount(c) {
  if (!c) return 0
  return { rent: c.rentFiles, property: c.propertyFiles, summary: (c.rentFiles || 0) + (c.propertyFiles || 0) }[props.kind] || 0
}

function lines(c) {
  switch (props.kind) {
    case 'rent':
      return [c.rentNoticeCount ? `${c.rentNoticeCount} 张租金通知单` : `${c.contractCount || 0} 份合同`, `租金 ¥${money(c.rentTotal)}`]
    case 'property':
      return [c.propertyNoticeCount ? `${c.propertyNoticeCount} 张物业通知单` : `${c.contractCount || 0} 份合同`, `物业 ¥${money(c.propertyTotal)}`]
    default:
      return [`租金 ¥${money(c.rentTotal)}`, `物业 ¥${money(c.propertyTotal)}`, `合计 ¥${money(c.grandTotal)}`]
  }
}

async function load() {
  loading.value = true
  try {
    const lists = await Promise.all(years.value.map((y) => leaseFeeApi.overview(y)))
    const next = {}
    lists.flat().forEach((c) => { next[c.period] = c })
    data.value = next
  } finally {
    loading.value = false
  }
}
watch(center, load, { immediate: true })
watch(center, (y) => emit('year', y), { immediate: true })
defineExpose({ refresh: load })
</script>

<style scoped>
.fmp-bar { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; flex-wrap: wrap; }
.fmp-title { flex: 1; display: flex; align-items: baseline; gap: 12px; flex-wrap: wrap; }
.fmp-title strong { font-size: 16px; }
.fmp-title span { font-size: 12px; color: var(--el-text-color-secondary); }
.fmp-years { display: grid; grid-template-columns: 1fr 1.25fr 1fr; gap: 16px; align-items: start; min-height: 200px; }
.year { border: 1px solid var(--el-border-color-lighter); border-radius: 10px; padding: 12px; background: var(--el-fill-color-blank); opacity: 0.88; }
.year.center { border-color: var(--el-color-primary-light-5); box-shadow: 0 2px 14px rgba(64, 128, 255, 0.12); opacity: 1; }
.year header { font-weight: 700; font-size: 15px; margin-bottom: 10px; text-align: center; }
.year.center header { font-size: 18px; color: var(--el-color-primary); }
.months { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
.month {
  display: flex; flex-direction: column; gap: 3px; text-align: left; cursor: pointer;
  padding: 8px 9px; border-radius: 8px; border: 1px solid var(--el-border-color);
  background: var(--el-fill-color-lighter); color: var(--el-text-color-primary); font: inherit; min-width: 0;
  transition: border-color 0.15s, background 0.15s, transform 0.15s;
}
.month:hover { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); transform: translateY(-1px); }
.month:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 1px; }
.month.now { border-color: var(--el-color-primary); }
.month.done { background: var(--el-color-success-light-9); border-color: var(--el-color-success-light-5); }
.month.empty { background: transparent; border-style: dashed; }
.m-label { font-weight: 700; display: flex; align-items: center; gap: 5px; flex-wrap: wrap; }
.m-line { font-size: 12px; line-height: 1.35; font-variant-numeric: tabular-nums; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.m-line.fee { font-weight: 600; color: var(--el-color-danger); }
.m-sub { font-size: 11px; font-weight: 600; color: var(--el-color-primary); font-variant-numeric: tabular-nums; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.m-line.muted { color: var(--el-text-color-placeholder); }
.m-doc { font-size: 11px; color: var(--el-text-color-placeholder); }
.m-doc.ok { color: var(--el-color-success); }
@media (max-width: 1100px) {
  .fmp-years { grid-template-columns: 1fr; }
}
</style>
