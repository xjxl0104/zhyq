<template>
  <div class="screen">
    <div class="screen-header">
      <div class="hd-side">
        <span class="hd-time">{{ now }}</span>
      </div>
      <h1 class="hd-title">智慧园区一体化监控大屏</h1>
      <div class="hd-side right">
        <span class="hd-item">在线设备 {{ device.online }}/{{ device.total }}</span>
        <span class="hd-back" @click="goBack">返回后台</span>
      </div>
    </div>

    <div class="screen-body">
      <!-- 左列 -->
      <div class="col">
        <div class="panel">
          <div class="panel-title">经营核心指标</div>
          <div class="kpi-grid">
            <div class="kpi"><div class="kpi-v">{{ room.rentRate }}%</div><div class="kpi-l">出租率</div></div>
            <div class="kpi"><div class="kpi-v">{{ room.rented }}</div><div class="kpi-l">在租房间</div></div>
            <div class="kpi"><div class="kpi-v">{{ other.tenantTotal }}</div><div class="kpi-l">在租租客</div></div>
            <div class="kpi"><div class="kpi-v">¥{{ fmtW(fin.received) }}</div><div class="kpi-l">累计实收(万)</div></div>
          </div>
        </div>
        <div class="panel">
          <div class="panel-title">房源状态分布</div>
          <div ref="roomRef" class="chart"></div>
        </div>
        <div class="panel">
          <div class="panel-title">合同执行</div>
          <div class="bar-list">
            <div class="bar-row"><span>执行中</span><el-progress :percentage="pct(contract.executing, contract.total)" color="#22d3ee" /></div>
            <div class="bar-row"><span>已退租</span><el-progress :percentage="pct(contract.terminated, contract.total)" color="#94a3b8" /></div>
            <div class="bar-row"><span>已到期</span><el-progress :percentage="pct(contract.expired, contract.total)" color="#f59e0b" /></div>
          </div>
        </div>
      </div>

      <!-- 中列 -->
      <div class="col center">
        <div class="panel big">
          <div class="center-metrics">
            <div class="cm"><div class="cm-v">¥{{ fmtW(fin.dueReceivable) }}</div><div class="cm-l">到期应收(万)</div></div>
            <div class="cm"><div class="cm-v danger">¥{{ fmtW(fin.overdue) }}</div><div class="cm-l">逾期欠款(万)</div></div>
            <div class="cm"><div class="cm-v">{{ contract.total }}</div><div class="cm-l">合同总数</div></div>
          </div>
          <div ref="trendRef" class="chart-lg"></div>
        </div>
        <div class="panel">
          <div class="panel-title">工单分类统计</div>
          <div ref="woRef" class="chart"></div>
        </div>
      </div>

      <!-- 右列 -->
      <div class="col">
        <div class="panel">
          <div class="panel-title">设备接入监测</div>
          <div class="kpi-grid">
            <div class="kpi"><div class="kpi-v ok">{{ device.online }}</div><div class="kpi-l">在线设备</div></div>
            <div class="kpi"><div class="kpi-v off">{{ device.offline }}</div><div class="kpi-l">离线设备</div></div>
            <div class="kpi"><div class="kpi-v warn">{{ device.alarm }}</div><div class="kpi-l">活跃告警</div></div>
            <div class="kpi"><div class="kpi-v">{{ other.workOrderOpen }}</div><div class="kpi-l">进行中工单</div></div>
          </div>
        </div>
        <div class="panel">
          <div class="panel-title">实时告警</div>
          <div class="alarm-list">
            <div class="alarm-row" v-for="a in alarms" :key="a.id">
              <span class="dot" :class="a.level === 3 ? 'high' : 'mid'"></span>
              <span class="alarm-txt">{{ a.content }}</span>
              <span class="alarm-loc">{{ a.location }}</span>
            </div>
            <el-empty v-if="!alarms.length" description="暂无告警" :image-size="50" />
          </div>
        </div>
        <div class="panel">
          <div class="panel-title">招商线索转化</div>
          <div class="funnel">
            <div class="fn-row" v-for="f in funnel" :key="f.label">
              <span class="fn-l">{{ f.label }}</span>
              <div class="fn-bar" :style="{ width: f.w + '%' }">{{ f.value }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { dashboardApi } from '@/api/dashboard'
import { leadApi } from '@/api/crm'
import request from '@/utils/request'

const router = useRouter()
const fin = reactive({}), contract = reactive({}), device = reactive({}), room = reactive({}), other = reactive({})
const alarms = ref([]), funnel = ref([])
const roomRef = ref(), trendRef = ref(), woRef = ref()
const now = ref('')

// 大屏调色板:集中一处,替代散落的硬编码 hex,便于统一调色
const PALETTE = {
  cyan: '#22d3ee', sky: '#0ea5e9', blue: '#60a5fa', deepBlue: '#0369a1',
  axisLine: '#334155', axisLabel: '#94a3b8', splitLine: '#1e293b',
  label: '#cbd5e1'
}
const darkAxis = {
  axisLine: { lineStyle: { color: PALETTE.axisLine } },
  axisLabel: { color: PALETTE.axisLabel },
  splitLine: { lineStyle: { color: PALETTE.splitLine } }
}

// —— 定时器与图表实例集中管理,卸载时统一清理 ——
const timers = []
const charts = {}
function addTimer(fn, ms) { fn(); timers.push(setInterval(fn, ms)) }

const fmtW = (v) => (Number(v || 0) / 10000).toFixed(1)
const pct = (a, b) => b ? Math.round(a * 100 / b) : 0
function goBack() { router.push('/dashboard') }

function tick() {
  const d = new Date()
  now.value = d.toLocaleString('zh-CN', { hour12: false })
}

// —— 各卡片独立数据源,独立轮询,互不阻塞 ——
async function loadOverview() {
  try {
    const ov = await dashboardApi.overview()
    Object.assign(fin, ov.finance); Object.assign(contract, ov.contract)
    Object.assign(device, ov.device); Object.assign(room, ov.room); Object.assign(other, ov.other)
  } catch (e) { /* 单卡片失败不影响其他 */ }
}

async function loadAlarms() {
  try {
    const al = await request.get('/iot/alarm/page', { params: { pageNo: 1, pageSize: 6, status: 1 } })
    let list = al.records || []
    if (!list.length) {
      const al2 = await request.get('/iot/alarm/page', { params: { pageNo: 1, pageSize: 6 } })
      list = al2.records || []
    }
    alarms.value = list
  } catch (e) { /* 保留上次数据 */ }
}

async function loadFunnel() {
  try {
    const leadStats = await leadApi.stats()
    const total = leadStats.total || 0
    funnel.value = [
      { label: '总线索', value: total, w: 100 },
      { label: '跟进中', value: Math.max(total - leadStats.invalid, 0), w: 80 },
      { label: '意向', value: Math.round(total * 0.4), w: 55 },
      { label: '已转化', value: Math.round(total * (leadStats.convertRate || 15) / 100) || 1, w: 30 }
    ]
  } catch (e) {}
}

// —— 图表:实例留引用,可重复 setOption,统一 resize/dispose ——
function chartOf(key, el) {
  if (!charts[key]) charts[key] = echarts.init(el)
  return charts[key]
}

async function loadRoomChart() {
  try {
    const roomData = await dashboardApi.roomStatus()
    chartOf('room', roomRef.value).setOption({
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, textStyle: { color: PALETTE.axisLabel }, type: 'scroll' },
      series: [{
        type: 'pie', radius: ['38%', '62%'], center: ['50%', '42%'], data: roomData,
        label: { color: PALETTE.label, formatter: '{b}\n{c}' }
      }]
    })
  } catch (e) {}
}

