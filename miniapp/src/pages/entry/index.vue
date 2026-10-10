<template>
  <view class="page-wrap entry-page">
    <view class="entry-hero">
      <view class="brand-mark"><image src="/static/icons/megaphone-white.svg" mode="aspectFit" /></view>
      <view class="entry-title">选择你的身份</view>
      <view class="entry-subtitle">进入全民营销或云仓商家工作台</view>
    </view>

    <view class="card role-card pressable" @click="goPartner">
      <view class="icon-box"><image src="/static/icons/megaphone.svg" mode="aspectFit" /></view>
      <view class="role-main">
        <view class="role-title">全民营销</view>
        <view class="role-desc">推荐客户、查看收益与团队</view>
      </view>
      <image class="chevron" src="/static/icons/chevron.svg" mode="aspectFit" />
    </view>

    <view class="card role-card pressable" @click="goWarehouse">
      <view class="icon-box"><image src="/static/icons/warehouse.svg" mode="aspectFit" /></view>
      <view class="role-main">
        <view class="role-title">云仓商家</view>
        <view class="role-desc">加盟进度、ERP 接入、订单与结算</view>
      </view>
      <image class="chevron" src="/static/icons/chevron.svg" mode="aspectFit" />
    </view>

    <view class="demo-entry pressable" @click="goDemo">
      <view class="demo-entry-main">
        <view class="demo-entry-title">无需登录，先体验功能</view>
        <view class="demo-entry-desc">临时演示数据 · 不会提交真实业务</view>
      </view>
      <image class="chevron" src="/static/icons/chevron.svg" mode="aspectFit" />
    </view>
  </view>
</template>

<script>
import { appShareMixin } from '@/utils/share'

export default { mixins: [appShareMixin] }
</script>

<script setup>
import { onLoad } from '@dcloudio/uni-app'
import { token, warehouseToken } from '@/utils/request'

// 已登录直接进对应工作台;未登录停留在此页让用户选身份
onLoad(q => {
  if (q?.select === '1') return
  if (token.get()) return uni.switchTab({ url: '/pages/home/index' })
  if (warehouseToken.get()) return uni.reLaunch({ url: '/pages/warehouse-dashboard/index' })
})

function goPartner() {
  if (token.get()) return uni.switchTab({ url: '/pages/home/index' })
  uni.reLaunch({ url: '/pages/login/index' })
}
function goWarehouse() {
  uni.reLaunch({ url: warehouseToken.get() ? '/pages/warehouse-dashboard/index' : '/pages/warehouse-login/index' })
}
function goDemo() { uni.navigateTo({ url: '/pages/demo/index' }) }
</script>

<style scoped>
.entry-page { padding: 48rpx 8rpx 56rpx; }
.entry-hero { padding: 24rpx 28rpx 40rpx; }
.entry-title { margin-top: 40rpx; color: var(--park-ink); font-size: 52rpx; font-weight: 600; line-height: 1.25; }
.entry-subtitle { margin-top: 12rpx; color: var(--park-muted); font-size: 27rpx; line-height: 1.6; }
.role-card { display: flex; align-items: center; gap: 24rpx; padding: 32rpx 28rpx; }
.role-main { flex: 1; min-width: 0; }
.role-title { color: var(--park-ink); font-size: 32rpx; font-weight: 600; }
.role-desc { margin-top: 6rpx; color: var(--park-muted); font-size: 25rpx; }
.demo-entry { display: flex; align-items: center; gap: 18rpx; margin: 40rpx 24rpx 0; padding: 24rpx 28rpx; border-radius: 20rpx; background: #eceef3; }
.demo-entry-main { flex: 1; min-width: 0; }
.demo-entry-title { color: var(--park-ink); font-size: 27rpx; font-weight: 500; }
.demo-entry-desc { margin-top: 4rpx; color: var(--park-muted); font-size: 23rpx; }
</style>
