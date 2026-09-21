<template>
  <view>
    <view class="card" style="background:#4f46e5;color:#fff">
      <view class="muted" style="color:#c7d2fe">累计收益(元)</view>
      <view style="font-size:56rpx;font-weight:600;margin:8rpx 0">{{ fmt(home.total) }}</view>
      <view style="display:flex;gap:40rpx;font-size:24rpx">
        <text>可提现 {{ fmt(home.withdrawable) }}</text>
        <text>冻结中 {{ fmt(home.frozen) }}</text>
        <text>本月 {{ fmt(home.month) }}</text>
      </view>
    </view>

    <view class="grid" style="margin:0 20rpx">
      <view class="stat" @click="go('/pages/referral/index')"><view>推荐客户</view><view class="muted">园区入驻 / 云仓服务</view></view>
      <view class="stat" @click="go('/pages/position/index')"><view>我的岗位</view><view class="muted">{{ me.positionCode || '-' }} · 晋升进度</view></view>
      <view class="stat" @click="go('/pages/team/index')"><view>我的团队</view><view class="muted">直属成员</view></view>
      <view class="stat" @click="go('/pages/withdraw/index')"><view>提现</view><view class="muted">可提 {{ fmt(home.withdrawable) }}</view></view>
    </view>

    <view class="card">
      <view style="font-weight:600;margin-bottom:12rpx">最近动态</view>
      <view v-if="!home.recent?.length" class="muted">还没有收益记录,去推荐第一位客户吧</view>
      <view class="row" v-for="(r, i) in home.recent" :key="i">
        <text>{{ STATUS[r.status] }}</text>
        <text class="money" :style="{ color: r.amount < 0 ? '#c00' : '' }">{{ fmt(r.amount) }}</text>
        <text class="muted">{{ r.time?.slice(0, 10) }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { meApi } from '@/api'
import { token } from '@/utils/request'

const STATUS = { 1: '冻结', 2: '可结算', 3: '已结算', 4: '已提现', 5: '作废' }
const home = ref({})
const me = ref({})
const fmt = (v) => Number(v || 0).toFixed(2)
const go = (url) => uni.navigateTo({ url })

onShow(async () => {
  if (!token.get()) return uni.reLaunch({ url: '/pages/login/index' })
  ;[home.value, me.value] = await Promise.all([meApi.home(), meApi.me()])
})
</script>
