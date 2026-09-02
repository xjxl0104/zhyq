<template>
  <div class="screen">
    <div class="screen-bg"></div>

    <header class="screen-header">
      <div class="screen-brand">
        <span class="brand-mark"><el-icon><Monitor /></el-icon></span>
        <div>
          <span class="screen-eyebrow">Live operation center</span>
          <h1>智慧园区监控中心</h1>
        </div>
      </div>
      <div class="header-actions">
        <span class="status-chip"><i class="pulse"></i>系统运行正常</span>
        <span class="status-chip"><i class="device-dot"></i>在线设备 {{ device.online || 0 }}/{{ device.total || 0 }}</span>
        <time>{{ now }}</time>
        <button class="back-button" type="button" @click="goBack"><el-icon><Back /></el-icon>返回后台</button>
      </div>
    </header>

    <main class="screen-body">
      <section class="screen-kpis" aria-label="监控核心指标">
        <article class="screen-kpi">
          <span class="kpi-icon blue"><el-icon><OfficeBuilding /></el-icon></span>
          <div><small>园区出租率</small><strong>{{ room.rentRate || 0 }}<i>%</i></strong></div>
          <span class="kpi-tag positive">经营</span>
        </article>
        <article class="screen-kpi">
          <span class="kpi-icon violet"><el-icon><Coin /></el-icon></span>
          <div><small>累计实收</small><strong><i>¥</i>{{ fmtW(fin.received) }}<i>万</i></strong></div>
          <span class="kpi-tag positive">财务</span>
        </article>
        <article class="screen-kpi">
          <span class="kpi-icon green"><el-icon><Cpu /></el-icon></span>
          <div><small>在线设备</small><strong>{{ device.online || 0 }}<i> / {{ device.total || 0 }}</i></strong></div>
          <span class="kpi-tag positive">物联</span>
        </article>
        <article class="screen-kpi">
          <span class="kpi-icon amber"><el-icon><Tools /></el-icon></span>
          <div><small>进行中工单</small><strong>{{ other.workOrderOpen || 0 }}</strong></div>
          <span class="kpi-tag attention">服务</span>
        </article>
      </section>

      <div class="primary-grid">
        <section class="screen-panel trend-panel">
          <div class="panel-head">
            <div><h2>营收趋势</h2><p>近 6 个月应收与实收变化</p></div>
            <span class="legend"><i class="due"></i>应收 <i class="paid"></i>实收</span>
          </div>
          <div class="finance-strip">
            <div><small>到期应收</small><strong>¥{{ fmtW(fin.dueReceivable) }}<i>万</i></strong></div>
            <div class="danger"><small>逾期欠款</small><strong>¥{{ fmtW(fin.overdue) }}<i>万</i></strong></div>
            <div><small>合同总数</small><strong>{{ contract.total || 0 }}<i>份</i></strong></div>
          </div>
          <div ref="trendRef" class="chart trend-chart" aria-label="营收趋势图"></div>
        </section>

        <section class="screen-panel room-panel">
          <div class="panel-head">
            <div><h2>房源状态</h2><p>园区空间即时占用结构</p></div>
            <span class="panel-icon"><el-icon><PieChart /></el-icon></span>
          </div>
          <div class="room-chart-wrap">
            <div ref="roomRef" class="chart room-chart" aria-label="房源状态分布图"></div>
            <div class="room-center"><strong>{{ room.total || 0 }}</strong><span>全部房源</span></div>
          </div>
        </section>
      </div>

      <div class="secondary-grid">
        <section class="screen-panel compact-panel">
          <div class="panel-head">
            <div><h2>合同执行</h2><p>当前状态占比</p></div>
            <span class="panel-icon"><el-icon><DocumentChecked /></el-icon></span>
          </div>
          <div class="bar-list">
            <div class="bar-row"><span><i class="bar-dot running"></i>执行中</span><el-progress :percentage="pct(contract.executing, contract.total)" :stroke-width="9" color="#0a24e9" /></div>
            <div class="bar-row"><span><i class="bar-dot closed"></i>已退租</span><el-progress :percentage="pct(contract.terminated, contract.total)" :stroke-width="9" color="#a9b6c0" /></div>
            <div class="bar-row"><span><i class="bar-dot expired"></i>已到期</span><el-progress :percentage="pct(contract.expired, contract.total)" :stroke-width="9" color="#d99022" /></div>
          </div>
        </section>

        <section class="screen-panel compact-panel">
          <div class="panel-head">
            <div><h2>工单分类</h2><p>服务需求实时构成</p></div>
            <span class="panel-icon"><el-icon><Histogram /></el-icon></span>
          </div>
          <div ref="woRef" class="chart small-chart" aria-label="工单分类统计图"></div>
        </section>

        <section class="screen-panel compact-panel">
          <div class="panel-head">
            <div><h2>实时告警</h2><p>优先展示未确认事件</p></div>
            <span class="alarm-count">{{ alarms.length }}</span>
          </div>
          <div class="alarm-list">
            <div v-for="a in alarms.slice(0, 4)" :key="a.id" class="alarm-row" :class="a.level === 3 ? 'high' : 'mid'">
              <span class="alarm-dot"></span>
              <span><strong>{{ a.content }}</strong><small>{{ a.location || '园区公共区域' }}</small></span>
              <span class="alarm-level">{{ a.level === 3 ? '高' : '中' }}</span>
            </div>
            <el-empty v-if="!alarms.length" description="暂无告警" :image-size="46" />
          </div>
        </section>

        <section class="screen-panel compact-panel">
          <div class="panel-head">
            <div><h2>线索转化</h2><p>招商漏斗推进情况</p></div>
            <span class="panel-icon"><el-icon><Promotion /></el-icon></span>
          </div>
          <div class="funnel">
            <div v-for="f in funnel" :key="f.label" class="funnel-row">
              <span>{{ f.label }}</span>
              <div class="funnel-track"><div class="funnel-bar" :style="{ width: `${f.w}%` }"></div></div>
              <strong>{{ f.value }}</strong>
            </div>
          </div>
        </section>
      </div>
    </main>
  </div>
