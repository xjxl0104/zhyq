<template><view><view class="card"><view class="title">承接客户</view><view v-for="c in rows" :key="c.id" class="row"><view><view>{{c.name || c.customerCode}}</view><view class="muted">{{c.serviceType || '客户'}}</view></view><text class="tag">{{c.status}}</text></view><view v-if="!rows.length" class="muted">暂无客户</view></view></view></template>
<script setup>
import { ref } from 'vue'; import { onShow } from '@dcloudio/uni-app'; import { warehouseApi } from '@/api/warehouse'; const rows = ref([]); onShow(async () => { const r = await warehouseApi.customers({ pageNo:1, pageSize:50 }); rows.value = r.records || [] })
</script>
