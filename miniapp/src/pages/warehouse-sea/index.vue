<template>
  <view class="page-wrap">
    <view class="card sea-hero">
      <view class="sea-hero-top">
        <view class="sea-hero-title">公海热单限时认领</view>
        <view class="sea-badge">主账号专享</view>
      </view>
      <view class="sea-hero-sub">
        园区自营清洗与 30 秒算价热留资货主 · 成单免切 70% 外部团队佣金（仅计提 15% P0 跟单奖）
      </view>
      <view class="quota-bar">
        <view>今日剩余：<b>{{ pool.dailyLeft ?? 5 }} / {{ pool.dailyCap ?? 5 }}</b> 户</view>
        <view>在途额度：<b>{{ pool.activeUsed ?? 0 }} / {{ pool.activeCap ?? 10 }}</b> 户</view>
      </view>
      <view class="sea-rule-tip">⏱ 认领后须在 {{ pool.slaHours ?? 72 }} 小时内登记首通接洽反馈，超时自动弹回园区公海。</view>
    </view>

    <view v-if="loading" class="card"><view class="wh-note">正在读取可认领公海热单…</view></view>
    <view v-else-if="error" class="card">
      <view class="wh-error">{{ error }}</view>
      <button class="wh-secondary" @click="load(page)">重新读取</button>
    </view>
    <view v-else-if="!rows.length" class="card">
      <view class="wh-note">当前暂无待认领热单，园区招商核实或新货主算价留资后将实时推送至此。</view>
    </view>

    <view v-for="item in rows" :key="item.customerId" class="card sea-item">
      <view class="sea-item-head">
        <view class="wh-item-title">{{ item.displayName }}</view>
        <view class="src-chip" :class="{ hot: item.seaSource === 'CALC_HOT' }">
          {{ item.seaSource === 'CALC_HOT' ? '🔥 算价热单' : '园区严选' }}
        </view>
      </view>
      <view class="wh-meta">
        主营类目：<b>{{ item.category || '电商货主' }}</b>
        <text v-if="item.estDailyOrders"> · 日估 {{ item.estDailyOrders }} 单</text>
        <text v-if="item.avgWeightKg"> · 均重 {{ item.avgWeightKg }}kg</text>
      </view>
      <view class="block-box">💡 需求与卡点：{{ item.blockNote || '待接洽确认搬仓与试发安排' }}</view>
      <view class="wh-meta">联系方式：<b>{{ item.contact }} · {{ item.phone }}</b></view>

      <view v-if="item.claimedByMe" class="claimed-zone">
        <view v-if="item.firstTouched" class="touch-ok">✔ 已登记首通反馈，已转入「承接客户」持续跟进</view>
        <template v-else>
          <view class="touch-warn">⏱ 剩 {{ item.hoursLeft ?? 72 }}h 首触达保活期，请回填首通接洽结果：</view>
          <input
            class="touch-inp"
            v-model="touchNotes[item.customerId]"
            maxlength="300"
            placeholder="填写首通电话结果（如：已约周四寄样试发）"
          />
          <button class="wh-primary-btn" :disabled="busyId === item.customerId" @click="submitTouch(item)">
            提交接洽反馈（解除超时回收）
          </button>
        </template>
      </view>
      <button
        v-else
        class="wh-primary-btn"
        :disabled="busyId === item.customerId || (pool.dailyLeft !== undefined && pool.dailyLeft <= 0)"
        @click="claimItem(item)"
      >
        限时认领并解锁老板电话
      </button>
    </view>
  </view>
</template>

<script>
import { appShareMixin } from '@/utils/share'
export default { mixins: [appShareMixin] }
</script>

<script setup>
import { reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { warehouseApi } from '@/api/warehouse'

const pool = reactive({})
const rows = ref([])
const page = ref(1)
const loading = ref(false)
const error = ref('')
const busyId = ref(null)
const touchNotes = reactive({})

async function load(next = 1) {
  loading.value = true
  error.value = ''
  try {
    const res = await warehouseApi.publicSea({ pageNo: next, pageSize: 20 })
    Object.assign(pool, res || {})
    rows.value = res?.records || []
    page.value = next
  } catch (e) {
    rows.value = []
    error.value = e?.message || '公海热单读取失败，请重试'
  } finally {
    loading.value = false
  }
}

async function claimItem(item) {
  if (busyId.value) return
  busyId.value = item.customerId
  try {
    const res = await warehouseApi.claimPublicSea(item.customerId)
    uni.showToast({ title: `认领成功：${res.phone || ''}`, icon: 'none', duration: 2800 })
    await load(page.value)
  } finally {
    busyId.value = null
  }
}

async function submitTouch(item) {
  const note = (touchNotes[item.customerId] || '').trim()
  if (note.length < 4) {
    uni.showToast({ title: '请填写至少4个字的首通接洽结果', icon: 'none' })
    return
  }
  busyId.value = item.customerId
  try {
    await warehouseApi.touchPublicSea(item.customerId, note)
    uni.showToast({ title: '已登记反馈，保活成功', icon: 'success' })
    await load(page.value)
  } finally {
    busyId.value = null
  }
}

onShow(() => load(1))
</script>

<style scoped>
@import '../../styles/warehouse.css';
.sea-hero { background: linear-gradient(135deg, #1e3a8a, #2563eb); color: #fff; }
.sea-hero-top { display: flex; justify-content: space-between; align-items: center; }
.sea-hero-title { font-size: 36rpx; font-weight: 700; }
.sea-badge { background: rgba(251, 191, 36, 0.22); color: #fde68a; padding: 4rpx 14rpx; border-radius: 999rpx; font-size: 22rpx; }
.sea-hero-sub { margin-top: 12rpx; font-size: 24rpx; color: #dbeafe; line-height: 1.5; }
.quota-bar { margin-top: 18rpx; background: rgba(255, 255, 255, 0.14); padding: 14rpx 18rpx; border-radius: 12rpx; display: flex; justify-content: space-between; font-size: 25rpx; }
.sea-rule-tip { margin-top: 12rpx; font-size: 22rpx; color: #bfdbfe; }
.sea-item-head { display: flex; justify-content: space-between; align-items: center; gap: 12rpx; margin-bottom: 10rpx; }
.src-chip { font-size: 22rpx; padding: 4rpx 12rpx; border-radius: 999rpx; background: #eff6ff; color: #2563eb; }
.src-chip.hot { background: #fff1f2; color: #e11d48; font-weight: 600; }
.block-box { margin: 14rpx 0; padding: 14rpx; background: #f8fafc; border-radius: 10rpx; font-size: 25rpx; color: #475569; }
.wh-primary-btn { margin-top: 16rpx; background: #2563eb; color: #fff; border-radius: 12rpx; font-size: 28rpx; font-weight: 600; padding: 16rpx 0; }
.claimed-zone { margin-top: 14rpx; padding-top: 14rpx; border-top: 1rpx dashed #cbd5e1; }
.touch-ok { font-size: 25rpx; color: #16a34a; font-weight: 600; }
.touch-warn { font-size: 24rpx; color: #d97706; margin-bottom: 10rpx; }
.touch-inp { border: 1rpx solid #cbd5e1; border-radius: 10rpx; padding: 14rpx 16rpx; font-size: 26rpx; background: #fff; }
</style>