</template>

<script setup>
import { nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { dashboardApi } from '@/api/dashboard'
import { leadApi } from '@/api/crm'
import request from '@/utils/request'

const router = useRouter()
const fin = reactive({})
const contract = reactive({})
const device = reactive({})
const room = reactive({})
const other = reactive({})
const alarms = ref([])
const funnel = ref([])
const roomRef = ref()
const trendRef = ref()
const woRef = ref()
const now = ref('')

const PALETTE = {
  blue: '#0a24e9', blueSoft: '#d7dbfb', violet: '#7256d8', green: '#5c9764',
  axisLine: '#e7eaf0', axisLabel: '#a9b6c0', splitLine: '#f0f2f5', label: '#29315d'
}
const lightAxis = {
  axisLine: { lineStyle: { color: PALETTE.axisLine } },
  axisTick: { show: false },
  axisLabel: { color: PALETTE.axisLabel },
  splitLine: { lineStyle: { color: PALETTE.splitLine, type: 'dashed' } }
}

const timers = []
const charts = {}
function addTimer(fn, ms) { fn(); timers.push(setInterval(fn, ms)) }
const fmtW = (v) => (Number(v || 0) / 10000).toFixed(1)
const pct = (a, b) => b ? Math.round(Number(a || 0) * 100 / Number(b)) : 0
function goBack() { router.push('/dashboard') }

function tick() {
  now.value = new Date().toLocaleString('zh-CN', { hour12: false })
}

async function loadOverview() {
  try {
    const ov = await dashboardApi.overview()
    Object.assign(fin, ov.finance || {})
    Object.assign(contract, ov.contract || {})
    Object.assign(device, ov.device || {})
    Object.assign(room, ov.room || {})
    Object.assign(other, ov.other || {})
  } catch (e) { /* 单卡片失败不影响其他区域 */ }
}

async function loadAlarms() {
  try {
    const result = await request.get('/iot/alarm/page', { params: { pageNo: 1, pageSize: 6, status: 1 } })
    let list = result.records || []
    if (!list.length) {
      const recent = await request.get('/iot/alarm/page', { params: { pageNo: 1, pageSize: 6 } })
      list = recent.records || []
    }
    alarms.value = list
  } catch (e) { /* 保留上次数据 */ }
}

async function loadFunnel() {
  try {
    const stats = await leadApi.stats()
    const total = Number(stats.total || 0)
    const invalid = Number(stats.invalid || 0)
    const converted = Math.round(total * Number(stats.convertRate || 0) / 100)
    funnel.value = [
      { label: '总线索', value: total, w: 100 },
      { label: '跟进中', value: Math.max(total - invalid, 0), w: total ? 78 : 0 },
      { label: '意向', value: Math.round(total * .4), w: total ? 54 : 0 },
      { label: '已转化', value: converted, w: total ? Math.max(18, Math.round(converted / total * 100)) : 0 }
    ]
  } catch (e) { /* 保留上次数据 */ }
}

function chartOf(key, el) {
  if (!el) return null
  if (!charts[key]) charts[key] = echarts.init(el)
  return charts[key]
}

async function loadRoomChart() {
  try {
    const data = await dashboardApi.roomStatus()
    chartOf('room', roomRef.value)?.setOption({
      color: ['#0a24e9', '#6f7df3', '#5c9764', '#d99022', '#d95c62', '#a9b6c0'],
      tooltip: { trigger: 'item', backgroundColor: '#0b0d17', borderWidth: 0, textStyle: { color: '#fff' } },
      legend: { bottom: 0, type: 'scroll', icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle: { color: PALETTE.axisLabel, fontSize: 10 } },
      series: [{
        type: 'pie', radius: ['55%', '75%'], center: ['50%', '43%'], data,
        itemStyle: { borderColor: '#fff', borderWidth: 4, borderRadius: 6 },
        label: { show: false }
      }]
    }, true)
  } catch (e) { /* 房源图独立容错 */ }
}

async function loadTrendChart() {
  try {
    const trend = await dashboardApi.revenueTrend()
    chartOf('trend', trendRef.value)?.setOption({
      tooltip: { trigger: 'axis', backgroundColor: '#0b0d17', borderWidth: 0, textStyle: { color: '#fff' } },
      grid: { left: 18, right: 18, top: 30, bottom: 12, containLabel: true },
      xAxis: { type: 'category', data: trend.months, boundaryGap: true, ...lightAxis },
      yAxis: { type: 'value', ...lightAxis },
      series: [
        { name: '应收', type: 'bar', data: trend.receivable, barMaxWidth: 26, itemStyle: { color: PALETTE.blueSoft, borderRadius: [7, 7, 0, 0] } },
        { name: '实收', type: 'line', data: trend.received, smooth: true, symbol: 'circle', symbolSize: 7, itemStyle: { color: PALETTE.blue, borderColor: '#fff', borderWidth: 2 }, lineStyle: { width: 3, color: PALETTE.blue }, areaStyle: { color: 'rgba(10,36,233,.06)' } }
      ]
    }, true)
  } catch (e) { /* 趋势图独立容错 */ }
}

async function loadWoChart() {
  try {
    const workorders = await dashboardApi.workOrderCategory()
    chartOf('wo', woRef.value)?.setOption({
      tooltip: { trigger: 'axis', backgroundColor: '#0b0d17', borderWidth: 0, textStyle: { color: '#fff' } },
      grid: { left: 14, right: 14, top: 16, bottom: 8, containLabel: true },
      xAxis: { type: 'category', data: workorders.map(item => item.name), ...lightAxis, axisLabel: { color: PALETTE.axisLabel, fontSize: 9 } },
      yAxis: { type: 'value', ...lightAxis },
      series: [{ type: 'bar', data: workorders.map(item => item.value), barMaxWidth: 22, itemStyle: { color: PALETTE.blue, borderRadius: [7, 7, 0, 0] } }]
    }, true)
  } catch (e) { /* 工单图独立容错 */ }
}

function resizeAll() { Object.values(charts).forEach(chart => chart?.resize?.()) }

onMounted(async () => {
  addTimer(tick, 1000)
  addTimer(loadAlarms, 15000)
  addTimer(loadOverview, 30000)
  addTimer(loadFunnel, 60000)
  await nextTick()
  addTimer(loadRoomChart, 60000)
  addTimer(loadTrendChart, 60000)
  addTimer(loadWoChart, 60000)
  window.addEventListener('resize', resizeAll)
})

onUnmounted(() => {
  timers.forEach(clearInterval)
  window.removeEventListener('resize', resizeAll)
  Object.values(charts).forEach(chart => chart?.dispose?.())
})
</script>

<style scoped>
.screen {
  --screen-text: #0b0d17;
  --screen-secondary: #68708a;
  position: relative;
  min-height: 100vh;
  overflow: auto;
  color: var(--screen-text);
  background: #eef1f5;
  font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Microsoft YaHei", sans-serif;
}
.screen-bg {
  position: fixed;
  inset: 0;
  pointer-events: none;
  background:
    radial-gradient(720px 420px at 8% -8%, rgba(10, 36, 233, .08), transparent 70%),
    radial-gradient(680px 420px at 95% 105%, rgba(114, 86, 216, .06), transparent 72%);
}
.screen-header {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  min-height: 82px;
  padding: 14px 24px;
  background: rgba(255, 255, 255, .92);
  border-bottom: 1px solid #e7eaf0;
  backdrop-filter: blur(18px);
}
.screen-brand { display: flex; align-items: center; gap: 12px; }
.brand-mark { display: grid; place-items: center; width: 42px; height: 42px; color: #fff; background: #0a24e9; border-radius: 13px; box-shadow: 0 9px 18px rgba(10, 36, 233, .18); }
.screen-eyebrow { color: #0a24e9; font-size: 9px; font-weight: 750; letter-spacing: .14em; text-transform: uppercase; }
.screen-brand h1 { margin: 3px 0 0; font-size: 20px; font-weight: 720; letter-spacing: -.025em; }
.header-actions { display: flex; align-items: center; justify-content: flex-end; gap: 9px; }
.status-chip { display: inline-flex; align-items: center; gap: 7px; min-height: 34px; padding: 0 11px; color: #68708a; background: #f7f8fb; border: 1px solid #e7eaf0; border-radius: 10px; font-size: 10px; white-space: nowrap; }
.pulse, .device-dot { width: 7px; height: 7px; border-radius: 50%; background: #5c9764; box-shadow: 0 0 0 4px #edf7ef; }
.pulse { animation: status-pulse 2s ease-in-out infinite; }
@keyframes status-pulse { 50% { opacity: .45; } }
.header-actions time { color: #68708a; font-size: 11px; font-variant-numeric: tabular-nums; white-space: nowrap; }
.back-button { display: inline-flex; align-items: center; gap: 6px; min-height: 35px; padding: 0 12px; color: #fff; background: #0a24e9; border: 0; border-radius: 10px; font: inherit; font-size: 10px; font-weight: 650; cursor: pointer; box-shadow: 0 7px 16px rgba(10, 36, 233, .16); }
.back-button:hover { background: #152eae; }

.screen-body { position: relative; z-index: 1; display: grid; gap: 14px; padding: 16px; }
.screen-kpis { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; }
.screen-kpi { display: flex; align-items: center; gap: 12px; min-width: 0; padding: 15px 16px; background: #fff; border: 1px solid #e7eaf0; border-radius: 16px; box-shadow: 0 8px 24px rgba(41, 49, 93, .045); }
.kpi-icon { display: grid; place-items: center; width: 38px; height: 38px; flex: 0 0 38px; border-radius: 11px; }
.kpi-icon.blue { color: #0a24e9; background: #eef0ff; }
.kpi-icon.violet { color: #7256d8; background: #f3efff; }
.kpi-icon.green { color: #5c9764; background: #edf7ef; }
.kpi-icon.amber { color: #d99022; background: #fff7e8; }
.screen-kpi > div { display: grid; min-width: 0; flex: 1; gap: 4px; }
.screen-kpi small { color: #a9b6c0; font-size: 9px; }
.screen-kpi strong { overflow: hidden; color: #0b0d17; font-size: 24px; font-weight: 740; letter-spacing: -.04em; font-variant-numeric: tabular-nums; white-space: nowrap; text-overflow: ellipsis; }
.screen-kpi strong i { color: #68708a; font-size: 11px; font-style: normal; font-weight: 600; letter-spacing: 0; }
.kpi-tag { align-self: flex-start; padding: 4px 7px; border-radius: 999px; font-size: 8px; font-weight: 700; }
.kpi-tag.positive { color: #5c9764; background: #edf7ef; }
.kpi-tag.attention { color: #d99022; background: #fff7e8; }

.primary-grid { display: grid; grid-template-columns: minmax(0, 2fr) minmax(330px, .82fr); gap: 14px; }
.secondary-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; }
.screen-panel { min-width: 0; background: #fff; border: 1px solid #e7eaf0; border-radius: 16px; box-shadow: 0 8px 24px rgba(41, 49, 93, .045); }
.panel-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; padding: 16px 17px 0; }
.panel-head h2 { margin: 0; color: #0b0d17; font-size: 13px; font-weight: 680; }
.panel-head p { margin: 4px 0 0; color: #a9b6c0; font-size: 9px; }
.panel-icon { display: grid; place-items: center; width: 31px; height: 31px; color: #0a24e9; background: #eef0ff; border-radius: 9px; }
.legend { display: flex; align-items: center; gap: 5px; color: #68708a; font-size: 9px; }
.legend i { width: 7px; height: 7px; margin-left: 5px; border-radius: 50%; }
.legend .due { background: #d7dbfb; }
.legend .paid { background: #0a24e9; }
.trend-panel, .room-panel { min-height: 374px; }
.finance-strip { display: grid; grid-template-columns: repeat(3, 1fr); gap: 9px; padding: 13px 17px 0; }
.finance-strip > div { display: grid; gap: 4px; padding: 10px 12px; background: #f8f9fb; border: 1px solid #f0f2f5; border-radius: 11px; }
.finance-strip small { color: #a9b6c0; font-size: 8px; }
.finance-strip strong { color: #0b0d17; font-size: 19px; font-weight: 720; letter-spacing: -.025em; }
.finance-strip strong i { color: #68708a; font-size: 9px; font-style: normal; margin-left: 2px; }
.finance-strip .danger { background: #fff7f7; }
.finance-strip .danger strong { color: #d95c62; }
.chart { width: 100%; }
.trend-chart { height: 270px; padding: 0 8px 8px; }
.room-chart-wrap { position: relative; }
.room-chart { height: 314px; padding: 0 6px 8px; }
.room-center { position: absolute; top: 122px; left: 50%; display: grid; justify-items: center; pointer-events: none; transform: translateX(-50%); }
.room-center strong { color: #0b0d17; font-size: 24px; }
.room-center span { color: #a9b6c0; font-size: 8px; }

.compact-panel { min-height: 250px; overflow: hidden; }
.bar-list { display: flex; flex-direction: column; gap: 18px; padding: 25px 17px 18px; }
.bar-row { display: grid; grid-template-columns: 60px 1fr; align-items: center; gap: 10px; }
.bar-row > span { display: inline-flex; align-items: center; gap: 6px; color: #29315d; font-size: 9px; }
.bar-dot { width: 6px; height: 6px; border-radius: 50%; }
.bar-dot.running { background: #0a24e9; }
.bar-dot.closed { background: #a9b6c0; }
.bar-dot.expired { background: #d99022; }
.bar-row :deep(.el-progress-bar__outer) { background: #f0f2f5 !important; }
.bar-row :deep(.el-progress__text) { color: #29315d !important; font-size: 9px !important; }
.small-chart { height: 196px; padding: 0 7px 7px; }
.alarm-count { display: grid; place-items: center; min-width: 27px; height: 27px; padding: 0 7px; color: #d95c62; background: #fff0f1; border-radius: 9px; font-size: 10px; font-weight: 700; }
.alarm-list { display: flex; flex-direction: column; gap: 7px; padding: 14px 17px 17px; }
.alarm-row { display: flex; align-items: center; gap: 9px; min-width: 0; padding: 8px 9px; background: #f8f9fb; border-radius: 10px; }
.alarm-dot { width: 7px; height: 7px; flex: 0 0 7px; border-radius: 50%; }
.alarm-row.high .alarm-dot { background: #d95c62; box-shadow: 0 0 0 4px #fff0f1; }
.alarm-row.mid .alarm-dot { background: #d99022; box-shadow: 0 0 0 4px #fff7e8; }
.alarm-row > span:nth-child(2) { display: grid; min-width: 0; flex: 1; gap: 2px; }
.alarm-row strong { overflow: hidden; color: #0b0d17; font-size: 9px; font-weight: 620; white-space: nowrap; text-overflow: ellipsis; }
.alarm-row small { overflow: hidden; color: #a9b6c0; font-size: 7px; white-space: nowrap; text-overflow: ellipsis; }
.alarm-level { padding: 3px 5px; color: #68708a; background: #fff; border-radius: 999px; font-size: 7px; }
.funnel { display: flex; flex-direction: column; gap: 13px; padding: 23px 17px 17px; }
.funnel-row { display: grid; grid-template-columns: 44px 1fr 26px; align-items: center; gap: 8px; }
.funnel-row > span { color: #68708a; font-size: 8px; }
.funnel-row > strong { color: #0b0d17; font-size: 9px; text-align: right; }
.funnel-track { height: 8px; overflow: hidden; background: #eef0ff; border-radius: 999px; }
.funnel-bar { height: 100%; min-width: 0; background: #0a24e9; border-radius: inherit; transition: width .7s ease; }

@media (max-width: 1500px) {
  .secondary-grid { grid-template-columns: 1fr 1fr; }
}
@media (max-width: 1100px) {
  .screen-header { align-items: flex-start; flex-direction: column; }
  .header-actions { flex-wrap: wrap; justify-content: flex-start; }
  .screen-kpis { grid-template-columns: 1fr 1fr; }
  .primary-grid { grid-template-columns: 1fr; }
}
@media (max-width: 720px) {
  .screen-kpis, .secondary-grid { grid-template-columns: 1fr; }
  .finance-strip { grid-template-columns: 1fr; }
}
@media (prefers-reduced-motion: reduce) {
  .pulse { animation: none; }
  .funnel-bar { transition: none; }
}
</style>
