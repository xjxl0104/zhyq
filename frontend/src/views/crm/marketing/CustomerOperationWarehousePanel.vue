<template>
  <section v-if="canQuery" class="table-card operation-panel" aria-labelledby="operation-warehouse-title">
    <div class="toolbar">
      <h2 id="operation-warehouse-title" class="title">客户与作业仓库</h2>
      <el-button :loading="loading.links" @click="loadLinks">刷新</el-button>
      <el-button v-if="canConfig" @click="openSite">新增作业仓库</el-button>
      <el-button v-if="canConfig" type="primary" @click="openLink">新增客户关联</el-button>
    </div>
    <p class="hint">为{{ warehouseName || '当前加盟云仓' }}登记客户（货主）实际使用的作业仓库。每个客户独立建档，建档后可另行配置发货数据来源。</p>
    <el-alert v-if="errors.links" :title="errors.links" type="error" :closable="false" />
    <p class="hint">点击某一行，下方“云仓发货量同步”只显示该客户的配置、快照和月度汇总；再次点击取消筛选。</p>
    <el-table v-loading="loading.links" :data="links" border size="small" :row-class-name="rowClass" empty-text="暂无客户关联，可选择已录入的客户并手动新增作业仓库。" @row-click="pickLink">
      <el-table-column prop="customerName" label="客户（货主）" min-width="160"><template #default="{ row }">{{ row.customerName || row.customerId }}</template></el-table-column>
      <el-table-column prop="operationWarehouseName" label="作业仓库" min-width="160" />
      <el-table-column label="仓库地址" min-width="200"><template #default="{ row }">{{ row.address || '未填写' }}</template></el-table-column>
      <el-table-column label="关联状态" width="100"><template #default="{ row }"><el-tag :type="Number(row.status) === 1 ? 'success' : 'info'">{{ Number(row.status) === 1 ? '启用' : '停用' }}</el-tag></template></el-table-column>
      <el-table-column label="发货量同步" min-width="250"><template #default="{ row }">
        <el-tag :type="syncOf(row).type" size="small">{{ syncOf(row).text }}</el-tag>
        <span v-if="syncOf(row).detail" class="sync-detail">{{ syncOf(row).detail }}</span>
        <el-button v-if="canConfig && !syncOf(row).configured && Number(row.status) === 1" link type="primary" size="small" @click.stop="emit('configure', linkFocus(row))">配置同步</el-button>
      </template></el-table-column>
      <el-table-column v-if="canConfig" label="操作" width="130" fixed="right"><template #default="{ row }">
        <el-button link :type="Number(row.status) === 1 ? 'warning' : 'success'" :loading="statusBusy === String(row.id)" :disabled="!!statusBusy && statusBusy !== String(row.id)" @click.stop="toggleStatus(row)">{{ Number(row.status) === 1 ? '停用' : '启用' }}</el-button>
        <el-button link type="danger" :loading="deleteBusy === String(row.id)" :disabled="!!statusBusy || (!!deleteBusy && deleteBusy !== String(row.id))" @click.stop="removeLink(row)">删除</el-button>
      </template></el-table-column>
    </el-table>
    <el-pagination class="pager" background layout="total, prev, pager, next" :total="linkTotal" :page-size="20" v-model:current-page="linkPage" @current-change="loadLinks" />

    <el-dialog v-model="linkDialog.visible" title="新增客户关联" width="min(560px, calc(100vw - 32px))" :close-on-click-modal="false" :close-on-press-escape="!loading.saveLink && !siteDialog.visible" :show-close="!loading.saveLink && !siteDialog.visible" @closed="closeLink">
      <p class="dialog-hint">加盟云仓：{{ warehouseName || '当前所选云仓' }}。选择本系统中独立登记的客户及实际作业仓库。</p>
      <el-form label-position="top" @submit.prevent="saveLink">
        <el-form-item label="客户（货主）" required>
          <el-select v-model="linkDialog.customerId" filterable remote :remote-method="searchCustomers" :loading="loading.customers" :disabled="loading.saveLink" placeholder="搜索已录入的客户" class="full-width" @change="selectCustomer">
            <el-option v-for="item in customerOptions" :key="item.id" :label="item.name" :value="String(item.id)" />
            <template #footer><el-pagination small layout="prev, pager, next" :total="customerTotal" :page-size="20" v-model:current-page="customerPage" @current-change="loadCustomers" /></template>
          </el-select>
        </el-form-item>
        <el-alert v-if="errors.customers" :title="errors.customers" type="error" :closable="false" />
        <el-form-item label="作业仓库" required>
          <el-select v-model="linkDialog.operationWarehouseId" filterable remote :remote-method="searchSites" :loading="loading.sites" :disabled="loading.saveLink" placeholder="选择已建档的作业仓库" class="full-width" @change="selectSite">
            <el-option v-for="item in siteOptions" :key="item.id" :label="item.name" :value="String(item.id)" />
            <template #footer><el-pagination small layout="prev, pager, next" :total="siteTotal" :page-size="20" v-model:current-page="sitePage" @current-change="loadSites" /></template>
          </el-select>
          <el-button link type="primary" :disabled="loading.saveLink" @click="openSite">手动新增作业仓库</el-button>
        </el-form-item>
        <p v-if="selectedSite?.address" class="hint selected-address">仓库地址：{{ selectedSite.address }}</p>
        <el-alert v-if="errors.sites" :title="errors.sites" type="error" :closable="false" />
        <el-alert v-if="errors.saveLink" :title="errors.saveLink" type="error" :closable="false" />
        <p class="hint">保存后仅建立客户与作业仓库的业务关系；发货数据来源需另行配置。</p>
      </el-form>
      <template #footer><el-button :disabled="loading.saveLink || siteDialog.visible" @click="linkDialog.visible = false">取消</el-button><el-button type="primary" :loading="loading.saveLink" :disabled="!canSaveLink || siteDialog.visible" @click="saveLink">保存关联</el-button></template>
    </el-dialog>

    <el-dialog v-model="siteDialog.visible" title="新增作业仓库" width="min(520px, calc(100vw - 32px))" append-to-body :close-on-click-modal="false" :close-on-press-escape="!loading.saveSite" :show-close="!loading.saveSite" @closed="closeSite">
      <p class="dialog-hint">所属加盟云仓：{{ warehouseName || '当前所选云仓' }}。请填写实际作业仓库。</p>
      <el-form label-position="top" @submit.prevent="saveSite">
        <el-form-item label="仓库名称" required><el-input v-model="siteDialog.name" maxlength="128" show-word-limit :disabled="loading.saveSite" placeholder="例如：广州白云作业仓" /></el-form-item>
        <el-form-item label="仓库地址"><el-input v-model="siteDialog.address" type="textarea" :rows="3" maxlength="300" show-word-limit :disabled="loading.saveSite" placeholder="填写实际仓库地址（选填）" /></el-form-item>
        <el-alert v-if="errors.saveSite" :title="errors.saveSite" type="error" :closable="false" />
      </el-form>
      <template #footer><el-button :disabled="loading.saveSite" @click="siteDialog.visible = false">取消</el-button><el-button type="primary" :loading="loading.saveSite" :disabled="!canSaveSite" @click="saveSite">保存仓库</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { mktWarehouseOperationsApi, mktYunshanApi } from '@/api/marketing'
