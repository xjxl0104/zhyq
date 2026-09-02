<template>
  <div class="commerce-page dashboard">
    <header class="commerce-page__header">
      <div>
        <div class="commerce-page__eyebrow">Park overview</div>
        <h1 class="commerce-page__title">园区经营首页</h1>
        <p class="commerce-page__subtitle">把今日待办、经营趋势与园区风险收进一个清晰的工作台。</p>
      </div>
      <div class="commerce-page__actions">
        <span class="commerce-chip"><el-icon><Calendar /></el-icon>近 30 天</span>
        <el-button class="commerce-action" type="primary" @click="$router.push('/screen')">
          <el-icon><Monitor /></el-icon><span>打开监控大屏</span>
        </el-button>
      </div>
    </header>

    <div class="stat-grid" aria-label="待办指标">
      <GlassSurface
        v-for="m in metrics"
        :key="m.key"
        variant="card"
        class="metric"
        :style="{ '--m-color': m.color, '--m-soft': m.soft }"
        role="button"
        tabindex="0"
        @click="$router.push(m.to)"
        @keydown.enter="$router.push(m.to)"
      >
        <div class="metric-top">
          <span class="metric-icon"><el-icon><component :is="m.icon" /></el-icon></span>
          <span class="metric-status">待处理</span>
        </div>
        <div class="metric-num">{{ wb[m.key] ?? 0 }}</div>
        <div class="metric-foot">
          <span class="metric-label">{{ m.label }}</span>
          <el-icon class="metric-go"><ArrowRight /></el-icon>
        </div>
      </GlassSurface>
    </div>

    <div class="dashboard-grid">
      <section class="commerce-card revenue-card">
        <div class="commerce-card__head">
          <div>
            <h2 class="commerce-card__title">经营概览</h2>
            <p class="commerce-card__meta">近 6 个月应收与实收表现</p>
          </div>
          <span class="commerce-badge"><el-icon><TrendCharts /></el-icon>实时口径</span>
        </div>

        <div class="overview-grid">
          <GlassSurface v-for="o in overviewCards" :key="o.label" variant="card" class="ov-card">
            <span class="ov-icon"><el-icon><component :is="o.icon" /></el-icon></span>
            <div>
              <div class="ov-label">{{ o.label }}</div>
              <div class="ov-num">{{ o.value }}</div>
            </div>
          </GlassSurface>
        </div>
        <div ref="trendRef" class="trend-chart" aria-label="应收实收趋势图"></div>
      </section>

      <div class="side-stack">
        <section class="commerce-card feed-card">
          <div class="commerce-card__head">
            <div>
              <h2 class="commerce-card__title">实时预警</h2>
              <p class="commerce-card__meta">优先展示未确认告警</p>
            </div>
            <el-button link type="primary" @click="$router.push('/iot/alarm')">查看全部</el-button>
          </div>
          <div class="commerce-card__body alarm-flow">
            <button
              v-for="a in alarms.slice(0, 4)"
              :key="a.id"
              class="alarm-card"
              :class="a.level === 3 ? 'high' : 'mid'"
              type="button"
              @click="$router.push('/iot/alarm')"
            >
              <span class="al-dot"></span>
              <span class="alarm-copy">
                <span class="al-txt">{{ a.content }}</span>
                <span class="al-loc">{{ a.location || '园区公共区域' }}</span>
              </span>
              <el-icon><ArrowRight /></el-icon>
            </button>
            <el-empty v-if="!alarms.length" description="暂无预警" :image-size="54" />
          </div>
        </section>

        <section class="commerce-card feed-card">
          <div class="commerce-card__head">
            <div>
              <h2 class="commerce-card__title">待办任务</h2>
              <p class="commerce-card__meta">按截止时间排序</p>
            </div>
            <el-button link type="primary" @click="$router.push('/oa/task')">查看全部</el-button>
          </div>
          <div class="commerce-card__body todo-list">
            <div v-for="t in todos.slice(0, 4)" :key="t.id" class="todo-item">
              <span class="todo-check"><el-icon><Check /></el-icon></span>
              <span class="todo-copy"><strong>{{ t.title }}</strong><small>{{ fmt(t.dueDate) || '待安排' }}</small></span>
              <span class="todo-priority" :class="t.priority === 3 ? 'urgent' : ''">{{ t.priority === 3 ? '紧急' : '普通' }}</span>
            </div>
            <el-empty v-if="!todos.length" description="暂无待办" :image-size="54" />
          </div>
        </section>
      </div>
    </div>

    <section class="commerce-card quick-card">
      <div class="quick-copy">
        <span class="commerce-page__eyebrow">Quick access</span>
        <h2 class="commerce-card__title">常用功能</h2>
      </div>
      <div class="quick-grid">
        <button v-for="q in quicks" :key="q.path" class="quick" type="button" @click="$router.push(q.path)">
          <span class="quick-icon"><el-icon><component :is="q.icon" /></el-icon></span>
          <span>{{ q.name }}</span>
          <el-icon class="quick-arrow"><ArrowRight /></el-icon>
        </button>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { dashboardApi } from '@/api/dashboard'
