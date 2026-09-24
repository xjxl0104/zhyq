<template><view class="page-wrap">
  <view v-if="error" class="card"><text class="error">{{ error }}</text><button @click="load(true)">重新加载</button></view>
  <view v-if="loading && !rows.length" class="card muted">正在加载消息…</view>
  <view v-else-if="!rows.length" class="card muted">暂无消息，客户归属和佣金变化将在这里通知您。</view>
  <view v-for="n in rows" :key="n.id" class="card" @click="read(n)">
    <view class="row"><text class="title">{{ n.title }}</text><text v-if="!n.readFlag" class="tag">未读</text></view>
    <view class="content">{{ n.content }}</view><view class="muted">{{ (n.sendTime || n.createTime || '').slice(0,16) }}</view>
    <button v-if="!n.readFlag" size="mini" @click.stop="read(n)">标记已读</button>
  </view>
  <button v-if="rows.length < total" class="btn ghost" :disabled="loading" @click="load(false)">加载更多</button>
</view></template>
<script setup>
import {ref} from 'vue'
import {onShow} from '@dcloudio/uni-app'
import {bizApi} from '@/api'
const rows=ref([]), total=ref(0), loading=ref(false), error=ref(''); let page=1
async function load(reset=true){if(loading.value)return;loading.value=true;error.value='';try{const next=reset?1:page+1;const data=await bizApi.notices({pageNo:next,pageSize:20});rows.value=reset?data.records:[...rows.value,...data.records];total.value=data.total;page=next}catch(e){error.value=e.message}finally{loading.value=false}}
async function read(n){try{await bizApi.noticeRead(n.id);n.readFlag=1}catch(e){error.value=e.message}}
onShow(()=>load(true))
</script>
<style scoped>.content{margin:18rpx 0;line-height:1.7;white-space:pre-wrap;overflow-wrap:anywhere}.error{color:#a3293e}.row{gap:16rpx;align-items:flex-start}</style>
