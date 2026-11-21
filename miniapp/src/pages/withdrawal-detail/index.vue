<template>
  <view class="page-wrap withdrawal-detail">
    <view v-if="error" class="card error">{{ error }}<button class="btn ghost" :disabled="loading || submitting" @click="load">重新加载</button></view>
    <view v-if="loading && !detail" class="card muted">正在读取提现详情…</view>
    <template v-if="detail">
      <view class="card">
        <view class="row heading"><text class="title">{{ detail.withdrawalNo }}</text><text class="tag">{{ withdrawalStage(detail) }}</text></view>
        <view class="field"><text class="muted">伙伴</text><text>{{ detail.promoterName || '姓名未记录' }}</text></view>
        <view class="field"><text class="muted">伙伴编号</text><text>{{ detail.promoterId }}</text></view>
        <view class="field"><text class="muted">联系电话</text><text>{{ detail.promoterPhone || '未记录' }}</text></view>
        <view class="field"><text class="muted">申请时间</text><text>{{ withdrawalTime(detail.createTime) }}</text></view>
        <view class="field"><text class="muted">申请金额</text><text class="money">{{ withdrawalAmount(detail.amount) }} 元</text></view>
        <view class="field"><text class="muted">预估代扣</text><text>{{ withdrawalAmount(detail.taxAmount) }} 元</text></view>
        <view class="field"><text class="muted">{{ Number(detail.status) === 3 ? '到账金额' : '预计到账' }}</text><text>{{ withdrawalAmount(detail.netAmount) }} 元</text></view>
      </view>
      <view class="card">
        <view class="title">申请时收款账户</view>
        <view class="muted intro">本次打款使用以下申请时记录的账户，修改个人资料不会替换此账户。</view>
        <view class="field"><text class="muted">收款人</text><text>{{ detail.accountName || '未记录' }}</text></view>
        <view class="field"><text class="muted">收款账号</text><text>{{ withdrawalAccount(detail) }}</text></view>
        <view v-if="detail.bankName" class="field"><text class="muted">开户行</text><text>{{ detail.bankName }}</text></view>
        <view v-if="detail.payNo" class="field"><text class="muted">付款流水</text><text>{{ detail.payNo }}</text></view>
        <view v-if="detail.payAt" class="field"><text class="muted">打款时间</text><text>{{ withdrawalTime(detail.payAt) }}</text></view>
      </view>
      <view class="card">
        <view class="title">审批进度</view>
        <view v-if="detail.approval" class="current-step">
          <view>{{ detail.approval.nodeName || '提现审批' }}</view>
          <view class="muted">审批人：{{ detail.approval.assigneeName || '待系统确认' }} · {{ approvalHistoryStatus(detail.approval.status) }}</view>
        </view>
        <view v-else class="muted intro">{{ Number(detail.status) === 1 ? '审批信息暂未同步，请刷新后查看。' : '本笔申请已结束审批。' }}</view>
        <view class="timeline-item"><view>提交提现申请</view><view class="muted">{{ withdrawalTime(detail.createTime) }}</view></view>
        <view v-for="(item, index) in detail.history || []" :key="`${item.nodeName}-${index}`" class="timeline-item">
          <view>{{ item.nodeName || '提现审批' }} · {{ approvalHistoryStatus(item.status) }}</view>
          <view class="muted">{{ item.assigneeName || '审批人未记录' }}{{ item.actTime ? ` · ${withdrawalTime(item.actTime)}` : '' }}</view>
          <view v-if="item.opinion" class="opinion">审批意见：{{ item.opinion }}</view>
        </view>
        <view v-if="detail.rejectReason" class="error intro">驳回原因：{{ detail.rejectReason }}</view>
        <view v-if="Number(detail.status) === 2" class="muted intro">审批已通过，等待财务按申请时收款账户打款。</view>
        <view v-if="Number(detail.status) === 3" class="timeline-item"><view>已打款</view><view class="muted">{{ withdrawalTime(detail.payAt) }}</view></view>
      </view>
      <view v-if="approvalMode && (canApprove || canReject)" class="card">
        <view class="title">处理申请</view>
        <view class="muted intro">请核对上述伙伴和收款资料。通过后进入待打款，驳回后金额退回伙伴可提现余额。</view>
        <textarea class="input reason" v-model="opinion" maxlength="500" :disabled="submitting" placeholder="请填写审批意见（必填，最多 500 字）" aria-label="审批意见" />
        <view v-if="actionError" class="error intro">{{ actionError }}</view>
        <view class="actions">
          <button v-if="canReject" class="btn ghost reject" :disabled="submitting || loading" @click="decide(false)">驳回</button>
          <button v-if="canApprove" class="btn" :loading="submitting" :disabled="submitting || loading" @click="decide(true)">审批通过</button>
        </view>
      </view>
      <view v-else-if="approvalMode && Number(detail.status) === 1" class="card muted">当前账号不是这笔申请的可操作审批人。</view>
      <button class="btn ghost refresh" :disabled="loading || submitting" @click="load">刷新进度</button>
    </template>
  </view>
