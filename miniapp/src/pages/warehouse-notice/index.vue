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
      <view v-if="!rows.length && loaded" class="muted empty">暂无通知</view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { warehouseApi } from '@/api/warehouse'

const rows = ref([])
const unread = ref(0)
const loaded = ref(false)

async function load() {
  const r = await warehouseApi.notices({ pageNo: 1, pageSize: 50 })
  rows.value = r.records || []
  const u = await warehouseApi.noticeUnread()
  unread.value = Number(u) || 0
  loaded.value = true
}

async function open(n) {
  if (!n.readAt) {
    await warehouseApi.noticeRead(n.id)
    n.readAt = new Date().toISOString()
    unread.value = Math.max(0, unread.value - 1)
  }
  if (n.bizType === 'settlement') uni.navigateTo({ url: '/pages/warehouse-settlement/index' })
  else if (n.bizType === 'bill') uni.navigateTo({ url: '/pages/warehouse-settlement/index' })
}

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

onShow(load)
</script>

<style scoped>
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
