<template>
  <view class="page-wrap">
    <view class="card">
      <view class="wh-title">个人信息</view>
      <view class="wh-note">姓名和联系电话用于业务联系，可按需补充。</view>
      <view v-if="loading" class="wh-note">正在读取个人信息…</view>
      <template v-else-if="ready">
        <view class="wh-label">姓名</view>
        <input class="wh-field" v-model="form.name" maxlength="32" :disabled="busy" placeholder="请输入姓名" aria-label="姓名" />
        <view class="wh-label">联系电话</view>
        <input class="wh-field" v-model="form.phone" type="number" maxlength="11" :disabled="busy || !phoneEditable" placeholder="可稍后补充" aria-label="联系电话" />
        <view v-if="!phoneEditable" class="wh-note">该手机号已关联微信，修改请联系园区运营。</view>
        <button class="btn" :loading="busy" :disabled="busy" @click="save">保存个人信息</button>
      </template>
      <view v-if="error" class="wh-error" role="alert">{{ error }}</view>
      <button v-if="!ready && !loading" class="wh-secondary" @click="load">重新读取</button>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { meApi } from '@/api'

const form = reactive({ name: '', phone: '' })
const loading = ref(false), ready = ref(false), busy = ref(false), error = ref(''), phoneEditable = ref(false)

async function load() {
  if (loading.value) return
  loading.value = true; error.value = ''
  try {
    const profile = await meApi.me()
    phoneEditable.value = profile.phoneEditable === true
    form.name = profile.name || ''
    form.phone = (phoneEditable.value ? profile.contactPhone : profile.phone) || ''
    ready.value = true
  } catch (e) { error.value = e.message || '个人信息读取失败，请重试' }
  finally { loading.value = false }
}

async function save() {
  if (busy.value || !ready.value) return
  error.value = ''
  const name = form.name.trim(), phone = form.phone.trim()
  if (phoneEditable.value && phone && !/^1\d{10}$/.test(phone)) { error.value = '请输入正确的 11 位手机号，或暂时留空'; return }
  busy.value = true
  try {
    await meApi.update({ name, ...(phoneEditable.value && phone ? { phone } : {}) })
    await load()
    if (error.value) error.value = '信息已保存，但重新读取失败，请退出页面后重试'
    else uni.showToast({ title: '个人信息已保存', icon: 'success' })
  } catch (e) { error.value = e.message || '保存失败，填写的信息已保留，请重试' }
  finally { busy.value = false }
}

onShow(() => { if (!ready.value) load() })
</script>

<style scoped>
@import '../../styles/warehouse.css';
</style>
