<template>
  <view v-if="b">
    <view class="card">
      <view class="muted">可提现余额(元)</view>
      <view style="font-size:56rpx;font-weight:600">{{ Number(b.balance).toFixed(2) }}</view>
      <view class="muted">最低 {{ b.minWithdraw }} 元 · {{ b.taxMode === 1 ? `按劳务报酬代扣个税 ${(b.taxRate * 100).toFixed(0)}%` : '灵活用工平台代征' }}</view>
    </view>
    <view class="card" v-if="!b.idVerified">
      <view class="muted">提现前请先完成实名与收款账户</view>
      <button class="btn" @click="uni.navigateTo({ url: '/pages/me/index' })">去实名</button>
    </view>
    <view class="card" v-else>
      <input class="input" v-model="amount" type="digit" placeholder="提现金额" />
      <view class="row" v-if="amount"><text class="muted">预计税额 / 到账</text><text>{{ tax }} / {{ net }}</text></view>
      <button class="btn" :loading="loading" @click="submit">申请提现</button>
    </view>
    <view class="card">
      <view style="font-weight:600;margin-bottom:12rpx">提现记录</view>
      <view v-if="!list.length" class="muted">暂无</view>
      <view class="row" v-for="w in list" :key="w.id">
        <view><view>{{ w.withdrawalNo }}</view><view class="muted">{{ w.createTime?.slice(0, 16) }}</view></view>
        <view style="text-align:right"><view class="money">{{ Number(w.netAmount).toFixed(2) }}</view><text class="tag" :class="w.status === 3 ? 'ok' : w.status === 4 ? 'warn' : ''">{{ STATUS[w.status] }}</text></view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { bizApi } from '@/api'
const STATUS = { 1: '待审核', 2: '已审核', 3: '已打款', 4: '已驳回' }
const b = ref(null); const list = ref([]); const amount = ref(''); const loading = ref(false)
const tax = computed(() => (Number(amount.value || 0) * (b.value?.taxMode === 1 ? Number(b.value.taxRate) : 0)).toFixed(2))
const net = computed(() => (Number(amount.value || 0) - Number(tax.value)).toFixed(2))
async function load() { ;[b.value, list.value] = await Promise.all([bizApi.balance(), bizApi.withdrawals({ pageNo: 1, pageSize: 50 }).then(r => r.records)]) }
async function submit() {
  loading.value = true
  try { await bizApi.withdraw(Number(amount.value)); uni.showToast({ title: '已提交,等待审核' }); amount.value = ''; load() } finally { loading.value = false }
}
onShow(load)
</script>
