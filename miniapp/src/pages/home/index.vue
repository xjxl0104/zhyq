<template>
  <view class="page-wrap home-page">
    <view class="card hero-card">
      <view class="eyebrow">DIPARK · PARTNER NETWORK</view>
      <view class="hero-title">园区伙伴工作台</view>
      <view class="hero-subtitle">连接客户、空间与云仓服务，让每一次推荐都有回响</view>
      <view class="hero-label">累计收益（元）</view>
      <view class="hero-number">{{ fmt(home.total) }}</view>
      <view class="hero-meta">
        <text class="hero-meta-item">可提现 {{ fmt(home.withdrawable) }}</text>
        <text class="hero-meta-item">冻结中 {{ fmt(home.frozen) }}</text>
        <text class="hero-meta-item">本月 {{ fmt(home.month) }}</text>
      </view>
    </view>

    <view class="section-head"><text class="section-title">快捷入口</text><text class="section-link">运营服务</text></view>
    <view class="grid quick-grid">
      <view class="quick-card pressable" @click="go('/pages/referral/index')">
        <view class="quick-icon">荐</view><view><view class="quick-title">推荐客户</view><view class="quick-desc">园区入驻 / 云仓服务</view></view>
      </view>
      <view class="quick-card pressable" @click="go('/pages/position/index')">
        <view class="quick-icon">岗</view><view><view class="quick-title">我的岗位</view><view class="quick-desc">{{ me.positionCode || '-' }} · 晋升进度</view></view>
      </view>
      <view class="quick-card pressable" @click="go('/pages/team/index')">
        <view class="quick-icon">团</view><view><view class="quick-title">我的团队</view><view class="quick-desc">查看直属成员</view></view>
      </view>
      <view class="quick-card pressable" @click="go('/pages/withdraw/index')">
        <view class="quick-icon">提</view><view><view class="quick-title">申请提现</view><view class="quick-desc">可提 {{ fmt(home.withdrawable) }}</view></view>
      </view>
    </view>

    <view class="section-head"><text class="section-title">最近动态</text><text class="caption">实时更新</text></view>
    <view class="card activity-card">
      <view v-if="!home.recent?.length" class="list-empty">还没有收益记录，去推荐第一位客户吧</view>
      <view class="row" v-for="(r, i) in home.recent" :key="i">
        <view><view>{{ STATUS[r.status] }}</view><view class="caption">{{ r.time?.slice(0, 16) }}</view></view>
        <text class="money" :class="{ negative: r.amount < 0 }">{{ fmt(r.amount) }}</text>
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

<style scoped>
.home-page { padding-top: 12rpx; }
.quick-grid { margin: 0 24rpx; }
.activity-card { padding-top: 14rpx; padding-bottom: 14rpx; }
.negative { color: #c84b5c; }
</style>
