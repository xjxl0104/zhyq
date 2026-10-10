<template>
  <view class="withdraw-page">
    <view v-if="error" class="card error-card" role="alert">
      <view class="section-title">{{ b ? '提现申请未完成' : '余额暂未加载' }}</view>
      <view class="muted">{{ error }}</view>
      <button class="btn ghost" :disabled="loading" @click="load">重新加载</button>
    </view>
    <view v-if="!b && !error" class="card muted">正在加载可提现余额和规则…</view>
    <template v-if="b">
      <view class="card">
        <view class="muted">本次可提现金额（元）</view>
        <view class="balance-amount">{{ cashAvailable ? cashAvailable.amount : '—' }}</view>
        <view class="muted">账户余额 {{ formatLedgerBalance(b.balance) }} 元</view>
        <view v-if="cashAvailable?.remainderMills" class="muted balance-note">不足 1 分的 {{ cashAvailable.remainder }} 元保留累计。</view>
      </view>

      <view class="card rules-card">
        <view class="section-title">提现规则</view>
        <view class="rule-item" v-for="rule in ruleRows" :key="rule.label">
          <view class="rule-label">{{ rule.label }}</view>
          <view class="rule-value">{{ rule.value }}</view>
        </view>
      </view>

      <view class="card">
        <view class="section-title">申请提现</view>
        <view v-if="Number(b.idVerified) !== 1" class="account-notice">
          <view>首次提现请先完善收款资料，确认款项转入本人账户。资料通过审核后可申请。</view>
          <button class="btn ghost" @click="openAccount">完善收款资料</button>
        </view>
        <view class="field-label">提现金额（元）</view>
        <input class="input" v-model="amount" type="digit" :disabled="loading || Number(b.idVerified) !== 1 || !cashAvailable?.cents" placeholder="输入金额，最多两位小数" aria-label="提现金额，单位元，最多两位小数" />
        <button class="all-amount" :disabled="loading || Number(b.idVerified) !== 1 || !cashAvailable?.cents" @click="fillAll">全部可提现金额</button>
        <view v-if="inputHint" class="muted input-hint">{{ inputHint }}</view>
        <view class="row" v-if="validAmount"><text class="muted">预估代扣 / 到账（元）</text><text>{{ tax }} / {{ net }}</text></view>
        <button class="btn" :loading="loading" :disabled="loading || !validAmount" @click="submit">提交提现申请</button>
        <view class="muted submit-note">提交后可在下方查看处理进度及付款流水。</view>
      </view>
    </template>

    <view class="card">
      <view class="section-title">提现记录</view>
      <view v-if="history.error" class="muted">{{ history.error }}<button class="btn ghost" :disabled="history.loading" @click="pager.retry()">重新读取记录</button></view>
      <view v-if="history.loading && !list.length" class="muted">正在读取提现记录…</view>
      <view v-else-if="!list.length && history.loaded && !history.error" class="muted">暂无提现记录。提交申请后，可在这里查看审核及打款进度。</view>
      <view class="row history-row" v-for="w in list" :key="w.id">
        <view class="history-details"><view>{{ w.withdrawalNo }}</view><view class="muted">{{ formatTime(w.createTime) }}</view><view v-if="w.rejectReason" class="muted">驳回原因：{{ w.rejectReason }}</view><view v-if="w.payNo" class="muted">付款流水：{{ w.payNo }}</view></view>
        <view class="history-amount"><view class="money">{{ Number(w.netAmount).toFixed(2) }}</view><text class="tag" :class="w.status === 3 ? 'ok' : w.status === 4 ? 'warn' : ''">{{ STATUS[w.status] || '处理中' }}</text></view>
      </view>
      <button v-if="list.length < history.total" class="btn ghost" :loading="history.loading" :disabled="history.loading" @click="pager.load(false)">加载更多提现记录</button>
      <view v-else-if="list.length" class="muted">共 {{ history.total }} 笔，已全部显示</view>
    </view>
  </view>
</template>

<script>
import { appShareMixin } from '@/utils/share'

export default { mixins: [appShareMixin] }
</script>