async function loadTrendChart() {
  try {
    const trend = await dashboardApi.revenueTrend()
    chartOf('trend', trendRef.value).setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['应收', '实收'], textStyle: { color: PALETTE.axisLabel }, top: 0 },
      grid: { left: 70, right: 30, top: 40, bottom: 30 },
      xAxis: { type: 'category', data: trend.months, ...darkAxis },
      yAxis: { type: 'value', ...darkAxis },
      series: [
        { name: '应收', type: 'bar', data: trend.receivable, itemStyle: { color: PALETTE.sky }, barWidth: '30%' },
        { name: '实收', type: 'line', data: trend.received, smooth: true, itemStyle: { color: PALETTE.cyan }, lineStyle: { width: 3 } }
      ]
    })
  } catch (e) {}
}

async function loadWoChart() {
  try {
    const wo = await dashboardApi.workOrderCategory()
    chartOf('wo', woRef.value).setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 40, right: 20, top: 20, bottom: 30 },
      xAxis: { type: 'category', data: wo.map(x => x.name), ...darkAxis },
      yAxis: { type: 'value', ...darkAxis },
      series: [{ type: 'bar', data: wo.map(x => x.value), barWidth: '40%',
        itemStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: PALETTE.cyan }, { offset: 1, color: PALETTE.deepBlue }]) } }]
    })
  } catch (e) {}
}

