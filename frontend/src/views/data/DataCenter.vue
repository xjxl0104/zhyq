<template>
  <div ref="boardRef" class="commerce-page data-center">
    <header class="commerce-page__header">
      <div>
        <h1 class="commerce-page__title">数据看板</h1>
        <p class="commerce-page__subtitle">经营数据与实时监控集中查看，告警每 15 秒、运营数据每分钟更新。</p>
      </div>
      <div class="commerce-page__actions">
        <span class="commerce-chip"><el-icon><Calendar /></el-icon>最近 6 个月</span>
        <!-- 刷新是否真的生效,光看数字看不出来(数字常常本来就没变)。给出更新时间 -->
        <span v-if="updatedAt" class="commerce-chip">更新于 {{ updatedAt }}</span>
        <el-button class="commerce-action" data-testid="fullscreen-dashboard" :aria-pressed="fullscreen" @click="toggleFullscreen">
          <el-icon><FullScreen /></el-icon><span>{{ fullscreen ? '退出全屏' : '全屏展示' }}</span>
        </el-button>
        <el-button class="commerce-action" type="primary" data-testid="refresh-dashboard" :loading="loading || refreshing" @click="refreshAll">
          <el-icon><Refresh /></el-icon><span>刷新数据</span>
        </el-button>
      </div>
    </header>

    <dl class="operations-summary" aria-label="园区运营指标">
      <div><dt>园区出租率</dt><dd>{{ room.rentRate || 0 }}<small>%</small></dd></div>
      <div><dt>在租房间 / 全部房源</dt><dd>{{ room.rented || 0 }} / {{ room.total || 0 }}</dd></div>
      <div><dt>在线设备 / 全部设备</dt><dd>{{ device.online || 0 }} / {{ device.total || 0 }}</dd></div>
      <div><dt>进行中工单</dt><dd>{{ other.workOrderOpen || 0 }}</dd></div>
    </dl>

    <div class="finance-kpis">
      <article v-for="item in financeCards" :key="item.key" class="finance-kpi commerce-card">
        <div class="finance-kpi__top">
          <span class="commerce-icon" :style="{ '--icon-color': item.color, '--icon-bg': item.soft }">
            <el-icon><component :is="item.icon" /></el-icon>
          </span>
          <span class="commerce-badge" :style="{ '--badge-color': item.color, '--badge-bg': item.soft }">{{ item.tag }}</span>
        </div>
        <strong>¥{{ fmtMoney(fin[item.key]) }}</strong>
        <span>{{ item.label }}</span>
      </article>
    </div>

    <div class="analytics-grid">
      <section class="commerce-card trend-card">
        <div class="commerce-card__head">
          <div>
            <h2 class="commerce-card__title">应收 / 实收趋势</h2>
            <p class="commerce-card__meta">近 6 个月现金回收表现</p>
          </div>
          <span class="legend-pills"><i class="receivable"></i>应收 <i class="received"></i>实收</span>
        </div>
        <div ref="trendRef" class="chart chart--trend" aria-label="应收实收趋势图"></div>
      </section>

      <section class="commerce-card room-card">
        <div class="commerce-card__head">
          <div>
            <h2 class="commerce-card__title">房源状态分布</h2>
            <p class="commerce-card__meta">全部空间的即时占用情况</p>
          </div>
          <span class="commerce-icon"><el-icon><OfficeBuilding /></el-icon></span>
        </div>
        <div class="room-chart-wrap">
          <div ref="roomRef" class="chart chart--room" aria-label="房源状态分布图"></div>
          <div class="room-total"><strong>{{ roomTotal }}</strong><span>房源总数</span></div>
        </div>
      </section>

      <section class="commerce-card income-card">
        <div class="commerce-card__head">
          <div>
            <h2 class="commerce-card__title">经营收入来源</h2>
            <p class="commerce-card__meta">仅统计有效账单；应收含滞纳金</p>
          </div>
          <span class="commerce-badge"><el-icon><DataLine /></el-icon>收入结构</span>
        </div>
        <div class="income-layout">
          <div class="income-metrics">
            <div class="income-item">
              <span class="income-dot billed"></span>
              <span><small>租金及物业应收</small><strong>¥{{ fmtMoney(incomeSources.rentPropertyBilled) }}</strong></span>
            </div>
            <div class="income-item">
              <span class="income-dot received"></span>
              <span><small>租金及物业实收</small><strong>¥{{ fmtMoney(incomeSources.rentPropertyReceived) }}</strong></span>
            </div>
            <div class="income-item">
              <span class="income-dot vending"></span>
              <span><small>售货机销售</small><strong>¥{{ fmtMoney(incomeSources.vendingSales) }}</strong></span>
            </div>
          </div>
          <div ref="sourceRef" class="chart income-chart" aria-label="经营收入来源图"></div>
        </div>
      </section>
    </div>

    <MonitoringPanels ref="monitoringRef" />

    <div class="operations-grid">
      <section class="commerce-card operation-card">
        <div class="commerce-card__head">
          <div><h2 class="commerce-card__title">合同执行</h2><p class="commerce-card__meta">全生命周期状态</p></div>
          <span class="commerce-icon"><el-icon><DocumentChecked /></el-icon></span>
        </div>
        <div class="mini-grid">
          <div class="mini"><span class="mv">{{ contract.total || 0 }}</span><span class="ml">合同总数</span></div>
          <div class="mini success"><span class="mv">{{ contract.executing || 0 }}</span><span class="ml">执行中</span></div>
          <div class="mini neutral"><span class="mv">{{ contract.terminated || 0 }}</span><span class="ml">已退租</span></div>
          <div class="mini warning"><span class="mv">{{ contract.expired || 0 }}</span><span class="ml">已到期</span></div>
        </div>
      </section>

      <section class="commerce-card operation-card">
        <div class="commerce-card__head">
          <div><h2 class="commerce-card__title">设备在线</h2><p class="commerce-card__meta">物联网接入健康度</p></div>
          <span class="commerce-icon"><el-icon><Cpu /></el-icon></span>
        </div>
        <div class="mini-grid">
          <div class="mini"><span class="mv">{{ device.total || 0 }}</span><span class="ml">设备总数</span></div>
          <div class="mini success"><span class="mv">{{ device.online || 0 }}</span><span class="ml">在线</span></div>
          <div class="mini neutral"><span class="mv">{{ device.offline || 0 }}</span><span class="ml">离线</span></div>
          <div class="mini danger"><span class="mv">{{ device.alarm || 0 }}</span><span class="ml">告警</span></div>
        </div>
      </section>

      <section class="commerce-card operation-card workorder-card">
        <div class="commerce-card__head">
          <div><h2 class="commerce-card__title">工单分类</h2><p class="commerce-card__meta">服务需求构成</p></div>
          <span class="commerce-icon"><el-icon><Tools /></el-icon></span>
        </div>
        <div ref="woRef" class="chart chart--small" aria-label="工单分类图"></div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onActivated, onBeforeUnmount, onDeactivated, onMounted, reactive, ref } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { dashboardApi } from '@/api/dashboard'
