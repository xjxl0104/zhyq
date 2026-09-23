<template>
  <view>
    <view v-if="state.error" class="card"><view class="muted">{{ state.error }}</view><button class="btn" :disabled="state.loading" @click="pager.retry()">重新加载</button></view>
    <view v-if="state.loading && !list.length" class="card muted">正在读取客户…</view>
    <view class="card" v-if="!list.length && state.loaded && !state.error"><view class="muted">还没有推荐客户</view><button class="btn" @click="uni.navigateTo({ url: '/pages/referral/index' })">去推荐</button></view>
    <view class="card" v-for="c in list" :key="c.id" @click="uni.navigateTo({ url: '/pages/customer-detail/index?id=' + c.id })">
      <view class="row">
        <view>
          <view style="font-weight:600">{{ c.name }}</view>
          <view class="muted">{{ c.contact || '' }} {{ c.phone }}</view>
        </view>
        <view style="text-align:right">
          <view><text class="tag" :class="lockClass(c)">{{ lockText(c) }}</text></view>
          <view class="muted" style="margin-top:6rpx">{{ c.grade ? c.grade + ' 级' : '未评级' }} · {{ STATUS[c.status] || '' }}</view>
        </view>
      </view>
    </view>
    <view v-if="list.length" class="card"><button v-if="list.length < state.total" class="btn ghost" :loading="state.loading" :disabled="state.loading" @click="pager.load(false)">加载更多客户</button><view v-else class="muted">共 {{ state.total }} 位客户，已全部显示</view></view>
    <button v-if="list.length" class="btn" @click="uni.navigateTo({ url: '/pages/referral/index' })">推荐新客户</button>
  </view>
</template>

<script setup>
import { reactive, computed, onUnmounted } from 'vue'
import { onShow, onReachBottom } from '@dcloudio/uni-app'
import { bizApi } from '@/api'
import { createPager } from '@/utils/pagination.mjs'

const STATUS = { 1: '跟进中', 2: '已签约', 3: '已流失' }
const state = reactive({})
const pager = createPager(bizApi.customers, state)
const list = computed(() => state.records)
const lockText = (c) => !c.lockStatus ? '未锁定' : c.lockStatus === 1 ? `预锁 · 剩 ${c.lockDaysLeft} 天` : c.lockStatus === 2 ? `锁定 · 剩 ${c.lockDaysLeft} 天` : c.lockStatus === 3 ? '已成交' : '已释放'
const lockClass = (c) => c.lockStatus === 2 || c.lockStatus === 3 ? 'ok' : c.lockStatus === 1 ? 'warn' : ''

onShow(() => pager.load())
onReachBottom(() => pager.load(false))
onUnmounted(pager.invalidate)
</script>
