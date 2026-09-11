<template>
  <div class="page-container">
    <!-- 统计卡 -->
    <div class="stat-row">
      <div class="stat-card">
        <div class="stat-label">合同总数</div>
        <div class="stat-value">{{ stats.total || 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">执行中</div>
        <div class="stat-value">{{ stats.running || 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">30天内到期</div>
        <div class="stat-value warn">{{ stats.expiring || 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">已到期</div>
        <div class="stat-value">{{ stats.expired || 0 }}</div>
      </div>
    </div>

    <!-- 类型页签:来自字典 supplier_contract_type,可在字典管理自行增删 -->
    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane label="全部" name="all" />
      <el-tab-pane v-for="t in types" :key="t.value" :label="t.label" :name="t.value" />
    </el-tabs>

    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="合同名称">
          <el-input v-model="query.name" placeholder="请输入合同名称" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="供应商">
          <el-select v-model="query.supplierId" placeholder="全部" clearable filterable style="width: 200px">
            <el-option v-for="s in suppliers" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
            <el-option label="草稿" :value="1" />
            <el-option label="执行中" :value="2" />
            <el-option label="已到期" :value="3" />
            <el-option label="已终止" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 表格区 -->
    <div class="table-card">
      <div class="toolbar">
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增合同</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column prop="code" label="合同编号" width="160" />
        <el-table-column prop="name" label="合同名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="supplierName" label="供应商" min-width="160" show-overflow-tooltip />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.contractType" :type="typeColor(row.contractType)">
              {{ typeLabel(row.contractType) }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="合同金额" width="140" align="right">
          <template #default="{ row }">{{ row.amount == null ? '-' : '¥' + money(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="服务期限" min-width="200">
          <template #default="{ row }">
            <span v-if="row.startDate || row.endDate">
              {{ row.startDate || '?' }} ~ {{ row.endDate || '?' }}
              <el-tag v-if="expiringSoon(row)" type="danger" size="small" class="soon">即将到期</el-tag>
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="payCycle" label="结算周期" width="110" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusColor(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button v-if="row.status === 1" link type="success" @click="changeStatus(row, 2)">生效</el-button>
            <el-popconfirm v-if="row.status === 2" title="确认标记为已到期?" @confirm="changeStatus(row, 3)">
              <template #reference><el-button link type="warning">标记到期</el-button></template>
            </el-popconfirm>
            <el-button v-if="row.status === 3" link type="success" @click="changeStatus(row, 2)">撤销到期</el-button>
            <el-popconfirm v-if="row.status === 1 || row.status === 2" title="确认终止该合同?"
                           @confirm="changeStatus(row, 4)">
              <template #reference><el-button link type="info">终止</el-button></template>
            </el-popconfirm>
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

    <!-- 表单弹窗 -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="680px">
      <el-form :model="form" label-width="130px" ref="formRef" :rules="rules">
        <el-form-item v-if="form.id" label="合同编号">
          <el-input v-model="form.code" disabled />
        </el-form-item>
        <el-form-item label="供应商" prop="supplierId">
          <el-select v-model="form.supplierId" placeholder="请选择供应商" filterable style="width: 100%">
            <el-option v-for="s in suppliers" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="合同名称" prop="name">
          <el-input v-model="form.name" placeholder="如:2026年度保洁服务合同" />
        </el-form-item>
        <el-form-item label="合同类型">
          <el-select v-model="form.contractType" placeholder="请选择类型" clearable style="width: 100%">
            <el-option v-for="t in types" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="合同金额">
          <el-input-number v-model="form.amount" :min="0" :precision="2" :controls="false"
                           placeholder="元" style="width: 100%" />
        </el-form-item>
        <el-form-item label="签订日期">
          <el-date-picker v-model="form.signDate" type="date" value-format="YYYY-MM-DD"
                          placeholder="选择日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="生效日期">
          <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD"
                          placeholder="选择日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="到期日期">
          <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD"
                          placeholder="选择日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结算周期">
          <el-select v-model="form.payCycle" placeholder="请选择" clearable allow-create filterable
                     style="width: 100%">
            <el-option label="月结" value="月结" />
            <el-option label="季结" value="季结" />
            <el-option label="年结" value="年结" />
            <el-option label="一次性" value="一次性" />
            <el-option label="按进度" value="按进度" />
          </el-select>
        </el-form-item>
        <el-form-item label="付款方式/账期">
          <el-input v-model="form.payTerms" placeholder="如:月结30天,银行转账" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="合同附件">
          <FileUpload v-model="attachFiles" biz-type="supplier_contract" :biz-id="form.id" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { supplierApi, supplierContractApi } from '@/api/supplier'
import { dictApi } from '@/api/system'
import { fileApi } from '@/api/file'
import { money } from '@/utils/format'
import FileUpload from '@/components/FileUpload.vue'

const types = ref([])
const typeLabel = (v) => types.value.find(t => t.value === v)?.label ?? v
const typeColor = (v) => types.value.find(t => t.value === v)?.color || 'primary'

const suppliers = ref([])

const loading = ref(false)
const list = ref([])
const total = ref(0)
const stats = ref({})
const activeTab = ref('all')
const query = reactive({
  pageNo: 1, pageSize: 10, name: '', supplierId: null, contractType: null, status: null
})

// 与后端 SupplierContractController 的状态常量与「即将到期」口径保持一致
const ST_RUNNING = 2
const ST_EXPIRED = 3
const EXPIRING_DAYS = 30

const statusText = (s) => ({ 1: '草稿', 2: '执行中', 3: '已到期', 4: '已终止' }[s] ?? '-')
const statusColor = (s) => ({ 1: 'info', 2: 'success', 3: 'warning', 4: 'danger' }[s] ?? 'info')

/**
 * 执行中且 30 天内到期 —— 与后端 stats 的 expiring 口径对齐。
 * 注意:new Date('YYYY-MM-DD') 按 UTC 零点解析,而 new Date() 是本地时间,
 * 东八区差 8 小时会让边界日(今天 / 今天+30)与后端算出不同结果。
 * 故两边都归一到「本地零点」后按整天比较。
 */
function expiringSoon(row) {
  if (row.status !== ST_RUNNING || !row.endDate) return false
  const end = new Date(row.endDate + 'T00:00:00')
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const days = Math.round((end - today) / 86400000)
  return days >= 0 && days <= EXPIRING_DAYS
}

async function loadTypes() {
  try {
    types.value = await dictApi.dataByType('supplier_contract_type') || []
  } catch (e) {
    types.value = []
  }
}
async function loadSuppliers() {
  try {
    suppliers.value = await supplierApi.list() || []
  } catch (e) {
    suppliers.value = []
  }
}
async function loadStats() {
  stats.value = await supplierContractApi.stats()
}
async function load() {
  loading.value = true
  try {
    const res = await supplierContractApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
function onTabChange(name) {
  query.pageNo = 1
  query.contractType = name === 'all' ? null : name
  load()
}
function reset() {
  Object.assign(query, { pageNo: 1, name: '', supplierId: null, status: null })
  load()
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const defaultForm = () => ({
  id: null, code: '', supplierId: null, name: '', contractType: null, amount: null,
  signDate: null, startDate: null, endDate: null, payCycle: '', payTerms: '', remark: ''
})
const form = reactive(defaultForm())
const attachFiles = ref([])
const rules = {
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  name: [{ required: true, message: '请输入合同名称', trigger: 'blur' }]
}

async function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑供应商合同' : '新增供应商合同'
  Object.assign(form, defaultForm())
  attachFiles.value = []
  if (row) {
    Object.assign(form, row)
    // 供应商下拉只取「正常」状态,编辑历史合同时其供应商可能已停用/归档,
    // 不兜底会回显空白、一保存就把关联丢了。
    if (row.supplierId && !suppliers.value.some(s => s.id === row.supplierId)) {
      suppliers.value = [...suppliers.value,
        { id: row.supplierId, name: (row.supplierName || '未知供应商') + '(已停用/归档)' }]
    }
    try { attachFiles.value = await fileApi.list('supplier_contract', row.id) } catch (e) { /* 忽略 */ }
  }
}
async function submit() {
  await formRef.value.validate()
  let newId = form.id
  if (form.id) await supplierContractApi.update(form)
  else newId = await supplierContractApi.add(form)
  const pendingIds = (attachFiles.value || []).filter(f => f && f.id && !f.bizId).map(f => f.id)
  if (newId && pendingIds.length) {
    try { await fileApi.attach('supplier_contract', newId, pendingIds) } catch (e) { /* 忽略 */ }
  }
  ElMessage.success('保存成功')
  dialog.visible = false
  load()
  loadStats()
}
async function changeStatus(row, status) {
  await supplierContractApi.changeStatus(row.id, status)
  ElMessage.success('操作成功')
  load()
  loadStats()
}
async function remove(id) {
  await supplierContractApi.remove(id)
  ElMessage.success('删除成功')
  load()
  loadStats()
}

onMounted(() => {
  loadTypes()
  loadSuppliers()
  loadStats()
  load()
})
</script>

<style scoped>
.stat-row { display: flex; gap: 16px; margin-bottom: 16px; }
.stat-card { flex: 1; background: var(--bg-card); border-radius: var(--radius);
  border: 1px solid var(--border); padding: 20px 22px;
  transition: border-color .18s, transform .18s; }
.stat-label { color: var(--text-secondary); font-size: 14px; }
.stat-value { font-size: 26px; font-weight: 600; margin-top: 8px; }
.stat-value.warn { color: #ea9a13; }
.soon { margin-left: 6px; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
