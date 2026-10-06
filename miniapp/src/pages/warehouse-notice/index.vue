<template>
  <view class="notice-page">
    <view class="card">
      <view class="head">
        <text class="title">消息通知</text>
        <text v-if="unread > 0" class="unread-badge">{{ unread }} 条未读</text>
      </view>
      <view v-for="n in rows" :key="n.id" class="row" :class="{ 'row-unread': !n.readAt }" @click="open(n)">
        <view class="row-main">
          <view class="row-title">{{ n.title }}</view>
          <view class="muted">{{ n.content }}</view>
          <view class="muted time">{{ formatTime(n.createTime) }}</view>
        </view>
        <text class="tag" :class="n.readAt ? 'ok' : 'warn'">{{ n.readAt ? '已读' : '未读' }}</text>
      </view>
      <view v-if="loading" class="muted empty">正在读取通知…</view>
      <view v-if="error" class="notice-error" role="alert">{{ error }}</view>
      <button v-if="error" :disabled="loading" @click="retry">重试</button>
      <view v-if="!rows.length && loaded && !error" class="muted empty">暂无通知</view>
      <button v-if="rows.length < state.total" :disabled="loading" :loading="loading" @click="pager.load(false)">加载更多</button>
    </view>
  </view>
</template>

<script>
import { appShareMixin } from '@/utils/share'

export default { mixins: [appShareMixin] }
</script>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onShow, onReachBottom } from '@dcloudio/uni-app'
import { warehouseApi } from '@/api/warehouse'
import { createPager } from '@/utils/pagination.mjs'

const state = reactive({})
const pager = createPager(params => warehouseApi.notices(params), state)
const rows = computed(() => state.records)
const loaded = computed(() => state.loaded)
const loading = computed(() => state.loading)
const unread = ref(0)
const readError = ref('')
const error = computed(() => state.error || readError.value)
const reading = new Set()
let refreshNo = 0

async function load() {
  const current = ++refreshNo
  readError.value = ''
  await Promise.all([pager.load(true), (async () => {
    try {
      const count = await warehouseApi.noticeUnread()
      if (current === refreshNo) unread.value = Number(count) || 0
    } catch (e) { if (current === refreshNo) readError.value = e.message || '未读数读取失败，请重试' }
  })()])
}
async function retry() {
  if (state.error) await pager.retry()
  else await load()
}
async function open(n) {
  if (reading.has(n.id)) return
  reading.add(n.id)
  readError.value = ''
  try {
    if (!n.readAt) {
      await warehouseApi.noticeRead(n.id)
      n.readAt = new Date().toISOString()
      unread.value = Math.max(0, unread.value - 1)
    }
    if (n.bizType === 'settlement' || n.bizType === 'bill') {
      uni.navigateTo({ url: '/pages/warehouse-settlement/index?kind=' + n.bizType })
    }
  } catch (e) { readError.value = e.message || '通知读取失败，请重试' }
  finally { reading.delete(n.id) }
}

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

onShow(load)
onReachBottom(() => pager.load(false))
</script>

<style scoped>
.notice-error { color: #a03424; margin: 20rpx 0; }
.notice-page { padding: 12rpx; }
.card { background: #fff; border-radius: 12rpx; padding: 20rpx; }
.head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16rpx; }
.title { font-size: 32rpx; font-weight: 600; }
.unread-badge { font-size: 24rpx; color: #e6a23c; }
.row { display: flex; align-items: center; justify-content: space-between; padding: 20rpx 0; border-bottom: 1rpx solid #f0f0f0; }
.row-unread .row-title { font-weight: 600; }
.row-main { flex: 1; min-width: 0; }
.row-title { font-size: 28rpx; color: #222; }
.muted { font-size: 24rpx; color: #999; margin-top: 6rpx; }
.time { color: #bbb; }
.tag { font-size: 22rpx; padding: 4rpx 14rpx; border-radius: 20rpx; flex-shrink: 0; }
.tag.ok { color: #67c23a; background: #f0f9eb; }
.tag.warn { color: #e6a23c; background: #fdf6ec; }
.empty { text-align: center; padding: 40rpx 0; }
</style>
