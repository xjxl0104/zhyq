<template>
  <view class="admin-overview">
    <view class="card filters">
      <view class="filter-title">{{ view === 'partners' ? '伙伴总览' : '云仓总览' }}</view>
      <view class="segments">
        <button v-for="item in presets" :key="item.value" class="segment" :class="{ active: preset === item.value }" @click="setPreset(item.value)">{{ item.label }}</button>
      </view>
      <view class="date-row"><picker mode="date" :value="range.startDate" :end="range.endDate" @change="setDate('startDate', $event)"><view>{{ range.startDate }} 起</view></picker>
        <text>—</text><picker mode="date" :value="range.endDate" :start="range.startDate" :end="today" @change="setDate('endDate', $event)"><view>{{ range.endDate }} 止</view></picker></view>
      <view class="caption">客户、伙伴及合同数量为当前档案；发货和佣金按所选日期统计。</view>
    </view>
    <view v-if="selectionId" class="section-head"><text class="section-title">{{ selectionName }}</text><text class="section-link" @click="back">返回全部 ›</text></view>
    <view v-if="error" class="card"><view class="error">{{ error }}</view><button class="btn secondary" @click="load(false)">重新加载</button></view>
    <view v-if="summary" class="card overview-card">
      <view class="total-label">{{ selectionId ? '范围内总发货量' : '总发货量' }}（单）</view>
      <view class="volume">{{ count(summary.shipmentVolume) }}</view>
      <view class="metric-grid">
        <view><text class="caption">伙伴</text><text>{{ count(summary.partnerCount) }}</text></view>
        <view><text class="caption">P4</text><text>{{ count(summary.p4Count) }}</text></view>
        <view><text class="caption">客户</text><text>{{ count(summary.customerCount) }}</text></view>
        <view><text class="caption">已签合同</text><text>{{ count(summary.contractCount) }}</text></view>
        <view><text class="caption">云仓</text><text>{{ count(summary.warehouseCount) }}</text></view>
      </view>
      <view class="row"><text>客户总佣金（元）</text><text class="money">{{ money(summary.commissionTotal) }}</text></view>
      <view class="row"><text>伙伴所得佣金（元）</text><text class="money">{{ money(summary.partnerCommission) }}</text></view>
      <view class="row"><text>待结算（元）</text><text>{{ money(summary.pendingCommission) }}</text></view>
      <view class="row"><text>已结算（含已提现）</text><text>{{ money(summary.settledCommission) }}</text></view>
      <view class="row"><text>已提现（元）</text><text>{{ money(summary.withdrawnAmount) }}</text></view>
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
      <view class="record-metrics"><view><text class="caption">发货量（单）</text><text>{{ count(row.summary?.shipmentVolume) }}</text></view><view><text class="caption">客户数</text><text>{{ count(row.summary?.customerCount) }}</text></view></view>
      <view class="row"><text>客户总佣金</text><text>{{ money(row.summary?.commissionTotal) }} 元</text></view>
      <view class="row"><text>伙伴所得佣金</text><text>{{ money(row.summary?.partnerCommission) }} 元</text></view>
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
const selectionId = ref(null), selectionName = ref(''), summary = ref(null), records = ref([])
const total = ref(0), page = ref(1), loading = ref(false), error = ref(''), kind = ref('')
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
  if (kind.value === 'customers') return navigateTo(`/pages/admin-customer/index?id=${row.id}&startDate=${range.value.startDate}&endDate=${range.value.endDate}`)
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
</style>