import MonitoringPanels from './MonitoringPanels.vue'

// 看板每 60 秒自己拉一次:财务数字随收款/出账随时在变,让用户靠手点刷新才看到新数
// 等于把「实时」做成了「手动」。定时器只在页面可见时跑,见下面 onActivated/onDeactivated
const AUTO_REFRESH_MS = 60000
const updatedAt = ref('')
let autoTimer = null
let disposed = false, resizeObserver
const boardRef = ref(), monitoringRef = ref(), fullscreen = ref(false), refreshing = ref(false)

const fin = reactive({})
const contract = reactive({})
const device = reactive({})
const incomeSources = reactive({})
const room = reactive({}), other = reactive({})
const roomData = ref([])
const loading = ref(false)
const roomRef = ref()
const trendRef = ref()
const woRef = ref()
const sourceRef = ref()
const charts = {}

const financeCards = [
  { key: 'dueReceivable', label: '到期应收', tag: '当前', icon: 'Wallet', color: '#0a24e9', soft: '#eef0ff' },
  { key: 'future30', label: '未来 30 天应收', tag: '预测', icon: 'Calendar', color: '#5c9764', soft: '#edf7ef' },
  { key: 'overdue', label: '逾期欠款', tag: '需关注', icon: 'WarningFilled', color: '#d95c62', soft: '#fff0f1' },
  { key: 'received', label: '累计实收', tag: '累计', icon: 'Coin', color: '#7256d8', soft: '#f3efff' }
]

