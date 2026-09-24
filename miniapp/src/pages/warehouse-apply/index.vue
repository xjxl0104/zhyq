<template>
  <view class="page-wrap"><view class="card">
    <view class="wh-title">加盟申请资料</view>
    <view v-if="loading" class="wh-note">正在读取申请资料…</view>
    <template v-else-if="ready">
      <view class="wh-status">{{ JOIN[form.joinStatus] || '状态待确认' }}</view>
      <view class="wh-note">{{ editable ? '请完善云仓与联系人信息，园区将据此审核。' : '资质已完成审核；如需变更资料，请联系园区运营。' }}</view>
      <template v-for="field in fields" :key="field.key"><view class="wh-label">{{ field.label }}</view><input class="wh-field" v-model="form[field.key]" :maxlength="field.max" :disabled="!editable || busy" :type="field.key === 'phone' ? 'number' : 'text'" :placeholder="field.label" :aria-label="field.label" /></template>
      <view class="wh-label">仓库面积（㎡，选填）</view><input class="wh-field" v-model="form.areaSqm" type="digit" :disabled="!editable || busy" placeholder="填写实际面积" />
      <view class="wh-label">日处理单量（选填）</view><input class="wh-field" v-model="form.dailyCapacity" type="number" :disabled="!editable || busy" placeholder="填写实际处理能力" />
      <view class="wh-label">经营品类（选填）</view><input class="wh-field" v-model="form.categories" maxlength="255" :disabled="!editable || busy" placeholder="例如服饰、食品" />
      <view class="wh-label">补充说明</view><textarea class="wh-field" v-model="form.remark" maxlength="500" :disabled="!editable || busy" placeholder="场地及服务情况，最多 500 字" />
      <view class="wh-label">资质与申请附件</view><WarehouseFiles v-model="attachments" :readonly="!editable || busy" @busy="fileBusy = $event" />
      <button v-if="editable" class="btn" :loading="busy" :disabled="busy || fileBusy" @click="save">{{ form.joinStatus === 1 ? '保存并重新提交审核' : '保存申请资料' }}</button>
      <button class="wh-secondary" @click="goProgress">查看加盟进度</button>
    </template>
    <view v-if="error" class="wh-error" role="alert">{{ error }}</view><button v-if="!ready && !loading" class="wh-secondary" @click="load">重新读取</button>
  </view></view>
</template>
<script setup>
import { reactive, ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { warehouseApi } from '@/api/warehouse'
import WarehouseFiles from '@/components/WarehouseFiles.vue'
import { JOIN, parseFiles } from '@/utils/warehouse-ui'
const form = reactive({ name: '', region: '', address: '', contact: '', phone: '', remark: '', joinStatus: 0 })
const fields = [{ key: 'name', label: '云仓名称', max: 100 }, { key: 'region', label: '所在区域', max: 64 }, { key: 'address', label: '详细地址', max: 255 }, { key: 'contact', label: '联系人', max: 32 }, { key: 'phone', label: '联系电话', max: 11 }]
const attachments = ref([]); const loading = ref(true); const ready = ref(false); const busy = ref(false); const fileBusy = ref(false); const error = ref('')
const editable = computed(() => [1, 2].includes(form.joinStatus))
async function load() { loading.value = true; error.value = ''; try { const [profile, steps] = await Promise.all([warehouseApi.apply(), warehouseApi.onboarding()]); Object.assign(form, profile); attachments.value = parseFiles(steps.find(s => s.step === 1)?.attachments); ready.value = true } catch(e) { error.value = e.message || '资料读取失败，请重试' } finally { loading.value = false } }
async function save() {
  if (busy.value || fileBusy.value || !editable.value) return
  error.value = ''
  if (fields.some(f => !String(form[f.key] || '').trim())) { error.value = '请填写完整的云仓名称、区域、地址及联系人信息'; return }
  if (!/^1\d{10}$/.test(form.phone)) { error.value = '请输入正确的 11 位手机号'; return }
  busy.value = true
  try { await warehouseApi.saveApply({ ...form, areaSqm: form.areaSqm === '' ? null : form.areaSqm, dailyCapacity: form.dailyCapacity === '' ? null : form.dailyCapacity }); await warehouseApi.attachments(attachments.value); if (form.joinStatus === 1) await warehouseApi.submitApply(); uni.showToast({ title: '申请已保存', icon: 'success' }); await load() } catch(e) { error.value = e.message || '保存失败，填写的资料已保留' } finally { busy.value = false }
}
function goProgress() { uni.navigateTo({ url: '/pages/warehouse-onboarding/index' }) }
onShow(async () => {
  if (!ready.value) return load()
  if (busy.value || fileBusy.value) return
  try { const latest = await warehouseApi.apply(); if (latest.joinStatus !== form.joinStatus) await load() }
  catch(e) { error.value = e.message || '申请状态读取失败，请重试' }
})
</script>
<style scoped>@import '../../styles/warehouse.css';</style>
