<script setup>
import { computed } from 'vue'
import TwinIcon from './TwinIcon.vue'
import { FLOORS, MODULES, PARK_REFERENCE, POINTS } from './twinData'

const props = defineProps({
  side: { type: String, default: 'left', validator: value => ['left', 'right'].includes(value) },
  floor: { type: Number, default: null },
  compact: { type: Boolean, default: false },
})
const emit = defineEmits(['open-module', 'select-floor', 'select-point'])
const occupancy = (FLOORS.reduce((sum, floor) => sum + floor.occupancy, 0) / FLOORS.length).toFixed(1)
const referenceArea = Number(PARK_REFERENCE.totalArea.replace(/,/g, '')) / 10000
const rent = [
  { month: '4月', billed: 69, collected: 64 },
  { month: '5月', billed: 73, collected: 68 },
  { month: '6月', billed: 70, collected: 67 },
  { month: '7月', billed: 76, collected: 72 },
  { month: '8月', billed: 81, collected: 75 },
  { month: '9月', billed: 78.6, collected: 74.8 },
]
const devices = MODULES.filter(module => ['camera', 'fire'].includes(module.id))
const pendingPoints = computed(() => POINTS
  .filter(point => ['property', 'fire'].includes(point.module))
  .sort((a, b) => Number(b.floor === props.floor) - Number(a.floor === props.floor)))
</script>

