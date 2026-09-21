<template><view><view class="card"><view class="title">加盟申请</view><input class="input" v-model="form.name" placeholder="云仓名称"/><input class="input" v-model="form.region" placeholder="所在区域"/><input class="input" v-model="form.address" placeholder="详细地址"/><input class="input" v-model="form.contact" placeholder="联系人"/><input class="input" v-model="form.phone" placeholder="联系电话"/><textarea class="input" v-model="form.remark" placeholder="补充说明"/><button class="btn" @click="save">保存申请</button></view></view></template>
<script setup>
import { reactive } from 'vue'; import { onLoad } from '@dcloudio/uni-app'; import { warehouseApi } from '@/api/warehouse'
const form = reactive({ name:'', region:'', address:'', contact:'', phone:'', remark:'' })
onLoad(async () => { Object.assign(form, await warehouseApi.apply()) })
async function save () { await warehouseApi.saveApply(form); uni.showToast({ title:'已保存', icon:'success' }) }
</script>
