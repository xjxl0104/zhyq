<template>
  <view>
    <view class="card">
      <view class="row"><text style="font-weight:600">直属成员 {{ list.length }} 人</text>
        <text v-if="me.positionCode === 'P4'" class="tag" @click="uni.navigateTo({ url: '/pages/allocation/index' })">团队分配 ›</text></view>
      <view v-if="!list.length" class="muted">还没有成员。分享邀请海报,朋友通过您的邀请码注册即加入。</view>
      <view class="row" v-for="t in list" :key="t.id">
        <view><view>{{ t.name }}</view><view class="muted">{{ POS[t.positionCode] || t.positionCode }} · {{ t.joinTime?.slice(0, 10) }}</view></view>
        <text class="tag" :class="t.status === 1 ? 'ok' : 'warn'">{{ t.status === 1 ? '正常' : '冻结' }}</text>
      </view>
    </view>
    <button class="btn" @click="uni.switchTab({ url: '/pages/me/index' })">查看我的邀请码</button>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { meApi } from '@/api'
const POS = { P1: '园区伙伴', P2: '银牌合伙人', P3: '金牌合伙人', P4: '钻石合伙人' }
const list = ref([]); const me = ref({})
onShow(async () => { ;[list.value, me.value] = await Promise.all([meApi.team(), meApi.me()]) })
</script>
