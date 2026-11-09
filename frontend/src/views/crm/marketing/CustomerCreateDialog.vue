<template>
  <el-dialog :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)" title="新增客户" width="min(720px, 94vw)" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy">
    <el-form label-width="140px" :model="form" @submit.prevent="submit">
      <el-form-item label="客户公司 / 店铺名称" required><el-input v-model="form.name" maxlength="100" /></el-form-item>
      <el-form-item label="品牌名称"><el-input v-model="form.brandName" maxlength="100" placeholder="选填，请填写准确名称" /></el-form-item>
      <el-form-item label="联系人"><el-input v-model="form.contact" maxlength="100" /></el-form-item>
      <el-form-item label="联系手机"><el-input v-model="form.phone" maxlength="11" placeholder="选填" /></el-form-item>
      <el-form-item label="推荐伙伴"><el-select v-model="form.referrerId" clearable filterable remote :remote-method="loadPartners" :loading="partnersLoading" placeholder="选填，可搜索伙伴姓名或邀请码" style="width:100%"><el-option v-for="p in partners" :key="p.id" :value="p.id" :label="`${p.name || '未命名伙伴'}（${p.inviteCode}）`" /></el-select><div class="field-hint">选择伙伴后锁定 3 个自然月，签约成交后长期有效；留空则作为园区直接客户建档。</div></el-form-item>
      <el-form-item label="需求类型"><el-select v-model="form.serviceType" @change="form.warehouseId = null"><el-option v-for="[value, label] in services" :key="value" :value="value" :label="label" /></el-select></el-form-item>
      <el-form-item v-if="form.serviceType !== 4" label="意向云仓"><el-select v-model="form.warehouseId" clearable filterable placeholder="不限" style="width:100%"><el-option v-for="w in warehouses" :key="w.id" :value="w.id" :label="w.name" /></el-select></el-form-item>
      <el-form-item label="需求"><el-input v-model="form.demand" maxlength="200" placeholder="面积 / 日单量 / 品类等" /></el-form-item>
      <CustomerRequirements v-model="form.requirements" />
      <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" maxlength="1000" show-word-limit /></el-form-item>
    </el-form>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <template #footer><el-button :disabled="busy" @click="$emit('update:modelValue', false)">取消</el-button><el-button type="primary" :loading="busy" :disabled="!ready" @click="submit">保存客户</el-button></template>
  </el-dialog>
</template>
<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { mktCustomerApi, mktWarehouseApi } from '@/api/marketing'
import { useProjectStore } from '@/stores/project'
import CustomerRequirements from './CustomerRequirements.vue'
import { emptyRequirements, validateRequirements } from './customerRequirements'
const props = defineProps({ modelValue: Boolean })
const emit = defineEmits(['update:modelValue', 'created'])
const projectStore = useProjectStore()
const fresh = () => ({ name:'', brandName:'', contact:'', phone:'', referrerId:null, serviceType:2, warehouseId:null, demand:'', remark:'', requirements:emptyRequirements() })
const form = ref(fresh()), busy = ref(false), error = ref(''), ready = ref(false)
const partners = ref([]), partnersLoading = ref(false), warehouses = ref([])
const services = [[2,'一件代发'],[1,'仓储'],[3,'仓配一体'],[4,'园区入驻']]
let partnerQuery = 0
async function loadPartners(keyword = '') {
  const request = ++partnerQuery
  partnersLoading.value = true
  try { const rows = await mktCustomerApi.referrers({ keyword }); if (request === partnerQuery) partners.value = rows }
  catch (e) { if (request === partnerQuery) error.value = e?.message || '伙伴列表读取失败' }
  finally { if (request === partnerQuery) partnersLoading.value = false }
}
watch(() => props.modelValue, async visible => {
  if (!visible) return
  form.value = fresh(); error.value = ''; ready.value = false
  if (!projectStore.currentProjectId) { error.value = '请先选择园区'; return }
  try {
    const rows = await mktWarehouseApi.online()
    warehouses.value = rows.filter(w => w.projectId == null || String(w.projectId) === String(projectStore.currentProjectId))
    await loadPartners()
    ready.value = true
  } catch (e) { error.value = e?.message || '客户选项读取失败，请关闭后重试' }
})
async function submit() {
  if (busy.value || !ready.value) return
  const name = form.value.name.trim(), phone = form.value.phone.trim()
  error.value = !name ? '请填写客户公司 / 店铺名称' : phone && !/^1\d{10}$/.test(phone) ? '请填写正确的 11 位手机号，或留空' : validateRequirements(form.value.requirements)
  if (error.value) return
  busy.value = true
  try {
    await mktCustomerApi.create({ ...form.value, name, phone:phone || null, projectId:projectStore.currentProjectId, warehouseId:form.value.serviceType === 4 ? null : form.value.warehouseId })
    ElMessage.success('客户已新增'); emit('update:modelValue', false); emit('created')
  } catch (e) { error.value = e?.message || '保存失败，请重试' }
  finally { busy.value = false }
}
</script>
<style scoped>.field-hint { color:var(--el-text-color-secondary); font-size:12px; line-height:1.7; margin-top:6px; }</style>
