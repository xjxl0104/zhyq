<template>
  <section v-if="canQuery" class="table-card yunshan-panel" aria-labelledby="yunshan-title">
    <div class="toolbar">
      <h2 id="yunshan-title" class="title">发货量同步</h2>
      <el-tag v-if="config" :type="config.configured ? 'success' : 'warning'">{{ config.configured ? '已配置' : '待配置' }}</el-tag>
      <el-button :loading="loading.maps" @click="refresh">刷新</el-button>
      <el-button v-if="canConfig" type="primary" :disabled="!config?.configured" @click="openMap">新增客户关联</el-button>
    </div>
    <p class="hint">按客户（货主）和作业仓库查看每日发货票数。统计在次日生成，当前用于同步和对账。</p>
    <p class="hint">数据来源：加盟云仓“云山”提供的发货量接口。当前关联加盟云仓：{{ warehouseName || '当前所选云仓' }}。接口目录由当前应用的授权范围决定，切换加盟云仓不会切换接口账号。</p>
    <el-alert v-if="config && !config.configured" type="warning" :closable="false" title="云山云仓的发货量接口尚未配置，请联系管理员配置后再关联客户或同步数据。历史快照仍可查询。" />
    <p v-if="config?.configured" class="config-note">接口：{{ config.baseUrl }}<span v-if="config.appId"> · 应用：{{ config.appId }}</span></p>
    <el-alert v-if="errors.maps" type="error" :closable="false" :title="errors.maps" />
    <el-table v-loading="loading.maps" :data="maps" size="small" border empty-text="暂无客户关联，请先选择客户并关联对应的作业仓库。">
      <el-table-column prop="customerName" label="客户（货主）" min-width="130"><template #default="{ row }">{{ row.customerName || row.customerId }}</template></el-table-column>
      <el-table-column label="该客户的接口记录" min-width="170"><template #default="{ row }"><div>{{ row.parentOwnerName || '未命名客户' }}</div><div class="entity-no">{{ row.parentOwnerNo }}</div></template></el-table-column>
      <el-table-column label="作业仓库" min-width="170"><template #default="{ row }"><div>{{ row.parentWarehouseName || '未命名仓库' }}</div><div class="entity-no">{{ row.parentWarehouseNo }}</div></template></el-table-column>
      <el-table-column label="每日拉取" width="90"><template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag></template></el-table-column>
      <el-table-column label="最近拉取" min-width="180"><template #default="{ row }">{{ row.lastSyncAt || '尚未拉取' }}<el-tag v-if="row.lastSyncOk != null" class="sync-state" :type="row.lastSyncOk === 1 ? 'success' : 'danger'">{{ row.lastSyncOk === 1 ? '成功' : '失败' }}</el-tag></template></el-table-column>
      <el-table-column label="操作" :width="canConfig ? 150 : 80" fixed="right"><template #default="{ row }">
        <el-button link type="primary" @click="selectMap(row.id)">查看</el-button>
        <el-button v-if="canConfig" link :type="row.status === 1 ? 'warning' : 'success'" :loading="statusBusy === String(row.id)" @click="toggleStatus(row)">{{ row.status === 1 ? '停用' : '启用' }}</el-button>
      </template></el-table-column>
    </el-table>

    <template v-if="maps.length">
      <el-divider />
      <el-form class="query-form" :inline="true" @submit.prevent>
        <el-form-item label="客户关联"><el-select v-model="mapId" aria-label="选择发货量客户关联" class="map-select" @change="changeMap">
          <el-option v-for="item in maps" :key="item.id" :value="String(item.id)" :label="mapLabel(item)" />
        </el-select></el-form-item>
        <el-form-item label="发货日期"><el-date-picker v-model="dateRange" class="date-range" type="daterange" value-format="YYYY-MM-DD" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" :clearable="false" :disabled-date="disabledDate" @change="changeRange" /></el-form-item>
        <el-form-item><el-button :loading="loading.volume" @click="queryVolume">查询快照</el-button><el-button v-if="canConfig" type="primary" :disabled="!config?.configured || selectedMap?.status !== 1" :loading="loading.sync" @click="sync">补拉此区间</el-button><el-button :disabled="!config?.configured || loading.sync" :loading="loading.reconcile" @click="reconcile">只读对账</el-button></el-form-item>
      </el-form>
      <p class="hint range-hint">北京时间，单次最多 92 天。停用关联后保留历史快照；缺少统计的日期不视作零票。</p>
      <el-alert v-if="errors.volume" type="error" :closable="false" :title="errors.volume" />
      <el-alert v-if="syncResult" :type="syncResult.daysMissing ? 'warning' : 'success'" :closable="false" :title="syncMessage" />
      <el-alert v-if="errors.sync" type="error" :closable="false" :title="errors.sync" />
      <div class="volume-summary" aria-live="polite">
        <span>区间已统计票数 <strong>{{ volume ? (volume.dataState === 'NO_STAT_DATA' ? '暂无统计' : `${formatCount(volume.totalConsignNum)} 票`) : '—' }}</strong></span>
        <span v-if="volume">有快照 {{ volume.daysWithData }} 天 · 无快照 {{ volume.daysMissing }} 天</span>
      </div>
      <el-table v-loading="loading.volume" :data="volume?.records || []" size="small" border empty-text="此区间暂无快照。完成补拉后再查询；无统计不代表零发货。">
        <el-table-column prop="statDate" label="发货日期" width="120" />
        <el-table-column label="发货票数" min-width="190" align="right"><template #default="{ row }">{{ formatCount(row.consignNum) }}</template></el-table-column>
        <el-table-column label="首次票数" min-width="190" align="right"><template #default="{ row }">{{ formatCount(row.firstConsignNum) }}</template></el-table-column>
        <el-table-column prop="revisedCount" label="修正次数" width="100" align="right" />
        <el-table-column prop="lastRevisedAt" label="最近修正" min-width="170"><template #default="{ row }">{{ row.lastRevisedAt || '—' }}</template></el-table-column>
        <el-table-column prop="fetchedAt" label="最近拉取" min-width="170" />
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next" :total="Number(volume?.total || 0)" v-model:current-page="volumePage" :page-size="20" @current-change="loadVolume" />

      <div v-if="reconciliation || errors.reconcile || loading.reconcile" class="reconcile-section" v-loading="loading.reconcile">
        <h3>只读对账结果</h3>
        <el-alert v-if="errors.reconcile" type="error" :closable="false" :title="errors.reconcile" />
        <template v-if="reconciliation">
          <el-alert v-if="reconciliation.summaryMatchesDaily === false" type="warning" :closable="false" title="接口返回的区间合计与逐日查询快照不一致，可能正在生成或更新统计，请稍后重新对账。" />
          <el-alert :type="reconciliation.dataState === 'NO_STAT_DATA' ? 'warning' : reconciliation.matched ? 'success' : 'warning'" :closable="false" :title="reconciliation.dataState === 'NO_STAT_DATA' ? '接口暂无统计，无法确认一致；已保留本地快照。' : reconciliation.matched ? '本地快照、接口逐日明细及区间合计一致。' : '发现差异，请核对以下日期。对账不会更新快照。'" />
          <dl class="reconcile-totals"><div><dt>本地快照</dt><dd>{{ formatCount(reconciliation.localTotal) }} 票</dd></div><div><dt>接口区间合计</dt><dd>{{ reconciliation.dataState === 'NO_STAT_DATA' ? '暂无统计' : `${formatCount(reconciliation.summaryTotal)} 票` }}</dd></div><div><dt>接口逐日合计</dt><dd>{{ reconciliation.dataState === 'NO_STAT_DATA' ? '暂无统计' : `${formatCount(reconciliation.dailyTotal)} 票` }}</dd></div><div><dt>差额（本地 − 接口）</dt><dd>{{ reconciliation.dataState === 'NO_STAT_DATA' ? '—' : `${formatCount(reconciliation.difference)} 票` }}</dd></div></dl>
          <el-table v-if="reconciliation.differences?.length" :data="reconciliation.differences" size="small" border>
            <el-table-column prop="statDate" label="日期" width="120" />
            <el-table-column label="本地票数" min-width="190" align="right"><template #default="{ row }">{{ formatCount(row.localConsignNum, '无快照') }}</template></el-table-column>
            <el-table-column label="接口票数" min-width="190" align="right"><template #default="{ row }">{{ formatCount(row.remoteConsignNum, '无统计') }}</template></el-table-column>
            <el-table-column label="差异" min-width="160"><template #default="{ row }">{{ differenceLabel(row.reason) }}</template></el-table-column>
          </el-table>
        </template>
      </div>
      <div class="toolbar logs-heading"><h3 class="title">发货量同步日志</h3><el-button size="small" :loading="loading.logs" @click="loadLogs">刷新日志</el-button></div>
      <el-alert v-if="errors.logs" type="error" :closable="false" :title="errors.logs" />
      <el-table v-loading="loading.logs" :data="logs" size="small" border empty-text="此客户关联尚无同步日志。">
        <el-table-column prop="createTime" label="拉取时间" min-width="170" />
        <el-table-column label="方式" width="90"><template #default="{ row }">{{ row.triggerType === 1 ? '定时拉取' : '手动补拉' }}</template></el-table-column>
        <el-table-column label="发货区间" min-width="210"><template #default="{ row }">{{ row.startDate }} 至 {{ row.endDate }}</template></el-table-column>
        <el-table-column label="结果" width="80"><template #default="{ row }"><el-tag :type="row.ok === 1 ? 'success' : 'danger'">{{ row.ok === 1 ? '成功' : '失败' }}</el-tag></template></el-table-column>
        <el-table-column label="票数" min-width="190" align="right"><template #default="{ row }">{{ row.ok === 1 && row.daysFetched === 0 ? '无统计' : formatCount(row.totalConsign) }}</template></el-table-column>
        <el-table-column label="天数（拉取 / 修正 / 缺失）" min-width="190"><template #default="{ row }">{{ row.daysFetched }} / {{ row.daysChanged }} / {{ row.daysMissing }}</template></el-table-column>
        <el-table-column label="说明" min-width="220"><template #default="{ row }">{{ row.errorMsg || row.errorCode || (row.daysFetched === 0 ? '本次未返回统计数据' : '—') }}</template></el-table-column>
      </el-table>
      <p class="hint">日志的“缺失”指本地已有快照、此次接口未返回的日期，原快照会保留。</p>
      <el-pagination class="pager" background layout="total, prev, pager, next" :total="logTotal" v-model:current-page="logPage" :page-size="20" @current-change="loadLogs" />
    </template>

    <el-dialog v-model="mapDialog.visible" title="新增发货量客户关联" width="min(560px, calc(100vw - 32px))" :close-on-click-modal="false" @closed="closeMap">
      <p class="dialog-hint">加盟云仓：{{ warehouseName || '当前所选云仓' }}。客户就是货主，只需选择一次。</p>
      <el-form label-position="top" @submit.prevent="saveMap">
        <el-form-item label="客户（货主）" required>
          <el-select v-model="mapDialog.customerId" filterable remote :remote-method="searchCustomers" :loading="loading.customers" :disabled="loading.owners || loading.save" placeholder="搜索已录入的客户" class="full-width" @change="matchCustomer">
            <el-option v-for="item in customers" :key="item.id" :label="item.name" :value="String(item.id)" />
            <template #footer><el-pagination small layout="prev, pager, next" :total="customerTotal" :page-size="20" v-model:current-page="customerPage" @current-change="loadCustomers" /></template>
          </el-select>
        </el-form-item>
        <template v-if="mapDialog.customerId && directoryState.ownersLoaded">
          <el-alert :type="selectedOwner ? 'info' : 'warning'" :closable="false" :title="customerLinkNotice" />
          <el-button link type="primary" :disabled="loading.save || loading.warehouses" @click="ownerPickerVisible = !ownerPickerVisible">{{ ownerPickerVisible ? '收起接口关联' : selectedOwner ? '核对或更正接口关联' : '关联该客户的接口记录' }}</el-button>
          <el-form-item v-if="ownerPickerVisible" label="同一客户在接口中的记录"><el-select v-model="mapDialog.parentOwnerNo" filterable :disabled="loading.save" placeholder="核对名称与编号，选择该客户对应的记录" class="full-width" @change="selectInterfaceCustomer"><el-option v-for="item in owners" :key="item.no" :label="`${item.name} · ${item.no}`" :value="String(item.no)" /></el-select></el-form-item>
        </template>
        <el-form-item label="作业仓库" required>
          <span v-if="directoryState.warehousesLoaded && entityWarehouses.length === 1">{{ entityWarehouses[0].name }}（已自动选择）</span>
          <el-select v-else v-model="mapDialog.parentWarehouseNo" filterable :loading="loading.warehouses" :disabled="!mapDialog.parentOwnerNo || loading.warehouses || loading.save" placeholder="选择客户后显示可用仓库" class="full-width"><el-option v-for="item in entityWarehouses" :key="item.no" :label="`${item.name} · ${item.no}`" :value="String(item.no)" /></el-select>
        </el-form-item>
        <p v-if="directoryState.warehousesLoaded" class="hint" role="status">该客户可用 {{ entityWarehouses.length }} 个作业仓库{{ entityWarehouses.length === 0 ? '，待接口授权：请核对该客户的仓库查询权限。' : entityWarehouses.length === 1 ? '；如需其他仓库，请核对接口授权。' : '，请选择实际承接业务的仓库。' }}</p>
      </el-form>
      <el-alert v-if="errors.directory" type="error" :closable="false" :title="errors.directory" />
      <el-alert v-if="errors.save" type="error" :closable="false" :title="errors.save" />
      <p class="hint">一个客户可以在多个作业仓库发货。同一组合不重复统计，停用后保留历史。</p>
      <div class="directory-tools">
        <span v-if="directoryState.ownersLoaded" class="hint" role="status">当前接口开放 {{ owners.length }} 个客户的查询权限</span>
        <el-button link type="primary" :loading="loading.owners" :disabled="loading.warehouses || loading.save" @click="reloadDirectory">重新读取授权目录</el-button>
      </div>
      <template #footer><el-button @click="mapDialog.visible = false">取消</el-button><el-button type="primary" :loading="loading.save" :disabled="!canSaveMap" @click="saveMap">保存关联</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { mktYunshanApi } from '@/api/marketing'
