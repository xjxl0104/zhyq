<template>
  <view class="page-wrap warehouse-page">
    <view class="card hero-card warehouse-hero">
      <view class="eyebrow">DIPARK · WAREHOUSE OPS</view>
      <view class="hero-title">云仓运营看板</view>
      <view class="hero-subtitle">把每一票订单、每一位客户都交付得更稳</view>
      <view class="status-line"><view class="status-dot"></view><text>运营服务正常</text><text class="status-time">实时数据</text></view>
    </view>

    <view class="section-head"><text class="section-title">今日概览</text><text class="section-link">运营指标</text></view>
    <view class="grid warehouse-stats">
      <view class="stat"><view class="muted">承接客户</view><view class="v">{{ stats.customerCount || 0 }}</view><view class="caption">持续合作</view></view>
      <view class="stat"><view class="muted">今日出库</view><view class="v">{{ stats.todayOrders || 0 }}</view><view class="caption">今日履约</view></view>
      <view class="stat"><view class="muted">本月出库</view><view class="v">{{ stats.monthOrders || 0 }}</view><view class="caption">累计单量</view></view>
      <view class="stat"><view class="muted">ERP 状态</view><view class="v status-value">{{ stats.erpStatus ?? '-' }}</view><view class="caption">同步连接</view></view>
    </view>

    <view class="section-head"><text class="section-title">运营入口</text><text class="caption">高频服务</text></view>
    <view class="card operation-card">
      <view class="operation-row pressable" @click="go('/pages/warehouse-apply/index')"><view class="operation-icon blue">申</view><view class="operation-copy"><view>加盟申请</view><view class="muted">提交资料，开启云仓合作</view></view><text class="operation-arrow">›</text></view>
      <view class="operation-row pressable" @click="go('/pages/warehouse-onboarding/index')"><view class="operation-icon cyan">进</view><view class="operation-copy"><view>加盟进度</view><view class="muted">查看审核与开通节点</view></view><text class="operation-arrow">›</text></view>
      <view class="operation-row pressable" @click="go('/pages/warehouse-erp/index')"><view class="operation-icon gold">ERP</view><view class="operation-copy"><view>ERP 接入</view><view class="muted">凭证、联通和同步日志</view></view><text class="operation-arrow">›</text></view>
      <view class="operation-row pressable" @click="go('/pages/warehouse-orders/index')"><view class="operation-icon ink">单</view><view class="operation-copy"><view>出库单</view><view class="muted">追踪订单与物流状态</view></view><text class="operation-arrow">›</text></view>
      <view class="operation-row pressable" @click="go('/pages/warehouse-notice/index')"><view class="operation-icon violet">信</view><view class="operation-copy"><view>消息通知</view><view class="muted">结算、账单与服务提醒</view></view><text class="operation-arrow">›</text></view>
      <view class="operation-row pressable" @click="go('/pages/account-security/index?role=wh')"><view class="operation-copy"><view>账号与密码</view><view class="muted">设置或修改账号密码登录</view></view><text class="operation-arrow">›</text></view>
      <view class="operation-row pressable" @click="logout"><view class="operation-copy"><view>退出登录</view></view><text class="operation-arrow">›</text></view>
    </view>
  </view>
</template>
<script setup>
import { reactive } from 'vue'; import { onShow } from '@dcloudio/uni-app'; import { warehouseApi } from '@/api/warehouse'; const stats = reactive({}); onShow(async () => Object.assign(stats, await warehouseApi.dashboard())); const go = url => uni.navigateTo({ url })
import { warehouseToken } from '@/utils/request'
function logout() { warehouseToken.clear(); uni.reLaunch({ url: '/pages/warehouse-login/index' }) }
</script>

<style scoped>
.warehouse-page { padding-top: 12rpx; }
.warehouse-hero { padding-bottom: 30rpx; }
.status-line { display: flex; align-items: center; gap: 12rpx; margin-top: 26rpx; color: rgba(255,255,255,.86); font-size: 23rpx; }
.status-dot { width: 14rpx; height: 14rpx; border-radius: 50%; background: #62d0a6; box-shadow: 0 0 0 7rpx rgba(98,208,166,.14); }
.status-time { margin-left: auto; color: rgba(255,255,255,.56); }
.warehouse-stats { margin: 0 24rpx; }
.status-value { font-size: 32rpx !important; }
.operation-card { padding-top: 12rpx; padding-bottom: 12rpx; }
.operation-row { display: flex; align-items: center; gap: 18rpx; min-height: 110rpx; border-bottom: 1rpx solid var(--park-line); }
.operation-row:last-child { border-bottom: 0; }
.operation-icon { display: flex; align-items: center; justify-content: center; width: 58rpx; height: 58rpx; flex: 0 0 58rpx; color: #fff; border-radius: 18rpx; font-size: 22rpx; font-weight: 700; }
.operation-icon.blue { background: linear-gradient(145deg, #637bff, #3857f5); }
.operation-icon.cyan { background: linear-gradient(145deg, #64d2df, #299cb0); }
.operation-icon.gold { background: linear-gradient(145deg, #f8ca69, #df961c); font-size: 17rpx; }
.operation-icon.ink { background: linear-gradient(145deg, #44527d, #283252); }
.operation-icon.violet { background: linear-gradient(145deg, #9b94f6, #6f63d0); }
.operation-copy { flex: 1; color: var(--park-text); font-size: 27rpx; font-weight: 600; }
.operation-copy .muted { margin-top: 5rpx; font-weight: 400; }
.operation-arrow { color: #abb3c7; font-size: 40rpx; font-weight: 300; }
</style>
