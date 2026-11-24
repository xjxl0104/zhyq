<template>
  <view class="admin-overview">
    <view class="card filters">
      <view class="filter-title">{{ view === 'partners' ? '伙伴总览' : '云仓总览' }}</view>
      <view class="segments">
        <button v-for="item in presets" :key="item.value" class="segment" :class="{ active: preset === item.value }" @click="setPreset(item.value)">{{ item.label }}</button>
      </view>
      <view class="date-row"><picker mode="date" :value="range.startDate" :end="range.endDate" @change="setDate('startDate', $event)"><view>{{ range.startDate }} 起</view></picker>
        <text>—</text><picker mode="date" :value="range.endDate" :start="range.startDate" :end="today" @change="setDate('endDate', $event)"><view>{{ range.endDate }} 止</view></picker></view>
      <view class="caption">{{ warehouse ? '承接、签约及入仓按客户去重；发货票数和发货客户按所选日期统计。' : '客户和合同为当前档案，发货量和佣金按所选日期统计。' }}</view>
    </view>
    <view v-if="selectionId" class="section-head"><text class="section-title">{{ selectionName }}</text><text class="section-link" @click="back">返回全部 ›</text></view>
    <view v-if="error" class="card"><view class="error">{{ error }}</view><button class="btn secondary" @click="load(false)">重新加载</button></view>
    <view v-if="summary" class="card overview-card">
      <view class="total-label">{{ selectionId ? '范围内总发货量' : '总发货量' }}（票）</view>
      <view class="volume">{{ count(summary.shipmentVolume) }}</view>
      <view class="metric-grid">
        <view v-for="item in indicators" :key="item.label"><text class="caption">{{ item.label }}</text><text>{{ count(item.value) }}</text></view>
      </view>
    </view>
    <view v-if="summary" class="card">
      <view class="chart-title">{{ warehouse ? '云仓客户服务情况' : '伙伴佣金结算情况' }}</view>
      <view v-if="!warehouse" class="row"><text>客户总佣金</text><text>{{ money(summary.commissionTotal) }} 元</text></view>
      <view v-if="!warehouse" class="row"><text>伙伴所得佣金</text><text>{{ money(summary.partnerCommission) }} 元</text></view>
      <view v-for="bar in distribution" :key="bar.label" class="bar-item">
        <view class="bar-label"><text>{{ bar.label }}</text><text>{{ bar.display }}</text></view>
        <view class="bar-track"><view class="bar-fill" :style="{width: bar.width,backgroundColor:bar.color}" /></view>
      </view>
      <view class="caption">{{ warehouse ? '签约含已签合同及业务员已跟进到签约的客户。入仓按货品入仓、正式发货阶段统计。' : '已结算金额包含已提现部分。' }}</view>
    </view>
    <view v-if="ranking.length" class="card">
      <view class="chart-title">{{ selectionId ? '客户' : warehouse ? '云仓' : group === 'p4' ? 'P4 团队' : '伙伴团队' }}发货量前 {{ ranking.length }}</view>
      <view v-for="bar in ranking" :key="bar.id" class="bar-item pressable" @click="select(bar)">
        <view class="bar-label"><text>{{ bar.name }} ›</text><text>{{ bar.display }}</text></view>
        <view class="bar-track"><view class="bar-fill" :style="{width:bar.width}" /></view>
      </view>
    </view>
    <view class="card filters">
      <view v-if="view === 'partners' && !selectionId" class="segments">
        <button class="segment" :class="{ active: group === 'p4' }" @click="setGroup('p4')">P4 团队</button>
        <button class="segment" :class="{ active: group === 'all' }" @click="setGroup('all')">全部伙伴</button>
      </view>
      <view class="search-row"><input class="input" v-model="keyword" maxlength="100" :placeholder="selectionId ? '搜索客户名称' : '搜索名称或手机号'" confirm-type="search" @confirm="search" /><button class="btn secondary search-button" @click="search">搜索</button></view>
      <view v-if="view === 'partners' && !selectionId" class="caption">每位伙伴包含本人及下级客户；团队存在上下级时，列表数字不重复累加到全局汇总。</view>
      <view v-else class="caption">点击列表查看客户与合同明细。</view>
    </view>
    <view v-if="loading" class="card muted">正在读取总览…</view>
    <view v-if="!loading && !error && !records.length" class="card muted">没有符合条件的记录</view>
    <view v-for="row in records" :key="row.id" class="card record pressable" @click="select(row)">
      <view class="record-head"><view class="record-title">{{ row.name }}</view><text class="section-link">查看 ›</text></view>
      <view class="caption">{{ subtitle(row) }}</view>
      <view class="record-metrics"><view><text class="caption">发货量（票）</text><text>{{ count(row.summary?.shipmentVolume) }}</text></view><view><text class="caption">{{ selectionId ? '已签合同' : '客户数' }}</text><text>{{ count(selectionId ? row.summary?.contractCount : row.summary?.customerCount) }}</text></view></view>
      <view v-if="warehouse" class="row"><text>签约 / 入仓客户</text><text>{{ count(row.summary?.signedCustomerCount) }} / {{ count(row.summary?.storedCustomerCount) }} 家</text></view>
      <view v-if="warehouse" class="row"><text>统计期有发货客户</text><text>{{ count(row.summary?.shippingCustomerCount) }} 家</text></view>
      <view v-else class="row"><text>客户总佣金</text><text>{{ money(row.summary?.commissionTotal) }} 元</text></view>
      <view v-if="!warehouse" class="row"><text>伙伴所得佣金</text><text>{{ money(row.summary?.partnerCommission) }} 元</text></view>
    </view>
    <button v-if="records.length < total && !error" class="btn secondary load-more" :loading="loading" :disabled="loading" @click="load(true)">加载更多（{{ records.length }}/{{ total }}）</button>
    <view v-else-if="total" class="list-end">共 {{ total }} 条，已全部显示</view>
  </view>