const roomTotal = computed(() => roomData.value.reduce((sum, item) => sum + Number(item.value || 0), 0))
const fmtMoney = (v) => Number(v || 0).toLocaleString('zh-CN', { maximumFractionDigits: 0 })

function chartOf(key, el) {
  if (!el || disposed) return null
  if (!charts[key]) charts[key] = echarts.init(el)
  return charts[key]
}

function renderSourceChart() {
  chartOf('source', sourceRef.value)?.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, backgroundColor: '#0b0d17', borderWidth: 0, textStyle: { color: '#fff' } },
    grid: { left: 112, right: 26, top: 10, bottom: 22 },
    xAxis: { type: 'value', splitLine: { lineStyle: { color: '#f0f2f5', type: 'dashed' } }, axisLabel: { color: '#a9b6c0' } },
    yAxis: { type: 'category', data: ['售货机销售', '租费实收', '租费应收'], axisTick: { show: false }, axisLine: { show: false }, axisLabel: { color: '#68708a' } },
    series: [{
      type: 'bar',
      barWidth: 19,
      data: [incomeSources.vendingSales || 0, incomeSources.rentPropertyReceived || 0, incomeSources.rentPropertyBilled || 0],
      itemStyle: { color: (params) => ['#5c9764', '#7256d8', '#0a24e9'][params.dataIndex], borderRadius: [0, 7, 7, 0] }
    }]
  }, true)
}

async function load() {
  if (loading.value || disposed) return
  loading.value = true
  try {
    const ov = await dashboardApi.overview()
    Object.assign(fin, ov.finance || {})
    Object.assign(contract, ov.contract || {})
    Object.assign(device, ov.device || {})
    Object.assign(incomeSources, ov.incomeSources || {})
    Object.assign(room, ov.room || {})
    Object.assign(other, ov.other || {})
    await nextTick()
    renderSourceChart()
  } catch (e) { /* 各区块保留现有值 */ }

  try {
    roomData.value = await dashboardApi.roomStatus() || []
    chartOf('room', roomRef.value)?.setOption({
      tooltip: { trigger: 'item', backgroundColor: '#0b0d17', borderWidth: 0, textStyle: { color: '#fff' } },
      legend: { bottom: 0, icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle: { color: '#68708a', fontSize: 10 } },
      color: ['#0a24e9', '#6f7df3', '#5c9764', '#d99022', '#d95c62', '#a9b6c0', '#277f9d'],
      series: [{ type: 'pie', radius: ['53%', '73%'], center: ['50%', '43%'], data: roomData.value, itemStyle: { borderColor: '#fff', borderWidth: 4, borderRadius: 6 }, label: { show: false } }]
    }, true)
  } catch (e) { /* 房源图独立容错 */ }

  try {
    const trend = await dashboardApi.revenueTrend()
    chartOf('trend', trendRef.value)?.setOption({
      tooltip: { trigger: 'axis', backgroundColor: '#0b0d17', borderWidth: 0, textStyle: { color: '#fff' } },
      grid: { left: 18, right: 18, top: 26, bottom: 12, containLabel: true },
      xAxis: { type: 'category', data: trend.months, boundaryGap: false, axisTick: { show: false }, axisLine: { lineStyle: { color: '#e7eaf0' } }, axisLabel: { color: '#a9b6c0' } },
      yAxis: { type: 'value', axisLine: { show: false }, axisTick: { show: false }, splitLine: { lineStyle: { color: '#f0f2f5', type: 'dashed' } }, axisLabel: { color: '#a9b6c0' } },
      series: [
        { name: '应收', type: 'line', data: trend.receivable, smooth: true, symbol: 'none', lineStyle: { width: 2, color: '#aab3fa' }, areaStyle: { color: 'rgba(170,179,250,.10)' } },
        { name: '实收', type: 'line', data: trend.received, smooth: true, symbol: 'circle', symbolSize: 7, itemStyle: { color: '#0a24e9', borderColor: '#fff', borderWidth: 2 }, lineStyle: { width: 3, color: '#0a24e9' }, areaStyle: { color: 'rgba(10,36,233,.07)' } }
      ]
    }, true)
  } catch (e) { /* 趋势图独立容错 */ }

  try {
    const wo = await dashboardApi.workOrderCategory()
    chartOf('wo', woRef.value)?.setOption({
      tooltip: { trigger: 'axis', backgroundColor: '#0b0d17', borderWidth: 0, textStyle: { color: '#fff' } },
      grid: { left: 16, right: 16, top: 18, bottom: 8, containLabel: true },
      xAxis: { type: 'category', data: wo.map(x => x.name), axisTick: { show: false }, axisLine: { lineStyle: { color: '#e7eaf0' } }, axisLabel: { color: '#a9b6c0', fontSize: 10 } },
      yAxis: { type: 'value', axisLine: { show: false }, axisTick: { show: false }, splitLine: { lineStyle: { color: '#f0f2f5', type: 'dashed' } }, axisLabel: { color: '#a9b6c0' } },
      series: [{ type: 'bar', data: wo.map(x => x.value), itemStyle: { color: '#0a24e9', borderRadius: [7, 7, 0, 0] }, barMaxWidth: 24 }]
    }, true)
  } catch (e) { /* 工单图独立容错 */ }
  updatedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  loading.value = false
}