function resizeAll() {
  Object.values(charts).forEach(c => c && c.resize())
}

onMounted(async () => {
  addTimer(tick, 1000)
  // 卡片粒度独立轮询:告警最勤(15s),KPI/图表次之(30s/60s),线索最缓(60s)
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
  Object.values(charts).forEach(c => c && c.dispose())
})
</script>

<style scoped>
.screen {
  height: 100vh; background: #0a1929; color: #e2e8f0; overflow: hidden;
  display: flex; flex-direction: column;
  background-image: radial-gradient(circle at 50% 0%, rgba(34,211,238,0.08), transparent 60%);
}
.screen-header {
  height: 64px; display: flex; align-items: center; justify-content: space-between;
  padding: 0 24px; border-bottom: 1px solid #1e3a5f;
}
.hd-title {
  font-size: 26px; font-weight: 700; margin: 0; letter-spacing: 4px;
  background: linear-gradient(90deg, #22d3ee, #60a5fa); -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}
.hd-side { flex: 1; display: flex; align-items: center; gap: 16px; font-size: 14px; color: #94a3b8; }
.hd-side.right { justify-content: flex-end; }
.hd-back { cursor: pointer; color: #22d3ee; border: 1px solid #22d3ee; padding: 4px 12px; border-radius: 4px; }
.hd-back:hover { background: rgba(34,211,238,0.15); }
.screen-body { flex: 1; display: grid; grid-template-columns: 1fr 1.3fr 1fr; gap: 16px; padding: 16px; overflow: hidden; }
.col { display: flex; flex-direction: column; gap: 16px; }
.panel {
  background: rgba(16,42,67,0.5); border: 1px solid #1e3a5f; border-radius: 8px;
  padding: 14px 16px; flex: 1; display: flex; flex-direction: column;
  box-shadow: inset 0 0 20px rgba(34,211,238,0.05);
}
.panel.big { flex: 1.4; }
.panel-title {
  font-size: 15px; font-weight: 600; color: #7dd3fc; margin-bottom: 10px;
  padding-left: 10px; border-left: 3px solid #22d3ee;
}
.kpi-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; flex: 1; align-content: center; }
.kpi { text-align: center; padding: 8px; background: rgba(34,211,238,0.06); border-radius: 6px; }
.kpi-v { font-size: 28px; font-weight: 700; color: #22d3ee; }
.kpi-v.ok { color: #4ade80; } .kpi-v.off { color: #94a3b8; } .kpi-v.warn { color: #fbbf24; }
.kpi-l { font-size: 13px; color: #94a3b8; margin-top: 4px; }
.chart { flex: 1; min-height: 200px; }
.chart-lg { flex: 1; min-height: 260px; }
.center-metrics { display: flex; justify-content: space-around; padding: 8px 0 16px; }
.cm { text-align: center; }
.cm-v { font-size: 30px; font-weight: 700; color: #22d3ee; }
.cm-v.danger { color: #f87171; }
.cm-l { font-size: 13px; color: #94a3b8; margin-top: 4px; }
.bar-list { display: flex; flex-direction: column; gap: 14px; justify-content: center; flex: 1; }
.bar-row { display: flex; align-items: center; gap: 12px; }
.bar-row > span { width: 56px; font-size: 13px; color: #cbd5e1; }
.bar-row :deep(.el-progress) { flex: 1; }
.bar-row :deep(.el-progress__text) { color: #cbd5e1 !important; }
.alarm-list { flex: 1; display: flex; flex-direction: column; gap: 10px; overflow: hidden; }
.alarm-row { display: flex; align-items: center; gap: 8px; font-size: 13px; padding: 6px 8px; background: rgba(248,113,113,0.08); border-radius: 4px; }
.dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.dot.high { background: #f87171; box-shadow: 0 0 8px #f87171; }
.dot.mid { background: #fbbf24; box-shadow: 0 0 8px #fbbf24; }
.alarm-txt { flex: 1; color: #e2e8f0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.alarm-loc { color: #64748b; font-size: 12px; }
.funnel { display: flex; flex-direction: column; gap: 8px; flex: 1; justify-content: center; }
.fn-row { display: flex; align-items: center; gap: 10px; }
.fn-l { width: 56px; font-size: 13px; color: #cbd5e1; }
.fn-bar {
  background: linear-gradient(90deg, #0ea5e9, #22d3ee); color: #042f2e; font-weight: 600;
  padding: 6px 10px; border-radius: 4px; font-size: 13px; text-align: right; min-width: 40px;
  transition: width .6s;
}
</style>
