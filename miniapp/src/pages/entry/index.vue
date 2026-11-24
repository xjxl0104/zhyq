<template>
  <view class="page-wrap entry-page">
    <view class="entry-hero">
      <view class="brand-mark">D</view>
      <view class="eyebrow">DIPARK · SMART PARK</view>
      <view class="entry-title">选择你的身份</view>
      <view class="entry-subtitle">进入全民营销或云仓商家工作台</view>
    </view>

    <view class="card role-card pressable" @click="goPartner">
      <view class="role-mark partner">P</view>
      <view class="role-main">
        <view class="role-title">全民营销</view>
        <view class="role-desc">推荐客户、查看收益与团队</view>
      </view>
      <view class="role-arrow">›</view>
    </view>

    <view class="card role-card pressable" @click="goWarehouse">
      <view class="role-mark warehouse">W</view>
      <view class="role-main">
        <view class="role-title">云仓商家</view>
        <view class="role-desc">加盟进度、ERP 接入、订单与结算</view>
      </view>
      <view class="role-arrow">›</view>
    </view>

    <view v-if="showDemo" class="demo-aux-wrap">
      <button class="demo-aux-link" @click="goDemo">查看功能演示（示例数据） ›</button>
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
import { demoEntryEnabled } from '@/utils/demo-entry-env'

const showDemo = typeof demoEntryEnabled === 'boolean' ? demoEntryEnabled : true

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
function goDemo() { if (showDemo) uni.navigateTo({ url: '/pages/demo/index' }) }
</script>

<style scoped>
.entry-page { padding: 32rpx 24rpx 56rpx; }
.entry-hero { padding: 30rpx 18rpx 34rpx; }
.entry-hero .brand-mark { margin-bottom: 34rpx; }
.entry-title { margin-top: 12rpx; color: var(--park-ink); font-size: 52rpx; font-weight: 750; letter-spacing: -1rpx; }
.entry-subtitle { max-width: 560rpx; margin-top: 12rpx; color: var(--park-muted); font-size: 26rpx; line-height: 1.6; }
.role-card { display: flex; align-items: center; gap: 24rpx; padding: 34rpx 30rpx; }
.role-mark { display: flex; align-items: center; justify-content: center; width: 88rpx; height: 88rpx; flex: 0 0 88rpx; border-radius: 22rpx; color: #fff; font-size: 40rpx; font-weight: 700; }
.role-mark.partner { background: linear-gradient(145deg, var(--park-blue-2), var(--park-blue)); }
.role-mark.warehouse { background: linear-gradient(145deg, #50c7d8, #299cb0); }
.role-main { flex: 1; }
.role-title { color: var(--park-ink); font-size: 32rpx; font-weight: 650; }
.role-desc { margin-top: 8rpx; color: var(--park-muted); font-size: 25rpx; }
.role-arrow { color: #c3cadb; font-size: 40rpx; }
.demo-aux-wrap { margin-top: 36rpx; text-align: center; }
.demo-aux-link { display: inline-block; margin: 0 auto; padding: 12rpx 20rpx; color: #8691a8; background: transparent; font-size: 24rpx; line-height: 1.5; }
.demo-aux-link::after { display: none; }
</style>
