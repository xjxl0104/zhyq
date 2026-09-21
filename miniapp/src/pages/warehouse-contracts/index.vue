<template><view><view class="card"><view class="title">合同与协议</view><view v-for="c in rows" :key="c.id" class="row"><view>{{c.title || c.contractNo || '合同'}}</view><text class="tag">{{c.status}}</text></view><button class="btn" @click="upload">上传协议引用</button><view v-if="!rows.length" class="muted">暂无合同</view></view></view></template>
<script setup>
import { ref } from 'vue'; import { onShow } from '@dcloudio/uni-app'; import { warehouseApi } from '@/api/warehouse'; const rows = ref([]); onShow(async () => { rows.value = await warehouseApi.contracts() || [] }); async function upload () { const r = await uni.chooseFile({ count:1 }); if (r.tempFilePaths?.[0]) { await warehouseApi.uploadAgreement(r.tempFilePaths[0]); uni.showToast({ title:'已提交', icon:'success' }) } }
</script>