import { hasPermission } from '@/utils/permission'

const props = defineProps({ warehouseId: { type: [String, Number], required: true }, warehouseName: { type: String, default: '' } })
const canQuery = computed(() => hasPermission('crm:marketing:erp:query'))
const canConfig = computed(() => hasPermission('crm:marketing:erp:config'))
const config = ref(null), maps = ref([]), mapId = ref(''), volume = ref(null), reconciliation = ref(null), syncResult = ref(null)
const logs = ref([]), logTotal = ref(0), logPage = ref(1), volumePage = ref(1), statusBusy = ref('')
const loading = reactive({ maps: false, volume: false, logs: false, sync: false, reconcile: false, owners: false, warehouses: false, customers: false, save: false })
const errors = reactive({ maps: '', volume: '', logs: '', sync: '', reconcile: '', directory: '', save: '' })
const mapDialog = reactive({ visible: false, customerId: '', parentOwnerNo: '', parentWarehouseNo: '' })
const owners = ref([]), entityWarehouses = ref([]), customers = ref([]), customerTotal = ref(0), customerPage = ref(1), customerKeyword = ref('')
const directoryState = reactive({ ownersLoaded: false, warehousesLoaded: false })
const selectedCustomer = ref(null), ownerPickerVisible = ref(false), ownerMatchKind = ref('')
const selectedOwner = computed(() => owners.value.find(item => String(item.no) === mapDialog.parentOwnerNo))
const customerLinkNotice = computed(() => {
  if (selectedOwner.value) {
    const prefix = ownerMatchKind.value === 'existing' ? '已沿用此前关联' : ownerMatchKind.value === 'name' ? '已匹配同名接口记录，请核对后保存' : '已选择对应接口记录，请核对后保存'
    return `${prefix}：${selectedOwner.value.name}（${selectedOwner.value.no}）。`
  }
  return ownerMatchKind.value === 'ambiguous'
    ? '待关联接口记录：找到多个可能对应的记录，请核对该客户的接口编号后选择。'
    : '待关联接口记录：当前目录中没有可明确对应的客户记录。名称不一致时可手动关联；目录中没有该客户时，需云山云仓管理员开放查询权限。'
})
const versions = { maps: 0, volume: 0, logs: 0, reconcile: 0, directory: 0, customers: 0 }
let disposed = false
const today = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
const shiftDate = (date, days) => new Date(Date.parse(`${date}T00:00:00Z`) + days * 86400000).toISOString().slice(0, 10)
const dateRange = ref([shiftDate(today(), -7), shiftDate(today(), -1)])
const selectedMap = computed(() => maps.value.find(item => String(item.id) === mapId.value))
const canSaveMap = computed(() => canConfig.value && mapDialog.customerId && owners.value.some(item => String(item.no) === mapDialog.parentOwnerNo) && entityWarehouses.value.some(item => String(item.no) === mapDialog.parentWarehouseNo) && !loading.warehouses && !loading.owners)
const syncMessage = computed(() => !syncResult.value ? '' : `补拉完成：${syncResult.value.daysFetched} 天，修正 ${syncResult.value.daysChanged} 天，缺失 ${syncResult.value.daysMissing} 天（保留原快照）。`)
const mapLabel = item => `${item.customerName || item.customerId} · ${item.parentOwnerName || item.parentOwnerNo} / ${item.parentWarehouseName || item.parentWarehouseNo}`
const formatCount = (value, empty = '—') => value == null || value === '' ? empty : String(value).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
const differenceLabel = reason => ({ LOCAL_MISSING: '本地无快照', REMOTE_MISSING: '接口本次无统计', VOLUME_MISMATCH: '票数不同' }[reason] || reason)
const disabledDate = value => {
  const date = `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`
  return date > today()
}
function rangeParams() {
  const [startDate, endDate] = dateRange.value || []
  if (!startDate || !endDate || startDate > endDate || endDate > today() || (Date.parse(endDate) - Date.parse(startDate)) / 86400000 >= 92) {
    ElMessage.warning('请选择不晚于今天、跨度不超过 92 天的日期区间'); return null
  }
  return { mapId: mapId.value, startDate, endDate }
}
function resetDetails() {
  for (const key of ['volume', 'logs', 'reconcile']) { versions[key]++; loading[key] = false; errors[key] = '' }
  volume.value = null; reconciliation.value = null; syncResult.value = null; errors.sync = ''; logs.value = []; logTotal.value = 0
}
async function refresh() {
  if (!canQuery.value || !props.warehouseId) return
  const version = ++versions.maps; loading.maps = true; errors.maps = ''
  try {
    const [settings, rows] = await Promise.all([mktYunshanApi.config(), mktYunshanApi.maps(String(props.warehouseId))])
    if (disposed || version !== versions.maps) return
    config.value = settings; maps.value = rows
    if (!rows.some(item => String(item.id) === mapId.value)) mapId.value = rows.length ? String(rows[0].id) : ''
    if (mapId.value) await changeMap()
  } catch (error) { if (version === versions.maps && !disposed) errors.maps = error.message || '无法加载发货量接口配置和客户关联，请重试。' }
  finally { if (version === versions.maps) loading.maps = false }
}
function selectMap(id) { mapId.value = String(id); changeMap() }
async function changeMap() { resetDetails(); volumePage.value = 1; logPage.value = 1; if (mapId.value) await Promise.all([loadVolume(), loadLogs()]) }
function changeRange() { versions.reconcile++; reconciliation.value = null; loading.reconcile = false; errors.reconcile = ''; syncResult.value = null; errors.sync = ''; queryVolume() }
function queryVolume() { volumePage.value = 1; volume.value = null; return loadVolume() }
async function loadVolume() {
  const params = rangeParams(); if (!canQuery.value || !mapId.value || !params) return
  const version = ++versions.volume; volume.value = null; loading.volume = true; errors.volume = ''
  try { const result = await mktYunshanApi.volume({ ...params, pageNo: volumePage.value, pageSize: 20 }); if (!disposed && version === versions.volume) volume.value = result }
  catch (error) { if (!disposed && version === versions.volume) errors.volume = error.message || '快照查询失败，请重试。' }
  finally { if (version === versions.volume) loading.volume = false }
}
async function loadLogs() {
  if (!canQuery.value || !mapId.value) return
  const version = ++versions.logs; loading.logs = true; errors.logs = ''
  try { const result = await mktYunshanApi.logs({ mapId: mapId.value, pageNo: logPage.value, pageSize: 20 }); if (!disposed && version === versions.logs) { logs.value = result.records; logTotal.value = Number(result.total) } }
  catch (error) { if (!disposed && version === versions.logs) { logs.value = []; errors.logs = error.message || '日志查询失败，请重试。' } }
  finally { if (version === versions.logs) loading.logs = false }
}
async function sync() {
  if (!canConfig.value || !config.value?.configured || loading.sync || selectedMap.value?.status !== 1) return
  const params = rangeParams(); if (!params) return
  const warehouse = props.warehouseId, target = mapId.value, range = dateRange.value.join('/'); loading.sync = true; errors.sync = ''; syncResult.value = null
  versions.reconcile++; reconciliation.value = null; loading.reconcile = false; errors.reconcile = ''
  try {
    const result = await mktYunshanApi.sync(target, { startDate: params.startDate, endDate: params.endDate })
    if (disposed || props.warehouseId !== warehouse || target !== mapId.value || dateRange.value.join('/') !== range) return
    syncResult.value = result; reconciliation.value = null; await Promise.all([loadVolume(), loadLogs()])
    const rows = await mktYunshanApi.maps(String(warehouse)); if (!disposed && props.warehouseId === warehouse) maps.value = rows
  } catch (error) { if (!disposed && props.warehouseId === warehouse && target === mapId.value && dateRange.value.join('/') === range) { errors.sync = error.message || '补拉失败，请查看拉取日志。'; await loadLogs() } }
  finally { loading.sync = false }
}
async function reconcile() {
  if (!canQuery.value || !config.value?.configured || loading.reconcile || loading.sync) return
  const params = rangeParams(); if (!params) return
  const version = ++versions.reconcile; loading.reconcile = true; errors.reconcile = ''; reconciliation.value = null
  try { const result = await mktYunshanApi.reconcile(params); if (!disposed && version === versions.reconcile) reconciliation.value = result }
  catch (error) { if (!disposed && version === versions.reconcile) errors.reconcile = error.message || '对账失败，请重试。' }
  finally { if (version === versions.reconcile) loading.reconcile = false }
}
async function toggleStatus(row) {
  if (!canConfig.value || statusBusy.value) return
  statusBusy.value = String(row.id); const warehouse = props.warehouseId
  try { await mktYunshanApi.setStatus(String(row.id), row.status === 1 ? 0 : 1); if (warehouse === props.warehouseId && !disposed) row.status = row.status === 1 ? 0 : 1 }
  catch (error) { if (!disposed) errors.maps = error.message || '状态更新失败，请重试。' }
  finally { statusBusy.value = '' }
}
// 云山目录固定分页，必须读取所有授权页；所有实体编码原样保留为字符串。
async function allEntities(method, params, version) {
  const rows = []; let current = 1, pages = 1
  do {
    const result = await method({ ...params, current })
    if (disposed || version !== versions.directory || !mapDialog.visible) return null
    rows.push(...result.records.map(item => ({ ...item, no: String(item.no) })))
    pages = Math.max(1, Number(result.pages || 1)); current++
  } while (current <= pages)
  return rows
}
async function openMap() {
  if (!canConfig.value || !config.value?.configured) return
  Object.assign(mapDialog, { visible: true, customerId: '', parentOwnerNo: '', parentWarehouseNo: '' }); owners.value = []; entityWarehouses.value = []; errors.directory = ''; errors.save = ''
  selectedCustomer.value = null; ownerPickerVisible.value = false; ownerMatchKind.value = ''
  customerPage.value = 1; customerKeyword.value = ''; loadCustomers()
  await reloadDirectory()
}
async function reloadDirectory() {
  if (!canConfig.value || !config.value?.configured || !mapDialog.visible || loading.save) return
  const version = ++versions.directory; loading.owners = true; loading.warehouses = false
  owners.value = []; entityWarehouses.value = []; mapDialog.parentOwnerNo = ''; mapDialog.parentWarehouseNo = ''; errors.directory = ''
  directoryState.ownersLoaded = false; directoryState.warehousesLoaded = false
  try { const rows = await allEntities(mktYunshanApi.owners, {}, version); if (rows) { owners.value = rows; directoryState.ownersLoaded = true } }
  catch (error) { if (version === versions.directory && !disposed) errors.directory = error.message || '授权货主加载失败，请关闭后重试。' }
  finally { if (version === versions.directory) loading.owners = false }
  if (version === versions.directory && directoryState.ownersLoaded && mapDialog.customerId) await matchCustomer()
}
function closeMap() { versions.directory++; versions.customers++; loading.owners = false; loading.warehouses = false; loading.customers = false; directoryState.ownersLoaded = false; directoryState.warehousesLoaded = false }
async function matchCustomer() {
  const id = mapDialog.customerId
  selectedCustomer.value = customers.value.find(item => String(item.id) === id)
    || (String(selectedCustomer.value?.id) === id ? selectedCustomer.value : null)
  mapDialog.parentOwnerNo = ''; ownerPickerVisible.value = false; ownerMatchKind.value = ''; errors.save = ''
  // 已确认的编号关联优先；只按完整名称匹配候选，不以简称或相似名称猜测客户身份。
  const previous = [...new Set(maps.value.filter(item => String(item.customerId) === id).map(item => String(item.parentOwnerNo)))]
  if (previous.length === 1) {
    const owner = owners.value.find(item => String(item.no) === previous[0])
    if (owner) { mapDialog.parentOwnerNo = String(owner.no); ownerMatchKind.value = 'existing' }
    // 既有编号已不在授权范围时，不用同名的另一个编号替代。
  } else if (previous.length > 1) {
    ownerMatchKind.value = 'ambiguous'
  } else {
    const name = selectedCustomer.value?.name?.trim()
    const matches = name ? owners.value.filter(item => item.name?.trim() === name) : []
    if (matches.length === 1) { mapDialog.parentOwnerNo = String(matches[0].no); ownerMatchKind.value = 'name' }
    else if (matches.length > 1) ownerMatchKind.value = 'ambiguous'
  }
  await changeOwner()
}
async function selectInterfaceCustomer() { ownerMatchKind.value = 'manual'; await changeOwner() }
async function changeOwner() {
  mapDialog.parentWarehouseNo = ''; entityWarehouses.value = []; errors.directory = ''; const version = ++versions.directory
  directoryState.warehousesLoaded = false; loading.warehouses = false
  if (!mapDialog.parentOwnerNo) return
  loading.warehouses = true
  try {
    const rows = await allEntities(mktYunshanApi.warehouses, { parentOwnerNo: mapDialog.parentOwnerNo }, version)
    if (rows) {
      entityWarehouses.value = rows; directoryState.warehousesLoaded = true
      if (rows.length === 1) mapDialog.parentWarehouseNo = String(rows[0].no)
    }
  }
  catch (error) { if (version === versions.directory && !disposed) errors.directory = error.message || '授权仓库加载失败，请重新选择货主。' }
  finally { if (version === versions.directory) loading.warehouses = false }
}
function searchCustomers(keyword) { customerKeyword.value = keyword; customerPage.value = 1; return loadCustomers() }
async function loadCustomers() {
  if (!canConfig.value || !mapDialog.visible) return
  const version = ++versions.customers; loading.customers = true
  try { const result = await mktYunshanApi.customers({ keyword: customerKeyword.value, pageNo: customerPage.value, pageSize: 20 }); if (!disposed && version === versions.customers) { customers.value = result.records; customerTotal.value = Number(result.total) } }
  catch (error) { if (!disposed && version === versions.customers) errors.directory = error.message || '客户加载失败，请重新搜索。' }
  finally { if (version === versions.customers) loading.customers = false }
}
async function saveMap() {
  if (!canSaveMap.value || loading.save) return
  const warehouse = props.warehouseId; loading.save = true; errors.save = ''
  try {
    await mktYunshanApi.createMap(String(warehouse), { customerId: mapDialog.customerId, parentOwnerNo: mapDialog.parentOwnerNo, parentWarehouseNo: mapDialog.parentWarehouseNo })
    if (!disposed && warehouse === props.warehouseId) { ElMessage.success('发货量客户关联已保存'); mapDialog.visible = false; await refresh() }
  } catch (error) { if (!disposed && warehouse === props.warehouseId) errors.save = error.message || '客户关联保存失败，请重试。' }
  finally { loading.save = false }
}
watch(() => props.warehouseId, () => { mapId.value = ''; maps.value = []; config.value = null; mapDialog.visible = false; closeMap(); resetDetails(); refresh() }, { immediate: true })
onBeforeUnmount(() => { disposed = true; Object.keys(versions).forEach(key => versions[key]++) })
</script>