import { todoApi } from '@/api/todo'
import request from '@/utils/request'
import { useChart } from '@/composables/useChart'
import GlassSurface from '@/components/GlassSurface.vue'

const wb = reactive({})
const metrics = [
  { key: 'contractPending', label: '合同待审核', color: '#d99022', soft: '#fff7e8', icon: 'DocumentChecked', to: '/contract/list' },
  { key: 'contractExpiring', label: '合同即将到期', color: '#d95c62', soft: '#fff0f1', icon: 'Timer', to: '/contract/list' },
  { key: 'leadFollow', label: '线索待跟进', color: '#0a24e9', soft: '#eef0ff', icon: 'Promotion', to: '/crm/lead' },
  { key: 'approvalPending', label: '审批待处理', color: '#7256d8', soft: '#f3efff', icon: 'Stamp', to: '/oa/approval' },
  { key: 'todoCount', label: '待办任务', color: '#5c9764', soft: '#edf7ef', icon: 'List', to: '/oa/task' },
  { key: 'billUnpaid', label: '待催缴账单', color: '#c75675', soft: '#fff0f5', icon: 'Wallet', to: '/finance/overdue' },
  { key: 'workOrderPending', label: '待处理工单', color: '#277f9d', soft: '#eaf8fb', icon: 'Tools', to: '/property/workorder' }
]

const overviewCards = ref([])
const todos = ref([])
const alarms = ref([])
const trendRef = ref()
const trendData = reactive({ months: [], receivable: [], received: [] })

const quicks = [
  { name: '租控管理', path: '/building/room', icon: 'OfficeBuilding' },
  { name: '合同列表', path: '/contract/list', icon: 'Document' },
  { name: '账单管理', path: '/finance/bill', icon: 'Money' },
  { name: '物业报修', path: '/property/workorder', icon: 'Tools' },
  { name: '线索管理', path: '/crm/lead', icon: 'Promotion' },
  { name: '监控大屏', path: '/screen', icon: 'Monitor' }
]

function fmt(v) { return v ? String(v).substring(5, 16) : '' }

const { refresh: refreshTrend } = useChart(trendRef, (theme) => ({
  animationDuration: 700,
  tooltip: { trigger: 'axis', backgroundColor: '#0b0d17', borderWidth: 0, textStyle: { color: '#fff' } },
  legend: { right: 4, top: 0, data: ['应收', '实收'], icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle: { color: theme.textColor } },
  grid: { left: 16, right: 12, top: 42, bottom: 8, containLabel: true },
  xAxis: { type: 'category', boundaryGap: true, data: trendData.months, axisTick: { show: false }, axisLine: { lineStyle: { color: theme.axisLine } }, axisLabel: { color: theme.axisLabel } },
  yAxis: { type: 'value', splitNumber: 4, axisLine: { show: false }, axisTick: { show: false }, splitLine: { lineStyle: { color: theme.splitLine, type: 'dashed' } }, axisLabel: { color: theme.axisLabel } },
  series: [
    { name: '应收', type: 'bar', data: trendData.receivable, barMaxWidth: 26, itemStyle: { color: '#d7dbfb', borderRadius: [7, 7, 0, 0] } },
    { name: '实收', type: 'line', data: trendData.received, smooth: true, symbol: 'circle', symbolSize: 7, itemStyle: { color: '#0a24e9', borderColor: '#fff', borderWidth: 2 }, lineStyle: { color: '#0a24e9', width: 3 }, areaStyle: { color: 'rgba(10,36,233,.06)' } }
  ]
}))

