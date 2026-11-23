<template>
  <view class="page-wrap">
    <view class="card">
      <view class="wh-title">业务员管理</view>
      <view class="wh-note">主账号维护业务员姓名及手机号。业务员使用该手机号完成微信授权后，只能查看分配给自己的客户。</view>
      <view v-if="loading" class="wh-note">正在读取业务员…</view>
      <view v-if="error" class="wh-error">{{ error }}</view>
      <template v-if="owner">
        <view v-if="!loading && !rows.length" class="wh-note">尚未添加业务员。</view>
        <view v-for="person in rows" :key="person.id" class="wh-item">
          <view class="wh-item-title">{{ person.name }} · {{ person.status === 1 ? '启用' : '停用' }}</view>
          <view class="wh-meta">{{ person.phone }} · {{ person.wechatBound ? '已绑定微信' : '待首次手机号授权' }}</view>
          <button class="wh-secondary" :disabled="busy" @click="edit(person)">维护业务员</button>
        </view>
        <button class="btn" :disabled="busy" @click="edit()">新增业务员</button>
      </template>
      <button v-if="error" class="wh-secondary" :disabled="loading" @click="load">重新读取</button>
    </view>
    <view v-if="editing && owner" class="card">
      <view class="wh-title">{{ editingId ? '维护业务员' : '新增业务员' }}</view>
      <view class="wh-label">真实姓名</view><input v-model="form.name" class="wh-field" maxlength="50" :disabled="busy" placeholder="填写业务员真实姓名" />
      <view class="wh-label">登录手机号</view><input v-model="form.phone" class="wh-field" type="number" maxlength="11" :disabled="busy" placeholder="微信授权使用的 11 位手机号" />
      <radio-group class="staff-status" @change="e => form.status = Number(e.detail.value)">
        <label><radio value="1" :checked="form.status === 1" :disabled="busy" color="#3857f5" />启用</label>
        <label><radio value="0" :checked="form.status === 0" :disabled="busy" color="#3857f5" />停用</label>
      </radio-group>
      <view class="wh-note">更换手机号后需重新授权微信；停用立即终止该业务员访问，已分配客户仍由主账号保管。</view>
      <view v-if="saveError" class="wh-error">{{ saveError }}</view>
      <view class="wh-actions"><button class="wh-secondary" :disabled="busy" @click="editing = false">取消</button><button class="btn" :disabled="busy" :loading="busy" @click="save">保存业务员</button></view>
    </view>
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
import { isWarehouseOwner, staffPayload } from '@/utils/warehouse-workspace.mjs'
const rows = ref([]), owner = ref(false), loading = ref(false), busy = ref(false), error = ref(''), saveError = ref('')
const editing = ref(false), editingId = ref(null), form = reactive({ name: '', phone: '', status: 1 })
async function load() {
  if (loading.value || busy.value) return
  loading.value = true; error.value = ''
  try {
    owner.value = isWarehouseOwner(await warehouseApi.me())
    if (!owner.value) { rows.value = []; editing.value = false; throw new Error('仅云仓主账号可以管理业务员') }
    rows.value = await warehouseApi.staff()
  } catch (e) { error.value = e.message || '业务员读取失败' } finally { loading.value = false }
}
function edit(person) { editingId.value = person?.id || null; Object.assign(form, { name: person?.name || '', phone: person?.phone || '', status: person?.status ?? 1 }); saveError.value = ''; editing.value = true }
async function save() {
  if (busy.value || !owner.value) return
  saveError.value = ''
  let payload
  try { payload = staffPayload(form) } catch (e) { saveError.value = e.message; return }
  busy.value = true
  try { await warehouseApi.saveStaff(editingId.value, payload); editing.value = false; uni.showToast({ title: '业务员已保存', icon: 'success' }) }
  catch (e) { saveError.value = e.message || '保存失败，请重试' }
  finally { busy.value = false }
  if (!editing.value) await load()
}
onShow(load)
</script>
<style scoped>@import '../../styles/warehouse.css'; .staff-status { display: flex; gap: 40rpx; margin: 24rpx 0; }</style>
