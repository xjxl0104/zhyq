<template>
  <view class="page-wrap settle-page">
    <view v-if="error" class="card" role="alert"><view class="muted">{{ error }}</view><button class="btn" :disabled="loading" @click="load">重新加载</button></view>
    <view v-if="loading && !data" class="card muted">正在读取结算数据…</view>
    <template v-if="data">
      <view class="card hero-card">
        <view class="hero-label">可提现余额（元）</view>
        <view class="hero-number">{{ money(totals.withdrawable) }}</view>
        <view class="hero-meta">
          <text class="hero-meta-item">累计佣金 {{ money(totals.total) }}</text>
          <text v-if="schedule.nextAutoWithdraw" class="hero-meta-item">下次自动提现 {{ schedule.nextAutoWithdraw }}</text>
        </view>
        <button class="hero-btn" @click="go('/pages/withdraw/index')">申请提现 ›</button>
      </view>

      <view class="section-head"><text class="section-title">资金流转</text><text class="caption">金额单位：元</text></view>
      <view class="card">
        <view class="flow-bar">
          <view v-for="s in segments" :key="s.key" :class="['flow-seg', s.tone]" :style="{ width: s.percent + '%' }" />
        </view>
        <view class="flow-grid">
          <view v-for="s in segments" :key="s.key" class="flow-item">
            <view class="flow-label"><text :class="['dot', s.tone]" />{{ s.label }}</view>
            <view class="flow-amount">{{ money(s.amount) }}</view>
          </view>
        </view>
      </view>

      <view class="section-head"><text class="section-title">结算时间节点</text></view>
      <view class="card steps">
        <view v-for="(step, i) in steps" :key="step.title" class="step">
          <view class="step-index">{{ i + 1 }}</view>
          <view class="step-main"><view class="step-title">{{ step.title }}</view><view class="step-desc">{{ step.desc }}</view></view>
        </view>
      </view>

      <view class="section-head"><text class="section-title">按合同结算</text><text class="caption">点击合同查看每月</text></view>
      <view v-if="!data.contracts.length" class="card muted">还没有出库佣金。客户发货后会按合同显示在这里。</view>
      <view v-for="c in data.contracts" :key="c.contractId" class="card contract-card pressable" @click="openContract(c)">
        <view class="contract-head">
          <view class="contract-main"><view class="contract-name">{{ c.customerName || '未命名客户' }}</view><view class="caption">{{ c.contractNo || '合同 #' + c.contractId }} · {{ c.monthCount }} 个月 · {{ c.tickets }} 票</view></view>
          <view class="contract-total"><view class="money">{{ money(c.total) }}</view><view class="caption">查看每月 ›</view></view>
        </view>
        <view class="mini-bar">
          <view class="flow-seg withdrawable" :style="{ width: shares(c).settled + '%' }" />
          <view class="flow-seg settleable" :style="{ width: shares(c).settleable + '%' }" />
          <view class="flow-seg frozen" :style="{ width: shares(c).frozen + '%' }" />
        </view>
        <view class="contract-foot caption"><text>已结算 {{ money(c.settled) }}</text><text>待结算 {{ money(c.settleable) }}</text><text>冻结 {{ money(c.frozen) }}</text></view>
      </view>

      <view class="section-head"><text class="section-title">最近提现</text><text class="section-link" @click="go('/pages/withdraw/index')">全部记录 ›</text></view>
      <view class="card">
        <view v-if="!data.withdrawals.length" class="muted">暂无提现记录。{{ withdrawalSchedule }}</view>
        <view v-for="w in data.withdrawals" :key="w.id" class="row wd-row">
          <view class="wd-main"><view>{{ String(w.createTime || '').replace('T', ' ').slice(0, 16) }}</view><view class="caption">{{ w.withdrawalNo }}</view><view v-if="w.rejectReason" class="caption">驳回原因：{{ w.rejectReason }}</view></view>
          <view class="wd-side"><view class="money">{{ money(w.amount) }}</view><text :class="['tag', Number(w.status) === 3 ? 'ok' : Number(w.status) === 4 ? 'warn' : '']">{{ statusText(w) }}</text><button class="record-link" @click="openWithdrawal(w.id)">审批进度 ›</button></view>
        </view>
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
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { settlementApi } from '@/api'
import { money, flowSegments, settleShares, withdrawalStatusText, withdrawalScheduleText, scheduleSteps } from '@/utils/settlement.mjs'

