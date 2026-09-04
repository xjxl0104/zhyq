<template>
  <div class="page-container">
    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="表计编号">
          <el-input v-model="query.code" placeholder="请输入编号" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="能源类型">
          <el-select v-model="query.energyType" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="t in energyTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="在用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="租户">
          <el-select v-model="query.tenantRefId" placeholder="全部" clearable filterable style="width: 220px">
            <el-option v-for="t in tenants" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 表格区 -->
    <div class="table-card">
      <div class="toolbar">
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增表计</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column prop="code" label="表计编号" min-width="140" />
        <el-table-column prop="name" label="名称" min-width="120" />
        <el-table-column label="能源类型" width="110">
          <template #default="{ row }">
            <el-tag :type="energyTagType(row.energyType)">{{ row.energyType }}</el-tag>
          </template>
        </el-table-column>
        <!-- 租户经 房间 → 执行中合同 → 租客 反查:表计本身只挂房间,已退租的旧合同不算 -->
        <el-table-column prop="tenantName" label="租户" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="cell-main">{{ row.tenantName || '-' }}</div>
            <div v-if="row.roomCode" class="cell-sub">{{ row.roomCode }}</div>
          </template>
        </el-table-column>
        <!-- 「日期」按本期账期理解:上次抄表日 ~ 本次抄表日,也就是这笔用量对应的区间 -->
        <el-table-column label="账期" width="185">
          <template #default="{ row }">
            <span v-if="row.periodEnd">{{ row.periodStart || '首次' }} ~ {{ row.periodEnd }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="usageAmount" label="用电量" width="110" align="right">
          <template #default="{ row }">
            <span v-if="row.usageAmount != null" class="usage">{{ row.usageAmount }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="currReading" label="当前读数" width="115" align="right">
          <template #default="{ row }">{{ row.currReading ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="lastReadTime" label="最近抄表时间" width="170">
          <template #default="{ row }">{{ row.lastReadTime || '未抄表' }}</template>
        </el-table-column>
        <el-table-column prop="ratio" label="倍率" width="80" />
        <el-table-column prop="lastReading" label="上次读数" width="110" align="right" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '在用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openReadings(row)">抄表记录</el-button>
            <!-- 一次抄表只出一张账单:已出过就置灰,不给用户重复点的机会 -->
            <el-button v-if="row.billed" link type="info" disabled>已出账</el-button>
            <el-button v-else link type="success" :loading="billing === row.id" @click="createBill(row)">计费</el-button>
            <el-button link type="info" @click="openLogs(row)">操作日志</el-button>
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除?" @confirm="remove(row.id)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                     :total="total" v-model:current-page="query.pageNo"
                     v-model:page-size="query.pageSize" :page-sizes="[10,20,50]" @change="load" />
    </div>

    <!-- 表计表单弹窗 -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="520px">
      <el-form :model="form" label-width="90px" ref="formRef" :rules="rules">
        <el-form-item label="表计编号" prop="code">
          <el-input v-model="form.code" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="能源类型" prop="energyType">
          <el-select v-model="form.energyType" placeholder="请选择" style="width: 100%">
            <el-option v-for="t in energyTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="倍率"><el-input-number v-model="form.ratio" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="上次读数"><el-input-number v-model="form.lastReading" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">在用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 抄表记录抽屉 -->
    <el-drawer v-model="readingDrawer.visible" :title="readingDrawer.title" size="60%">
      <div class="toolbar">
        <el-button type="primary" @click="openReadingDialog()"><el-icon><Plus /></el-icon>新增抄表</el-button>
      </div>
      <el-table :data="readingList" v-loading="readingLoading" border stripe>
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column prop="prevReading" label="上次读数" min-width="110" />
        <el-table-column prop="currReading" label="本次读数" min-width="110" />
        <el-table-column prop="usageAmount" label="用量" min-width="100" />
        <el-table-column prop="readSource" label="抄表方式" min-width="110" />
        <el-table-column prop="readTime" label="抄表时间" width="170" />
        <el-table-column prop="fee" label="费用" width="100" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-popconfirm title="确认删除?" @confirm="removeReading(row.id)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next"
                     :total="readingTotal" v-model:current-page="readingQuery.pageNo"
                     v-model:page-size="readingQuery.pageSize" @change="loadReadings" />
    </el-drawer>

    <!-- 抄表表单弹窗 -->
    <el-dialog v-model="readingDialog.visible" title="新增抄表" width="480px">
      <el-form :model="readingForm" label-width="90px" ref="readingFormRef">
        <el-form-item label="上次读数">
          <el-input-number v-model="readingForm.prevReading" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item label="本次读数">
          <el-input-number v-model="readingForm.currReading" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item label="抄表方式">
          <el-select v-model="readingForm.readSource" style="width: 100%">
            <el-option v-for="s in readSources" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="抄表时间">
          <el-date-picker v-model="readingForm.readTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="readingDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitReading">确定</el-button>
      </template>
    </el-dialog>

    <!-- 操作日志:sys_oper_log 没有业务对象 id 列,后端按 模块 + 表计编号/路径 匹配 -->
    <el-dialog v-model="logDialog.visible" width="820px"
               :title="`操作日志 - ${logDialog.meter?.code || ''} ${logDialog.meter?.name || ''}`">
      <el-table :data="logDialog.rows" v-loading="logDialog.loading" border stripe size="small" max-height="420">
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column prop="action" label="操作" width="150" />
        <el-table-column prop="operator" label="操作人" width="120" />
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <el-tag :type="row.success === 1 ? 'success' : 'danger'">
              {{ row.success === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="errorMsg" label="失败原因" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.errorMsg || '-' }}</template>
        </el-table-column>
        <el-table-column prop="ip" label="来源 IP" min-width="140" show-overflow-tooltip />
        <el-table-column prop="costMs" label="耗时" width="90" align="right">
          <template #default="{ row }">{{ row.costMs }} ms</template>
        </el-table-column>
        <el-table-column prop="createTime" label="时间" width="170" />
      </el-table>
      <div v-if="!logDialog.loading && !logDialog.rows.length" class="empty-tip">
        暂无操作日志（新增/修改/删除/计费会记录在这里）
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { meterApi, readingApi } from '@/api/energy'
import { tenantApi } from '@/api/tenant'

const energyTypes = ['电', '水', '燃气', '热力']
const readSources = ['自动采集', '人工', '估算', '补录', '换表']

const loading = ref(false)
const list = ref([])
const total = ref(0)
const EMPTY_QUERY = { code: '', energyType: null, status: null, tenantRefId: null }
const query = reactive({ pageNo: 1, pageSize: 10, ...EMPTY_QUERY })
// 租户下拉用租客档案全量:表计的租户是经合同反查出来的,档案才是名册的真相源
const tenants = ref([])
const billing = ref(null)
const logDialog = reactive({ visible: false, meter: null, loading: false, rows: [] })

function energyTagType(type) {
  const map = { '电': 'warning', '水': 'primary', '燃气': 'danger', '热力': 'success' }
  return map[type] || 'info'
}

async function load() {
  loading.value = true
  try {
    const res = await meterApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
function reset() {
  Object.assign(query, { pageNo: 1, ...EMPTY_QUERY })
  load()
}
// 查询回第 1 页,换条件后停在旧页码多半是一屏空白
function search() {
  query.pageNo = 1
  return load()
}

// 计费:把最近一次抄表变成一张能源费账单。后端按「表计+抄表记录」做幂等,
// 重复点不会出两张;出账后本行按钮变「已出账」
async function createBill(row) {
  const period = row.periodEnd ? `${row.periodStart || '首次'} ~ ${row.periodEnd}` : '最近一次抄表'
  await ElMessageBox.confirm(
    `将按 ${period} 的抄表结果为「${row.tenantName || '该表计'}」生成一张能源费账单` +
    `（用量 ${row.usageAmount ?? '-'}，金额 ¥${row.latestFee ?? '-'}）。`,
    '生成能源费账单', { type: 'warning', confirmButtonText: '确认生成', cancelButtonText: '取消' })
  billing.value = row.id
  try {
    const bill = await meterApi.createBill(row.id)
    ElMessage.success(`已生成账单 ${bill?.code || ''}，可在「财务 → 所有账单」收款`)
    await load()
  } finally {
    billing.value = null
  }
}

async function openLogs(row) {
  Object.assign(logDialog, { visible: true, meter: row, loading: true, rows: [] })
  try {
    logDialog.rows = await meterApi.operLogs(row.id) || []
  } finally {
    logDialog.loading = false
  }
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const form = reactive({ id: null, code: '', name: '', energyType: '电', ratio: 1, lastReading: 0, status: 1 })
const rules = {
  code: [{ required: true, message: '请输入表计编号', trigger: 'blur' }],
  energyType: [{ required: true, message: '请选择能源类型', trigger: 'change' }]
}

function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑表计' : '新增表计'
  if (row) Object.assign(form, row)
  else Object.assign(form, { id: null, code: '', name: '', energyType: '电', ratio: 1, lastReading: 0, status: 1 })
}
async function submit() {
  await formRef.value.validate()
  if (form.id) await meterApi.update(form)
  else await meterApi.add(form)
  ElMessage.success('保存成功')
  dialog.visible = false
  load()
}
async function remove(id) {
  await meterApi.remove(id)
  ElMessage.success('删除成功')
  load()
}

// 抄表记录抽屉
const readingDrawer = reactive({ visible: false, title: '', meter: null })
const readingLoading = ref(false)
const readingList = ref([])
const readingTotal = ref(0)
const readingQuery = reactive({ pageNo: 1, pageSize: 10, meterId: null })

function openReadings(row) {
  readingDrawer.visible = true
  readingDrawer.title = `抄表记录 - ${row.code}`
  readingDrawer.meter = row
  readingQuery.pageNo = 1
  readingQuery.meterId = row.id
  loadReadings()
}
async function loadReadings() {
  readingLoading.value = true
  try {
    const res = await readingApi.page(readingQuery)
    readingList.value = res.records
    readingTotal.value = res.total
  } finally {
    readingLoading.value = false
  }
}
async function removeReading(id) {
  await readingApi.remove(id)
  ElMessage.success('删除成功')
  loadReadings()
}

const readingFormRef = ref()
const readingDialog = reactive({ visible: false })
const readingForm = reactive({ meterId: null, prevReading: 0, currReading: 0, readSource: '人工', readTime: null })

function openReadingDialog() {
  readingDialog.visible = true
  Object.assign(readingForm, {
    meterId: readingDrawer.meter?.id,
    prevReading: readingDrawer.meter?.lastReading || 0,
    currReading: 0,
    readSource: '人工',
    readTime: null
  })
}
async function submitReading() {
  await readingApi.add(readingForm)
  ElMessage.success('保存成功')
  readingDialog.visible = false
  loadReadings()
}

onMounted(async () => {
  // 租客名册取不到只是少一个下拉,不该拖垮表计列表
  const [tenantList] = await Promise.allSettled([tenantApi.list(), load()])
  if (tenantList.status === 'fulfilled') tenants.value = tenantList.value || []
})
</script>

<style scoped>
.cell-main { line-height: 1.4; }
.cell-sub { margin-top: 2px; font-size: 12px; line-height: 1.3; color: var(--el-text-color-secondary); }
.usage { font-weight: 600; font-variant-numeric: tabular-nums; }
.empty-tip { text-align: center; color: var(--el-text-color-secondary); padding: 20px 0; font-size: 13px; }
.pager { margin-top: 16px; justify-content: flex-end; }
.toolbar { margin-bottom: 12px; }
</style>
