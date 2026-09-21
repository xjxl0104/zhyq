<template><view><view class="card"><view class="title">云仓商家端</view><view class="muted">登录后查看加盟进度、ERP 和结算</view><input v-if="mock" class="input" v-model="code" placeholder="开发模式微信标识"/><input class="input" v-model="warehouseId" type="number" placeholder="云仓编号(可选)"/><button class="btn" @click="login">微信登录</button></view><view v-if="needBind" class="card"><input v-if="mock" class="input" v-model="phone" type="number" maxlength="11" placeholder="手机号"/><button v-else class="btn" open-type="getPhoneNumber" @getphonenumber="onPhone">授权手机号</button><button class="btn" @click="bind">绑定并进入</button></view></view></template>
<script setup>
import { ref } from 'vue'
import { warehouseAuthApi } from '@/api/warehouse'
import { warehouseToken } from '@/utils/request'
const mock = import.meta.env.VITE_MP_MOCK_LOGIN !== 'false'
const code = ref(''); const warehouseId = ref(''); const phone = ref(''); const needBind = ref(false); const openid = ref(''); const encryptedData = ref(''); const iv = ref('')
async function login () { const c = mock ? code.value.trim() : (await uni.login({ provider: 'weixin' })).code; if (!c) return; const r = await warehouseAuthApi.wxLogin(c, warehouseId.value ? Number(warehouseId.value) : undefined); if (r.registered) return done(r.token); openid.value = r.openid; needBind.value = true }
function onPhone (e) { encryptedData.value = e.detail?.encryptedData || ''; iv.value = e.detail?.iv || '' }
async function bind () { const data = mock ? { openid: openid.value, phone: phone.value } : { openid: openid.value, encryptedData: encryptedData.value, iv: iv.value }; const r = await warehouseAuthApi.bindPhone(data); if (r.token) done(r.token) }
function done (t) { warehouseToken.set(t); uni.reLaunch({ url: '/pages/warehouse-dashboard/index' }) }
</script>
<style scoped>.title{font-size:38rpx;font-weight:600;margin-bottom:12rpx}</style>