<style scoped>
.yunshan-panel { min-width: 0; }
.toolbar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.title { font-size: 16px; font-weight: 600; margin: 0 auto 0 0; }
.hint, .config-note, .dialog-hint { color: var(--el-text-color-regular); font-size: 13px; line-height: 1.6; }
.hint { margin: 12px 0; }
.config-note { overflow-wrap: anywhere; }
.entity-no { color: var(--el-text-color-secondary); font-size: 12px; overflow-wrap: anywhere; }
.sync-state { margin-left: 6px; }
.yunshan-panel :deep(.el-alert) { margin: 12px 0; }
.query-form { display: flex; flex-wrap: wrap; gap: 0 12px; }
.query-form :deep(.el-form-item) { margin-right: 0; }
.map-select { width: 300px; }
.date-range { max-width: 100%; }
.range-hint { margin-top: 0; }
.volume-summary { display: flex; flex-wrap: wrap; gap: 8px 24px; padding: 14px 0; color: var(--el-text-color-regular); font-variant-numeric: tabular-nums; }
.volume-summary strong { margin-left: 8px; color: var(--el-text-color-primary); font-size: 17px; }
.pager { margin-top: 12px; justify-content: flex-end; overflow-x: auto; }
.reconcile-section { margin-top: 24px; }
.reconcile-section h3 { font-size: 15px; margin: 0 0 12px; }
.reconcile-totals { display: flex; flex-wrap: wrap; gap: 16px 32px; margin: 16px 0; font-variant-numeric: tabular-nums; }
.reconcile-totals dt { color: var(--el-text-color-regular); font-size: 13px; }
.reconcile-totals dd { margin: 6px 0 0; font-weight: 600; overflow-wrap: anywhere; }
.logs-heading { margin: 28px 0 12px; }
.full-width { width: 100%; }
.directory-tools { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
@media (max-width: 640px) {
  .toolbar .title { flex-basis: 100%; }
  .query-form :deep(.el-form-item) { width: 100%; }
  .query-form :deep(.el-form-item__content) { min-width: 0; flex-wrap: wrap; gap: 8px; }
  .query-form :deep(.el-button + .el-button) { margin-left: 0; }
  .map-select { width: 100%; }
  .date-range { width: 100%; }
  .volume-summary strong { display: inline-block; overflow-wrap: anywhere; }
}
</style>
