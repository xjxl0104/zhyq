<template>
  <div class="page-container">
    <!-- 合并年月表 + 月度趋势图切换 -->
    <template v-if="!month">
      <FeeMonthPicker ref="picker" kind="combined" @select="openMonth" @year="(y) => (chartYear = y)" />
      <div class="chart-switch-bar">
        <el-radio-group v-model="chartKind" size="small">
          <el-radio-button value="electric">电费趋势</el-radio-button>
          <el-radio-button value="water">水费趋势</el-radio-button>
          <el-radio-button value="garbage">垃圾处理费趋势</el-radio-button>
        </el-radio-group>
      </div>
      <FeeCharts ref="charts" :kind="chartKind" :year="chartYear" />
    </template>

    <!-- 选定月份明细：顶部一键切换「电费 / 水费 / 垃圾处理费」 -->
    <template v-else>
      <div class="detail-head">
        <el-button @click="backToBoard"><el-icon><ArrowLeft /></el-icon>返回年月表</el-button>
        <h3>{{ monthLabel }}</h3>
        <el-radio-group :model-value="activeTab" class="fee-tab-switch" @update:model-value="switchTab">
          <el-radio-button value="electric">电费</el-radio-button>
          <el-radio-button value="water">水费</el-radio-button>
          <el-radio-button value="garbage">垃圾处理费</el-radio-button>
        </el-radio-group>
        <el-tag v-if="locked" type="success">已确认出账（冻结，只读）</el-tag>
        <el-tag v-else-if="currentPeriod?.noticeStale" type="warning">读数、总量或总额有改动，通知单需重新生成</el-tag>
      </div>

      <UtilityFeeSheet
        v-if="activeTab !== 'garbage'"
        ref="utilityRef"
        embedded
        :embedded-month="month"
        :embedded-energy-type="activeTab === 'electric' ? '电' : '水'"
        @period-change="(p) => (currentPeriod = p)"
      />
      <GarbageFee
        v-else
        ref="garbageRef"
        embedded
        :embedded-month="month"
        @period-change="(p) => (currentPeriod = p)"
      />
    </template>
  </div>
</template>

<script setup>
import { computed, onActivated, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import FeeMonthPicker from './components/FeeMonthPicker.vue'
import FeeCharts from './components/FeeCharts.vue'
import UtilityFeeSheet from './UtilityFeeSheet.vue'
import GarbageFee from './GarbageFee.vue'

const route = useRoute()
const normalizeTab = (val) => {
  const v = String(val || '').toLowerCase()
  if (v === 'water' || v === 'garbage' || v === 'electric') return v
  return 'electric'
}

const month = ref(null)
const activeTab = ref(normalizeTab(route.query.tab))
const chartKind = ref(activeTab.value)
const chartYear = ref(new Date().getFullYear())
const currentPeriod = ref(null)
const picker = ref(null)
const charts = ref(null)
const utilityRef = ref(null)
const garbageRef = ref(null)

const locked = computed(() => currentPeriod.value?.status === 'CONFIRMED')
const monthLabel = computed(() => (month.value ? `${month.value.slice(0, 4)}年${Number(month.value.slice(5))}月` : ''))

function openMonth(ym) {
  month.value = ym
}

async function switchTab(next) {
  if (!next || next === activeTab.value) return
  if (activeTab.value !== 'garbage' && utilityRef.value?.hasDirty?.()) {
    try {
      await ElMessageBox.confirm('当前有未保存的表计读数，切换页签会放弃未保存修改，是否继续？', '切换费用项', {
        type: 'warning', confirmButtonText: '放弃并切换', cancelButtonText: '留在本页'
      })
    } catch {
      return
    }
  }
  activeTab.value = next
  chartKind.value = next
}

async function backToBoard() {
  if (activeTab.value !== 'garbage' && utilityRef.value?.hasDirty?.()) {
    try {
      await ElMessageBox.confirm('当前有未保存的表计读数，返回年月表会放弃未保存修改，是否继续？', '返回年月表', {
        type: 'warning', confirmButtonText: '放弃并返回', cancelButtonText: '继续编辑'
      })
    } catch {
      return
    }
  }
  month.value = null
  currentPeriod.value = null
}

watch(() => route.query.tab, (q) => {
  if (!q) return
  const next = normalizeTab(q)
  activeTab.value = next
  chartKind.value = next
})

let firstActivation = true
onActivated(async () => {
  if (firstActivation) { firstActivation = false; return }
  if (!month.value) {
    picker.value?.refresh()
    charts.value?.refresh()
  } else if (activeTab.value !== 'garbage') {
    await utilityRef.value?.refresh?.()
  } else {
    await garbageRef.value?.refresh?.()
  }
})
</script>

<style scoped>
.chart-switch-bar { display: flex; justify-content: flex-end; margin: 12px 0 -4px; }
.detail-head { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; flex-wrap: wrap; }
.detail-head h3 { margin: 0; font-size: 16px; }
.fee-tab-switch :deep(.el-radio-button__inner) { font-weight: 600; padding: 8px 18px; }
</style>
