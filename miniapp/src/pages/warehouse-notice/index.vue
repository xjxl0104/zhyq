<template><view><view class="card"><view class="title">消息通知</view><view v-for="n in rows" :key="n.id" class="row"><view><view>{{n.title}}</view><view class="muted">{{n.createTime}}</view></view><text class="tag" :class="n.read ? 'ok' : 'warn'">{{n.read ? '已读' : '未读'}}</text></view><view v-if="!rows.length" class="muted">暂无通知</view></view></view></template>
<script setup>
import { ref } from 'vue'; import { onShow } from '@dcloudio/uni-app'; import { warehouseApi } from '@/api/warehouse'; const rows = ref([]); onShow(async () => { const r = await warehouseApi.notices({ pageNo:1, pageSize:50 }); rows.value = r.records || [] })
</script>
