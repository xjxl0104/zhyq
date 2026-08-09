<template>
  <div class="screen">
    <div class="screen-bg"></div>
    <header class="screen-header">
      <div class="hd-side">
        <span class="hd-pill"><i class="pulse"></i>系统运行中</span>
        <span class="hd-time">{{ now }}</span>
      </div>
      <h1 class="hd-title">智慧园区一体化监控中心</h1>
      <div class="hd-side right">
        <span class="hd-pill"><i class="pulse ok"></i>在线设备 {{ device.online || 0 }}/{{ device.total || 0 }}</span>
        <span class="hd-back" @click="goBack">返回后台</span>
      </div>
    </header>

    <div class="screen-body">
      <!-- 左列 -->
      <div class="col">
        <section class="panel">
          <div class="panel-title">经营核心指标</div>
          <div class="kpi-grid">
            <div class="kpi"><div class="kpi-v">{{ room.rentRate || 0 }}<i>%</i></div><div class="kpi-l">出租率</div></div>
            <div class="kpi"><div class="kpi-v">{{ room.rented || 0 }}</div><div class="kpi-l">在租房间</div></div>
            <div class="kpi"><div class="kpi-v">{{ other.tenantTotal || 0 }}</div><div class="kpi-l">在租租客</div></div>
            <div class="kpi"><div class="kpi-v"><i>¥</i>{{ fmtW(fin.received) }}<i>万</i></div><div class="kpi-l">累计实收</div></div>
          </div>
        </section>
        <section class="panel">
          <div class="panel-title">房源状态分布</div>
          <div ref="roomRef" class="chart"></div>
        </section>
        <section class="panel">
          <div class="panel-title">合同执行</div>
          <div class="bar-list">
            <div class="bar-row"><span>执行中</span><el-progress :percentage="pct(contract.executing, contract.total)" :stroke-width="10" color="#22d3ee" /></div>
            <div class="bar-row"><span>已退租</span><el-progress :percentage="pct(contract.terminated, contract.total)" :stroke-width="10" color="#64748b" /></div>
            <div class="bar-row"><span>已到期</span><el-progress :percentage="pct(contract.expired, contract.total)" :stroke-width="10" color="#f59e0b" /></div>
          </div>
        </section>
      </div>

      <!-- 中列 -->
      <div class="col center">
        <section class="panel big">
          <div class="center-metrics">
            <div class="cm"><div class="cm-v"><i>¥</i>{{ fmtW(fin.dueReceivable) }}<i>万</i></div><div class="cm-l">到期应收</div></div>
            <div class="cm hero"><div class="cm-v danger"><i>¥</i>{{ fmtW(fin.overdue) }}<i>万</i></div><div class="cm-l">逾期欠款</div></div>
            <div class="cm"><div class="cm-v">{{ contract.total || 0 }}</div><div class="cm-l">合同总数</div></div>
          </div>
          <div class="panel-title inline">营收趋势 · 应收 vs 实收</div>
          <div ref="trendRef" class="chart-lg"></div>
        </section>
        <section class="panel">
          <div class="panel-title">工单分类统计</div>
          <div ref="woRef" class="chart"></div>
        </section>
      </div>

      <!-- 右列 -->
      <div class="col">
        <section class="panel">
          <div class="panel-title">设备接入监测</div>
          <div class="kpi-grid">
            <div class="kpi"><div class="kpi-v ok">{{ device.online || 0 }}</div><div class="kpi-l">在线设备</div></div>
            <div class="kpi"><div class="kpi-v off">{{ device.offline || 0 }}</div><div class="kpi-l">离线设备</div></div>
            <div class="kpi"><div class="kpi-v warn">{{ device.alarm || 0 }}</div><div class="kpi-l">活跃告警</div></div>
            <div class="kpi"><div class="kpi-v">{{ other.workOrderOpen || 0 }}</div><div class="kpi-l">进行中工单</div></div>
          </div>
        </section>
        <section class="panel">
          <div class="panel-title">实时告警</div>
          <div class="alarm-list">
            <div class="alarm-row" v-for="a in alarms" :key="a.id" :class="a.level === 3 ? 'high' : 'mid'">
              <span class="dot" :class="a.level === 3 ? 'high' : 'mid'"></span>
              <span class="alarm-txt">{{ a.content }}</span>
              <span class="alarm-loc">{{ a.location }}</span>
            </div>
            <el-empty v-if="!alarms.length" description="暂无告警" :image-size="50" />
          </div>
        </section>
        <section class="panel">
          <div class="panel-title">招商线索转化</div>
          <div class="funnel">
            <div class="fn-row" v-for="f in funnel" :key="f.label">
              <span class="fn-l">{{ f.label }}</span>
              <div class="fn-track"><div class="fn-bar" :style="{ width: f.w + '%' }"><span>{{ f.value }}</span></div></div>
            </div>
          </div>
        </section>
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
      color: ['#22d3ee', '#60a5fa', '#34d399', '#fbbf24', '#a78bfa', '#fb7185'],
      tooltip: { trigger: 'item', backgroundColor: 'rgba(8,18,34,.92)', borderColor: PALETTE.axisLine, textStyle: { color: '#e6f0fa' } },
      legend: { bottom: 0, textStyle: { color: PALETTE.axisLabel }, type: 'scroll', icon: 'circle', itemWidth: 8, itemHeight: 8 },
      series: [{
        type: 'pie', radius: ['46%', '68%'], center: ['50%', '42%'], data: roomData,
        itemStyle: { borderColor: '#0a1526', borderWidth: 3, borderRadius: 6 },
        label: { color: PALETTE.label, formatter: '{b}\n{c}' },
        emphasis: { scaleSize: 6, itemStyle: { shadowBlur: 18, shadowColor: 'rgba(34,211,238,.5)' } }
      }]
    })
  } catch (e) {}
}

