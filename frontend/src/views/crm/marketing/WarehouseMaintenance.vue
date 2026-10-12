<template>
  <el-drawer v-model="visible" title="加盟云仓维护记录" size="min(94vw, 1100px)">
    <p class="note">保留云仓新增、申请删除、撤销及正式删除记录。已删除云仓仍可查询。</p>
    <div class="filters"><el-input v-model="query.keyword" clearable placeholder="云仓名称或编码" maxlength="100" @keyup.enter="search" /><el-button type="primary" :loading="loading" @click="search">查询</el-button></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-table :data="rows" v-loading="loading" border>
      <el-table-column label="云仓" min-width="170"><template #default="{row}">{{ snapshot(row).name || row.name }}<div class="note">{{ snapshot(row).code || row.code }} · #{{ row.warehouseId }}</div></template></el-table-column>
      <el-table-column label="动作" width="130"><template #default="{row}">{{ ACTION[row.action] || row.action }}</template></el-table-column>
      <el-table-column prop="operator" label="操作人" width="110" />
      <el-table-column prop="createTime" label="操作时间" width="175" />
      <el-table-column prop="reason" label="操作原因" min-width="180" />
      <el-table-column label="状态变化 / 计划删除时间" min-width="200"><template #default="{row}">{{ STATUS[parse(row.beforeJson).status] || '—' }} → {{ STATUS[parse(row.afterJson).status] || '—' }}<div v-if="parse(row.afterJson).deleteDueAt" class="note">{{ parse(row.afterJson).deleteDueAt.replace('T', ' ') }}</div></template></el-table-column>
    </el-table>
    <el-pagination class="pager" background layout="total, prev, pager, next" :total="total" v-model:current-page="query.pageNo" :page-size="query.pageSize" @change="load" />
  </el-drawer>
</template>
<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { mktWarehouseApi } from '@/api/marketing'
const props = defineProps({ modelValue: Boolean })
const emit = defineEmits(['update:modelValue'])
const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
const ACTION = { 'warehouse.apply': '新增云仓', 'warehouse.delete.request': '申请删除并冻结', 'warehouse.delete.cancel': '撤销删除', 'warehouse.delete.complete': '到期正式删除', 'account.delete': '历史账号删除' }
const STATUS = { 1: '申请', 2: '资质审核', 3: '待配置订单接入', 4: '待签协议', 5: '已上线', 6: '暂停', 7: '已退出/删除', 8: '冻结待删除' }
const query = reactive({ pageNo: 1, pageSize: 20, keyword: '' })
const rows = ref([]), total = ref(0), loading = ref(false), error = ref('')
function parse(value) { try { return JSON.parse(value || '{}') || {} } catch { return {} } }
const snapshot = row => ({ ...parse(row.beforeJson), ...parse(row.afterJson) })
let request = 0
async function load() {
  const current = ++request
  loading.value = true; error.value = ''
  try { const result = await mktWarehouseApi.maintenance({ ...query }); if (current === request) { rows.value = result.records; total.value = result.total } }
  catch (e) { if (current === request) error.value = e.message || '维护记录读取失败，请重试' }
  finally { if (current === request) loading.value = false }
}
function search() { query.pageNo = 1; load() }
watch(visible, value => { if (value) search(); else { ++request; loading.value = false } })
</script>
<style scoped>
.note { color: var(--el-text-color-secondary); font-size: 12px; margin: 6px 0; }
.filters { display: flex; gap: 12px; margin: 16px 0; max-width: 460px; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
