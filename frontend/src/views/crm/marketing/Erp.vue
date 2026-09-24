<template>
  <div class="page-container">
    <div class="search-bar">
      <el-form :inline="true">
        <el-form-item label="云仓">
          <el-select v-model="warehouseId" placeholder="选择云仓" style="width: 260px" @change="load">
            <el-option v-for="w in warehouses" :key="w.id" :label="`${w.code} ${w.name}`" :value="w.id" />
          </el-select>
        </el-form-item>
      </el-form>
    </div>

    <template v-if="warehouseId">
      <div class="table-card">
        <div class="toolbar">
          <span class="title">接入凭证</span>
          <template v-if="cred">
            <el-button @click="resetSecret">重置密钥</el-button>
            <el-button @click="toggleEnv">{{ cred.env === 1 ? '切正式' : '切回沙箱' }}</el-button>
            <el-button :type="cred.status === 1 ? 'warning' : 'success'" @click="toggleStatus">{{ cred.status === 1 ? '停用' : '启用' }}</el-button>
          </template>
          <el-button v-else type="primary" @click="issue">签发沙箱凭证</el-button>
        </div>
        <el-descriptions v-if="cred" :column="2" border>
          <el-descriptions-item label="App-Id"><code>{{ cred.appId }}</code></el-descriptions-item>
          <el-descriptions-item label="环境"><el-tag :type="cred.env === 2 ? 'success' : 'info'">{{ cred.env === 2 ? '正式' : '沙箱' }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="状态"><el-tag :type="cred.status === 1 ? 'success' : 'danger'">{{ cred.status === 1 ? '启用' : '停用' }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="最近同步">{{ cred.lastSyncAt || '尚未收到事件' }}</el-descriptions-item>
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
          <el-button type="primary" @click="mapDialog.visible = true">新增映射</el-button>
        </div>
        <el-table :data="mappings" size="small" border>
          <el-table-column prop="customerId" label="客户 ID" width="100" />
          <el-table-column prop="customerCode" label="货主编码" width="200" />
          <el-table-column label="状态" width="80"><template #default="{ row }">{{ row.status === 1 ? '启用' : '停用' }}</template></el-table-column>
          <el-table-column prop="createTime" label="创建" width="160" />
          <el-table-column label="操作" width="90"><template #default="{ row }"><el-popconfirm title="删除映射?" @confirm="deleteMapping(row.id)"><template #reference><el-button link type="danger">删除</el-button></template></el-popconfirm></template></el-table-column>
        </el-table>
        <p class="hint">未映射的 customer_code 视为云仓自有客户:记日志(UNMAPPED)、不计佣、不进园区结算。补映射后重发事件即可入账。</p>
      </div>

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
      <template #footer><el-button @click="mapDialog.visible = false">取消</el-button><el-button type="primary" @click="saveMapping">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { mktErpApi, mktWarehouseApi } from '@/api/marketing'

const origin = window.location.origin
const warehouses = ref([])
const warehouseId = ref(null)
const cred = ref(null)
const secret = ref('')
const mappings = ref([])
const logs = ref([]); const logTotal = ref(0); const logPage = ref(1); const logOk = ref(null)
const mapDialog = reactive({ visible: false, customerId: null, customerCode: '' })

async function load() {
  secret.value = ''
  ;[cred.value, mappings.value] = await Promise.all([mktErpApi.get(warehouseId.value), mktErpApi.mappings(warehouseId.value)])
  loadLogs()
}
async function loadLogs() {
  const r = await mktErpApi.logs({ warehouseId: warehouseId.value, ok: logOk.value, pageNo: logPage.value, pageSize: 20 })
  logs.value = r.records; logTotal.value = r.total
}
async function issue() { const r = await mktErpApi.issue(warehouseId.value); cred.value = r; secret.value = r.secret; ElMessage.success('已签发') }
async function resetSecret() {
  await ElMessageBox.confirm('重置后旧密钥立即失效,云仓需更新配置。确认?', '重置密钥', { type: 'warning' })
  const r = await mktErpApi.resetSecret(warehouseId.value); secret.value = r.secret; ElMessage.success('已重置')
}
async function toggleEnv() { await mktErpApi.update(warehouseId.value, { env: cred.value.env === 1 ? 2 : 1 }); load() }
async function toggleStatus() { await mktErpApi.update(warehouseId.value, { status: cred.value.status === 1 ? 0 : 1 }); load() }
async function saveMapping() {
  if (!mapDialog.customerId || !mapDialog.customerCode.trim()) return ElMessage.error('请填完整')
  await mktErpApi.saveMapping(warehouseId.value, { customerId: mapDialog.customerId, customerCode: mapDialog.customerCode.trim() })
  ElMessage.success('已保存'); mapDialog.visible = false; mapDialog.customerCode = ''; mappings.value = await mktErpApi.mappings(warehouseId.value)
}
async function deleteMapping(id) { await mktErpApi.deleteMapping(id); mappings.value = await mktErpApi.mappings(warehouseId.value) }
const copy = (t) => navigator.clipboard.writeText(t).then(() => ElMessage.success('已复制'))

onMounted(async () => { warehouses.value = (await mktWarehouseApi.page({ pageNo: 1, pageSize: 200 })).records })
</script>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 8px; }
.title { font-weight: 600; margin-right: auto; }
.hint { color: var(--el-text-color-secondary); font-size: 12px; margin-top: 12px; }
.mb { margin: 12px 0; }
.secret { font-size: 14px; word-break: break-all; margin-right: 8px; }
.pager { margin-top: 12px; justify-content: flex-end; }
</style>
