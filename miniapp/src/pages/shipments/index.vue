<template>
  <view class="page-wrap shipments-page">
    <view class="card filter-card">
      <view class="scope-tabs">
        <button :class="['scope-tab', { selected: scope === 'own' }]" @click="switchScope('own')">本人客户</button>
        <button v-if="canViewTeam || scope === 'team'" :class="['scope-tab', { selected: scope === 'team' }]" @click="switchScope('team')">下级成员</button>
      </view>
      <view class="presets"><button v-for="item in presets" :key="item.key" :class="['preset', { active: preset === item.key }]" @click="choosePreset(item.key)">{{ item.label }}</button></view>
      <view class="date-row">
        <picker mode="date" :value="startDate" @change="event => changeDate('startDate', event.detail.value)"><view class="date-input"><text class="muted">开始</text><text>{{ startDate }}</text></view></picker>
        <text class="muted">至</text>
        <picker mode="date" :value="endDate" @change="event => changeDate('endDate', event.detail.value)"><view class="date-input"><text class="muted">结束</text><text>{{ endDate }}</text></view></picker>
      </view>
      <button class="btn secondary" :loading="loading" :disabled="loading" @click="load">查询发货量</button>
      <view v-if="error" class="error" role="alert">{{ error }}</view>
    </view>
    <button v-if="memberId" class="btn ghost back-team" @click="backToTeam">‹ 返回团队汇总</button>
    <view v-if="loading" class="card muted">正在统计发货量…</view>
    <template v-else-if="report">
      <view class="card summary-card">
        <view class="title">{{ summaryTitle }}</view>
        <view class="period muted">{{ report.startDate }} 至 {{ report.endDate }}</view>
        <view class="shipment-total">{{ formatVolume(report.totalVolume) }}<text class="unit">单</text></view>
        <view class="summary-counts"><text v-if="scope === 'team' && !memberId">{{ report.memberCount || 0 }} 位成员</text><text>{{ report.customerCount || 0 }} 位客户</text><text>{{ report.contractCount || 0 }} 份合同</text></view>
        <view v-if="scope === 'team' && !memberId" class="note">统计全部下级成员各自推荐客户的发货量，不含您本人的发货量。</view>
        <view class="note">{{ report.notice || '按已同步的发货日期统计，今日发货数据可能尚未完整。' }}</view>
        <view v-if="report.dataAsOf" class="note">数据更新：{{ formatTime(report.dataAsOf) }}</view>
      </view>
      <view class="card details-card">
        <view class="title">{{ teamOverview ? '按团队成员查看' : '按客户合同查看' }}</view>
        <view v-if="!rows.length" class="empty muted">{{ teamOverview ? '当前范围内暂无可显示的团队成员发货数据' : '当前范围内暂无客户合同发货数据' }}</view>
        <template v-if="teamOverview">
          <view v-for="member in visibleRows" :key="member.memberId" class="row report-row member-row" @click="openMember(member)">
            <view class="row-label"><view class="row-title">{{ member.name || '未命名成员' }}</view><view class="note">{{ member.positionCode }} · {{ member.customerCount || 0 }} 位客户 · {{ member.contractCount || 0 }} 份合同</view></view>
            <view class="row-value"><text class="volume">{{ formatVolume(member.volume) }}</text><text class="note"> 单 ›</text></view>
          </view>
        </template>
        <template v-else>
          <view v-for="(contract, index) in visibleRows" :key="`${contract.contractId || 'unbound'}-${contract.customerId || index}`" class="row report-row">
            <view class="row-label"><view class="row-title">{{ contract.customerName || '未命名客户' }}</view><view class="note">合同：{{ contract.contractNo || '未关联合同' }}</view><view v-if="contract.warehouseName" class="note">{{ contract.warehouseName }}</view></view>
            <view class="row-value"><text class="volume">{{ formatVolume(contract.volume) }}</text><text class="note"> 单</text></view>
          </view>
        </template>
        <button v-if="shown < rows.length" class="btn ghost" @click="shown += PAGE_SIZE">加载更多（{{ shown }} / {{ rows.length }}）</button>
        <view v-else-if="rows.length" class="note list-end">共 {{ rows.length }} {{ teamOverview ? '位成员' : '条合同记录' }}，已全部显示</view>
      </view>
    </template>
  </view>