async function refreshAll() {
  if (refreshing.value) return
  refreshing.value = true
  try { await Promise.all([load(), monitoringRef.value?.refresh()]) }
  finally { refreshing.value = false }
}

async function toggleFullscreen() {
  try {
    if (document.fullscreenElement === boardRef.value) await document.exitFullscreen()
    else if (boardRef.value?.requestFullscreen) await boardRef.value.requestFullscreen()
    else ElMessage.info('当前浏览器不支持全屏展示，请使用浏览器的全屏功能。')
  } catch { ElMessage.warning('未能切换全屏，请重试或使用浏览器的全屏功能。') }
}
function syncFullscreen() {
  fullscreen.value = document.fullscreenElement === boardRef.value
  nextTick(resizeAll)
}
function leavePage() {
  stopAuto()
  if (document.fullscreenElement === boardRef.value) document.exitFullscreen()?.catch(() => {})
}

function startAuto() {
  stopAuto()
  autoTimer = setInterval(load, AUTO_REFRESH_MS)
}
function stopAuto() {
  if (autoTimer) { clearInterval(autoTimer); autoTimer = null }
}

function resizeAll() { Object.values(charts).forEach(chart => chart?.resize?.()) }

onMounted(() => {
  load()
  startAuto()
  window.addEventListener('resize', resizeAll)
  document.addEventListener('fullscreenchange', syncFullscreen)
  resizeObserver = new ResizeObserver(resizeAll)
  resizeObserver.observe(boardRef.value)
})

// Layout.vue 把路由页包在 <keep-alive> 里,切走触发的是 deactivated 而不是 unmounted。
// 只写 onMounted/onBeforeUnmount 的话:切回来看到的是离开前的旧快照,而定时器又一直在
// 后台空转。改成随页面可见性起停,切回来立刻补一次
let activatedBefore = false
onActivated(() => {
  if (activatedBefore) load()
  activatedBefore = true
  startAuto()
  nextTick(resizeAll)
})
onDeactivated(leavePage)

onBeforeUnmount(() => {
  disposed = true
  leavePage()
  window.removeEventListener('resize', resizeAll)
  document.removeEventListener('fullscreenchange', syncFullscreen)
  resizeObserver?.disconnect()
  Object.values(charts).forEach(chart => chart?.dispose?.())
})
</script>