</template>
<script setup>
import { ref, computed, watch, onUnmounted } from 'vue'
import { adminOverviewApi } from '@/api'
import { navigateTo } from '@/utils/nav.mjs'
import { overviewRange, overviewQuery, overviewMoney as money, overviewCount as count, PARTNER_STATUS, WAREHOUSE_STATUS } from '@/utils/admin-overview.mjs'
const props = defineProps({ view: { type: String, required: true }, refreshKey: { type: Number, default: 0 } })
const presets = [{ value: 'all', label: '累计' }, { value: 'month', label: '本月' }, { value: 'recent', label: '近30天' }]
const preset = ref('all'), range = ref(overviewRange('all')), group = ref('p4'), keyword = ref('')
const appliedKeyword = ref('')
const today = computed(() => overviewRange('all').endDate)
const warehouse = computed(() => props.view === 'warehouses')
const selectionId = ref(null), selectionName = ref(''), summary = ref(null), records = ref([])
const total = ref(0), page = ref(1), loading = ref(false), error = ref(''), kind = ref('')
const indicators = computed(() => (warehouse.value
  ? [['云仓','warehouseCount'],['期内发货客户','shippingCustomerCount'],['关联客户','customerCount'],['已签合同','contractCount'],['签约客户','signedCustomerCount'],['已入仓客户','storedCustomerCount']]
  : [['涉及云仓','warehouseCount'],['团队伙伴','partnerCount'],['P4 伙伴','p4Count'],['客户','customerCount'],['已签合同','contractCount']]
).map(([label,key]) => ({ label, value:summary.value?.[key] })))
function chartBars(items) {
  const max = Math.max(0,...items.map(item => Math.abs(Number(item.value) || 0)))
  return items.map(item => ({ ...item, width: (max ? 100 * Math.abs(Number(item.value) || 0) / max : 0) + '%' }))
}
const distribution = computed(() => {
  const fields = warehouse.value
    ? [['待承接','pendingCustomerCount','#c48418'],['已承接','acceptedCustomerCount','#536af4'],['已签约','signedCustomerCount','#387cca'],['已入仓','storedCustomerCount','#178a83'],['期内有发货','shippingCustomerCount','#207a52']]
    : [['待结算','pendingCommission','#bd891b'],['已结算','settledCommission','#328b88'],['已提现','withdrawnAmount','#566ce4']]
  return chartBars(fields.map(([label,key,color]) => {
    const value = summary.value?.[key]
    return {label,value,color,display: warehouse.value ? count(value) + ' 家' : money(value) + ' 元'}
  }))
})
const ranking = computed(() => chartBars(records.value.slice(0,5).filter(row => Number(row.summary?.shipmentVolume) > 0)
  .map(row => ({...row,value:row.summary.shipmentVolume,display:count(row.summary.shipmentVolume) + ' 票'}))))
