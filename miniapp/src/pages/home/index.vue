<template>
  <view class="page-wrap home-page">
    <view v-if="error" class="card"><view class="muted">{{ error }}</view><button class="btn" :disabled="loading" @click="load">重新加载</button></view>
    <view v-if="loading" class="card muted">正在读取收益…</view>
    <view v-if="home" class="card hero-card">
      <view class="hero-label">累计收益（元）</view>
      <view class="hero-number">{{ fmt(home.total) }}</view>
      <view class="hero-meta">
        <text class="hero-meta-item">可提现 {{ fmt(home.withdrawable) }}</text>
        <text class="hero-meta-item">冻结中 {{ fmt(home.frozen) }}</text>
        <text class="hero-meta-item">本月 {{ fmt(home.month) }}</text>
      </view>
    </view>

    <view class="section-head"><text class="section-title">常用功能</text></view>
    <view class="grid quick-grid">
      <view class="quick-card pressable" @click="go('/pages/referral/index')">
        <view class="quick-icon"><image src="/static/icons/user-plus.svg" mode="aspectFit" /></view><view><view class="quick-title">推荐客户</view><view class="quick-desc">园区入驻 / 云仓服务</view></view>
      </view>
      <view class="quick-card pressable" @click="go('/pages/position/index')">
        <view class="quick-icon"><image src="/static/icons/medal.svg" mode="aspectFit" /></view><view><view class="quick-title">我的称号</view><view class="quick-desc">{{ me.positionCode || '-' }} · 查看详情</view></view>
      </view>
      <view class="quick-card pressable" @click="go('/pages/team/index')">
        <view class="quick-icon"><image src="/static/icons/team.svg" mode="aspectFit" /></view><view><view class="quick-title">我的团队</view><view class="quick-desc">查看直属成员</view></view>
      </view>
      <view class="quick-card pressable" @click="go('/pages/withdraw/index')">
        <view class="quick-icon"><image src="/static/icons/wallet.svg" mode="aspectFit" /></view><view><view class="quick-title">申请提现</view><view class="quick-desc">可提 {{ fmt(home?.withdrawable) }}</view></view>
      </view>
      <view v-if="me.positionCode === 'P4' && Number(me.status) === 1" class="quick-card pressable" @click="go('/pages/allocation/index')">
        <view class="quick-icon"><image src="/static/icons/sliders.svg" mode="aspectFit" /></view><view><view class="quick-title">客户定价与分佣</view><view class="quick-desc">按客户自定义每单金额</view></view>
      </view>
    </view>
    <view class="home-note">积分及礼品兑换规则待配置，当前收益按金额展示。</view>

    <view class="section-head"><text class="section-title">最近动态</text><text class="section-link" @click="uni.navigateTo({url:'/pages/notices/index'})">消息通知</text></view>
    <view v-if="home" class="card activity-card">
      <view v-if="!home.recent?.length" class="list-empty">还没有收益记录，去推荐第一位客户吧</view>
      <view class="row" v-for="(r, i) in home.recent" :key="i">
        <view><view>{{ STATUS[r.status] }}</view><view class="caption">{{ r.time?.slice(0, 16) }}</view></view>
        <text class="money" :class="{ negative: r.amount < 0 }">{{ fmt(r.amount) }}</text>
      </view>
    </view>
  </view>
</template>

<script>
import { appShareMixin } from '@/utils/share'

export default { mixins: [appShareMixin] }
</script>

<script setup>
import { ref, onUnmounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { meApi } from '@/api'
import { token } from '@/utils/request'

const STATUS = { 1: '冻结', 2: '可结算', 3: '已结算', 4: '已提现', 5: '作废' }
const home = ref(null)
const me = ref({})
const error = ref(''), loading = ref(false)
const fmt = (v) => v == null ? '—' : Number(v).toFixed(3)
const go = (url) => uni.navigateTo({ url })
let requestNo = 0

async function load() {
  const request = ++requestNo
  home.value = null
  me.value = {}
  error.value = ''
  loading.value = false
  if (!token.get()) return uni.reLaunch({ url: '/pages/login/index' })
  loading.value = true
  try {
    const [summary, profile] = await Promise.all([meApi.home(), meApi.me()])
    if (request !== requestNo) return
    home.value = summary
    me.value = profile
  } catch (failure) {
    if (request === requestNo) error.value = failure.message || '收益读取失败，请重新加载'
  } finally {
    if (request === requestNo) loading.value = false
  }
}
onShow(load)
onUnmounted(() => { requestNo++ })
</script>

<style scoped>
.home-page { padding-top: 12rpx; }
.quick-grid { margin: 0 24rpx; }
.activity-card { padding-top: 14rpx; padding-bottom: 14rpx; }
.negative { color: var(--park-danger); }
.hero-card .hero-label { margin-top: 0; }
.home-note { margin: 20rpx 28rpx 0; color: var(--park-muted); font-size: 23rpx; line-height: 1.6; }
</style>
