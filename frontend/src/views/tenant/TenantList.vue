<template>
  <div class="page-container">
    <!-- 统计卡 -->
    <div class="stat-row">
      <div class="stat-card">
        <div class="stat-label">租客总量</div>
        <div class="stat-value">{{ stats.total || 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">企业租客</div>
        <div class="stat-value">{{ stats.enterprise || 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">个人租客</div>
        <div class="stat-value">{{ stats.personal || 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">合同数量</div>
        <div class="stat-value">{{ stats.contractCount || 0 }}</div>
      </div>
    </div>

    <!-- 分类页签 -->
    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane label="全部" name="all" />
      <el-tab-pane label="企业" name="enterprise" />
      <el-tab-pane label="个人" name="personal" />
      <el-tab-pane label="已归档" name="archived" />
    </el-tabs>

    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="名称">
          <el-input v-model="query.name" placeholder="请输入名称" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="行业">
          <el-input v-model="query.industry" placeholder="请输入行业" clearable style="width: 180px" />
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
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增租客</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="row.tenantType === 1 ? 'primary' : 'success'">
              {{ row.tenantType === 1 ? '企业' : '个人' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="contact" label="联系人" min-width="100" />
        <el-table-column prop="phone" label="电话" min-width="130" />
        <el-table-column label="所属园区" min-width="130">
          <template #default="{ row }">{{ projectName(row.projectId) }}</template>
        </el-table-column>
        <el-table-column prop="industry" label="行业" min-width="100" />
        <el-table-column prop="tags" label="标签" min-width="120" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '正常' : '已归档' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDetail(row)">详情</el-button>
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确认归档?" @confirm="archive(row.id)">
              <template #reference><el-button link type="warning">归档</el-button></template>
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
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="620px">
      <el-form :model="form" label-width="130px" ref="formRef" :rules="rules">
        <el-form-item label="租客编码" prop="code">
          <el-input v-model="form.code" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="类型" prop="tenantType">
          <el-radio-group v-model="form.tenantType">
            <el-radio :value="1">企业</el-radio>
            <el-radio :value="2">个人</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contact" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="所属园区">
          <el-select v-model="form.projectId" placeholder="选择园区" clearable style="width: 100%">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="行业"><el-input v-model="form.industry" /></el-form-item>
        <el-form-item label="标签"><el-input v-model="form.tags" placeholder="逗号分隔" /></el-form-item>
        <template v-if="form.tenantType === 1">
          <el-form-item label="统一社会信用代码"><el-input v-model="form.creditCode" /></el-form-item>
          <el-form-item label="法人"><el-input v-model="form.legalPerson" /></el-form-item>
          <el-form-item label="注册地址"><el-input v-model="form.regAddress" /></el-form-item>
          <el-form-item label="成立日期">
            <el-date-picker v-model="form.establishDate" type="date" value-format="YYYY-MM-DD"
                            placeholder="选择日期" style="width: 100%" />
          </el-form-item>
        </template>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
        <el-form-item label="附件">
          <FileUpload v-model="attachFiles" biz-type="tenant" :biz-id="form.id" />
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
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { tenantApi } from '@/api/tenant'
import { projectApi } from '@/api/building'
import { fileApi } from '@/api/file'
import FileUpload from '@/components/FileUpload.vue'

const router = useRouter()
function showDetail(row) {
  router.push(`/tenant/detail/${row.id}`)
}

// 园区名称映射
const projects = ref([])
const projectName = (id) => projects.value.find(p => p.id === id)?.name ?? (id ?? '-')
projectApi.list().then(p => { projects.value = p || [] }).catch(() => {})

const loading = ref(false)
const list = ref([])
const total = ref(0)
const stats = ref({})
const activeTab = ref('all')
const query = reactive({ pageNo: 1, pageSize: 10, name: '', industry: '', tenantType: null, status: null })

async function loadStats() {
  stats.value = await tenantApi.stats()
}
async function load() {
  loading.value = true
  try {
    const res = await tenantApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
function onTabChange(name) {
  query.pageNo = 1
  query.tenantType = null
  query.status = null
  if (name === 'enterprise') query.tenantType = 1
  else if (name === 'personal') query.tenantType = 2
  else if (name === 'archived') query.status = 2
  else query.status = 1
  load()
}
function reset() {
  Object.assign(query, { pageNo: 1, name: '', industry: '' })
  load()
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const defaultForm = () => ({
  id: null, code: '', name: '', tenantType: 1, contact: '', phone: '', projectId: null,
  industry: '', tags: '', creditCode: '', legalPerson: '', regAddress: '', establishDate: null,
  status: 1, remark: ''
})
const form = reactive(defaultForm())
const attachFiles = ref([])
const rules = {
  code: [{ required: true, message: '请输入租客编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  tenantType: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

async function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑租客' : '新增租客'
  Object.assign(form, defaultForm())
  attachFiles.value = []
  if (row) {
    Object.assign(form, row)
    try { attachFiles.value = await fileApi.list('tenant', row.id) } catch (e) { /* 忽略 */ }
  }
}
async function submit() {
  await formRef.value.validate()
  let newId = form.id
  if (form.id) await tenantApi.update(form)
  else newId = await tenantApi.add(form)
  const pendingIds = (attachFiles.value || []).filter(f => f && f.id && !f.bizId).map(f => f.id)
  if (newId && pendingIds.length) {
    try { await fileApi.attach('tenant', newId, pendingIds) } catch (e) { /* 忽略,不阻断保存 */ }
  }
  ElMessage.success('保存成功')
  dialog.visible = false
  load()
  loadStats()
}
async function archive(id) {
  await tenantApi.archive(id)
  ElMessage.success('归档成功')
  load()
  loadStats()
}
async function remove(id) {
  await tenantApi.remove(id)
  ElMessage.success('删除成功')
  load()
  loadStats()
}

onMounted(() => {
  query.status = 1
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
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
