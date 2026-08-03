<template>
  <div class="page-container vending-page">
    <div class="hero-card">
      <div>
        <div class="eyebrow">园区运营 / 经营服务</div>
        <h2>自动售货机</h2>
        <p>安全进入厂商系统，并在智慧园区内沉淀可审计的经营数据。</p>
      </div>
      <div class="hero-actions">
        <el-button @click="importVisible = true">导入标准数据</el-button>
        <el-button type="primary" @click="openVendor">打开厂商系统</el-button>
      </div>
    </div>

    <div class="boundary-grid">
      <el-alert title="厂商暂未提供开放 API" description="当前仅提供受控外部入口，不自动登录、不抓取页面、不保存厂商密码或 Cookie。" type="info" :closable="false" show-icon />
      <el-alert title="仅支持智慧园区标准模板" description="厂商原生导出格式需取得真实样例并验证后才能支持。" type="warning" :closable="false" show-icon />
    </div>

    <el-row :gutter="14" class="stats-row">
      <el-col :xs="12" :sm="6"><div class="stat-card"><span>机器总数</span><strong>{{ stats.machineCount || 0 }}</strong></div></el-col>
      <el-col :xs="12" :sm="6"><div class="stat-card"><span>在线机器</span><strong>{{ stats.onlineCount || 0 }}</strong></div></el-col>
      <el-col :xs="12" :sm="6"><div class="stat-card"><span>未恢复故障</span><strong>{{ stats.openFaultCount || 0 }}</strong></div></el-col>
      <el-col :xs="12" :sm="6"><div class="stat-card"><span>今日销售额</span><strong>¥{{ money(stats.todaySales) }}</strong></div></el-col>
    </el-row>

    <div v-if="lastConfirmedBatchId" class="batch-bar">
      <span>最近确认批次 #{{ lastConfirmedBatchId }} 已写入本地数据</span>
      <el-button link type="danger" @click="rollbackLastBatch">撤销该批次</el-button>
    </div>

    <div class="data-card">
      <div class="data-title"><strong>本地经营数据</strong><span>数据来自经人工确认的标准模板</span></div>
      <el-tabs v-model="activeType" @tab-change="changeType">
        <el-tab-pane v-for="type in vendingTypes" :key="type.name" :label="type.label" :name="type.name" />
      </el-tabs>
      <el-table :data="records" v-loading="loading" border stripe height="420">
        <el-table-column v-for="column in activeDefinition.columns" :key="column[0]"
          :prop="column[0]" :label="column[1]" :width="column[2]" show-overflow-tooltip>
          <template #default="{ row }">{{ formatVendingCell(row, column) }}</template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
        :total="total" v-model:current-page="query.pageNo" v-model:page-size="query.pageSize"
        :page-sizes="[20, 50, 100]" @change="loadPage" />
    </div>

    <VendingImportDialog v-model="importVisible" :initial-type="activeType" @confirmed="afterConfirmed" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { vendingApi } from '@/api/vending'
import VendingImportDialog from './components/VendingImportDialog.vue'
import { formatVendingCell, openExternalVending, vendingTypes } from './vendingModel'

const loading = ref(false)
const importVisible = ref(false)
const activeType = ref('MACHINE')
const records = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 20 })
const config = ref({ externalUrl: '', apiAvailable: false, nativeFormatSupported: false })
const stats = ref({})
const lastConfirmedBatchId = ref(null)
const activeDefinition = computed(() => vendingTypes.find(type => type.name === activeType.value) || vendingTypes[0])

function money(value) { return Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) }

async function loadPage() {
  loading.value = true
  try {
    const data = await vendingApi.page(activeType.value, query)
    records.value = data?.records || []
    total.value = data?.total || 0
  } finally { loading.value = false }
}

function changeType() { query.pageNo = 1; loadPage() }

function openVendor() {
  try {
    openExternalVending(config.value.externalUrl)
    vendingApi.openAudit().catch(() => {})
  } catch (error) {
    ElMessage.error(error.message)
  }
}

function afterConfirmed(result) {
  lastConfirmedBatchId.value = result.batchId
  loadPage()
  vendingApi.stats().then(data => { stats.value = data || {} })
}

async function rollbackLastBatch() {
  await ElMessageBox.confirm('撤销会删除仍由该批次最后写入的本地副本，审计记录会保留。是否继续？')
  await vendingApi.rollback(lastConfirmedBatchId.value)
  lastConfirmedBatchId.value = null
  ElMessage.success('批次已撤销')
  loadPage()
}

onMounted(async () => {
  const [externalConfig, summary] = await Promise.all([vendingApi.config(), vendingApi.stats()])
  config.value = externalConfig || config.value
  stats.value = summary || {}
  await loadPage()
})
</script>

<style scoped>
.vending-page { display:grid; gap:14px; }
.hero-card,.data-card { background:#fff; border-radius:12px; padding:20px; box-shadow:0 1px 4px rgba(0,21,41,.06); }
.hero-card { display:flex; justify-content:space-between; align-items:center; background:linear-gradient(135deg,#f4f8ff,#fff 58%,#f3fbf7); }
.eyebrow { color:#409eff; font-size:12px; letter-spacing:.08em; margin-bottom:8px; }
.hero-card h2 { margin:0 0 8px; font-size:25px; }.hero-card p { margin:0; color:#606266; }
.hero-actions { display:flex; gap:10px; }.boundary-grid { display:grid; grid-template-columns:1fr 1fr; gap:14px; }
.stats-row { margin:0!important; }.stat-card { background:#fff; border:1px solid #ebeef5; border-radius:10px; padding:18px; display:flex; flex-direction:column; gap:8px; }
.stat-card span { color:#909399; font-size:13px; }.stat-card strong { font-size:25px; color:#303133; }
.data-title { display:flex; justify-content:space-between; align-items:center; }.data-title span { color:#909399; font-size:12px; }
.pager { margin-top:16px; justify-content:flex-end; }.batch-bar { display:flex; justify-content:space-between; align-items:center; background:#f0f9eb; border:1px solid #e1f3d8; border-radius:8px; padding:10px 14px; color:#529b2e; }
@media (max-width:760px) { .hero-card { align-items:flex-start; gap:16px; flex-direction:column; }.boundary-grid { grid-template-columns:1fr; } }
</style>
