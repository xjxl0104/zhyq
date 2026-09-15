<template>
  <div class="monitoring-grid">
    <section class="commerce-card monitoring-panel" aria-label="实时告警">
      <div class="commerce-card__head">
        <div>
          <h2 class="commerce-card__title">实时告警</h2>
          <p class="commerce-card__meta">{{ alarmLabel }} · 每 15 秒更新</p>
        </div>
        <RouterLink class="panel-link" to="/iot/alarm">查看全部</RouterLink>
      </div>
      <p v-if="alarmError" class="panel-status" role="status">{{ alarmError }}</p>
      <ul v-if="alarms.length" class="alarm-list">
        <li v-for="alarm in alarms" :key="alarm.id" class="alarm-row">
          <el-tag :type="alarm.level === 3 ? 'danger' : alarm.level === 2 ? 'warning' : 'info'" size="small">{{ { 1: '低', 2: '中', 3: '高' }[alarm.level] || '未知' }}</el-tag>
          <div><strong>{{ alarm.content }}</strong><span>{{ alarm.location || '园区公共区域' }}</span></div>
          <span class="alarm-state">{{ alarmStates[alarm.status] || '未知状态' }}</span>
        </li>
      </ul>
      <el-empty v-else-if="alarmLoaded && !alarmError" description="暂无告警" :image-size="46" />
      <p v-else-if="!alarmError" class="panel-status">正在加载告警…</p>
    </section>

    <section class="commerce-card monitoring-panel" aria-label="招商转化">
      <div class="commerce-card__head">
        <div><h2 class="commerce-card__title">招商转化</h2><p class="commerce-card__meta">各阶段实际数量 · 每分钟更新</p></div>
        <RouterLink class="panel-link" to="/crm/analysis">查看分析</RouterLink>
      </div>
      <p v-if="funnelError" class="panel-status" role="status">{{ funnelError }}</p>
      <ul v-if="funnel.length" class="funnel-list">
        <li v-for="item in funnel" :key="item.name" class="funnel-row">
          <span>{{ item.name }}</span>
          <span class="funnel-track" aria-hidden="true"><i :style="{ width: `${funnelMax ? Number(item.value || 0) / funnelMax * 100 : 0}%` }"></i></span>
          <strong>{{ item.value || 0 }}</strong>
        </li>
      </ul>
      <el-empty v-else-if="funnelLoaded && !funnelError" description="暂无招商数据" :image-size="46" />
      <p v-else-if="!funnelError" class="panel-status">正在加载招商数据…</p>
    </section>
  </div>
</template>

<script setup>
import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref } from 'vue'
import { alarmApi } from '@/api/iot'
import { analysisApi } from '@/api/crm'

const alarms = ref([]), alarmTotal = ref(0), recentAlarms = ref(false), alarmLoaded = ref(false), alarmError = ref('')
const funnel = ref([]), funnelLoaded = ref(false), funnelError = ref('')
const alarmStates = { 1: '待确认', 2: '已确认', 3: '处理中', 4: '已恢复', 5: '已关闭', 6: '误报' }
const alarmLabel = computed(() => !alarmLoaded.value ? '优先展示未确认事件' : recentAlarms.value ? '最近告警' : `待确认 ${alarmTotal.value} 条`)
const funnelMax = computed(() => Math.max(0, ...funnel.value.map(item => Number(item.value || 0))))
let active = false, disposed = false, alarmTimer, funnelTimer, alarmPending, funnelPending

function loadAlarms() {
  if (alarmPending) return alarmPending
  alarmPending = (async () => {
    try {
      let result = await alarmApi.page({ pageNo: 1, pageSize: 4, status: 1 })
      const recent = !(result.records || []).length
      if (recent) result = await alarmApi.page({ pageNo: 1, pageSize: 4 })
      if (disposed) return
      alarms.value = result.records || []
      alarmTotal.value = Number(result.total || 0)
      recentAlarms.value = recent
      alarmLoaded.value = true
      alarmError.value = ''
    } catch {
      if (!disposed) alarmError.value = alarmLoaded.value ? '更新失败，显示上次告警；可点击刷新数据重试。' : '告警加载失败，可点击刷新数据重试。'
    }
  })().finally(() => { alarmPending = null })
  return alarmPending
}

function loadFunnel() {
  if (funnelPending) return funnelPending
  funnelPending = (async () => {
    try {
      const data = await analysisApi.funnel()
      if (disposed) return
      funnel.value = data || []
      funnelLoaded.value = true
      funnelError.value = ''
    } catch {
      if (!disposed) funnelError.value = funnelLoaded.value ? '更新失败，显示上次招商数据；可点击刷新数据重试。' : '招商数据加载失败，可点击刷新数据重试。'
    }
  })().finally(() => { funnelPending = null })
  return funnelPending
}

function refresh() { return Promise.all([loadAlarms(), loadFunnel()]) }
function activate() {
  if (active) return
  active = true
  refresh()
  alarmTimer = setInterval(loadAlarms, 15000)
  funnelTimer = setInterval(loadFunnel, 60000)
}
function deactivate() {
  active = false
  clearInterval(alarmTimer)
  clearInterval(funnelTimer)
}
onMounted(activate)
onActivated(activate)
onDeactivated(deactivate)
onBeforeUnmount(() => { disposed = true; deactivate() })
defineExpose({ refresh })
</script>

<style scoped>
.monitoring-grid { display:grid;grid-template-columns:1.3fr 1fr;gap:16px;margin:16px 0; }
.monitoring-panel { min-width:0; }
.panel-link { color:var(--brand);font-size:12px;text-decoration:none;white-space:nowrap; }
.panel-link:hover { text-decoration:underline; }
.panel-link:focus-visible { outline:2px solid var(--brand);outline-offset:4px;border-radius:2px; }
.panel-status { margin:16px 20px;color:var(--text-secondary);font-size:13px; }
.alarm-list,.funnel-list { list-style:none;margin:0;padding:0 20px 16px; }
.alarm-row { display:flex;align-items:center;gap:12px;padding:11px 0;border-bottom:1px solid var(--border); }
.alarm-row:last-child { border-bottom:0; }
.alarm-row > div { display:grid;gap:5px;flex:1;min-width:0; }
.alarm-row strong { font-size:13px;font-weight:600;color:var(--text-body);overflow-wrap:anywhere; }
.alarm-row div span,.alarm-state { font-size:12px;color:var(--text-secondary); }
.alarm-state { white-space:nowrap; }
.funnel-row { display:grid;grid-template-columns:64px minmax(0,1fr) 48px;align-items:center;gap:14px;padding:12px 0;font-size:13px;color:var(--text-body); }
.funnel-row strong { text-align:right;font-variant-numeric:tabular-nums; }
.funnel-track { height:9px;background:var(--bg-subtle);border-radius:5px;overflow:hidden; }
.funnel-track i { display:block;height:100%;background:var(--brand);border-radius:5px; }
@media (max-width:1000px) { .monitoring-grid { grid-template-columns:1fr; } }
</style>