async function loadTrendChart() {
  try {
    const trend = await dashboardApi.revenueTrend()
    chartOf('trend', trendRef.value).setOption({
      tooltip: { trigger: 'axis', backgroundColor: 'rgba(8,18,34,.92)', borderColor: PALETTE.axisLine, textStyle: { color: '#e6f0fa' } },
      legend: { data: ['应收', '实收'], textStyle: { color: PALETTE.axisLabel }, top: 0, icon: 'roundRect', itemWidth: 12, itemHeight: 8 },
      grid: { left: 60, right: 24, top: 40, bottom: 28 },
      xAxis: { type: 'category', data: trend.months, boundaryGap: true, ...darkAxis },
      yAxis: { type: 'value', ...darkAxis },
      series: [
        { name: '应收', type: 'bar', data: trend.receivable, barWidth: '32%',
          itemStyle: { borderRadius: [4,4,0,0], color: new echarts.graphic.LinearGradient(0,0,0,1,[{offset:0,color:'rgba(96,165,250,.9)'},{offset:1,color:'rgba(96,165,250,.15)'}]) } },
        { name: '实收', type: 'line', data: trend.received, smooth: true, symbol: 'circle', symbolSize: 7,
          itemStyle: { color: PALETTE.cyan, borderColor: '#0a1526', borderWidth: 2 },
          lineStyle: { width: 3, shadowBlur: 12, shadowColor: 'rgba(34,211,238,.6)' },
          areaStyle: { color: new echarts.graphic.LinearGradient(0,0,0,1,[{offset:0,color:'rgba(34,211,238,.28)'},{offset:1,color:'rgba(34,211,238,0)'}]) } }
      ]
    })
  } catch (e) {}
}

