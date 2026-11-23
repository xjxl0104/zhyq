<template>
  <view class="page-wrap">
    <view v-if="loading" class="card wh-note">正在读取客户信息…</view>
    <view v-if="error" class="card"><view class="wh-error">{{ error }}</view><button class="wh-secondary" @click="load">重新读取</button></view>
    <template v-if="customer">
      <view class="card">
        <view class="wh-title">{{ customer.name }}</view>
        <view class="wh-status">{{ assignmentLabel(customer.warehouseAssignmentStatus) }}</view>
        <view class="wh-meta">{{ customer.contact || '未填写联系人' }} {{ customer.phone || '' }}</view>
        <view class="wh-meta">负责业务员：{{ customer.salespersonName || '待主账号分配' }}{{ customer.salespersonStatus === 0 ? '（已停用）' : '' }}</view>
        <CustomerRequirements :requirements="customer.requirements" :demand="customer.demand" :remark="customer.remark" />
      </view>
      <view v-if="customer.isOwner" class="card">
        <view class="wh-title">分配业务员</view>
        <view v-if="staffError" class="wh-error">{{ staffError }}</view>
        <picker :range="staffOptions" range-key="name" :value="staffIndex" :disabled="busy || !!staffError" @change="e => staffIndex = Number(e.detail.value)"><view class="wh-field">{{ staffOptions[staffIndex]?.name || '请选择业务员' }}</view></picker>
        <button class="wh-secondary" :disabled="busy || !!staffError" @click="assign">保存业务员分配</button>
        <button class="wh-secondary" :disabled="busy" @click="manageStaff">管理业务员</button>
      </view>
      <view class="card">
        <view class="wh-title">更新服务进度</view>
        <view class="wh-status">{{ warehouseStageLabel(customer.warehouseSalesStage) }}</view>
        <view class="wh-note">业务员承接 → 价格谈判 → 签约 → 货品入仓 → 正式发货</view>
        <view v-if="customer.warehouseAssignmentStatus === 1" class="wh-actions">
          <button class="btn" :disabled="busy" @click="accept">确认承接客户</button>
          <button v-if="customer.isOwner" class="wh-secondary" :disabled="busy" @click="showReject = !showReject">暂不承接</button>
        </view>
        <view v-if="showReject && customer.isOwner"><textarea v-model="rejectReason" class="wh-field" maxlength="500" placeholder="填写暂不承接原因" /><button class="wh-secondary" :disabled="busy" @click="reject">提交原因</button></view>
        <template v-if="customer.warehouseAssignmentStatus === 2 && customer.warehouseSalespersonId">
          <radio-group class="stages" @change="e => stage = Number(e.detail.value)"><label v-for="(label,index) in WAREHOUSE_STAGES.slice(1)" :key="label" class="stage-option"><radio :value="String(index + 1)" :checked="stage === index + 1" :disabled="busy" color="#3857f5" />{{ label }}</label></radio-group>
          <textarea v-model="summary" class="wh-field" maxlength="450" :disabled="busy" placeholder="填写本次跟进说明（选填，向推荐伙伴展示）" />
          <button class="btn" :loading="busy" :disabled="busy" @click="saveProgress">保存服务进度</button>
        </template>
        <view v-else-if="customer.warehouseAssignmentStatus === 2" class="wh-note">请先分配业务员，再记录业务服务进度。</view>
        <view v-if="actionError" class="wh-error">{{ actionError }}</view>
        <button v-if="customer.isOwner && customer.warehouseAssignmentStatus === 2 && customer.status !== 3" class="wh-secondary" :disabled="busy" @click="draft">起草直签合同</button>
      </view>
      <CustomerProgress :customer="customer" />
    </template>
  </view>
</template>
<script>
import { appShareMixin } from '@/utils/share'
export default { mixins: [appShareMixin] }
</script>
<script setup>
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { warehouseApi } from '@/api/warehouse'
import { assignmentLabel } from '@/utils/warehouse-ui'
import { WAREHOUSE_STAGES, warehouseStageLabel, stagePayload } from '@/utils/warehouse-workspace.mjs'
import CustomerRequirements from '@/components/CustomerRequirements.vue'
import CustomerProgress from '@/components/CustomerProgress.vue'
const id = ref(null), customer = ref(null), loading = ref(false), busy = ref(false), error = ref(''), actionError = ref(''), staffError = ref('')
const staffOptions = ref([{ id: null, name: '待分配 / 取消分配' }]), staffIndex = ref(0), stage = ref(1), summary = ref(''), showReject = ref(false), rejectReason = ref('')
async function load() {
  if (!id.value || loading.value || busy.value) return
  loading.value = true; error.value = ''; staffError.value = ''
  try {
    const data = await warehouseApi.customerDetail(id.value)
    customer.value = data; stage.value = Number(data.warehouseSalesStage) || 1
    if (data.isOwner) {
      try {
        const rows = await warehouseApi.staff()
        staffOptions.value = [{ id: null, name: '待分配 / 取消分配' }, ...rows.filter(p => p.status === 1)]
        staffIndex.value = Math.max(0, staffOptions.value.findIndex(p => p.id === data.warehouseSalespersonId))
      } catch (e) { staffError.value = e.message || '业务员列表读取失败' }
    }
  } catch (e) { customer.value = null; error.value = e.message || '客户读取失败' } finally { loading.value = false }
}
async function act(operation, message) {
  if (busy.value) return
  busy.value = true; actionError.value = ''
  let ok = false
  try { await operation(); ok = true; uni.showToast({ title: message, icon: 'success' }) } catch (e) { actionError.value = e.message || '保存失败，请重试' } finally { busy.value = false }
  if (ok) { summary.value = ''; showReject.value = false; await load() }
}
function assign() { const staff = staffOptions.value[staffIndex.value]; if (!staff || staffError.value) return; act(() => warehouseApi.assignSalesperson(id.value, staff.id), '分配已保存') }
function accept() { act(() => warehouseApi.acceptCustomer(id.value), '已确认承接') }
function reject() { if (!rejectReason.value.trim()) { actionError.value = '请填写暂不承接原因'; return } act(() => warehouseApi.rejectCustomer(id.value,rejectReason.value.trim()),'已提交原因') }
function saveProgress() { let data; try { data = stagePayload(stage.value,summary.value) } catch (e) { actionError.value = e.message; return } act(() => warehouseApi.customerProgress(id.value,data),'进度已更新') }
function manageStaff() { uni.navigateTo({ url: '/pages/warehouse-staff/index' }) }
function draft() { uni.navigateTo({ url: '/pages/warehouse-contracts/index?customerId=' + id.value + '&customerName=' + encodeURIComponent(customer.value.name || '') }) }
onLoad(options => { if (/^[1-9]\d*$/.test(String(options.id || ''))) id.value = Number(options.id); else error.value = '客户参数无效'; load() })
onShow(load)
</script>
<style scoped>@import '../../styles/warehouse.css'; .stages { margin: 24rpx 0; } .stage-option { display: flex; align-items: center; padding: 16rpx 0; font-size: 28rpx; }</style>
