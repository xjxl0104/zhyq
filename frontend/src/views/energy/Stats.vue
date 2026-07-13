<template>
  <div class="page-container">
    <!-- 概览卡:电 / 水 两组 -->
    <div class="ov-groups">
      <div class="ov-group">
        <div class="ov-head">
          <el-icon class="ov-ic elec"><Lightning /></el-icon>
          <span class="ov-title">用电概览</span>
          <span class="ov-unit">单位:kWh / 元</span>
        </div>
        <div class="stat-cards">
          <div class="stat-card">
            <div class="num" style="color:#4f46e5">{{ fmtNum(elec.today) }}</div>
            <div class="label">今日用量</div>
            <div class="sub">费用 ¥{{ fmtMoney(elec.todayFee) }}</div>
          </div>
          <div class="stat-card">
            <div class="num" style="color:#2563eb">{{ fmtNum(elec.month) }}</div>
            <div class="label">当月用量</div>
            <div class="sub">费用 ¥{{ fmtMoney(elec.monthFee) }}</div>
          </div>
          <div class="stat-card">
            <div class="num" style="color:#0ea5e9">{{ fmtNum(elec.year) }}</div>
            <div class="label">当年用量</div>
            <div class="sub">费用 ¥{{ fmtMoney(elec.yearFee) }}</div>
          </div>
        </div>
      </div>

      <div class="ov-group">
        <div class="ov-head">
          <el-icon class="ov-ic water"><Coordinate /></el-icon>
          <span class="ov-title">用水概览</span>
          <span class="ov-unit">单位:吨 / 元</span>
        </div>
        <div class="stat-cards">
          <div class="stat-card">
            <div class="num" style="color:#06b6d4">{{ fmtNum(water.today) }}</div>
            <div class="label">今日用量</div>
            <div class="sub">费用 ¥{{ fmtMoney(water.todayFee) }}</div>
          </div>
          <div class="stat-card">
            <div class="num" style="color:#0891b2">{{ fmtNum(water.month) }}</div>
            <div class="label">当月用量</div>
            <div class="sub">费用 ¥{{ fmtMoney(water.monthFee) }}</div>
          </div>
          <div class="stat-card">
            <div class="num" style="color:#0e7490">{{ fmtNum(water.year) }}</div>
            <div class="label">当年用量</div>
            <div class="sub">费用 ¥{{ fmtMoney(water.yearFee) }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 双图:用量趋势 + 费用趋势 -->
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="12">
        <el-card shadow="never" header="近6月用量趋势(电/水)">
          <div ref="usageRef" style="height: 300px"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" header="近6月费用趋势(电/水)">
          <div ref="feeRef" style="height: 300px"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 当月表计用量排行 -->
    <el-card shadow="never" header="当月表计用量排行 Top10" style="margin-top: 16px">
      <div v-if="ranks.length" class="rank-list">
        <div v-for="(r, i) in ranks" :key="r.code" class="rank-row">
          <span class="rank-no" :class="{ top: i < 3 }">{{ i + 1 }}</span>
          <div class="rank-info">
            <div class="rank-name">{{ r.name }}
              <el-tag size="small" :type="tagType(r.energyType)" effect="plain">{{ r.energyType }}</el-tag>
            </div>
            <div class="rank-code">{{ r.code }}</div>
          </div>
          <div class="rank-bar-wrap">
            <div class="rank-bar" :style="{ width: barWidth(r.usage), background: barColor(r.energyType) }"></div>
          </div>
          <span class="rank-val">{{ fmtNum(r.usage) }}</span>
        </div>
      </div>
      <el-empty v-else description="本月暂无抄表数据" :image-size="70" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { energyStatsApi } from '@/api/energy'

const elec = reactive({}), water = reactive({})
const ranks = ref([])
const usageRef = ref(), feeRef = ref()

const fmtNum = (v) => Number(v || 0).toLocaleString('zh-CN', { maximumFractionDigits: 1 })
const fmtMoney = (v) => Number(v || 0).toLocaleString('zh-CN', { maximumFractionDigits: 0 })

const tagType = (t) => ({ '电': 'warning', '水': 'primary', '燃气': 'danger', '热力': 'success' }[t] || 'info')
const barColor = (t) => (t === '水' ? '#06b6d4' : '#4f46e5')

let maxUsage = 0
const barWidth = (v) => {
  if (!maxUsage) return '0%'
  return Math.max(6, Math.round(Number(v || 0) / maxUsage * 100)) + '%'
}

onMounted(async () => {
  const ov = await energyStatsApi.overview()
  Object.assign(elec, ov.electric || {})
  Object.assign(water, ov.water || {})

  ranks.value = await energyStatsApi.meterRank() || []
  maxUsage = ranks.value.reduce((m, r) => Math.max(m, Number(r.usage || 0)), 0)

  await nextTick()

  const trend = await energyStatsApi.trend(6)
  const axisBase = {
    xAxis: { type: 'category', data: trend.months, axisLine: { lineStyle: { color: '#e0e3e9' } }, axisLabel: { color: '#9aa1ac' } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#f0f2f5' } }, axisLabel: { color: '#9aa1ac' } },
    grid: { left: 60, right: 20, top: 40, bottom: 30 }
  }

  echarts.init(usageRef.value).setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['用电(kWh)', '用水(吨)'] },
    ...axisBase,
    series: [
      { name: '用电(kWh)', type: 'line', data: trend.electric, smooth: true, symbolSize: 7, areaStyle: { color: 'rgba(79,70,229,0.10)' }, lineStyle: { width: 3 }, itemStyle: { color: '#4f46e5' } },
      { name: '用水(吨)', type: 'line', data: trend.water, smooth: true, symbolSize: 7, areaStyle: { color: 'rgba(6,182,212,0.10)' }, lineStyle: { width: 3 }, itemStyle: { color: '#06b6d4' } }
    ]
  })

  echarts.init(feeRef.value).setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['电费', '水费'] },
    ...axisBase,
    series: [
      { name: '电费', type: 'bar', data: trend.electricFee, itemStyle: { color: '#4f46e5', borderRadius: [4, 4, 0, 0] }, barWidth: '28%' },
      { name: '水费', type: 'bar', data: trend.waterFee, itemStyle: { color: '#06b6d4', borderRadius: [4, 4, 0, 0] }, barWidth: '28%' }
    ]
  })
})
</script>