</template>
<script>
import { appShareMixin } from '@/utils/share'
export default { mixins: [appShareMixin] }
</script>
<script setup>
import { ref, computed } from 'vue'
import { onLoad, onShow, onHide, onUnload, onPullDownRefresh } from '@dcloudio/uni-app'
import { bizApi, withdrawalApprovalApi } from '@/api'
import { withdrawalStage, withdrawalAmount, withdrawalTime, withdrawalAccount, approvalHistoryStatus, canDecideWithdrawal, approvalOpinionError } from '@/utils/withdrawal-approval.mjs'
const detail = ref(null), loading = ref(false), submitting = ref(false), error = ref(''), actionError = ref(''), opinion = ref(''), approvalMode = ref(false)
let id = '', requestId = 0
const canApprove = computed(() => approvalMode.value && canDecideWithdrawal(detail.value, true))
const canReject = computed(() => approvalMode.value && canDecideWithdrawal(detail.value, false))
onLoad(options => { id = /^\d+$/.test(String(options?.id)) ? String(options.id) : ''; approvalMode.value = options?.approval === '1' })
async function load() {
  if (submitting.value) return
  const request = ++requestId
  loading.value = true; error.value = ''; actionError.value = ''; detail.value = null
  try {
    if (!id) throw new Error('提现单编号无效，请返回列表重新打开')
    const result = await (approvalMode.value ? withdrawalApprovalApi.detail(id) : bizApi.withdrawal(id))
    if (request === requestId) detail.value = result
  } catch (e) { if (request === requestId) error.value = e.message || '提现详情读取失败，请重试' }
  finally { if (request === requestId) loading.value = false }
}
function confirmDecision(approved, record) {
  return new Promise(resolve => uni.showModal({
    title: approved ? '确认审批通过' : '确认驳回申请',
    content: `${record.promoterName || '伙伴'}（编号 ${record.promoterId}）\n申请 ${withdrawalAmount(record.amount)} 元，预计到账 ${withdrawalAmount(record.netAmount)} 元\n收款人：${record.accountName || '未记录'}\n${withdrawalAccount(record)}`,
    confirmText: approved ? '确认通过' : '确认驳回',
    success: result => resolve(result.confirm === true), fail: () => resolve(false)
  }))
}
async function decide(approved) {
  if (submitting.value || loading.value || !approvalMode.value || !canDecideWithdrawal(detail.value, approved)) return
  actionError.value = approvalOpinionError(opinion.value)
  if (actionError.value) return
  const request = requestId, record = detail.value, remark = opinion.value.trim()
  submitting.value = true
  let changed = false
  try {
    if (!await confirmDecision(approved, record) || request !== requestId) return
    await withdrawalApprovalApi.audit(id, { approved, remark })
    if (request !== requestId) return
    changed = true; opinion.value = ''
    uni.showToast({ title: approved ? '审批已通过' : '申请已驳回', icon: 'none' })
  } catch (e) {
    if (request === requestId) actionError.value = e.message || '处理未完成，请刷新进度后重试'
  } finally {
    if (request === requestId) {
      submitting.value = false
      if (changed) await load()
    }
  }
}
function clear() { requestId++; detail.value = null; opinion.value = ''; actionError.value = ''; loading.value = false; submitting.value = false }
onShow(load)
onHide(clear)
onUnload(clear)
onPullDownRefresh(async () => { try { await load() } finally { uni.stopPullDownRefresh() } })
</script>
<style scoped>
.withdrawal-detail{padding-bottom:48rpx}.heading{gap:20rpx;align-items:flex-start;flex-wrap:wrap}.heading .title{font-size:30rpx;overflow-wrap:anywhere}.field{display:flex;justify-content:space-between;align-items:flex-start;gap:24rpx;padding:23rpx 0;border-bottom:1rpx solid #e2e7f1;font-size:29rpx;line-height:1.6}.field:last-child{border-bottom:0}.field>text:first-child{flex-shrink:0}.field>text:last-child{text-align:right;overflow-wrap:anywhere;min-width:0}.intro{margin-top:20rpx;line-height:1.7;overflow-wrap:anywhere}.current-step{background:#edf0ff;border-radius:18rpx;padding:24rpx;margin-top:24rpx;line-height:1.7}.timeline-item{margin-top:24rpx;padding-left:24rpx;border-left:4rpx solid #6376ed;line-height:1.7;overflow-wrap:anywhere}.opinion{margin-top:8rpx;font-size:28rpx}.reason{margin-top:24rpx;width:100%;height:200rpx;padding:20rpx;box-sizing:border-box}.actions{display:flex;gap:20rpx;margin-top:24rpx}.actions .btn{flex:1;margin:0}.reject{color:#a3293e;border-color:#e3cbd1}.error{color:#a3293e;line-height:1.7}.refresh{margin:24rpx 30rpx}.money{font-variant-numeric:tabular-nums}button:focus-visible,textarea:focus-visible{outline:3rpx solid #263dc2;outline-offset:4rpx}
</style>
