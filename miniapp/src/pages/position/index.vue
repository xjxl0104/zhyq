<template>
  <view v-if="p">
    <view class="card">
      <view class="muted">当前岗位</view>
      <view style="font-size:40rpx;font-weight:600">{{ p.name }} <text class="tag">份额 {{ p.sharePct }}%</text></view>
      <view class="muted">自 {{ p.since?.slice(0, 10) }}</view>
    </view>
    <view class="card">
      <view style="font-weight:600;margin-bottom:12rpx">近 12 个月业绩</view>
      <view class="row"><text class="muted">成交额</text><text class="money">{{ Number(p.amount12m).toFixed(2) }}</text></view>
      <view class="row"><text class="muted">成交单数</text><text>{{ p.orders12m }}</text></view>
    </view>
    <view class="card" v-if="p.next">
      <view style="font-weight:600;margin-bottom:12rpx">晋升到 {{ p.next.name }}</view>
      <view class="row"><text class="muted">成交额</text><text>{{ Number(p.amount12m).toFixed(0) }} / {{ Number(p.next.needAmount).toFixed(0) }}</text></view>
      <progress :percent="pct(p.amount12m, p.next.needAmount)" stroke-width="6" activeColor="#4f46e5" />
      <view class="row"><text class="muted">成交单数</text><text>{{ p.orders12m }} / {{ p.next.needOrders }}</text></view>
      <progress :percent="pct(p.orders12m, p.next.needOrders)" stroke-width="6" activeColor="#4f46e5" />
      <view class="muted" style="margin-top:12rpx">每月 1 日自动复核,达标即晋升。门槛只看您本人的成交,不看人数。</view>
    </view>
    <view class="card" v-else><view class="muted">您已是最高岗位</view></view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { meApi } from '@/api'
const p = ref(null)
const pct = (a, b) => (!b ? 100 : Math.min(100, Math.round((Number(a) / Number(b)) * 100)))
onShow(async () => { p.value = await meApi.position() })
</script>