async function load() {
  try { Object.assign(wb, await dashboardApi.workbench()) } catch (e) { /* 保留 0 占位 */ }

  try {
    const ov = await dashboardApi.overview()
    overviewCards.value = [
      { label: '在租房间', value: `${ov.room.rented} / ${ov.room.total}`, icon: 'House' },
      { label: '出租率', value: `${ov.room.rentRate}%`, icon: 'PieChart' },
      { label: '执行中合同', value: ov.contract.executing, icon: 'DocumentChecked' },
      { label: '在租租客', value: ov.other.tenantTotal, icon: 'UserFilled' }
    ]
  } catch (e) {
    overviewCards.value = [
      { label: '在租房间', value: '—', icon: 'House' },
      { label: '出租率', value: '—', icon: 'PieChart' },
      { label: '执行中合同', value: '—', icon: 'DocumentChecked' },
      { label: '在租租客', value: '—', icon: 'UserFilled' }
    ]
  }

  try { todos.value = await todoApi.list() } catch (e) { todos.value = [] }
  try {
    const al = await request.get('/iot/alarm/page', { params: { pageNo: 1, pageSize: 5, status: 1 } })
    let list = al.records || []
    if (!list.length) {
      const recent = await request.get('/iot/alarm/page', { params: { pageNo: 1, pageSize: 5 } })
      list = recent.records || []
    }
    alarms.value = list
  } catch (e) { alarms.value = [] }

  try {
    const t = await dashboardApi.revenueTrend()
    Object.assign(trendData, { months: t.months, receivable: t.receivable, received: t.received })
  } catch (e) { /* 趋势图留空 */ }
  await nextTick()
  refreshTrend()
}

onMounted(load)
</script>