const data = ref(null), error = ref(''), loading = ref(false)
const totals = computed(() => data.value?.totals || {})
const schedule = computed(() => data.value?.schedule || {})
const segments = computed(() => flowSegments(totals.value))
const steps = computed(() => scheduleSteps(schedule.value))
const withdrawalSchedule = computed(() => withdrawalScheduleText(schedule.value))
const shares = settleShares
const statusText = withdrawalStatusText
const go = url => uni.navigateTo({ url })
function openContract(c) { go(`/pages/settlement/contract?id=${encodeURIComponent(c.contractId)}`) }
function openWithdrawal(id) { if (/^\d+$/.test(String(id))) go(`/pages/withdrawal-detail/index?id=${id}`) }
let seq = 0
async function load() {
  const current = ++seq
  loading.value = true; error.value = ''
  try { const res = await settlementApi.overview(); if (current === seq) data.value = res }
  catch (e) { if (current === seq) error.value = e.message || '结算数据读取失败，请重试' }
  finally { if (current === seq) { loading.value = false; uni.stopPullDownRefresh() } }
}
onShow(load)
onPullDownRefresh(load)
</script>

<style scoped>
.settle-page { padding-bottom: 40rpx; }
.record-link { margin: 12rpx 0 0; padding: 0; font-size: 25rpx; line-height: 1.8; color: var(--park-blue); background: transparent; }
.hero-btn { margin: 24rpx 0 0; padding: 0 28rpx; width: fit-content; color: #1d2a57; background: #fff; border-radius: 999rpx; font-size: 26rpx; font-weight: 650; line-height: 64rpx; }
.flow-bar, .mini-bar { display: flex; height: 22rpx; overflow: hidden; border-radius: 11rpx; background: #eef1f8; }
.mini-bar { height: 14rpx; margin-top: 20rpx; }
.flow-seg { height: 100%; }
.frozen { background: #f5b84b; }
.settleable { background: #637bff; }
.withdrawable { background: #39c49a; }
.withdrawing { background: #50c7d8; }
.withdrawn { background: #9aa4bd; }
.flow-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 20rpx 12rpx; margin-top: 26rpx; }
.flow-label { display: flex; align-items: center; gap: 8rpx; color: var(--park-muted); font-size: 23rpx; }
.dot { display: inline-block; width: 16rpx; height: 16rpx; border-radius: 4rpx; }
.flow-amount { margin-top: 6rpx; color: var(--park-text); font-size: 30rpx; font-weight: 650; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; }
.steps { padding-top: 12rpx; padding-bottom: 12rpx; }
.step { display: flex; gap: 20rpx; padding: 16rpx 0; }
.step + .step { border-top: 1rpx solid var(--park-line); }
.step-index { flex-shrink: 0; width: 44rpx; height: 44rpx; color: #fff; border-radius: 50%; background: var(--park-blue); font-size: 24rpx; line-height: 44rpx; text-align: center; }
.step-title { color: var(--park-text); font-size: 28rpx; font-weight: 650; }
.step-desc { margin-top: 4rpx; color: var(--park-muted); font-size: 24rpx; line-height: 1.6; }
.contract-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16rpx; }
.contract-main { min-width: 0; flex: 1; }
.contract-name { color: var(--park-text); font-size: 30rpx; font-weight: 650; overflow-wrap: anywhere; }
.contract-total { flex-shrink: 0; text-align: right; }
.contract-foot { display: flex; flex-wrap: wrap; gap: 8rpx 24rpx; margin-top: 14rpx; }
.wd-row { align-items: flex-start; gap: 16rpx; }
.wd-main { min-width: 0; flex: 1; overflow-wrap: anywhere; }
.wd-side { flex-shrink: 0; text-align: right; }
</style>