<style scoped>
.ov-groups { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.ov-group { background: #fff; border-radius: 8px; padding: 16px 20px; box-shadow: 0 1px 4px rgba(0, 21, 41, 0.06); }
.ov-head { display: flex; align-items: center; gap: 8px; margin-bottom: 14px; }
.ov-ic { font-size: 20px; }
.ov-ic.elec { color: #4f46e5; }
.ov-ic.water { color: #06b6d4; }
.ov-title { font-size: 15px; font-weight: 600; color: #303133; }
.ov-unit { margin-left: auto; font-size: 12px; color: #9aa1ac; }
.stat-cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.stat-card { padding: 14px 10px; background: #f8fafc; border-radius: 8px; text-align: center; }
.stat-card .num { font-size: 24px; font-weight: 700; }
.stat-card .label { font-size: 13px; color: #6b7280; margin-top: 4px; }
.stat-card .sub { font-size: 12px; color: #9aa1ac; margin-top: 4px; }

.rank-list { display: flex; flex-direction: column; gap: 12px; }
.rank-row { display: flex; align-items: center; gap: 12px; }
.rank-no { flex-shrink: 0; width: 24px; height: 24px; line-height: 24px; text-align: center; border-radius: 6px; font-size: 13px; font-weight: 600; color: #6b7280; background: #f0f2f5; }
.rank-no.top { color: #fff; background: #4f46e5; }
.rank-info { width: 220px; flex-shrink: 0; }
.rank-name { font-size: 14px; color: #303133; display: flex; align-items: center; gap: 6px; }
.rank-code { font-size: 12px; color: #9aa1ac; margin-top: 2px; }
.rank-bar-wrap { flex: 1; height: 12px; background: #f0f2f5; border-radius: 6px; overflow: hidden; }
.rank-bar { height: 100%; border-radius: 6px; transition: width 0.4s; }
.rank-val { flex-shrink: 0; width: 90px; text-align: right; font-size: 14px; font-weight: 600; color: #303133; }
</style>
