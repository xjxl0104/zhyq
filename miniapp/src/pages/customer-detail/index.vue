<template>
  <view class="page-wrap">
    <view v-if="error" class="card"><view class="error">{{ error }}</view><button @click="load">重新加载</button></view>
    <view v-if="loading && !c" class="card muted">正在读取客户资料…</view>
    <template v-if="c">
      <view class="card">
        <view class="title">{{ c.name }}</view>
        <view class="row"><text class="muted">联系人</text><text>{{ c.contact || '未填写' }} {{ c.phone }}</text></view>
        <view class="row"><text class="muted">评级</text><text>{{ c.grade || '未评级' }}</text></view>
        <view class="row"><text class="muted">状态</text><text>{{ STATUS[c.status] || '待确认' }}</text></view>
        <view class="row"><text class="muted">客户归属</text><text>{{ lockText }}</text></view>
        <view class="row"><text class="muted">意向云仓</text><text>{{ c.intendedWarehouseName || '不限' }}</text></view>
        <view class="row"><text class="muted">分派云仓</text><text>{{ c.assignedWarehouseName || '待园区分派' }}</text></view>
        <view class="row"><text class="muted">承接状态</text><text>{{ ASSIGN[c.warehouseAssignmentStatus || 0] }}</text></view>
        <view class="row"><text class="muted">推荐时间</text><text>{{ c.createTime?.slice(0, 16) }}</text></view>
      </view>
      <view class="card">
        <view class="title">服务进度</view>
        <view class="progress">{{ c.publicProgress || '园区尚未更新服务进度，请稍后查看。' }}</view>
        <view v-if="c.progressUpdatedAt" class="muted">更新于 {{ c.progressUpdatedAt.slice(0, 16) }}</view>
      </view>
      <view v-if="c.canExtend" class="card">
        <view class="muted">近60天有到访或方案记录且符合延期次数限制时，可以延长锁定90天。</view>
        <button class="btn" :loading="busy" :disabled="busy" @click="extend">延长锁定90天</button>
      </view>
      <button class="btn ghost" :disabled="loading" @click="load">刷新进度</button>
      <DeleteCustomerButton :customer="c" :disabled="loading || busy" @deleted="uni.switchTab({ url: '/pages/customers/index' })" />
    </template>
  </view>
</template>
<script setup>
import { ref, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { bizApi } from '@/api'
import DeleteCustomerButton from '@/components/DeleteCustomerButton.vue'
const STATUS = {1:'跟进中',2:'已签约',3:'已流失'}
const ASSIGN = {0:'待分派',1:'待商家确认',2:'已承接',3:'商家已拒绝，等待重新分派'}
const c = ref(null), error = ref(''), loading = ref(false), busy = ref(false)
let id
onLoad(q => { id = q.id })
onShow(load)
async function load() {
  if (!id || loading.value) return
  loading.value = true; error.value = ''
  try { c.value = await bizApi.customer(id) } catch (e) { error.value = e.message } finally { loading.value = false }
}
const lockText = computed(() => {
  const x = c.value
  if (x.lockStatus === 1) return `预锁，剩余${x.lockDaysLeft}天`
  if (x.lockStatus === 2) return `有效锁定，剩余${x.lockDaysLeft}天`
  if (x.lockStatus === 3) return '已成交'
  return x.lockStatus === 4 ? '已释放' : '未锁定'
})
async function extend() {
  if (busy.value) return
  busy.value = true
  try { await bizApi.extend(id, '伙伴申请延长锁定'); uni.showToast({title:'已延期'}); await load() }
  catch (e) { error.value = e.message } finally { busy.value = false }
}
</script>
<style scoped>
.row{gap:24rpx;align-items:flex-start}.row text:last-child{max-width:66%;text-align:right;overflow-wrap:anywhere}.progress{margin:20rpx 0;white-space:pre-wrap;line-height:1.7;overflow-wrap:anywhere}.error{color:#a3293e;margin-bottom:20rpx}
</style>
