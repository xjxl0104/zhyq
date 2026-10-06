<template>
  <view class="page-wrap">
    <view class="card hero-card">
      <view class="hero-title">{{ profile?.warehouseName || '云仓工作台' }}</view>
      <view class="hero-subtitle">{{ JOIN[stats.joinStatus] || '正在读取状态' }} · {{ stats.orderMode === 'manual' ? '人工导入出库单' : '外部 ERP 接入' }}</view>
    </view>
    <view v-if="profile && !profile.profileComplete && profile.profileEditable" class="card profile-prompt">
      <view class="wh-item-title">个人信息可稍后补充</view>
      <view class="wh-note">先使用工作台，需要时再填写云仓名称、联系人和联系电话。</view>
      <button class="wh-secondary" @click="go('warehouse-profile')">完善个人信息</button>
    </view>
    <view v-if="error" class="card">
      <view class="wh-error" role="alert">{{ error }}</view>
      <button class="wh-secondary" :disabled="loading" @click="load">重新读取</button>
    </view>
    <view class="card">
      <view v-if="loading" class="wh-note" role="status">正在更新运营数据…</view>
      <view class="wh-row">
        <view class="wh-item"><view class="wh-note">已承接客户</view><view class="wh-title">{{ stats.customerCount ?? '—' }}</view></view>
        <view class="wh-item"><view class="wh-note">待确认客户</view><view class="wh-title">{{ stats.pendingCustomers ?? '—' }}</view></view>
        <view class="wh-item"><view class="wh-note">今日出库</view><view class="wh-title">{{ stats.todayOrders ?? '—' }}</view></view>
        <view class="wh-item"><view class="wh-note">本月出库</view><view class="wh-title">{{ stats.monthOrders ?? '—' }}</view></view>
      </view>
      <view class="wh-note">{{ stats.orderMode === 'manual' ? '订单由园区运营导入后同步展示。' : 'ERP 状态：' + (ERP[stats.erpStatus] || '待确认') }}</view>
    </view>
    <view class="card">
      <view class="wh-item-title">业务办理</view>
      <button v-for="item in entries" :key="item.page" class="entry" @click="go(item.page)">
        <view>{{ item.title }}</view>
        <view class="wh-note">{{ item.note }}</view>
      </button>
      <button class="wh-secondary" @click="logout">退出登录</button>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { warehouseApi } from '@/api/warehouse'
import { warehouseToken } from '@/utils/request'
import { JOIN } from '@/utils/warehouse-ui'

const stats = reactive({})
const profile = ref(null)
const loading = ref(false)
const error = ref('')
const ERP = { 0: '未接入', 1: '沙箱联调', 2: '正式联通', 3: '连接中断' }
const entries = [
  { page: 'warehouse-profile', title: '个人信息', note: '补充云仓名称、联系人和联系电话' },
  { page: 'warehouse-apply', title: '加盟申请', note: '完善资料与上传资质附件' },
  { page: 'warehouse-onboarding', title: '加盟进度', note: '查看审核、协议及上线状态' },
  { page: 'warehouse-customers', title: '承接客户', note: '确认分派并更新服务进度' },
  { page: 'warehouse-orders', title: '出库单', note: '查看园区导入的订单与物流' },
  { page: 'warehouse-contracts', title: '合同与协议', note: '签署件上传、直签合同备案' },
  { page: 'warehouse-settlement', title: '账单与结算', note: '核对费用、确认或提出异议' },
  { page: 'warehouse-notice', title: '消息通知', note: '查看业务办理与结算提醒' },
  { page: 'warehouse-erp', title: 'ERP 接口', note: '外部接入的凭证与同步日志' },
  { page: 'account-security', title: '账号与密码', note: '设置或修改登录密码' }
]

async function load() {
  if (loading.value) return
  loading.value = true
  error.value = ''
  const results = await Promise.allSettled([warehouseApi.dashboard(), warehouseApi.me()])
  if (results[0].status === 'fulfilled') Object.assign(stats, results[0].value)
  if (results[1].status === 'fulfilled') profile.value = results[1].value
  const failed = results.find(result => result.status === 'rejected')
  if (failed) error.value = failed.reason?.message || '工作台信息读取失败，请重试'
  loading.value = false
}

function go(page) {
  uni.navigateTo({ url: '/pages/' + page + '/index' + (page === 'account-security' ? '?role=wh' : '') })
}

function logout() {
  warehouseToken.clear()
  uni.reLaunch({ url: '/pages/entry/index?select=1' })
}

onShow(load)
</script>

<style scoped>
@import '../../styles/warehouse.css';
.entry { display: block; width: 100%; padding: 24rpx 0; text-align: left; background: transparent; color: #17203e; border-bottom: 1rpx solid #e6eaf3; border-radius: 0; font-size: 32rpx; line-height: 1.5; }
.entry .wh-note { margin: 6rpx 0; font-size: 26rpx; }
.hero-subtitle { margin-top: 20rpx; color: #dce3ff; font-size: 28rpx; line-height: 1.6; }
.profile-prompt .wh-note { margin: 12rpx 0 20rpx; }
</style>
