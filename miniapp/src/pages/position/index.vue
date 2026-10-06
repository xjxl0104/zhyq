<template>
  <view>
    <view v-if="error" class="card"><view class="muted">{{ error }}</view><button class="btn" :disabled="loading" @click="load">重新加载</button></view>
    <view v-if="loading" class="card muted">正在读取称号资料…</view>
    <template v-if="p">
    <view class="card">
      <view class="muted">当前称号</view>
      <view style="font-size:40rpx;font-weight:600">{{ p.name }} <text class="tag">{{ p.code }}</text></view>
      <view class="muted">自 {{ p.since?.slice(0, 10) }}</view>
      <view class="muted" style="margin-top:20rpx">P1–P4 的每单金额均由 P4 按客户自定义，称号不对应固定金额或比例。</view>
      <button v-if="p.code === 'P4'" class="btn secondary" @click="uni.navigateTo({ url: '/pages/allocation/index' })">客户定价与分佣</button>
    </view>
    <view class="card">
      <view style="font-weight:600;margin-bottom:12rpx">近 12 个月业绩</view>
      <view class="row"><text class="muted">成交额</text><text class="money">{{ Number(p.amount12m).toFixed(2) }}</text></view>
      <view class="row"><text class="muted">成交单数</text><text>{{ p.orders12m }}</text></view>
    </view>
    <view class="card">
      <view style="font-weight:600;margin-bottom:12rpx">称号说明</view>
      <view class="muted">称号由所属 P4 设置，不按成交额或单数自动晋升。P4 可管理邀请体系成员称号，并配置负责客户的每单金额。</view>
      <button v-if="p.code === 'P4'" class="btn secondary" @click="uni.navigateTo({ url: '/pages/team/index' })">管理团队称号</button>
    </view>
    </template>
  </view>
</template>

<script setup>
import { ref, onUnmounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { meApi } from '@/api'
const p = ref(null)
const error = ref(''), loading = ref(false)
let requestNo = 0
async function load() {
  const request = ++requestNo
  p.value = null
  error.value = ''
  loading.value = true
  try {
    const position = await meApi.position()
    if (request === requestNo) p.value = position
  } catch (failure) {
    if (request === requestNo) error.value = failure.message || '称号资料读取失败，请重新加载'
  } finally {
    if (request === requestNo) loading.value = false
  }
}
onShow(load)
onUnmounted(() => { requestNo++ })
</script>
