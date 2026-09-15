<template>
  <div class="page-container">
    <!-- 统计卡 -->
    <div class="stat-row">
      <div class="stat-card">
        <div class="stat-label">供应商总量</div>
        <div class="stat-value">{{ stats.total || 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">正常合作</div>
        <div class="stat-value">{{ stats.normal || 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">已停用</div>
        <div class="stat-value">{{ stats.disabled || 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">已归档</div>
        <div class="stat-value">{{ stats.archived || 0 }}</div>
      </div>
    </div>

    <!-- 类别页签:来自字典 supplier_category,在「系统管理→字典管理」新增类别后这里自动出现 -->
    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane label="全部" name="all" />
      <el-tab-pane v-for="c in categories" :key="c.value" :label="c.label" :name="c.value" />
    </el-tabs>

    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="名称">
          <el-input v-model="query.name" placeholder="请输入供应商名称" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="query.contact" placeholder="请输入联系人" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
            <el-option label="正常" :value="1" />
            <el-option label="已停用" :value="2" />
            <el-option label="已归档" :value="3" />
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
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增供应商</el-button>
        <el-button @click="importVisible = true"><el-icon><Upload /></el-icon>表格导入</el-button>
        <span class="toolbar-tip">类别不够用?到「系统管理 → 字典管理 → 供应商类别」里自己加</span>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column prop="code" label="供应商编号" width="120" />
        <el-table-column prop="name" label="供应商名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="类别" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.category" :type="categoryColor(row.category)">
              {{ categoryLabel(row.category) }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="contact" label="联系人" width="100" />
        <el-table-column prop="phone" label="联系电话" min-width="130" />
        <el-table-column prop="creditCode" label="统一社会信用代码" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusColor(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button v-if="row.status === 1" link type="warning" @click="changeStatus(row, 2)">停用</el-button>
            <el-button v-else link type="success" @click="changeStatus(row, 1)">启用</el-button>
            <el-popconfirm v-if="row.status !== 3" title="确认归档?" @confirm="changeStatus(row, 3)">
              <template #reference><el-button link type="info">归档</el-button></template>
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
      <el-form :model="form" label-width="140px" ref="formRef" :rules="rules">
        <el-form-item v-if="form.id" label="供应商编号">
          <el-input v-model="form.code" disabled />
        </el-form-item>
        <el-form-item label="供应商名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="类别" prop="category">
          <el-select v-model="form.category" placeholder="请选择类别" clearable style="width: 100%">
            <el-option v-for="c in categories" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="统一社会信用代码"><el-input v-model="form.creditCode" /></el-form-item>
        <el-form-item label="法人"><el-input v-model="form.legalPerson" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contact" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
        <el-form-item label="注册地址"><el-input v-model="form.regAddress" /></el-form-item>
        <el-form-item label="开户行"><el-input v-model="form.bankName" /></el-form-item>
        <el-form-item label="银行账号"><el-input v-model="form.bankAccount" /></el-form-item>
        <el-form-item label="经营范围">
          <el-input v-model="form.businessScope" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="资质说明">
          <el-input v-model="form.qualification" type="textarea" :rows="2"
                    placeholder="如:一级施工资质、ISO9001 等;证照扫描件请传附件" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="资质附件">
          <FileUpload v-model="attachFiles" biz-type="supplier" :biz-id="form.id" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 表格导入 -->
    <el-dialog v-model="importVisible" title="导入供应商档案" width="580px" @close="importResult = null">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 14px"
                title="按表头文字认列，不要求列顺序；表头至少要有「供应商名称」或「公司名称」列。类别可填中文名（如 物业服务）。统一社会信用代码或名称已存在的会自动跳过，不会覆盖已有档案。" />
      <el-upload drag :auto-upload="false" :limit="1" :accept="IMPORT_ACCEPT"
                 :on-change="onFileChange" :on-remove="() => (importFile = null)" :file-list="[]">
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">把文件拖到这里，或<em>点击选择</em></div>
        <template #tip>
          <div class="el-upload__tip">
            支持 Excel(.xlsx / .xls / WPS .et)、CSV / TXT、Word(.docx 中的表格)
            <el-button link type="primary" @click.stop="downloadTemplate">下载模板</el-button>
          </div>
        </template>
      </el-upload>
      <div v-if="importFile" class="picked">已选择：{{ importFile.name }}</div>

      <div v-if="importResult" class="import-result">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="导入成功">{{ importResult.imported }} 条</el-descriptions-item>
          <el-descriptions-item label="已存在跳过">{{ importResult.skipped }} 条</el-descriptions-item>
          <el-descriptions-item label="失败">{{ importResult.errors.length }} 条</el-descriptions-item>
        </el-descriptions>
        <ul v-if="importResult.errors.length" class="err-list">
          <li v-for="(e, i) in importResult.errors" :key="i">{{ e }}</li>
        </ul>
      </div>

      <template #footer>
        <el-button @click="importVisible = false">关闭</el-button>
        <el-button type="primary" :loading="importing" :disabled="!importFile" @click="doImport">开始导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { supplierApi } from '@/api/supplier'
import { dictApi } from '@/api/system'
import { fileApi } from '@/api/file'
import FileUpload from '@/components/FileUpload.vue'

// 类别字典:使用方可在「系统管理→字典管理」自行增删,这里不写死
const categories = ref([])
const categoryLabel = (v) => categories.value.find(c => c.value === v)?.label ?? v
const categoryColor = (v) => categories.value.find(c => c.value === v)?.color || 'primary'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const stats = ref({})
const activeTab = ref('all')
const query = reactive({ pageNo: 1, pageSize: 10, name: '', contact: '', category: null, status: null })

const statusText = (s) => ({ 1: '正常', 2: '已停用', 3: '已归档' }[s] ?? '-')
const statusColor = (s) => ({ 1: 'success', 2: 'warning', 3: 'info' }[s] ?? 'info')

async function loadCategories() {
  try {
    categories.value = await dictApi.dataByType('supplier_category') || []
  } catch (e) {
    categories.value = []
  }
}
async function loadStats() {
  stats.value = await supplierApi.stats()
}
async function load() {
  loading.value = true
  try {
    const res = await supplierApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
function onTabChange(name) {
  query.pageNo = 1
  query.category = name === 'all' ? null : name
  load()
}
function reset() {
  Object.assign(query, { pageNo: 1, name: '', contact: '', status: null })
  load()
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const defaultForm = () => ({
  id: null, code: '', name: '', category: null, creditCode: '', legalPerson: '',
  contact: '', phone: '', email: '', regAddress: '', bankName: '', bankAccount: '',
  businessScope: '', qualification: '', status: 1, remark: ''
})
const form = reactive(defaultForm())
const attachFiles = ref([])
const rules = {
  name: [{ required: true, message: '请输入供应商名称', trigger: 'blur' }]
}

async function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑供应商' : '新增供应商'
  Object.assign(form, defaultForm())
  attachFiles.value = []
  if (row) {
    Object.assign(form, row)
    try { attachFiles.value = await fileApi.list('supplier', row.id) } catch (e) { /* 忽略 */ }
  }
}
async function submit() {
  await formRef.value.validate()
  let newId = form.id
  if (form.id) await supplierApi.update(form)
  else newId = await supplierApi.add(form)
  const pendingIds = (attachFiles.value || []).filter(f => f && f.id && !f.bizId).map(f => f.id)
  if (newId && pendingIds.length) {
    try { await fileApi.attach('supplier', newId, pendingIds) } catch (e) { /* 忽略,不阻断保存 */ }
  }
  ElMessage.success('保存成功')
  dialog.visible = false
  load()
  loadStats()
}
async function changeStatus(row, status) {
  await supplierApi.changeStatus(row.id, status)
  ElMessage.success('操作成功')
  load()
  loadStats()
}
async function remove(id) {
  await supplierApi.remove(id)
  ElMessage.success('删除成功')
  load()
  loadStats()
}

// 表格导入
const IMPORT_ACCEPT = '.xlsx,.xls,.et,.csv,.txt,.tsv,.docx'
const TEMPLATE_HEADERS = ['供应商名称', '类别', '统一社会信用代码', '法定代表人', '联系人', '联系电话', '邮箱',
  '注册地址', '开户行', '银行账号', '经营范围', '资质说明', '状态', '备注']
const importVisible = ref(false)
const importing = ref(false)
const importFile = ref(null)
const importResult = ref(null)
function onFileChange(f) {
  importFile.value = f.raw
  importResult.value = null
}
async function doImport() {
  importing.value = true
  try {
    const fd = new FormData()
    fd.append('file', importFile.value)
    importResult.value = await supplierApi.importFile(fd)
    ElMessage.success(`导入完成：成功 ${importResult.value.imported} 条`)
    importFile.value = null
    await Promise.all([load(), loadStats()])
  } finally {
    importing.value = false
  }
}
function downloadTemplate() {
  const sample = ['示例物业服务有限公司', categories.value[0]?.label || '物业服务', '', '', '张三', '13800000000', '',
    '', '', '', '保洁、绿化', '', '正常', '']
  const csv = String.fromCharCode(0xfeff) + [TEMPLATE_HEADERS, sample].map((r) => r.join(',')).join('\r\n')
  const a = document.createElement('a')
  a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
  a.download = '供应商档案导入模板.csv'
  a.click()
  URL.revokeObjectURL(a.href)
}

onMounted(() => {
  loadCategories()
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
.toolbar-tip { margin-left: 12px; color: var(--text-secondary); font-size: 12px; }
.pager { margin-top: 16px; justify-content: flex-end; }
.picked { margin-top: 10px; font-size: 13px; color: #606266; }
.import-result { margin-top: 14px; }
.err-list { margin: 10px 0 0; padding-left: 18px; color: #f56c6c; font-size: 13px; max-height: 160px; overflow: auto; }
</style>
