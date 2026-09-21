<template><view><view class="card"><view class="title">加盟进度</view><view v-for="s in steps" :key="s.step" class="row"><view>第 {{s.step}} 步</view><view :class="['tag', s.status === 2 ? 'ok' : s.status === 3 ? 'warn' : '']">{{label(s.status)}}</view><view class="muted">{{s.rejectReason || ''}}</view></view></view></view></template>
<script setup>
import { ref } from 'vue'; import { onShow } from '@dcloudio/uni-app'; import { warehouseApi } from '@/api/warehouse'
const steps = ref([]); const label = s => ({0:'待处理',1:'进行中',2:'已通过',3:'已驳回'}[s] || '未知'); onShow(async () => { steps.value = await warehouseApi.onboarding() })
</script>