<template>
  <aside class="twin-operations" :class="[`twin-operations--${side}`, { 'is-compact': compact }]" :aria-label="side === 'left' ? '经营数据面板（演示）与园区参考资料' : '运营数据面板（演示）'">
    <template v-if="side === 'left'">
      <section class="ops-card ops-occupancy">
        <header class="ops-heading">
          <h3>空间出租</h3><span class="ops-demo">演示</span>
          <button class="ops-link" type="button" aria-label="进入园区空间" @click="emit('open-module', 'park')"><TwinIcon name="chevron" :size="15" /></button>
        </header>
        <div class="ops-occupancy-overview">
          <div class="ops-occupancy-metric"><strong>{{ occupancy }}<small>%</small></strong><span>{{ FLOORS.length }} 层平均出租率 · 演示</span></div>
          <svg class="ops-occupancy-ring" viewBox="0 0 48 48" aria-hidden="true">
            <circle class="ops-ring-track" cx="24" cy="24" r="18" fill="none" stroke-width="5" />
            <circle class="ops-ring-value" cx="24" cy="24" r="18" fill="none" stroke-width="5" stroke-linecap="round" pathLength="100" :stroke-dasharray="`${occupancy} 100`" transform="rotate(-90 24 24)" />
          </svg>
        </div>
        <div class="ops-floor-list" aria-label="点击楼层定位模型">
          <button v-for="item in [...FLOORS].reverse()" :key="item.id" class="ops-floor-row" :class="{ 'is-selected': floor === item.id }" type="button" :aria-label="`定位 ${item.label}，演示出租率 ${item.occupancy}%`" :aria-pressed="floor === item.id" @click="emit('select-floor', item.id)">
            <b>{{ item.label }}</b><span class="ops-floor-track"><span :style="{ width: item.occupancy + '%' }" /></span><span>{{ item.occupancy }}<small>%</small></span>
          </button>
        </div>
        <div class="ops-reference" :aria-label="`来源：${PARK_REFERENCE.source}，园区建筑面积${referenceArea}万平方米，${PARK_REFERENCE.freightLifts}台货梯`">
          <span class="ops-reference-source">{{ PARK_REFERENCE.source }}</span>
          <span>{{ referenceArea }}万㎡</span><span>·</span><span>{{ PARK_REFERENCE.freightLifts }}台货梯</span>
        </div>
      </section>

      <section class="ops-card ops-rent">
        <header class="ops-heading">
          <h3>租金收缴</h3><span class="ops-demo">演示</span>
          <button class="ops-link" type="button" aria-label="进入租赁合同" @click="emit('open-module', 'contract')"><TwinIcon name="chevron" :size="15" /></button>
        </header>
        <div class="ops-metric-line"><div><strong>74.8<small>万元</small></strong><span>9月已收租金</span></div><div class="ops-rent-ratio"><b>95.2<small>%</small></b><span>收缴率</span></div></div>
        <div class="ops-rent-chart" role="img" aria-label="演示租金趋势，4月至9月应收分别为69、73、70、76、81、78.6万元，已收分别为64、68、67、72、75、74.8万元">
          <div v-for="item in rent" :key="item.month" class="ops-rent-column"><div class="ops-rent-bars"><i :style="{ height: `${item.billed / 90 * 100}%` }" /><i :style="{ height: `${item.collected / 90 * 100}%` }" /></div><span>{{ item.month }}</span></div>
        </div>
        <div class="ops-chart-legend"><span><i class="ops-dot ops-dot--pale" />应收</span><span><i class="ops-dot" />已收</span><span>单位：万元</span></div>
      </section>

      <section class="ops-card ops-property">
        <header class="ops-heading">
          <h3>物业工单</h3><span class="ops-demo">演示</span>
          <button class="ops-link" type="button" aria-label="进入物业服务" @click="emit('open-module', 'property')"><TwinIcon name="chevron" :size="15" /></button>
        </header>
        <div class="ops-workorder-summary"><div><strong>12</strong><span>待处理</span></div><div><strong>8</strong><span>处理中</span></div><div><strong>96<small>%</small></strong><span>完成率</span></div></div>
      </section>
    </template>

    <template v-else>
      <section class="ops-card ops-devices">
        <header class="ops-heading"><h3>安防设备</h3><span class="ops-demo">演示</span><TwinIcon class="ops-heading-icon" name="eye" :size="16" /></header>
        <div class="ops-device-total"><strong>176<small>个点位</small></strong></div>
        <button v-for="device in devices" :key="device.id" class="ops-device-row" type="button" :aria-label="`进入${device.name}`" @click="emit('open-module', device.id)">
          <span class="ops-device-icon" :class="{ 'ops-device-icon--fire': device.id === 'fire' }"><TwinIcon :name="device.icon" :size="18" /></span>
          <span class="ops-device-copy"><b>{{ device.name }}</b><span>{{ device.id === 'camera' ? '通道状态示例' : '月度巡检示例' }}</span></span>
          <strong>{{ device.metric }}<small>个</small></strong><TwinIcon name="chevron" :size="13" />
        </button>
        <div class="ops-device-status"><span><i class="ops-dot" />正常 172</span><span><i class="ops-dot ops-dot--amber" />待检查 4</span></div>
      </section>

      <section class="ops-card ops-pending">
        <header class="ops-heading"><h3>运营待办</h3><span class="ops-demo">演示</span><span class="ops-count">02</span></header>
        <div class="ops-pending-caption">{{ floor ? `${floor}F 关联事项优先` : '从事项定位到空间' }}</div>
        <button v-for="point in pendingPoints" :key="point.id" class="ops-pending-row" type="button" :class="{ 'is-selected': floor === point.floor }" :aria-label="`定位${point.name}，${point.status}`" @click="emit('select-point', point)">
          <span class="ops-pending-floor">{{ point.floor }}F</span><span class="ops-pending-copy"><b>{{ point.module === 'fire' ? '东区消防月度巡检' : '装卸平台例行维保' }}</b><span>{{ point.code }}<em>{{ point.status }}</em></span></span><TwinIcon name="chevron" :size="13" />
        </button>
      </section>

      <section class="ops-card ops-energy">
        <header class="ops-heading"><h3>公共区域用电</h3><span class="ops-demo">演示</span><button class="ops-link" type="button" aria-label="进入能源管理" @click="emit('open-module', 'energy')"><TwinIcon name="chevron" :size="15" /></button></header>
        <div class="ops-metric-line"><div><strong>12,486<small>kWh</small></strong><span>本月累计用电</span></div></div>
        <svg class="ops-energy-chart" viewBox="0 0 200 61" role="img" aria-label="演示近7日用电趋势：420、385、438、401、467、428、451千瓦时" preserveAspectRatio="none">
          <path class="ops-energy-grid" d="M0 56H200M0 30H200M0 4H200" fill="none" stroke-dasharray="3 4" />
          <path class="ops-energy-fill" d="M2 34L34 48L67 27L100 42L133 10L166 29L198 18V58H2Z" />
          <path class="ops-energy-line" d="M2 34L34 48L67 27L100 42L133 10L166 29L198 18" fill="none" stroke-width="2.2" stroke-linejoin="round" />
          <circle class="ops-energy-end" cx="198" cy="18" r="3" />
        </svg>
        <div class="ops-energy-axis"><span>近7日</span><span>单位：kWh</span></div>
      </section>
    </template>
  </aside>
