<template>
  <view class="page-wrap approvals-page">
    <view class="card">
      <view class="title">提现审批</view>
      <view v-if="access.canApproveWithdrawals === true" class="muted intro">{{ access.approverName }}，请核对伙伴、提现金额和申请时收款账户后处理。</view>
      <view v-else-if="checking" class="muted intro">正在核对审批权限…</view>
      <view v-if="error" class="error intro">{{ error }}<button class="btn ghost" :disabled="checking" @click="load">重新加载</button></view>
      <view v-if="access.canApproveWithdrawals === true" class="tabs">
        <button class="tab" :class="tab === 'pending' ? 'active' : ''" @click="setTab('pending')">待我审批</button>
        <button class="tab" :class="tab === 'all' ? 'active' : ''" @click="setTab('all')">全部申请</button>
      </view>
    </view>
    <template v-if="access.canApproveWithdrawals === true">
      <view v-if="state.error" class="card error">{{ state.error }}<button class="btn ghost" :disabled="state.loading" @click="pager.retry()">重试</button></view>
      <view v-if="state.loading && !state.records.length" class="card muted">正在读取提现申请…</view>
      <view v-else-if="state.loaded && !state.records.length && !state.error" class="card muted">{{ tab === 'pending' ? '暂无待您审批的提现申请。' : '暂无提现申请。' }}</view>
      <view v-for="item in state.records" :key="item.id" class="card application">
        <view class="row identity"><text class="title">{{ item.promoterName || '姓名未记录' }}</text><text class="tag">{{ withdrawalStage(item) }}</text></view>
        <view class="muted">伙伴编号 {{ item.promoterId }} · {{ item.promoterPhone || '手机号未记录' }}</view>
        <view class="row"><text>申请金额</text><text class="money">{{ withdrawalAmount(item.amount) }} 元</text></view>
        <view class="row"><text>预计到账</text><text>{{ withdrawalAmount(item.netAmount) }} 元</text></view>
        <view class="detail">收款人：{{ item.accountName || '未记录' }}</view>
        <view class="detail">{{ withdrawalAccount(item) }}{{ item.bankName ? ` · ${item.bankName}` : '' }}</view>
        <view class="muted detail">{{ item.withdrawalNo }} · {{ withdrawalTime(item.createTime) }}</view>
        <button class="btn ghost" @click="openDetail(item.id)">核对资料与审批进度 ›</button>
      </view>
      <button v-if="state.records.length < state.total" class="btn ghost" :loading="state.loading" :disabled="state.loading" @click="pager.load(false)">加载更多</button>
      <view v-else-if="state.records.length" class="muted count">共 {{ state.total }} 笔，已全部显示</view>
    </template>
  </view>
</template>
<script>
import { appShareMixin } from '@/utils/share'
export default { mixins: [appShareMixin] }
</script>
<script setup>
import { ref, reactive } from 'vue'
import { onShow, onHide, onUnload, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import { withdrawalApprovalApi } from '@/api'
import { createPager } from '@/utils/pagination.mjs'
import { withdrawalStage, withdrawalAmount, withdrawalTime, withdrawalAccount } from '@/utils/withdrawal-approval.mjs'
const access = ref({}), checking = ref(false), error = ref(''), tab = ref('pending')
const state = reactive({}), pager = createPager(withdrawalApprovalApi.page, state)
let requestId = 0
async function load() {
  const request = ++requestId
  checking.value = true; error.value = ''; access.value = {}; pager.invalidate(); state.records = []
  try {
    const capability = await withdrawalApprovalApi.capability()
    if (request !== requestId) return
    if (capability?.canApproveWithdrawals !== true) throw new Error('当前账号没有提现审批权限')
    access.value = capability
    await pager.load(true, { status: tab.value === 'pending' ? 1 : undefined })
  } catch (e) { if (request === requestId) error.value = e.message || '审批权限读取失败，请重试' }
  finally { if (request === requestId) checking.value = false }
}
function setTab(value) {
  if (tab.value === value || access.value.canApproveWithdrawals !== true) return
  tab.value = value
  pager.load(true, { status: value === 'pending' ? 1 : undefined })
}
function openDetail(id) { if (/^\d+$/.test(String(id))) uni.navigateTo({ url: `/pages/withdrawal-detail/index?id=${id}&approval=1` }) }
function clear() { requestId++; pager.invalidate(); state.records = []; access.value = {}; checking.value = false }
onShow(load)
onHide(clear)
onUnload(clear)
onReachBottom(() => { if (access.value.canApproveWithdrawals === true) pager.load(false) })
onPullDownRefresh(async () => { try { await load() } finally { uni.stopPullDownRefresh() } })
</script>
<style scoped>
.approvals-page{padding-bottom:48rpx}.intro,.detail{margin-top:16rpx;line-height:1.65;overflow-wrap:anywhere}.tabs{display:flex;margin-top:24rpx;border-bottom:1rpx solid #e2e7f1}.tab{flex:1;border-radius:0;background:transparent;color:#58617d;font-size:30rpx;line-height:88rpx}.tab.active{color:#263dc2;border-bottom:4rpx solid #3857f5;font-weight:600}.identity{align-items:flex-start;gap:18rpx}.identity .title{min-width:0;overflow-wrap:anywhere}.identity .tag{flex-shrink:0}.application .row{margin:20rpx 0;gap:16rpx;flex-wrap:wrap}.detail{font-size:28rpx}.count{text-align:center;padding:24rpx}.error{color:#a3293e;line-height:1.6}.money{font-variant-numeric:tabular-nums}.btn{margin-top:24rpx}button:focus-visible{outline:3rpx solid #263dc2;outline-offset:4rpx}
</style>