import { hasPermission } from '@/utils/permission'

const props = defineProps({ warehouseId: { type: [String, Number], required: true }, warehouseName: { type: String, default: '' } })
// select: 选中/取消选中某一行(null 表示取消)，用于联动下方发货量同步；configure: 为该客户配置发货量同步
const emit = defineEmits(['select', 'configure'])
const canQuery = computed(() => hasPermission('crm:marketing:erp:query'))
const canConfig = computed(() => hasPermission('crm:marketing:erp:config'))
// IDs are strings throughout: server-generated warehouse and customer IDs can exceed Number.MAX_SAFE_INTEGER.
const idOf = value => value == null || (typeof value === 'number' && !Number.isSafeInteger(value)) ? '' : String(value)
const warehouseKey = computed(() => idOf(props.warehouseId))
const links = ref([]), linkTotal = ref(0), linkPage = ref(1), statusBusy = ref('')
const deleteBusy = ref(''), selectedLinkId = ref(''), syncMaps = ref([])
const linkFocus = row => ({ customerId: idOf(row.customerId), operationWarehouseId: idOf(row.operationWarehouseId), customerName: row.customerName || '' })
const rowClass = ({ row }) => idOf(row.id) && idOf(row.id) === selectedLinkId.value ? 'link-selected' : ''
function pickLink(row) {
  const id = idOf(row.id)
  if (!id) return
  if (selectedLinkId.value === id) { selectedLinkId.value = ''; emit('select', null); return }
  selectedLinkId.value = id; emit('select', linkFocus(row))
}
// 每一行对应的发货量同步配置：按客户 + 作业仓库匹配（与上方“发货量接口”独立配置）
function syncOf(row) {
  const customerId = idOf(row.customerId), siteId = idOf(row.operationWarehouseId)
  const sameCustomer = syncMaps.value.filter(item => String(item.customerId) === customerId)
  let map = sameCustomer.find(item => String(item.operationWarehouseId ?? '') === siteId)
  // 作业仓库功能上线前建立的历史配置没有记录作业仓库；该客户在本页只有一条关联时，按客户对应到这条关联
  const legacy = !map && links.value.filter(item => idOf(item.customerId) === customerId).length === 1
    ? sameCustomer.find(item => item.operationWarehouseId == null) : null
  map = map || legacy
  if (!map) return { type: 'info', text: '未配置', detail: '', configured: false }
  if (Number(map.status) !== 1) return { type: 'warning', text: '已停用', detail: '保留历史快照', configured: true }
  const when = map.lastSyncAt ? String(map.lastSyncAt).slice(0, 16) : ''
  const detail = (legacy ? '历史配置（未关联作业仓库）· ' : '') + (when ? `最近拉取 ${when} ${Number(map.lastSyncOk) === 1 ? '成功' : '失败'}` : '尚未拉取')
  return { type: Number(map.lastSyncOk) === 0 ? 'danger' : 'success', text: Number(map.lastSyncOk) === 0 ? '已配置 · 拉取失败' : '已配置', detail, configured: true }
}
async function loadSyncMaps() {
  const warehouse = warehouseKey.value
  if (!canQuery.value || !warehouse) return
  try {
    const rows = await mktYunshanApi.maps(warehouse)
    if (!disposed && warehouse === warehouseKey.value) syncMaps.value = Array.isArray(rows) ? rows : []
  } catch { if (!disposed && warehouse === warehouseKey.value) syncMaps.value = [] }
}
async function removeLink(row) {
  if (!canConfig.value || deleteBusy.value || statusBusy.value || !warehouseKey.value || !idOf(row.id)) return
  try {
    await ElMessageBox.confirm(`确认删除「${row.customerName || row.customerId} · ${row.operationWarehouseName || ''}」这条关联？删除后可重新添加；已配置发货量同步的关联不能删除。`, '删除客户关联', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
  } catch { return }
  const context = epoch, warehouse = warehouseKey.value, id = idOf(row.id)
  deleteBusy.value = id; errors.links = ''
  try {
    await mktWarehouseOperationsApi.deleteLink(warehouse, id)
    if (!current(context, warehouse)) return
    ElMessage.success('客户关联已删除')
    if (selectedLinkId.value === id) { selectedLinkId.value = ''; emit('select', null) }
    await loadLinks()
  } catch (error) { if (current(context, warehouse)) errors.links = error.message || '删除失败，请重试。' }
  finally { if (current(context, warehouse)) deleteBusy.value = '' }
}
const customers = ref([]), customerTotal = ref(0), customerPage = ref(1), customerKeyword = ref(''), selectedCustomer = ref(null)
const sites = ref([]), siteTotal = ref(0), sitePage = ref(1), siteKeyword = ref(''), selectedSite = ref(null)
const linkDialog = reactive({ visible: false, customerId: '', operationWarehouseId: '' })
const siteDialog = reactive({ visible: false, name: '', address: '' })
const loading = reactive({ links: false, customers: false, sites: false, saveLink: false, saveSite: false })
const errors = reactive({ links: '', customers: '', sites: '', saveLink: '', saveSite: '' })
let disposed = false, epoch = 0, linkEpoch = 0, siteEpoch = 0
const versions = { links: 0, customers: 0, sites: 0 }
const withSelected = (rows, selected) => selected && !rows.some(row => idOf(row.id) === idOf(selected.id)) ? [selected, ...rows] : rows
const customerOptions = computed(() => withSelected(customers.value, selectedCustomer.value))
const siteOptions = computed(() => withSelected(sites.value, selectedSite.value).filter(row => Number(row.status) === 1))
const canSaveLink = computed(() => canConfig.value && !!warehouseKey.value && linkDialog.visible && !loading.saveLink && !loading.customers && !loading.sites && !errors.customers && !errors.sites &&
  !!linkDialog.customerId && customerOptions.value.some(row => idOf(row.id) === linkDialog.customerId) &&
  !!linkDialog.operationWarehouseId && siteOptions.value.some(row => idOf(row.id) === linkDialog.operationWarehouseId))
const canSaveSite = computed(() => canConfig.value && !!warehouseKey.value && siteDialog.visible && !loading.saveSite && !!siteDialog.name.trim() && siteDialog.name.trim().length <= 128 && siteDialog.address.trim().length <= 300)
const current = (context, warehouse) => !disposed && context === epoch && warehouse === warehouseKey.value

async function loadLinks() {
  if (!canQuery.value || !warehouseKey.value) return
  const context = epoch, warehouse = warehouseKey.value, version = ++versions.links
  loading.links = true; errors.links = ''
  try {
    const result = await mktWarehouseOperationsApi.links(warehouse, { pageNo: linkPage.value, pageSize: 20 })
    if (!current(context, warehouse) || version !== versions.links) return
    links.value = result.records || []; linkTotal.value = Number(result.total || 0)
    loadSyncMaps()
  } catch (error) { if (current(context, warehouse) && version === versions.links) errors.links = error.message || '客户关联加载失败，请重试。' }
  finally { if (current(context, warehouse) && version === versions.links) loading.links = false }
}
async function openLink() {
  if (!canConfig.value || !warehouseKey.value || loading.saveLink || siteDialog.visible) return
  closeLink(); linkDialog.visible = true
  await Promise.all([loadCustomers(), loadSites()])
}
function closeLink() {
  linkEpoch++; versions.customers++; versions.sites++
  linkDialog.customerId = ''; linkDialog.operationWarehouseId = ''
  customers.value = []; sites.value = []; selectedCustomer.value = null; selectedSite.value = null
  customerTotal.value = 0; siteTotal.value = 0; customerPage.value = 1; sitePage.value = 1
  customerKeyword.value = ''; siteKeyword.value = ''
  loading.customers = false; loading.sites = false; errors.customers = ''; errors.sites = ''; errors.saveLink = ''
}
function selectCustomer(value) { selectedCustomer.value = customerOptions.value.find(row => idOf(row.id) === idOf(value)) || null }
function selectSite(value) { selectedSite.value = siteOptions.value.find(row => idOf(row.id) === idOf(value)) || null }
function searchCustomers(keyword) { customerKeyword.value = keyword.trim(); customerPage.value = 1; return loadCustomers() }
function searchSites(keyword) { siteKeyword.value = keyword.trim(); sitePage.value = 1; return loadSites() }
async function loadCustomers() {
  if (!canConfig.value || !linkDialog.visible || !warehouseKey.value) return
  const context = epoch, warehouse = warehouseKey.value, dialog = linkEpoch, version = ++versions.customers
  loading.customers = true; errors.customers = ''
  const valid = () => current(context, warehouse) && dialog === linkEpoch && version === versions.customers && linkDialog.visible
  try {
    const result = await mktYunshanApi.customers({ warehouseId: warehouseKey.value, keyword: customerKeyword.value, pageNo: customerPage.value, pageSize: 20 })
    if (!valid()) return
    customers.value = (result.records || []).filter(row => idOf(row.id)); customerTotal.value = Number(result.total || 0)
  } catch (error) { if (valid()) { customers.value = []; errors.customers = error.message || '客户加载失败，请重新搜索。' } }
  finally { if (valid()) loading.customers = false }
}
async function loadSites() {
  if (!canConfig.value || !linkDialog.visible || !warehouseKey.value) return
  const context = epoch, warehouse = warehouseKey.value, dialog = linkEpoch, version = ++versions.sites
  loading.sites = true; errors.sites = ''
  const valid = () => current(context, warehouse) && dialog === linkEpoch && version === versions.sites && linkDialog.visible
  try {
    const result = await mktWarehouseOperationsApi.sites(warehouse, { keyword: siteKeyword.value, pageNo: sitePage.value, pageSize: 20, status: 1 })
    if (!valid()) return
    sites.value = (result.records || []).filter(row => idOf(row.id) && Number(row.status) === 1); siteTotal.value = Number(result.total || 0)
  } catch (error) { if (valid()) { sites.value = []; errors.sites = error.message || '作业仓库加载失败，请重新搜索。' } }
  finally { if (valid()) loading.sites = false }
}
function openSite() {
  if (!canConfig.value || !warehouseKey.value || loading.saveLink || loading.saveSite) return
  closeSite(); siteDialog.visible = true
}
function closeSite() { siteEpoch++; siteDialog.name = ''; siteDialog.address = ''; errors.saveSite = '' }
async function saveSite() {
  if (!canSaveSite.value) return
  const context = epoch, warehouse = warehouseKey.value, dialog = siteEpoch, parentDialog = linkEpoch
  const payload = { name: siteDialog.name.trim(), address: siteDialog.address.trim() }
  const valid = () => current(context, warehouse) && dialog === siteEpoch && siteDialog.visible
  loading.saveSite = true; errors.saveSite = ''
  try {
    const result = await mktWarehouseOperationsApi.createSite(warehouse, payload)
    if (!valid()) return
    const id = idOf(result)
    if (!id || !/^\d+$/.test(id)) throw new Error('仓库已提交，但未获得有效编号。请刷新仓库列表核对，勿重复新增。')
    if (linkDialog.visible && parentDialog === linkEpoch) {
      versions.sites++; loading.sites = false
      const created = { id, ...payload, status: 1 }
      selectedSite.value = created; linkDialog.operationWarehouseId = id
      sites.value = [created, ...sites.value.filter(row => idOf(row.id) !== id)]
      siteTotal.value++; errors.sites = ''
    }
    ElMessage.success('作业仓库已保存'); siteDialog.visible = false
  } catch (error) { if (valid()) errors.saveSite = error.message || '作业仓库保存失败，请重试。' }
  finally { if (current(context, warehouse) && dialog === siteEpoch) loading.saveSite = false }
}
async function saveLink() {
  if (!canSaveLink.value || siteDialog.visible) return
  const context = epoch, warehouse = warehouseKey.value, dialog = linkEpoch
  const payload = { customerId: linkDialog.customerId, operationWarehouseId: linkDialog.operationWarehouseId }
  loading.saveLink = true; errors.saveLink = ''
  try {
    await mktWarehouseOperationsApi.createLink(warehouse, payload)
    if (!current(context, warehouse) || dialog !== linkEpoch) return
    ElMessage.success('客户关联已保存'); linkDialog.visible = false; linkPage.value = 1; await loadLinks()
  } catch (error) { if (current(context, warehouse) && dialog === linkEpoch) errors.saveLink = error.message || '客户关联保存失败，请重试。' }
  finally { if (current(context, warehouse)) loading.saveLink = false }
}
async function toggleStatus(row) {
  if (!canConfig.value || statusBusy.value || !warehouseKey.value || !idOf(row.id)) return
  const context = epoch, warehouse = warehouseKey.value, id = idOf(row.id)
  statusBusy.value = id; errors.links = ''
  try {
    await mktWarehouseOperationsApi.setLinkStatus(warehouse, id, { status: Number(row.status) === 1 ? 0 : 1 })
    if (current(context, warehouse)) await loadLinks()
  } catch (error) { if (current(context, warehouse)) errors.links = error.message || '关联状态更新失败，请重试。' }
  finally { if (current(context, warehouse)) statusBusy.value = '' }
}
watch(warehouseKey, () => {
  epoch++; versions.links++; linkDialog.visible = false; siteDialog.visible = false; closeLink(); closeSite()
  links.value = []; linkTotal.value = 0; linkPage.value = 1; statusBusy.value = ''
  deleteBusy.value = ''; selectedLinkId.value = ''; syncMaps.value = []
  Object.keys(loading).forEach(key => { loading[key] = false }); Object.keys(errors).forEach(key => { errors[key] = '' })
  loadLinks()
}, { immediate: true, flush: 'sync' })
onBeforeUnmount(() => { disposed = true; epoch++ })
// 发货量同步区增删配置后，由父页面刷新本列表的“发货量同步”列
defineExpose({ reload: loadLinks })
</script>

<style scoped>
.operation-panel { min-width: 0; }
.toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.title { margin: 0 auto 0 0; font-size: 16px; font-weight: 600; }
.hint, .dialog-hint { margin: 12px 0; color: var(--el-text-color-regular); font-size: 13px; line-height: 1.6; overflow-wrap: anywhere; }
.full-width { width: 100%; }
.operation-panel :deep(.el-alert) { margin: 12px 0; }
.selected-address { margin-top: -8px; }
.sync-detail { margin: 0 8px 0 6px; color: var(--el-text-color-secondary); font-size: 12px; }
.operation-panel :deep(.el-table__row) { cursor: pointer; }
.operation-panel :deep(.link-selected > td) { background: var(--el-color-primary-light-9) !important; }
.pager { margin-top: 12px; justify-content: flex-end; overflow-x: auto; }
@media (max-width: 640px) {
  .toolbar .title { flex-basis: 100%; }
  .toolbar :deep(.el-button + .el-button) { margin-left: 0; }
}
</style>
