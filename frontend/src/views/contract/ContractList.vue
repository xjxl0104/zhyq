<template>
  <div class="page-container">
    <!-- 合同类型页签(规格书:合同列表/电子/意向/草稿/优惠/成本) -->
    <el-tabs v-model="typeTab" @tab-change="onTypeTab">
      <el-tab-pane label="全部合同" name="all" />
      <el-tab-pane label="正式合同" name="1" />
      <el-tab-pane label="意向合同" name="2" />
      <el-tab-pane label="草稿合同" name="3" />
      <el-tab-pane label="电子合同" name="4" />
      <el-tab-pane label="优惠合同" name="5" />
      <el-tab-pane label="成本合同" name="6" />
    </el-tabs>

    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="合同编号">
          <el-input v-model="query.code" placeholder="请输入合同编号" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="租客">
          <el-select v-model="query.tenantRefId" placeholder="全部" clearable filterable style="width: 180px">
            <el-option v-for="t in tenants" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="clearFilter">清空条件</el-button>
          <!-- 与账单页同口径:重置 = 真清数据,不是清筛选条件 -->
          <el-button type="danger" plain @click="reset">重置</el-button>
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
        <el-table-column prop="code" label="合同编号" min-width="150" />
        <el-table-column label="租客" min-width="150">
          <template #default="{ row }">{{ tenantName(row.tenantRefId) }}</template>
        </el-table-column>
        <el-table-column label="园区" min-width="130">
          <template #default="{ row }">{{ projectName(row.projectId) }}</template>
        </el-table-column>
        <el-table-column label="起止日期" min-width="200">
          <template #default="{ row }">{{ row.startDate }} ~ {{ row.endDate }}</template>
        </el-table-column>
        <el-table-column label="租赁单价" width="110">
          <template #default="{ row }">{{ row.rentPrice }} 元/㎡</template>
        </el-table-column>
        <el-table-column label="面积" width="100">
          <template #default="{ row }">{{ row.rentArea }} ㎡</template>
        </el-table-column>
        <el-table-column label="保证金" width="110">
          <template #default="{ row }">{{ row.deposit }} 元</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDetail(row)">详情</el-button>
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <!-- 草稿:提交审批 -->
            <el-popconfirm v-if="row.status === 1" title="确认提交审批?" @confirm="submit(row.id)">
              <template #reference><el-button link type="warning">提交审批</el-button></template>
            </el-popconfirm>
            <!-- 待审核:审批通过 -->
            <el-popconfirm v-if="row.status === 2" title="确认审批通过?通过后将生成账单计划" @confirm="approve(row.id)">
              <template #reference><el-button link type="success">审批通过</el-button></template>
            </el-popconfirm>
            <!-- 执行中:退租 -->
            <el-popconfirm v-if="row.status === 5" title="确认退租?房源将被释放" @confirm="terminate(row.id)">
              <template #reference><el-button link type="danger">退租</el-button></template>
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
      <el-form :model="form" label-width="100px" ref="formRef" :rules="rules">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="合同编号" prop="code">
              <el-input v-model="form.code" :disabled="!!form.id" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="合同类型">
              <el-select v-model="form.contractType" style="width: 100%">
                <el-option label="正式" :value="1" />
                <el-option label="意向" :value="2" />
                <el-option label="草稿" :value="3" />
                <el-option label="电子" :value="4" />
                <el-option label="优惠" :value="5" />
                <el-option label="成本" :value="6" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="租客" prop="tenantRefId">
              <el-select v-model="form.tenantRefId" placeholder="选择租客" filterable style="width: 100%">
                <el-option v-for="t in tenants" :key="t.id" :label="t.name" :value="t.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="园区" prop="projectId">
              <el-select v-model="form.projectId" placeholder="选择园区" style="width: 100%">
                <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="起始日期" prop="startDate">
              <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束日期" prop="endDate">
              <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="租赁单价">
              <el-input v-model.number="form.rentPrice" placeholder="元/㎡/月" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="物业单价">
              <el-input v-model.number="form.propertyPrice" placeholder="元/㎡/月" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="租赁面积">
              <el-input v-model.number="form.rentArea" placeholder="㎡" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="保证金">
              <el-input v-model.number="form.deposit" placeholder="元" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计租方式">
              <el-select v-model="form.chargeMode" style="width: 100%">
                <el-option label="按面积单价" :value="1" />
                <el-option label="固定金额" :value="2" />
                <el-option label="阶梯单价" :value="3" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="付款周期">
              <el-select v-model="form.payCycle" style="width: 100%">
                <el-option label="月付(1)" :value="1" />
                <el-option label="季付(3)" :value="3" />
                <el-option label="半年付(6)" :value="6" />
                <el-option label="年付(12)" :value="12" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="免租月数">
              <el-input v-model.number="form.freeMonths" placeholder="月" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="附件">
              <FileUpload v-model="attachFiles" biz-type="contract" :biz-id="form.id" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { contractApi } from '@/api/contract'
import { fileApi } from '@/api/file'
import FileUpload from '@/components/FileUpload.vue'
import { tenantApi } from '@/api/tenant'
import { projectApi } from '@/api/building'

const router = useRouter()