<script setup>
import { ref, computed, reactive, onUnmounted } from 'vue'
import { onShow, onReachBottom } from '@dcloudio/uni-app'
import { bizApi } from '@/api'
import { createPager } from '@/utils/pagination.mjs'
import { withdrawalBalanceView, formatLedgerBalance, isValidWithdrawal, withdrawalRuleRows } from '@/utils/withdrawal.mjs'
const STATUS = { 1: '待审核', 2: '已审核', 3: '已打款', 4: '已驳回' }
const error=ref('')
const b = ref(null); const amount = ref(''); const loading = ref(false)
const history=reactive({}),pager=createPager(bizApi.withdrawals,history),list=computed(()=>history.records)
const tax = computed(() => (Number(amount.value || 0) * (b.value?.taxMode === 1 ? Number(b.value.taxRate) : 0)).toFixed(2))
const net = computed(() => (Number(amount.value || 0) - Number(tax.value)).toFixed(2))
const cashAvailable = computed(() => withdrawalBalanceView(b.value))
const ruleRows = computed(() => withdrawalRuleRows(b.value))
const inputHint = computed(() => {
  if (!b.value) return '请先读取最新可提现余额'
  if (Number(b.value.idVerified) !== 1) return '请先完善收款资料，通过审核后即可申请。'
  if (!cashAvailable.value?.cents) return '当前暂无可提现金额。佣金结算后会计入余额。'
  if (!amount.value) return '请输入提现金额，或选择全部可提现金额。'
  if (!validAmount.value) return '请按页面额度填写金额，最多保留两位小数。'
  return ''
})
function openAccount() { uni.switchTab({ url: '/pages/me/index' }) }
function formatTime(value) { return typeof value === 'string' ? value.replace('T', ' ').slice(0, 16) : '—' }
const validAmount = computed(() => !!b.value && Number(b.value.idVerified) === 1 && !!cashAvailable.value && isValidWithdrawal(amount.value, cashAvailable.value.amount, b.value.minWithdraw ?? 0))
function fillAll() { if (!loading.value && cashAvailable.value?.cents) amount.value = cashAvailable.value.amount }
function hasInvalidMinimum(balance) { return balance?.minWithdraw == null || !Number.isFinite(Number(balance.minWithdraw)) || Number(balance.minWithdraw) < 0 }
let balanceRequest=0
async function load() { const request=++balanceRequest;b.value=null;error.value='';const records=pager.load();try{const balance=await bizApi.balance();if(request===balanceRequest){if(!withdrawalBalanceView(balance)||hasInvalidMinimum(balance))throw new Error('余额数据暂不可用，请重新加载');b.value=balance}}catch(e){if(request===balanceRequest)error.value=e.message||'余额加载失败，请重试'}await records }
async function submit() {
  if(loading.value || !validAmount.value)return
  loading.value = true
  error.value = ''
  try { await bizApi.withdraw(amount.value.trim()); uni.showToast({ title: '已提交,等待审核' }); amount.value = ''; await load() }
  catch (failure) { error.value = failure.message || '提现申请失败，请重试' }
  finally { loading.value = false }
}
onShow(load)
onReachBottom(()=>pager.load(false))
onUnmounted(()=>{balanceRequest++;pager.invalidate()})
</script>

<style scoped>
.withdraw-page { padding-bottom: 28rpx; }
.balance-amount { margin: 12rpx 0; color: var(--park-ink); font-size: 56rpx; font-weight: 650; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; }
.balance-note { margin-top: 12rpx; }
.section-title { margin-bottom: 24rpx; font-size: 32rpx; font-weight: 650; color: var(--park-ink); }
.rule-item + .rule-item { margin-top: 24rpx; padding-top: 24rpx; border-top: 1rpx solid var(--park-line, #e2e6ef); }
.rule-label { margin-bottom: 8rpx; color: var(--park-ink); font-size: 28rpx; font-weight: 600; }
.rule-value, .account-notice { color: #6b7386; font-size: 28rpx; line-height: 1.7; overflow-wrap: anywhere; }
.field-label { margin-bottom: 12rpx; font-size: 28rpx; font-weight: 600; }
.account-notice { margin-bottom: 28rpx; }
.all-amount { margin: 12rpx 0; padding: 12rpx 0; text-align: left; background: transparent; color: var(--park-blue); font-size: 28rpx; }
.all-amount[disabled] { color: #6b7386; background: transparent; }
.input-hint, .submit-note { margin-top: 16rpx; line-height: 1.6; }
.history-row { align-items: flex-start; gap: 20rpx; }
.history-details { min-width: 0; overflow-wrap: anywhere; }
.history-amount { flex-shrink: 0; text-align: right; }
button:focus-visible { outline: 2px solid var(--park-blue); outline-offset: 2px; }
</style>
