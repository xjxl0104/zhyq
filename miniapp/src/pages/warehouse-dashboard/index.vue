<template>
  <view class="page-wrap">
    <WarehouseDashboard :profile="profile" :stats="stats" :loading="loading" :error="error" @open="go" @refresh="load" />
    <button class="wh-secondary sign-out" @click="logout">退出登录</button>
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
import { warehouseToken } from '@/utils/request'
import WarehouseDashboard from '@/components/WarehouseDashboard.vue'

const stats = reactive({})
const profile = ref(null)
const loading = ref(false)
const error = ref('')
async function load() {
  if (loading.value) return
  loading.value = true
  error.value = ''
  const results = await Promise.allSettled([warehouseApi.dashboard(), warehouseApi.me()])
  if (results[0].status === 'fulfilled') Object.assign(stats, results[0].value)
  profile.value = results[1].status === 'fulfilled' ? results[1].value : null
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
.sign-out { margin:32rpx 24rpx; }
</style>