<style scoped>
.data-center:fullscreen { overflow:auto;height:100%;width:100%;box-sizing:border-box;background:#f4f5fb;padding:24px; }
.data-center:fullscreen .commerce-page__header { align-items:center; }
.operations-summary { display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:20px;margin:0 0 18px;padding:18px 20px;background:var(--bg-card);border:1px solid var(--border);border-radius:14px; }
.operations-summary dt { font-size:12px;color:var(--text-secondary); }
.operations-summary dd { margin:7px 0 0;font-size:24px;font-weight:700;color:var(--text-title);font-variant-numeric:tabular-nums; }
.operations-summary small { font-size:14px;margin-left:4px; }
.data-center .commerce-page__actions { flex-shrink:0;max-width:52%; }
.finance-kpis { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 16px; }
.finance-kpi { display: flex; flex-direction: column; gap: 7px; padding: 17px 18px; }
.finance-kpi__top { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.finance-kpi > strong { color: var(--text-title); font-size: clamp(22px, 2vw, 30px); font-weight: 740; letter-spacing: -.045em; font-variant-numeric: tabular-nums; }
.finance-kpi > span { color: var(--text-secondary); font-size: 11px; }

.analytics-grid { display: grid; grid-template-columns: minmax(0, 1.7fr) minmax(300px, .8fr); gap: 16px; }
.trend-card, .room-card { min-height: 380px; }
.chart { width: 100%; }
.chart--trend { height: 310px; padding: 4px 12px 12px; }
.room-chart-wrap { position: relative; padding: 0 8px 10px; }
.chart--room { height: 310px; }
.room-total { position: absolute; top: 122px; left: 50%; display: grid; justify-items: center; pointer-events: none; transform: translateX(-50%); }
.room-total strong { color: var(--text-title); font-size: 25px; line-height: 1; }
.room-total span { margin-top: 5px; color: var(--text-muted); font-size: 9px; }
.legend-pills { display: flex; align-items: center; gap: 6px; color: var(--text-secondary); font-size: 10px; }
.legend-pills i { width: 7px; height: 7px; margin-left: 5px; border-radius: 50%; }
.legend-pills .receivable { background: #aab3fa; }
.legend-pills .received { background: #0a24e9; }

.income-card { grid-column: 1 / -1; }
.income-layout { display: grid; grid-template-columns: minmax(270px, .72fr) minmax(420px, 1.45fr); gap: 20px; align-items: center; padding: 12px 20px 20px; }
.income-metrics { display: grid; gap: 9px; }
.income-item { display: flex; align-items: center; gap: 11px; padding: 12px; background: #f8f9fb; border: 1px solid #f0f2f5; border-radius: 12px; }
.income-item > span:last-child { display: grid; gap: 3px; }
.income-item small { color: var(--text-muted); font-size: 10px; }
.income-item strong { color: var(--text-title); font-size: 18px; font-weight: 700; letter-spacing: -.025em; }
.income-dot { width: 9px; height: 34px; flex: 0 0 9px; border-radius: 999px; }
.income-dot.billed { background: #0a24e9; }
.income-dot.received { background: #7256d8; }
.income-dot.vending { background: #5c9764; }
.income-chart { height: 220px; }

.operations-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; margin-top: 16px; }
.operation-card { min-height: 280px; }
.mini-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; padding: 16px 20px 20px; }
.mini { display: flex; flex-direction: column; align-items: flex-start; gap: 4px; padding: 15px; background: #f7f8fb; border: 1px solid #f0f2f5; border-radius: 12px; }
.mv { color: #0a24e9; font-size: 24px; font-weight: 740; font-variant-numeric: tabular-nums; }
.ml { color: var(--text-secondary); font-size: 10px; }
.mini.success .mv { color: #5c9764; }
.mini.neutral .mv { color: #68708a; }
.mini.warning .mv { color: #d99022; }
.mini.danger .mv { color: #d95c62; }
.chart--small { height: 214px; padding: 0 10px 8px; }

@media (max-width: 1100px) {
  .data-center .commerce-page__header { align-items:flex-start;flex-direction:column;gap:14px; }
  .data-center .commerce-page__actions { max-width:none;justify-content:flex-start; }
  .operations-summary { grid-template-columns:1fr 1fr; }
  .finance-kpis { grid-template-columns: 1fr 1fr; }
  .analytics-grid { grid-template-columns: 1fr; }
  .income-card { grid-column: auto; }
  .operations-grid { grid-template-columns: 1fr 1fr; }
}
@media (max-width: 760px) {
  .data-center:fullscreen { padding:16px; }
  .finance-kpis, .operations-grid { grid-template-columns: 1fr; }
  .income-layout { grid-template-columns: 1fr; }
}
</style>
