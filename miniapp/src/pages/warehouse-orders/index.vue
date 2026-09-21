<template><view><view class="card"><view class="title">出库单</view><view v-for="o in rows" :key="o.id" class="row"><view><view>{{o.sourceNo}}</view><view class="muted">{{o.logisticsNo || '暂无物流单号'}}</view></view><view><view>{{o.qty || 0}} 件</view><view class="tag">{{o.status}}</view></view></view><view v-if="!rows.length" class="muted">暂无出库单</view></view></view></template>
<script setup>
import { ref } from 'vue'; import { onShow } from '@dcloudio/uni-app'; import { warehouseApi } from '@/api/warehouse'; const rows = ref([]); onShow(async () => { const r = await warehouseApi.orders({ pageNo:1, pageSize:50 }); rows.value = r.records || [] })
</script>