async function loadWoChart() {
  try {
    const wo = await dashboardApi.workOrderCategory()
    chartOf('wo', woRef.value).setOption({
      tooltip: { trigger: 'axis', backgroundColor: 'rgba(8,18,34,.92)', borderColor: PALETTE.axisLine, textStyle: { color: '#e6f0fa' } },
      grid: { left: 40, right: 20, top: 20, bottom: 30 },
      xAxis: { type: 'category', data: wo.map(x => x.name), ...darkAxis },
      yAxis: { type: 'value', ...darkAxis },
      series: [{ type: 'bar', data: wo.map(x => x.value), barWidth: '44%',
        itemStyle: { borderRadius: [4,4,0,0], color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: PALETTE.cyan }, { offset: 1, color: PALETTE.deepBlue }]) },
        emphasis: { itemStyle: { shadowBlur: 14, shadowColor: 'rgba(34,211,238,.5)' } } }]
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
/* ===== 设计令牌:深空指挥中心 ===== */
.screen {
  --bg-0: #060d1a;          /* 最底 */
  --bg-1: #0a1526;          /* 面板底 */
  --line: rgba(56,120,180,.28);
  --line-strong: rgba(56,189,248,.55);
  --accent: #38bdf8;        /* 主青蓝 */
  --accent-2: #22d3ee;
  --accent-3: #60a5fa;
  --ok: #34d399; --warn: #fbbf24; --danger: #fb7185;
  --text: #e6f0fa; --text-dim: #7f9cb8; --text-mute: #4f6785;
  position: relative;
  height: 100vh; overflow: hidden; color: var(--text);
  display: flex; flex-direction: column;
  background: var(--bg-0);
  font-family: -apple-system, "PingFang SC", "Microsoft YaHei", sans-serif;
}
/* 背景:径向光晕 + 细网格纹理 */
.screen-bg {
  position: absolute; inset: 0; z-index: 0; pointer-events: none;
  background:
    radial-gradient(1200px 500px at 50% -8%, rgba(56,189,248,.14), transparent 62%),
    radial-gradient(900px 500px at 100% 100%, rgba(96,165,250,.08), transparent 60%),
    linear-gradient(rgba(56,120,180,.05) 1px, transparent 1px) 0 0/40px 40px,
    linear-gradient(90deg, rgba(56,120,180,.05) 1px, transparent 1px) 0 0/40px 40px;
}
.screen-header, .screen-body { position: relative; z-index: 1; }

/* ===== 顶栏 ===== */
.screen-header {
  height: 72px; display: flex; align-items: center; justify-content: space-between;
  padding: 0 28px;
  border-bottom: 1px solid var(--line);
  background: linear-gradient(180deg, rgba(56,189,248,.06), transparent);
}
.hd-title {
  font-size: 28px; font-weight: 800; margin: 0; letter-spacing: 6px; white-space: nowrap;
  background: linear-gradient(90deg, #7dd3fc, #e0f2fe 50%, #7dd3fc);
  -webkit-background-clip: text; -webkit-text-fill-color: transparent;
  text-shadow: 0 0 24px rgba(56,189,248,.35);
}
.hd-side { flex: 1; display: flex; align-items: center; gap: 16px; font-size: 14px; }
.hd-side.right { justify-content: flex-end; }
.hd-time { color: var(--text-dim); font-variant-numeric: tabular-nums; letter-spacing: 1px; }
.hd-pill {
  display: inline-flex; align-items: center; gap: 7px;
  padding: 5px 12px; border-radius: 999px; font-size: 13px; color: var(--text-dim);
  background: rgba(56,189,248,.07); border: 1px solid var(--line);
}
.hd-pill .pulse { width: 7px; height: 7px; border-radius: 50%; background: var(--accent); box-shadow: 0 0 8px var(--accent); animation: pulse 1.6s infinite; }
.hd-pill .pulse.ok { background: var(--ok); box-shadow: 0 0 8px var(--ok); }
@keyframes pulse { 0%,100%{opacity:1;transform:scale(1)} 50%{opacity:.35;transform:scale(.7)} }
.hd-back {
  cursor: pointer; color: var(--accent); border: 1px solid var(--line-strong);
  padding: 6px 16px; border-radius: 6px; transition: all .2s; font-size: 13px;
}
.hd-back:hover { background: rgba(56,189,248,.16); box-shadow: 0 0 16px rgba(56,189,248,.3); }

/* ===== 主体栅格 ===== */
.screen-body { flex: 1; display: grid; grid-template-columns: 1fr 1.32fr 1fr; gap: 18px; padding: 18px 20px; overflow: hidden; }
.col { display: flex; flex-direction: column; gap: 18px; min-height: 0; }

/* ===== 面板:切角 + 渐变发丝边 + 内发光 ===== */
.panel {
  position: relative; flex: 1; display: flex; flex-direction: column; min-height: 0;
  padding: 16px 18px;
  background: linear-gradient(180deg, rgba(16,32,56,.72), rgba(8,18,34,.72));
  border: 1px solid var(--line);
  border-radius: 4px;
  box-shadow: inset 0 1px 0 rgba(120,200,255,.06), 0 8px 30px rgba(0,0,0,.35);
  clip-path: polygon(0 0, calc(100% - 14px) 0, 100% 14px, 100% 100%, 14px 100%, 0 calc(100% - 14px));
}
/* 左上 / 右下 角标高亮 */
.panel::before, .panel::after {
  content: ''; position: absolute; width: 16px; height: 16px; pointer-events: none;
}
.panel::before { top: -1px; left: -1px; border-top: 2px solid var(--accent); border-left: 2px solid var(--accent); }
.panel::after { bottom: -1px; right: -1px; border-bottom: 2px solid var(--accent); border-right: 2px solid var(--accent); }
.panel.big { flex: 1.5; }
.panel-title {
  position: relative; font-size: 15px; font-weight: 700; color: #bfe6ff; margin-bottom: 12px;
  padding-left: 12px; letter-spacing: 1px;
}
.panel-title::before {
  content: ''; position: absolute; left: 0; top: 2px; bottom: 2px; width: 3px;
  background: linear-gradient(180deg, var(--accent), var(--accent-3)); border-radius: 2px;
  box-shadow: 0 0 8px var(--accent);
}
.panel-title.inline { margin: 6px 0 4px; }
/* ===== KPI 卡 ===== */
.kpi-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; flex: 1; align-content: center; }
.kpi {
  position: relative; text-align: center; padding: 14px 8px; border-radius: 4px;
  background: linear-gradient(180deg, rgba(56,189,248,.09), rgba(56,189,248,.02));
  border: 1px solid var(--line); overflow: hidden;
}
.kpi::before { content:''; position:absolute; left:0; top:0; bottom:0; width:2px; background: var(--accent); opacity:.7; }
.kpi-v { font-size: 30px; font-weight: 800; color: var(--accent-2); line-height: 1.1; font-variant-numeric: tabular-nums; text-shadow: 0 0 16px rgba(34,211,238,.35); }
.kpi-v i { font-size: 14px; font-style: normal; font-weight: 600; color: var(--text-dim); margin: 0 1px; }
.kpi-v.ok { color: var(--ok); text-shadow: 0 0 16px rgba(52,211,153,.35); }
.kpi-v.off { color: var(--text-dim); text-shadow: none; }
.kpi-v.warn { color: var(--warn); text-shadow: 0 0 16px rgba(251,191,36,.35); }
.kpi-l { font-size: 13px; color: var(--text-dim); margin-top: 6px; letter-spacing: 1px; }

.chart { flex: 1; min-height: 200px; }
.chart-lg { flex: 1; min-height: 260px; }

/* ===== 中央核心指标 ===== */
.center-metrics { display: flex; justify-content: space-around; gap: 12px; padding: 6px 0 14px; }
.cm { flex: 1; text-align: center; padding: 12px 6px; border-radius: 4px; background: rgba(56,189,248,.05); border: 1px solid var(--line); }
.cm.hero { background: rgba(251,113,133,.08); border-color: rgba(251,113,133,.3); }
.cm-v { font-size: 32px; font-weight: 800; color: var(--accent-2); font-variant-numeric: tabular-nums; text-shadow: 0 0 18px rgba(34,211,238,.35); }
.cm-v i { font-size: 15px; font-style: normal; font-weight: 600; color: var(--text-dim); margin: 0 2px; }
.cm-v.danger { color: var(--danger); text-shadow: 0 0 18px rgba(251,113,133,.4); }
.cm-l { font-size: 13px; color: var(--text-dim); margin-top: 6px; letter-spacing: 1px; }

/* ===== 合同执行进度 ===== */
.bar-list { display: flex; flex-direction: column; gap: 18px; justify-content: center; flex: 1; }
.bar-row { display: flex; align-items: center; gap: 12px; }
.bar-row > span { width: 56px; font-size: 13px; color: var(--text); }
.bar-row :deep(.el-progress) { flex: 1; }
.bar-row :deep(.el-progress-bar__outer) { background: rgba(56,120,180,.18) !important; }
.bar-row :deep(.el-progress__text) { color: var(--text) !important; font-weight: 600; }

/* ===== 实时告警 ===== */
.alarm-list { flex: 1; display: flex; flex-direction: column; gap: 9px; overflow: hidden; }
.alarm-row {
  display: flex; align-items: center; gap: 9px; font-size: 13px; padding: 9px 11px; border-radius: 4px;
  border-left: 2px solid transparent;
}
.alarm-row.high { background: rgba(251,113,133,.1); border-left-color: var(--danger); }
.alarm-row.mid { background: rgba(251,191,36,.08); border-left-color: var(--warn); }
.dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.dot.high { background: var(--danger); box-shadow: 0 0 10px var(--danger); animation: pulse 1.4s infinite; }
.dot.mid { background: var(--warn); box-shadow: 0 0 10px var(--warn); }
.alarm-txt { flex: 1; color: var(--text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.alarm-loc { color: var(--text-mute); font-size: 12px; }

/* ===== 招商漏斗 ===== */
.funnel { display: flex; flex-direction: column; gap: 14px; flex: 1; justify-content: center; }
.fn-row { display: flex; align-items: center; gap: 12px; }
.fn-l { width: 56px; font-size: 13px; color: var(--text); }
.fn-track { flex: 1; height: 26px; background: rgba(56,120,180,.14); border-radius: 4px; overflow: hidden; }
.fn-bar {
  height: 100%; display: flex; align-items: center; justify-content: flex-end;
  background: linear-gradient(90deg, var(--accent-3), var(--accent-2));
  border-radius: 4px; min-width: 42px; transition: width .8s cubic-bezier(.16,1,.3,1);
  box-shadow: 0 0 16px rgba(34,211,238,.4);
}
.fn-bar span { color: #04222e; font-weight: 700; font-size: 13px; padding-right: 10px; }
</style>
