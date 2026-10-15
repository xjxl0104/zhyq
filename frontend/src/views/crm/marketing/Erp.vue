<template>
  <div class="page-container mkt-page">
    <div class="search-bar">
      <el-form :inline="true">
        <el-form-item label="云仓">
          <el-select v-model="warehouseId" :disabled="busy" placeholder="选择云仓" style="width: 260px" @change="changeWarehouse">
            <el-option v-for="w in warehouses" :key="w.id" :label="`${w.code} ${w.name}`" :value="w.id" />
          </el-select>
        </el-form-item>
      </el-form>
    </div>

    <template v-if="warehouseId">
      <el-alert v-if="error" type="error" :closable="false" :title="error" class="mb" />
      <el-alert :type="warehouse?.erpStatus === 2 ? 'success' : 'warning'" :closable="false" class="mb"
        :title="warehouse?.erpStatus === 2 ? '正式 ERP 最近有有效签名通信。请同时关注最近业务同步、待处理事件和周期对账。' : '连接步骤：签发沙箱凭证并校验 → 切换正式环境 → ERP 发送签名心跳。没有订单时仍可保持连接。'" />
      <el-alert v-if="onboardingMode" type="info" :closable="false" class="mb"
        title="当前云仓选择了 ERP 接入：正式凭证通过签名心跳后，可确认并进入加盟协议环节；真实订单仍需合同和货主映射。" />
      <div class="table-card">
        <div class="toolbar">
          <span class="title">接入凭证</span>
          <el-button v-if="onboardingMode && canAuditOnboarding" :disabled="busy || !canConfirmErp" type="success" @click="confirmErp">正式 ERP 已联通，继续加盟</el-button>
          <el-tag v-if="onboardingMode && warehouse?.erpStatus !== 2" type="warning">等待正式环境签名心跳</el-tag>
          <el-button link :disabled="busy || loading" @click="load">刷新状态</el-button>
          <template v-if="cred && canConfigure">
            <el-button :disabled="busy" @click="resetSecret">重置密钥</el-button>
            <el-button :disabled="busy" @click="toggleEnv">{{ cred.env === 1 ? '切换正式环境' : '切回沙箱环境' }}</el-button>
            <el-button :disabled="busy" :type="cred.status === 1 ? 'warning' : 'success'" @click="toggleStatus">{{ cred.status === 1 ? '停用' : '启用' }}</el-button>
          </template>
          <el-button v-if="!cred && canConfigure" :disabled="busy || loading || !!error" type="primary" @click="issue">签发沙箱凭证</el-button>
        </div>
        <el-descriptions v-if="cred" :column="2" border>
          <el-descriptions-item label="App-Id"><code>{{ cred.appId }}</code></el-descriptions-item>
          <el-descriptions-item label="环境"><el-tag :type="cred.env === 2 ? 'success' : 'info'">{{ cred.env === 2 ? '正式' : '沙箱' }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="状态"><el-tag :type="cred.status === 1 ? 'success' : 'danger'">{{ cred.status === 1 ? '启用' : '停用' }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="最近签名心跳">{{ cred.lastHeartbeatAt || '尚未验证连接' }}</el-descriptions-item>
          <el-descriptions-item label="最近业务同步">{{ cred.lastSyncAt || '尚无正式业务同步' }}</el-descriptions-item>
          <el-descriptions-item label="心跳地址"><code>GET {{ origin }}{{ cred.heartbeatEndpoint || '/api/open/v1/erp/heartbeat' }}</code></el-descriptions-item>
          <el-descriptions-item label="接口地址" :span="2"><code>POST {{ origin }}{{ cred.endpoint }}</code></el-descriptions-item>
        </el-descriptions>
        <el-alert v-if="secret" type="warning" :closable="false" class="mb" title="密钥只显示这一次,请立即复制交给云仓;关闭后无法再查看,只能重置。">
          <code class="secret">{{ secret }}</code>
          <el-button link type="primary" @click="copy(secret)">复制</el-button>
        </el-alert>
        <p class="hint">签名:<code>X-Signature = hex(HMAC-SHA256(secret, X-Timestamp + "\n" + X-Nonce + "\n" + body))</code>;时间戳 ±300s;nonce 5 分钟内不重复。完整契约见 <code>docs/marketing/ERP接入契约.md</code>。</p>
      </div>

      <div class="table-card">
        <div class="toolbar">
          <span class="title">货主编码映射(订单归属只认它)</span>
          <el-button v-if="canConfigure" :disabled="busy" type="primary" @click="openMapping">新增映射</el-button>
        </div>
        <el-table :data="mappings" size="small" border>
          <el-table-column prop="customerId" label="客户 ID" width="100" />
          <el-table-column prop="customerCode" label="货主编码" width="200" />
          <el-table-column label="状态" width="80"><template #default="{ row }">{{ row.status === 1 ? '启用' : '停用' }}</template></el-table-column>
          <el-table-column prop="createTime" label="创建" width="160" />
          <el-table-column v-if="canConfigure" label="操作" width="90"><template #default="{ row }"><el-popconfirm title="删除映射?" @confirm="deleteMapping(row.id)"><template #reference><el-button :disabled="busy" link type="danger">删除</el-button></template></el-popconfirm></template></el-table-column>
        </el-table>
        <p class="hint">未匹配的发货事件进入待匹配队列，暂不计佣。保存映射后自动排队补处理，通常在 3 分钟内执行；也可在下方填写原因后重试原事件。重复处理不会重复计佣。</p>
      </div>

      <div class="table-card">
        <div class="toolbar"><span class="title">待处理事件</span><el-button :disabled="busy" @click="loadExceptions">刷新</el-button></div>
        <el-table :data="exceptions" size="small" border>
          <el-table-column prop="orderNo" label="出库单号" min-width="160" />
          <el-table-column prop="event" label="事件" width="150" />
          <el-table-column label="处理状态" width="120"><template #default="{ row }">{{ exceptionStatus[row.status] || row.status }}</template></el-table-column>
          <el-table-column prop="lastError" label="待处理原因" min-width="220" />
          <el-table-column prop="nextRetryAt" label="下次重试" width="170" />
          <el-table-column v-if="canConfigure" label="操作" width="110"><template #default="{ row }"><el-button link type="primary" :disabled="busy || !cred || cred.env !== 2 || cred.status !== 1 || ['PROCESSING','RECEIVED'].includes(row.status)" @click="retryEvent(row)">重试原事件</el-button></template></el-table-column>
        </el-table>
        <el-pagination class="pager" background layout="total, prev, pager, next" :total="exceptionTotal" v-model:current-page="exceptionPage" :page-size="20" @change="loadExceptions" />
      </div>

      <ErpReconciliation :key="warehouseId" :warehouse-id="warehouseId" />

      <div class="table-card">
        <div class="toolbar">
          <span class="title">同步日志</span>
          <el-radio-group v-model="logOk" size="small" @change="loadLogs"><el-radio-button :value="null">全部</el-radio-button><el-radio-button :value="1">成功</el-radio-button><el-radio-button :value="0">失败</el-radio-button></el-radio-group>
        </div>
        <el-table :data="logs" size="small" border>
          <el-table-column prop="createTime" label="时间" width="160" />
          <el-table-column prop="event" label="事件" width="140" />
          <el-table-column prop="orderNo" label="单号" width="180" />
          <el-table-column prop="httpStatus" label="HTTP" width="70" />
          <el-table-column prop="errorCode" label="结果码" width="130" />
          <el-table-column prop="error" label="说明" min-width="200" />
          <el-table-column prop="referralOrderId" label="计佣订单" width="90" />
        </el-table>
        <el-pagination class="pager" background layout="total, prev, pager, next" :total="logTotal" v-model:current-page="logPage" :page-size="20" @change="loadLogs" />
      </div>
    </template>

    <el-dialog v-model="mapDialog.visible" title="新增货主编码映射" width="420px">
      <el-form label-width="90px">
        <el-form-item label="客户 ID" required><el-input-number v-model="mapDialog.customerId" :min="1" style="width: 100%" /></el-form-item>
        <el-form-item label="货主编码" required><el-input v-model="mapDialog.customerCode" placeholder="云仓 ERP 里该客户的编码" /></el-form-item>
      </el-form>
      <template #footer><el-button :disabled="busy" @click="mapDialog.visible = false">取消</el-button><el-button :loading="busy" :disabled="busy" type="primary" @click="saveMapping">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { mktErpApi, mktWarehouseApi } from '@/api/marketing'
import { hasPermission } from '@/utils/permission'
import ErpReconciliation from './ErpReconciliation.vue'

const origin = window.location.origin
const route = useRoute()
const router = useRouter()
const onboardingMode = computed(() => route.query.onboarding === '1')
const warehouses = ref([])
const warehouseId = ref(route.query.warehouseId ? Number(route.query.warehouseId) : null)
const warehouse = ref(null)
const cred = ref(null)
const secret = ref('')
const busy = ref(false), loading = ref(false), error = ref('')
const canConfigure = computed(() => hasPermission('crm:marketing:erp:config'))
const canAuditOnboarding = computed(() => hasPermission('crm:marketing:onboarding:audit'))
const exceptions = ref([]), exceptionPage = ref(1), exceptionTotal = ref(0)
const exceptionStatus = { WAITING_MAPPING:'待匹配客户', RETRY:'等待重试', DEAD:'需人工处理', RECEIVED:'已排队', PROCESSING:'处理中' }
const mappings = ref([])
const logs = ref([]); const logTotal = ref(0); const logPage = ref(1); const logOk = ref(null)
const mapDialog = reactive({ visible: false, warehouseId: null, customerId: null, customerCode: '' })
let requestNo = 0, logRequestNo = 0, exceptionRequestNo = 0

async function load() {
  const target = warehouseId.value, request = ++requestNo
  if (!target) return
  secret.value = ''
  loading.value = true; error.value = ''; warehouse.value = null; cred.value = null; mappings.value = []
  try {
    // 凭证读取会核对实时心跳状态，再读取云仓，避免展示上一次的连接状态。
    const credential = await mktErpApi.get(target)
    const [w, maps] = await Promise.all([mktWarehouseApi.get(target), mktErpApi.mappings(target)])
    if (request !== requestNo || target !== warehouseId.value) return
    warehouse.value = w; cred.value = credential; mappings.value = maps
    await Promise.all([loadLogs(), loadExceptions()])
  } catch (e) { if (request === requestNo) error.value = e.message || 'ERP 资料加载失败，请刷新重试' }
  finally { if (request === requestNo) loading.value = false }
}
function changeWarehouse() {
  mapDialog.visible = false; logPage.value = 1; exceptionPage.value = 1
  logs.value = []; exceptions.value = []; logTotal.value = 0; exceptionTotal.value = 0
  return load()
}
async function loadLogs() {
  const target = warehouseId.value, request = ++logRequestNo
  try {
    const r = await mktErpApi.logs({ warehouseId: target, ok: logOk.value, pageNo: logPage.value, pageSize: 20 })
    if (target === warehouseId.value && request === logRequestNo) { logs.value = r.records; logTotal.value = r.total }
  } catch (e) { if (target === warehouseId.value && request === logRequestNo) error.value = e.message || '同步日志加载失败' }
}
async function loadExceptions() {
  const target = warehouseId.value, request = ++exceptionRequestNo
  try {
    const r = await mktErpApi.exceptions(target, { pageNo: exceptionPage.value, pageSize: 20 })
    if (target === warehouseId.value && request === exceptionRequestNo) { exceptions.value = r.records; exceptionTotal.value = r.total }
  } catch (e) { if (target === warehouseId.value && request === exceptionRequestNo) error.value = e.message || '待处理事件加载失败' }
}
async function action(fn) {
  if (busy.value || loading.value) return
  busy.value = true
  try { await fn() } catch (e) { if (e !== 'cancel' && e !== 'close') ElMessage.error(e.message || '操作失败，请重试') }
  finally { busy.value = false }
}
async function issue() { await action(async () => { const r = await mktErpApi.issue(warehouseId.value); cred.value = r; secret.value = r.secret; ElMessage.success('已签发沙箱凭证') }) }
async function resetSecret() {
  await action(async () => {
    await ElMessageBox.confirm('重置后旧密钥立即失效，ERP 更新配置并重新发送签名心跳后恢复连接。', '重置密钥', { type: 'warning' })
    const r = await mktErpApi.resetSecret(warehouseId.value); await load(); cred.value = r; secret.value = r.secret; ElMessage.success('已重置')
  })
}
async function toggleEnv() { await action(async () => { await ElMessageBox.confirm('切换环境后需重新发送签名心跳。正式环境将按真实合同处理订单，请先完成联调。', '切换 ERP 环境', { type:'warning' }); await mktErpApi.update(warehouseId.value, { env: cred.value.env === 1 ? 2 : 1 }); await load() }) }
async function toggleStatus() { await action(async () => { await mktErpApi.update(warehouseId.value, { status: cred.value.status === 1 ? 0 : 1 }); await load() }) }
function openMapping() { Object.assign(mapDialog, { visible:true, warehouseId:warehouseId.value, customerId:null, customerCode:'' }) }
async function saveMapping() {
  if (!mapDialog.customerId || !mapDialog.customerCode.trim()) return ElMessage.error('请填完整')
  if (mapDialog.warehouseId !== warehouseId.value) return ElMessage.error('云仓已切换，请重新打开映射表单')
  await action(async () => {
    const result = await mktErpApi.saveMapping(warehouseId.value, { customerId: mapDialog.customerId, customerCode: mapDialog.customerCode.trim() })
    ElMessage.success(result?.queued ? `已保存，${result.queued} 条事件已排队补处理` : '映射已保存'); mapDialog.visible = false; await load()
  })
}
async function deleteMapping(id) { await action(async () => { await mktErpApi.deleteMapping(id); await load() }) }
async function retryEvent(row) {
  await action(async () => {
    const { value } = await ElMessageBox.prompt('请说明已修复的问题或重试原因', '重试原事件', { inputValidator:v => !!v?.trim() && v.trim().length <= 200 || '请填写 1–200 字的原因' })
    await mktErpApi.retry(warehouseId.value, row.id, { reason:value.trim() }); ElMessage.success('已排队，后台将按原事件补处理'); await loadExceptions()
  })
}
const copy = (t) => navigator.clipboard.writeText(t).then(() => ElMessage.success('已复制'))
const canConfirmErp = computed(() => onboardingMode.value && warehouse.value?.joinStatus === 3 && warehouse.value?.orderMode === 'erp' && warehouse.value?.erpStatus === 2)
async function confirmErp() {
  if (!canAuditOnboarding.value || !canConfirmErp.value) return
  await action(async () => {
    await mktWarehouseApi.confirmErp(warehouseId.value)
    ElMessage.success('ERP 接入已确认，请继续上传加盟协议')
    await router.push('/crm/marketing/onboarding')
  })
}

onMounted(async () => {
  warehouses.value = (await mktWarehouseApi.page({ pageNo: 1, pageSize: 200 })).records
  if (warehouseId.value) await load()
})
</script>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 8px; }
.title { font-weight: 600; margin-right: auto; }
.hint { color: var(--el-text-color-secondary); font-size: 12px; margin-top: 12px; }
.mb { margin: 12px 0; }
.secret { font-size: 14px; word-break: break-all; margin-right: 8px; }
.pager { margin-top: 12px; justify-content: flex-end; }
</style>