</template>
<script>
import { appShareMixin } from '@/utils/share'
export default { mixins: [appShareMixin] }
</script>
<script setup>
import { computed, ref } from 'vue'
import { onLoad, onShow, onHide, onUnload, onPullDownRefresh } from '@dcloudio/uni-app'
import { shipmentApi } from '@/api'
import { PAGE_SIZE, formatVolume, rangePreset, validateRange } from '@/utils/shipments.mjs'
const defaults = rangePreset('month')
const scope = ref('own'), memberId = ref(null), canViewTeam = ref(false)
const startDate = ref(defaults.startDate), endDate = ref(defaults.endDate), preset = ref('month')
const report = ref(null), error = ref(''), loading = ref(false), shown = ref(PAGE_SIZE)
const presets = [{ key: 'month', label: '本月' }, { key: 'lastMonth', label: '上月' }, { key: 'week', label: '近7天' }, { key: 'yesterday', label: '昨日' }]
const teamOverview = computed(() => scope.value === 'team' && !memberId.value)
const rows = computed(() => report.value ? (teamOverview.value ? report.value.members : report.value.contracts) || [] : [])
const visibleRows = computed(() => rows.value.slice(0, shown.value))
const summaryTitle = computed(() => memberId.value ? `${report.value?.memberName || '团队成员'}的发货量` : scope.value === 'team' ? '团队发货量汇总' : '本人客户发货量汇总')
const formatTime = value => String(value).replace('T', ' ').slice(0, 16)
let sequence = 0, visible = false
onLoad(query => { scope.value = query.scope === 'team' ? 'team' : 'own' })
onShow(() => { visible = true; load() })
function stop() { visible = false; sequence++; loading.value = false; uni.stopPullDownRefresh() }
onHide(stop)
onUnload(stop)
onPullDownRefresh(load)
async function load() {
  if (!visible) return
  const current = ++sequence
  error.value = validateRange(startDate.value, endDate.value)
  report.value = null; shown.value = PAGE_SIZE
  if (error.value) { loading.value = false; uni.stopPullDownRefresh(); return }
  loading.value = true
  try {
    const data = await shipmentApi.report({ scope: scope.value, startDate: startDate.value, endDate: endDate.value, ...(memberId.value ? { memberId: memberId.value } : {}) })
    if (!visible || current !== sequence) return
    report.value = data; canViewTeam.value = !!data.canViewTeam
  } catch (failure) {
    if (visible && current === sequence) error.value = failure.message || '发货量读取失败，请重试'
  } finally {
    if (current === sequence) { loading.value = false; uni.stopPullDownRefresh() }
  }
}
function switchScope(next) {
  if (next === 'team' && !canViewTeam.value && scope.value !== 'team') return
  scope.value = next; memberId.value = null; load()
}
function openMember(member) { if (!teamOverview.value || !member.memberId) return; memberId.value = member.memberId; load() }
function backToTeam() { memberId.value = null; load() }
function choosePreset(key) {
  const range = rangePreset(key)
  preset.value = key; startDate.value = range.startDate; endDate.value = range.endDate; load()
}
function changeDate(field, value) {
  preset.value = ''; if (field === 'startDate') startDate.value = value; else endDate.value = value
  sequence++; loading.value = false; report.value = null; error.value = ''
}
</script>
<style scoped>
.scope-tabs{display:flex;gap:12rpx}.scope-tab{flex:1;margin:0;padding:0 12rpx;font-size:28rpx;background:#f4f6fb;color:#56627d;line-height:80rpx}.scope-tab.selected{background:#e8edff;color:var(--park-blue);font-weight:600}.presets{display:flex;gap:12rpx;flex-wrap:wrap;margin-top:24rpx}.preset{margin:0;padding:0 18rpx;background:#f4f6fb;font-size:24rpx;line-height:58rpx;color:#56627d}.preset.active{color:var(--park-blue);background:#e8edff}.date-row{display:flex;align-items:center;gap:14rpx;margin-top:24rpx}.date-row picker{min-width:0;flex:1}.date-input{display:flex;flex-direction:column;gap:8rpx;padding:16rpx 12rpx;border:1rpx solid #e1e5ef;border-radius:14rpx;font-size:25rpx}.date-input .muted{font-size:22rpx}.shipment-total{margin:24rpx 0;font-size:56rpx;color:var(--park-blue);font-weight:650;overflow-wrap:anywhere}.unit{font-size:26rpx;font-weight:400;margin-left:12rpx}.summary-counts{display:flex;flex-wrap:wrap;gap:24rpx;font-size:25rpx}.period{margin-top:16rpx;font-size:24rpx}.note{margin-top:10rpx;color:#66718b;font-size:24rpx;line-height:1.6;overflow-wrap:anywhere}.report-row{gap:16rpx;align-items:flex-start}.row-label{flex:1;min-width:0}.row-title{font-size:28rpx;font-weight:600;overflow-wrap:anywhere}.row-value{flex:0 1 42%;text-align:right;overflow-wrap:anywhere}.volume{font-size:30rpx;color:var(--park-blue);font-weight:600}.member-row:active{opacity:.7}.empty{padding:32rpx 0;line-height:1.6}.error{margin-top:20rpx;color:#a93145;line-height:1.6}.list-end{text-align:center;padding-top:20rpx}.back-team{margin-bottom:24rpx}
</style>
