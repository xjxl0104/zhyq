<template>
  <view>
    <view v-if="error" class="card"><view class="muted">{{error}}</view><button class="btn" @click="load">重试</button></view>
    <view v-if="!b && !error" class="card muted">正在加载可提现余额…</view>
    <template v-if="b">
    <view class="card">
      <view class="muted">可提现余额(元)</view>
      <view style="font-size:56rpx;font-weight:600">{{ Number(b.balance).toFixed(2) }}</view>
      <view class="muted">最低 {{ b.minWithdraw }} 元 · {{ b.taxMode === 1 ? `配置的预估代扣比例  ${(b.taxRate * 100).toFixed(0)}%` : '灵活用工平台代征' }}</view>
    </view>
    <view class="card" v-if="!b.idVerified">
      <view class="muted">提现前请先提交收款资料，并等待园区人工审核通过</view>
      <button class="btn" @click="uni.navigateTo({ url: '/pages/me/index' })">查看收款资料</button>
    </view>
    <view class="card" v-else>
      <input class="input" v-model="amount" type="digit" placeholder="提现金额（最多两位小数）" />
      <button size="mini" @click="amount=String(b.balance)">全部可提现余额</button>
      <view class="muted">申请须覆盖完整佣金流水，扣回款会优先抵扣。最终代扣与到账金额以财务审核为准。</view>
      <view class="row" v-if="amount"><text class="muted">预计税额 / 到账</text><text>{{ tax }} / {{ net }}</text></view>
      <button class="btn" :loading="loading" :disabled="loading || !validAmount" @click="submit">申请提现</button>
    </view>
    <view class="card">
      <view style="font-weight:600;margin-bottom:12rpx">提现记录</view>
      <view v-if="!list.length" class="muted">还没有提现记录，已结算的佣金达到最低金额后可申请。</view>
      <view class="row" v-for="w in list" :key="w.id">
        <view><view>{{ w.withdrawalNo }}</view><view class="muted">{{ w.createTime?.slice(0, 16) }}</view><view v-if="w.rejectReason" class="muted">驳回原因：{{w.rejectReason}}</view><view v-if="w.payNo" class="muted">付款流水：{{w.payNo}}</view></view>
        <view style="text-align:right"><view class="money">{{ Number(w.netAmount).toFixed(2) }}</view><text class="tag" :class="w.status === 3 ? 'ok' : w.status === 4 ? 'warn' : ''">{{ STATUS[w.status] }}</text></view>
      </view>
    </view>
    </template>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { bizApi } from '@/api'
const STATUS = { 1: '待审核', 2: '已审核', 3: '已打款', 4: '已驳回' }
const error=ref('')
const b = ref(null); const list = ref([]); const amount = ref(''); const loading = ref(false)
const tax = computed(() => (Number(amount.value || 0) * (b.value?.taxMode === 1 ? Number(b.value.taxRate) : 0)).toFixed(2))
const net = computed(() => (Number(amount.value || 0) - Number(tax.value)).toFixed(2))
const validAmount=computed(()=>/^\d+(\.\d{1,2})?$/.test(amount.value)&&Number(amount.value)>=Number(b.value?.minWithdraw||0)&&Number(amount.value)>0&&Number(amount.value)<=Number(b.value?.balance||0))
async function load() { error.value='';try{;[b.value,list.value]=await Promise.all([bizApi.balance(),bizApi.withdrawals({pageNo:1,pageSize:100}).then(r=>r.records||[])])}catch(e){error.value=e.message||'余额加载失败，请重试'} }
async function submit() {
  if(loading.value || !validAmount.value)return
  loading.value = true
  try { await bizApi.withdraw(amount.value); uni.showToast({ title: '已提交,等待审核' }); amount.value = ''; await load() } finally { loading.value = false }
}
onShow(load)
</script>