<style scoped>
.dashboard { --metric-gap: 12px; }
.stat-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(128px, 1fr));
  gap: var(--metric-gap);
  margin-bottom: 16px;
}
.metric {
  min-width: 0;
  padding: 15px;
  cursor: pointer;
  background: #fff;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-card);
  transition: transform .18s ease, border-color .18s ease, box-shadow .18s ease;
}
.metric:hover, .metric:focus-visible {
  border-color: #cdd3e0;
  outline: none;
  transform: translateY(-2px);
  box-shadow: 0 12px 26px rgba(41, 49, 93, .08);
}
.metric :deep(.glass-surface__veil) { display: none; }
.metric-top, .metric-foot { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.metric-icon {
  display: inline-grid; place-items: center; width: 32px; height: 32px;
  color: var(--m-color); background: var(--m-soft); border-radius: 10px;
}
.metric-status { color: var(--text-muted); font-size: 9px; font-weight: 650; }
.metric-num {
  margin: 18px 0 9px;
  color: var(--text-title);
  font-size: 27px;
  font-weight: 740;
  font-variant-numeric: tabular-nums;
  letter-spacing: -.04em;
}
.metric-label { min-width: 0; overflow: hidden; color: var(--text-secondary); font-size: 11px; white-space: nowrap; text-overflow: ellipsis; }
.metric-go { flex: 0 0 auto; color: var(--m-color); font-size: 12px; }

.dashboard-grid { display: grid; grid-template-columns: minmax(0, 2.05fr) minmax(300px, .95fr); gap: 16px; }
.revenue-card { min-height: 520px; }
.overview-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; padding: 16px 20px 0; }
.ov-card {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 13px;
  background: #f8f9fc;
  border: 1px solid #eef0f4;
  border-radius: 13px;
  box-shadow: none;
}
.ov-card :deep(.glass-surface__content) { display: flex; align-items: center; gap: 11px; }
.ov-card :deep(.glass-surface__veil) { display: none; }
.ov-icon { display: grid; place-items: center; width: 30px; height: 30px; color: var(--brand); background: #eef0ff; border-radius: 9px; }
.ov-label { color: var(--text-muted); font-size: 10px; }
.ov-num { margin-top: 3px; color: var(--text-title); font-size: 17px; font-weight: 720; letter-spacing: -.025em; }
.trend-chart { height: 352px; margin: 4px 14px 12px; }

.side-stack { display: grid; gap: 16px; grid-template-rows: 1fr 1fr; }
.feed-card { min-height: 0; overflow: hidden; }
.alarm-flow, .todo-list { display: flex; flex-direction: column; gap: 8px; }
.alarm-card {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px 11px;
  color: inherit;
  text-align: left;
  background: #f8f9fb;
  border: 1px solid transparent;
  border-radius: 11px;
  cursor: pointer;
}
.alarm-card:hover { background: #f3f5f9; border-color: var(--border); }
.al-dot { width: 8px; height: 8px; flex: 0 0 8px; border-radius: 50%; }
.alarm-card.high .al-dot { background: #d95c62; box-shadow: 0 0 0 4px #fff0f1; }
.alarm-card.mid .al-dot { background: #d99022; box-shadow: 0 0 0 4px #fff7e8; }
.alarm-copy { display: grid; min-width: 0; flex: 1; gap: 3px; }
.al-txt { overflow: hidden; color: var(--text-title); font-size: 11px; font-weight: 600; white-space: nowrap; text-overflow: ellipsis; }
.al-loc { color: var(--text-muted); font-size: 9px; }
.alarm-card > .el-icon { color: var(--text-muted); font-size: 11px; }

.todo-item { display: flex; align-items: center; gap: 10px; min-width: 0; padding: 8px 0; border-bottom: 1px solid #f0f2f5; }
.todo-item:last-child { border-bottom: 0; }
.todo-check { display: grid; place-items: center; width: 28px; height: 28px; flex: 0 0 28px; color: var(--brand); background: #eef0ff; border-radius: 9px; }
.todo-copy { display: grid; min-width: 0; flex: 1; gap: 3px; }
.todo-copy strong { overflow: hidden; color: var(--text-title); font-size: 11px; font-weight: 620; white-space: nowrap; text-overflow: ellipsis; }
.todo-copy small { color: var(--text-muted); font-size: 9px; }
.todo-priority { padding: 3px 6px; color: #68708a; background: #f0f2f5; border-radius: 999px; font-size: 8px; }
.todo-priority.urgent { color: #c14950; background: #fff0f1; }

.quick-card { display: flex; align-items: center; gap: 24px; margin-top: 16px; padding: 18px 20px; }
.quick-copy { width: 140px; flex: 0 0 140px; }
.quick-copy .commerce-page__eyebrow { margin-bottom: 5px; font-size: 9px; }
.quick-grid { display: grid; grid-template-columns: repeat(6, 1fr); gap: 10px; width: 100%; }
.quick {
  display: flex; align-items: center; gap: 9px; min-width: 0; padding: 10px 11px;
  color: var(--text-body); background: #f8f9fb; border: 1px solid transparent; border-radius: 11px; cursor: pointer;
  transition: background .16s, border-color .16s, color .16s;
}
.quick:hover { color: var(--brand); background: #eef0ff; border-color: #d7dbfb; }
.quick-icon { display: grid; place-items: center; width: 28px; height: 28px; flex: 0 0 28px; color: var(--brand); background: #fff; border-radius: 8px; }
.quick > span:nth-child(2) { overflow: hidden; font-size: 10px; font-weight: 600; white-space: nowrap; text-overflow: ellipsis; }
.quick-arrow { margin-left: auto; color: var(--text-muted); font-size: 10px; }

@media (max-width: 1380px) {
  .stat-grid { grid-template-columns: repeat(4, 1fr); }
  .quick-grid { grid-template-columns: repeat(3, 1fr); }
}
@media (max-width: 1100px) {
  .dashboard-grid { grid-template-columns: 1fr; }
  .side-stack { grid-template-columns: 1fr 1fr; grid-template-rows: auto; }
}
@media (max-width: 760px) {
  .stat-grid, .overview-grid, .side-stack { grid-template-columns: 1fr 1fr; }
  .quick-card { align-items: flex-start; flex-direction: column; }
  .quick-copy { width: auto; flex-basis: auto; }
  .quick-grid { grid-template-columns: 1fr 1fr; }
}
</style>