// 租客/园区 名称映射(下拉 + 表格名称解析)
const tenants = ref([])
const projects = ref([])
const tenantName = (id) => tenants.value.find(t => t.id === id)?.name ?? (id ?? '-')
const projectName = (id) => projects.value.find(p => p.id === id)?.name ?? (id ?? '-')
async function loadRefs() {
  try {
    const [t, p] = await Promise.all([tenantApi.list(), projectApi.list()])
    tenants.value = t || []
    projects.value = p || []
  } catch (e) { /* 下拉数据失败不阻塞列表 */ }
}

const statusOptions = [
  { value: 1, label: '草稿' },
  { value: 2, label: '待审核' },
  { value: 3, label: '待签署' },
  { value: 4, label: '待执行' },
  { value: 5, label: '执行中' },
  { value: 6, label: '变更中' },
  { value: 7, label: '退租中' },
  { value: 8, label: '已到期' },
  { value: 9, label: '已终止' },
  { value: 10, label: '已归档' }
]
const statusMap = statusOptions.reduce((m, s) => (m[s.value] = s.label, m), {})
function statusText(v) { return statusMap[v] || '-' }
function statusTagType(v) {
  // 待处理=warning / 执行中=primary / 成功=success / 终止异常=danger / 归档=info
  if ([2, 3, 4, 7].includes(v)) return 'warning'
  if ([5, 6].includes(v)) return 'primary'
  if (v === 8) return 'success'
  if (v === 9) return 'danger'
  if (v === 10) return 'info'
  return 'info' // 草稿
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const typeTab = ref('all')
const query = reactive({ pageNo: 1, pageSize: 10, code: '', tenantRefId: null, status: null, contractType: null })

function onTypeTab(name) {
  query.pageNo = 1
  query.contractType = name === 'all' ? null : Number(name)
  load()
}

async function load() {
  loading.value = true
  try {
    const res = await contractApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
/** 清空筛选条件(不动数据):页签选的合同类型保留,只清编号/租客/状态并回到第一页 */
function clearFilter() {
  Object.assign(query, { pageNo: 1, code: '', tenantRefId: null, status: null })
  load()
}

/**
 * 重置合同:录错/演示数据的重来通道。与账单页「重置」同一语义 ——
 * 作废未产生实收的全部合同,名下账单收过款的整份保留。
 */
async function reset() {
  try {
    await ElMessageBox.confirm(
      '将作废所有「未产生任何实收」的合同(名下账单收过款的整份保留)。' +
      '同时把关联房源放回可租、解除应收登记表的合同关联。确定重置?',
      '重置合同',
      { type: 'warning', confirmButtonText: '确定重置', cancelButtonText: '取消' }
    )
  } catch (e) {
    return // 用户取消
  }
  const res = await contractApi.reset()
  ElMessage.success(`已重置:作废 ${res.deleted} 份合同，保留 ${res.kept} 份(有实收)`)
  Object.assign(query, { pageNo: 1 })
  load()
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const emptyForm = () => ({
  id: null, code: '', contractType: 1, tenantRefId: null, projectId: null,
  startDate: '', endDate: '', rentPrice: 0, propertyPrice: 0, rentArea: 0,
  deposit: 0, chargeMode: 1, payCycle: 3, freeMonths: 0, remark: ''
})
const form = reactive(emptyForm())
const attachFiles = ref([])
const rules = {
  code: [{ required: true, message: '请输入合同编号', trigger: 'blur' }],
  tenantRefId: [{ required: true, message: '请选择租客', trigger: 'change' }],
  projectId: [{ required: true, message: '请选择园区', trigger: 'change' }],
  startDate: [{ required: true, message: '请选择起始日期', trigger: 'change' }],
  endDate: [{ required: true, message: '请选择结束日期', trigger: 'change' }]
}

async function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑合同' : '新增合同'
  attachFiles.value = []
  if (row) {
    Object.assign(form, row)
    // 编辑:载入已关联附件
    try { attachFiles.value = await fileApi.list('contract', row.id) } catch (e) { /* 忽略 */ }
  } else {
    Object.assign(form, emptyForm())
  }
}
async function submitForm() {
  await formRef.value.validate()
  let contractId = form.id
  if (form.id) {
    await contractApi.update(form)
  } else {
    contractId = await contractApi.add(form)
  }
  // 先传后回填:把新上传(bizId 为空)的附件关联到本合同
  const pendingIds = (attachFiles.value || [])
    .filter(f => f && f.id && !f.bizId)
    .map(f => f.id)
  if (contractId && pendingIds.length) {
    try { await fileApi.attach('contract', contractId, pendingIds) } catch (e) { /* 忽略,不阻断保存 */ }
  }
  ElMessage.success('保存成功')
  dialog.visible = false
  load()
}
async function remove(id) {
  await contractApi.remove(id)
  ElMessage.success('删除成功')
  load()
}
async function submit(id) {
  await contractApi.submit(id)
  ElMessage.success('已提交审批')
  load()
}
async function approve(id) {
  await contractApi.approve(id)
  ElMessage.success('审批通过,已生成账单计划')
  load()
}
async function terminate(id) {
  await contractApi.terminate(id)
  ElMessage.success('已退租')
  load()
}

function showDetail(row) {
  router.push(`/contract/detail/${row.id}`)
}

onMounted(() => { loadRefs(); load() })
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