let sequence = 0
async function load(append = false) {
  if (append && loading.value) return
  const current = ++sequence
  const next = append ? page.value + 1 : 1
  if (!append) { records.value = []; summary.value = null; total.value = 0 }
  loading.value = true; error.value = ''
  try {
    const result = await adminOverviewApi.overview(overviewQuery({ view: props.view, selectionId: selectionId.value, group: group.value, keyword: appliedKeyword.value, range: range.value, page: next }))
    if (current !== sequence) return
    records.value = append ? [...records.value, ...(result.records || [])] : result.records || []
    summary.value = result.summary; total.value = result.total || 0; page.value = next
    selectionName.value = result.selectionName || ''; kind.value = result.kind
  } catch (e) { if (current === sequence) error.value = e.message || '总览读取失败，请重试' }
  finally { if (current === sequence) loading.value = false }
}
function setPreset(value) { preset.value = value; range.value = overviewRange(value); load() }
function setDate(key, event) { range.value = { ...range.value, [key]: event.detail.value }; preset.value = 'custom'; load() }
function setGroup(value) { group.value = value; load() }
function search() { appliedKeyword.value = keyword.value; load() }
function back() { selectionId.value = null; keyword.value = ''; appliedKeyword.value = ''; load() }
function select(row) {
  if (kind.value === 'customers') return navigateTo(`/pages/admin-customer/index?id=${row.id}&startDate=${range.value.startDate}&endDate=${range.value.endDate}${warehouse.value ? '&warehouseId=' + selectionId.value : ''}`)
  selectionId.value = row.id; keyword.value = ''; appliedKeyword.value = ''; load()
}
function subtitle(row) {
  if (kind.value === 'partners') return `${row.positionCode || '未设置称号'} · ${PARTNER_STATUS[row.status] || '未知状态'}${row.phone ? ' · ' + row.phone : ''}`
  if (kind.value === 'warehouses') return `${WAREHOUSE_STATUS[row.joinStatus] || '未知状态'}${row.contact ? ' · ' + row.contact : ''}${row.phone ? ' · ' + row.phone : ''}`
  return [row.brandName && `品牌：${row.brandName}`, row.referrerName && `推荐人：${row.referrerName}`].filter(Boolean).join(' · ')
}
watch(() => props.view, () => { selectionId.value = null; keyword.value = ''; appliedKeyword.value = ''; group.value = 'p4'; load() }, { immediate: true })
watch(() => props.refreshKey, () => load())
onUnmounted(() => { sequence++ })
</script>
<style scoped>
.filter-title { font-size: 36rpx; font-weight: 700; margin-bottom: 20rpx; }
.segments { display: flex; gap: 12rpx; margin-bottom: 20rpx; }
.segment { flex: 1; margin: 0; padding: 12rpx 8rpx; font-size: 28rpx; line-height: 1.5; background: #f1f3fc; color: #58617d; border-radius: 14rpx; }
.segment.active { color: #fff; background: #3857f5; }
.segment::after { border: 0; }
.date-row { display: flex; justify-content: space-between; gap: 12rpx; font-size: 26rpx; margin-bottom: 18rpx; }
.date-row picker { padding: 12rpx 4rpx; color: #2e47cc; }
.total-label { color: #58617d; font-size: 28rpx; }
.volume { font-size: 64rpx; font-weight: 800; color: #3857f5; margin: 14rpx 0 24rpx; }
.metric-grid { display: flex; flex-wrap: wrap; gap: 24rpx; margin-bottom: 22rpx; }
.metric-grid > view { min-width: 96rpx; display: flex; flex-direction: column; gap: 10rpx; font-size: 34rpx; font-weight: 600; }
.search-row { display: flex; align-items: center; gap: 16rpx; margin-bottom: 16rpx; }
.search-row .input { min-width: 0; flex: 1; margin: 0; }
.search-button { width: 120rpx; margin: 0; padding: 14rpx 10rpx; font-size: 28rpx; }
.record-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 18rpx; margin-bottom: 12rpx; }
.record-title { font-size: 32rpx; font-weight: 700; flex: 1; overflow-wrap: anywhere; }
.record-metrics { display: flex; gap: 60rpx; margin-top: 22rpx; }
.record-metrics > view { display: flex; flex-direction: column; font-size: 36rpx; gap: 8rpx; }
.row { gap: 20rpx; font-size: 28rpx; }
.row > text:last-child { text-align: right; }
.error { color: #a3293e; }
.load-more { margin: 24rpx; width: calc(100% - 48rpx); }
.chart-title {font-size:32rpx;font-weight:700;margin-bottom:14rpx}
.bar-item {padding:11rpx 0}.bar-label {display:flex;align-items:center;justify-content:space-between;gap:22rpx;font-size:26rpx}
.bar-label text:first-child {flex:1;min-width:0;overflow-wrap:anywhere}.bar-label text:last-child {font-weight:600}
.bar-track {height:12rpx;border-radius:8rpx;background:#edf0f8;margin:9rpx 0 4rpx;overflow:hidden}
.bar-fill {height:100%;border-radius:8rpx;background:#4165da}.metric-grid {display:grid;grid-template-columns:repeat(2,minmax(0,1fr))}
</style>