</template>

<style scoped>
.twin-operations {
  --panel-compact: var(--ops-compact, 0);
  --panel-padding-x: 12px;
  --panel-bg: var(--ops-panel-bg, rgba(246, 251, 255, .82));
  --panel-ink: var(--ops-ink, #244761);
  --panel-muted: var(--ops-muted, #708b9d);
  --panel-line: var(--ops-line, rgba(121, 157, 181, .22));
  --panel-accent: var(--ops-accent, #279bb8);
  --panel-soft: var(--ops-accent-soft, color-mix(in srgb, var(--panel-accent) 12%, transparent));
  --panel-track: var(--ops-track, color-mix(in srgb, var(--panel-muted) 17%, transparent));
  --panel-bar: var(--ops-bar, color-mix(in srgb, var(--panel-accent) 70%, var(--panel-muted)));
  --panel-amber: var(--ops-amber, #b58d51);
  display: flex; flex-direction: column; gap: var(--ops-gap, calc(8px - var(--panel-compact) * 2px)); height: 100%; min-width: 0;
  color: var(--panel-ink); font-size: 12px; line-height: 1.25; font-variant-numeric: tabular-nums;
}
.twin-operations.is-compact { --ops-compact: 1; }
.ops-card { min-width: 0; padding: var(--ops-card-padding, calc(9px - var(--panel-compact) * 2px) var(--panel-padding-x)); border: 1px solid var(--panel-line); border-radius: 10px; background: var(--panel-bg); box-shadow: var(--ops-shadow, 0 5px 20px rgba(28, 63, 92, .07)); backdrop-filter: blur(16px) saturate(1.1); -webkit-backdrop-filter: blur(16px) saturate(1.1); }
.ops-heading { display: flex; align-items: center; gap: 7px; min-height: 19px; margin-bottom: calc(7px - var(--panel-compact) * 3px); }
.ops-heading h3 { margin: 0; font-size: 13px; font-weight: 650; letter-spacing: .2px; color: var(--panel-ink); }
.ops-demo { padding: 1px 4px; border: 1px solid var(--panel-line); border-radius: 4px; color: var(--panel-muted); font-size: 11px; line-height: 14px; font-weight: 400; white-space: nowrap; }
.twin-operations button { font: inherit; cursor: pointer; -webkit-tap-highlight-color: transparent; }
.twin-operations button:focus-visible { outline: 2px solid var(--panel-accent); outline-offset: 3px; }
.ops-link { display: grid; place-items: center; width: 23px; height: 23px; margin: -2px -5px -2px auto; padding: 0; border: 0; border-radius: 5px; background: transparent; color: var(--panel-muted); }
.ops-link:hover { background: var(--panel-soft); color: var(--panel-accent); }
.ops-heading-icon { margin-left: auto; color: var(--panel-muted); }
.ops-occupancy { display: flex; flex-direction: column; flex: 1.55; }
.ops-occupancy-overview { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin: 1px 0 calc(9px - var(--panel-compact) * 3px); }
.ops-occupancy-metric { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.ops-occupancy-metric > strong { color: var(--panel-ink); font-size: calc(30px - var(--panel-compact) * 2px); line-height: 1; font-weight: 650; letter-spacing: -.7px; white-space: nowrap; }
.ops-occupancy-metric small { margin-left: 3px; color: var(--panel-muted); font-size: 15px; font-weight: 500; letter-spacing: 0; }
.ops-occupancy-metric > span { color: var(--panel-muted); font-size: 11px; white-space: nowrap; }
.ops-occupancy-ring { display: block; flex: 0 0 44px; width: 44px; height: 44px; }
.ops-ring-track { stroke: var(--panel-track); }
.ops-ring-value { stroke: var(--panel-accent); }
.ops-floor-list { display: flex; flex: 1; flex-direction: column; justify-content: space-between; gap: calc(3px - var(--panel-compact) * 2px); }
.ops-floor-row { display: grid; grid-template-columns: 24px minmax(0, 1fr) 36px; align-items: center; gap: 9px; width: 100%; min-height: var(--ops-floor-height, calc(19px - var(--panel-compact) * 1px)); padding: calc(2px - var(--panel-compact) * 1px) 4px; border: 0; border-radius: 4px; color: var(--panel-muted); background: transparent; text-align: right; }
.ops-floor-row b { color: var(--panel-ink); font-size: 11px; font-weight: 550; text-align: left; }
.ops-floor-row > span:last-child { color: var(--panel-ink); font-size: 11px; white-space: nowrap; }
.ops-floor-row small { color: var(--panel-muted); font-size: 11px; margin-left: 1px; }
.ops-floor-track { height: 6px; background: var(--panel-track); border-radius: 4px; }
.ops-floor-track > span { display: block; height: 100%; border-radius: 4px; background: var(--panel-bar); }
.ops-floor-row:hover, .ops-floor-row.is-selected { background: var(--panel-soft); }
.ops-floor-row:hover b, .ops-floor-row.is-selected b, .ops-floor-row.is-selected > span:last-child { color: var(--panel-accent); }
.ops-floor-row.is-selected .ops-floor-track > span { background: var(--panel-accent); }
.ops-reference { display: flex; align-items: center; justify-content: space-between; gap: 4px; margin-top: calc(8px - var(--panel-compact) * 2px); padding-top: calc(7px - var(--panel-compact) * 2px); border-top: 1px solid var(--panel-line); color: var(--panel-muted); font-size: 11px; line-height: 1.2; white-space: nowrap; }
.ops-reference-source { color: var(--panel-muted); }
.ops-dot { display: inline-block; width: 5px; height: 5px; margin-right: 5px; border-radius: 50%; background: var(--panel-accent); vertical-align: 2px; }
.ops-rent { flex: .95; }
.ops-metric-line { display: flex; align-items: flex-end; justify-content: space-between; gap: 7px; }
.ops-metric-line > div { display: flex; flex-direction: column; gap: 5px; }
.ops-metric-line strong, .ops-device-total > strong { color: var(--panel-ink); font-size: 24px; line-height: 1; font-weight: 650; letter-spacing: -.6px; }
.ops-metric-line strong small, .ops-device-total > strong small { margin-left: 4px; color: var(--panel-muted); font-size: 11px; font-weight: 400; letter-spacing: 0; }
.ops-metric-line > div > span { color: var(--panel-muted); font-size: 11px; }
.ops-rent-ratio { align-items: flex-end; }
.ops-rent-ratio b { color: var(--panel-accent); font-size: 17px; font-weight: 600; }
.ops-rent-ratio small { font-size: 11px; margin-left: 1px; }
.ops-rent-chart { display: flex; justify-content: space-between; gap: 9px; margin: calc(8px - var(--panel-compact) * 2px) 0 calc(5px - var(--panel-compact) * 1px); }
.ops-rent-column { flex: 1; min-width: 0; text-align: center; }
.ops-rent-bars { display: flex; justify-content: center; align-items: flex-end; gap: 3px; height: var(--ops-rent-height, calc(40px - var(--panel-compact) * 8px)); border-bottom: 1px solid var(--panel-line); background: repeating-linear-gradient(to top, transparent 0, transparent 19px, var(--panel-line) 19px, var(--panel-line) 20px); }
.ops-rent-bars > i { width: 6px; border-radius: 2px 2px 0 0; background: var(--panel-track); }
.ops-rent-bars > i:last-child { background: var(--panel-accent); }
.ops-rent-column > span { display: block; margin-top: 5px; color: var(--panel-muted); font-size: 11px; white-space: nowrap; }
.ops-chart-legend { display: flex; align-items: center; gap: 8px; color: var(--panel-muted); font-size: 11px; }
.ops-chart-legend > span:last-child { margin-left: auto; }
.ops-dot--pale { background: var(--panel-track); }
.ops-property { flex: .5; }
.ops-workorder-summary { display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; }
.ops-workorder-summary > div { display: flex; flex-direction: column; gap: 3px; padding-right: 4px; border-right: 1px solid var(--panel-line); }
.ops-workorder-summary > div:last-child { border: 0; }
.ops-workorder-summary strong { font-size: 19px; line-height: 1.1; font-weight: 600; color: var(--panel-ink); }
.ops-workorder-summary > div:first-child strong { color: var(--panel-amber); }
.ops-workorder-summary small { font-size: 11px; }
.ops-workorder-summary span { color: var(--panel-muted); font-size: 11px; }
.ops-devices { flex: 1.1; }
.ops-device-total { display: flex; flex-direction: column; margin-bottom: 8px; }
.ops-device-row { display: flex; align-items: center; gap: 7px; width: 100%; padding: calc(6px - var(--panel-compact) * 1px) 0; border: 0; border-top: 1px solid var(--panel-line); background: transparent; text-align: left; color: var(--panel-muted); }
.ops-device-row:hover { color: var(--panel-accent); }
.ops-device-icon { display: grid; place-items: center; flex: 0 0 29px; width: 29px; height: 31px; border: 1px solid var(--panel-line); border-radius: 7px; background: var(--panel-soft); color: var(--panel-accent); }
.ops-device-icon--fire { background: color-mix(in srgb, var(--panel-amber) 12%, transparent); color: var(--panel-amber); }
.ops-device-copy { display: flex; flex: 1; min-width: 0; flex-direction: column; gap: 5px; }
.ops-device-copy b { font-size: 12px; color: var(--panel-ink); font-weight: 500; white-space: nowrap; }
.ops-device-copy > span { font-size: 11px; color: var(--panel-muted); white-space: nowrap; }
.ops-device-row > strong { font-size: 18px; color: var(--panel-ink); font-weight: 600; }
.ops-device-row > strong small { font-size: 11px; margin-left: 2px; font-weight: 400; }
.ops-device-status { display: flex; justify-content: space-between; gap: 8px; padding-top: 7px; border-top: 1px solid var(--panel-line); color: var(--panel-muted); font-size: 11px; }
.ops-dot--amber { background: var(--panel-amber); }
.ops-pending { flex: 1; }
.ops-count { margin-left: auto; color: var(--panel-muted); font-size: 12px; letter-spacing: 1px; }
.ops-pending-caption { color: var(--panel-muted); font-size: 11px; margin-bottom: 4px; }
.ops-pending-row { display: flex; align-items: center; gap: 7px; width: 100%; padding: calc(6px - var(--panel-compact) * 1px) 0; border: 0; border-bottom: 1px solid var(--panel-line); background: transparent; color: var(--panel-muted); text-align: left; }
.ops-pending-row:hover, .ops-pending-row.is-selected { color: var(--panel-accent); }
.ops-pending-floor { display: grid; place-items: center; flex: 0 0 26px; height: 29px; border: 1px solid var(--panel-line); border-radius: 6px; background: var(--panel-soft); color: var(--panel-accent); font-size: 11px; }
.ops-pending-row.is-selected .ops-pending-floor { border-color: var(--panel-accent); }
.ops-pending-copy { display: flex; flex: 1; min-width: 0; flex-direction: column; gap: 6px; }
.ops-pending-copy b { color: var(--panel-ink); font-size: 12px; font-weight: 500; white-space: nowrap; }
.ops-pending-copy > span { display: flex; align-items: center; justify-content: space-between; gap: 4px; color: var(--panel-muted); font-size: 11px; }
.ops-pending-copy em { color: var(--panel-amber); font-size: 11px; font-style: normal; white-space: nowrap; }
.ops-energy { flex: .85; }
.ops-energy-chart { display: block; width: 100%; height: calc(40px - var(--panel-compact) * 8px); margin-top: 8px; overflow: visible; }
.ops-energy-grid { stroke: var(--panel-line); }
.ops-energy-fill { fill: var(--panel-soft); }
.ops-energy-line { stroke: var(--panel-accent); }
.ops-energy-end { fill: var(--panel-accent); }
.ops-energy-axis { display: flex; justify-content: space-between; margin-top: 7px; color: var(--panel-muted); font-size: 11px; }
@media (max-width: 1440px) {
  .twin-operations { --panel-padding-x: 10px; }
  .ops-device-row { gap: 5px; }
  .ops-device-icon { flex-basis: 26px; width: 26px; }
  .ops-device-row > svg { display: none; }
  .ops-pending-row { gap: 5px; }
  .ops-pending-row > svg { display: none; }
}
</style>
