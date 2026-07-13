<template>
  <div class="page-container">
    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="标题">
          <el-input v-model="query.title" placeholder="请输入标题" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="草稿" :value="1" />
            <el-option label="已发送" :value="2" />
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
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增站内信</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="接收租客" min-width="140">
          <template #default="{ row }">
            {{ row.tenantRefId ? (tenantMap[row.tenantRefId] || `租客#${row.tenantRefId}`) : '全部租客' }}
          </template>
        </el-table-column>
        <el-table-column label="渠道" width="90">
          <template #default="{ row }">
            <el-tag effect="plain">{{ row.channel || '站内信' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 2 ? 'success' : 'info'">{{ row.status === 2 ? '已发送' : '草稿' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="已读" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.status === 2" :type="row.readFlag === 1 ? 'success' : 'warning'">
              {{ row.readFlag === 1 ? '已读' : '未读' }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="sendTime" label="发送时间" width="170">
          <template #default="{ row }">{{ row.sendTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-popconfirm v-if="row.status === 1" title="确认发送该站内信?" @confirm="send(row.id)">
              <template #reference><el-button link type="success">发送</el-button></template>
            </el-popconfirm>
            <el-button v-if="row.status === 1" link type="primary" @click="openDialog(row)">编辑</el-button>
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
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="560px">
      <el-form :model="form" label-width="90px" ref="formRef" :rules="rules">
        <el-form-item label="标题" prop="title"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="正文" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="5" placeholder="请输入消息正文" />
        </el-form-item>
        <el-form-item label="接收租客">
          <el-select v-model="form.tenantRefId" placeholder="不选=全部租客" clearable filterable style="width: 100%">
            <el-option v-for="t in tenants" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="渠道">
          <el-radio-group v-model="form.channel">
            <el-radio value="站内信">站内信</el-radio>
            <el-radio value="短信">短信</el-radio>
            <el-radio value="微信">微信</el-radio>
          </el-radio-group>
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
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { messageApi, tenantApi } from '@/api/tenant'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const tenants = ref([])
const tenantMap = computed(() => Object.fromEntries(tenants.value.map(t => [t.id, t.name])))
const query = reactive({ pageNo: 1, pageSize: 10, title: '', status: null })

async function load() {
  loading.value = true
  try {
    const res = await messageApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
function reset() {
  Object.assign(query, { pageNo: 1, title: '', status: null })
  load()
}
async function loadTenants() {
  tenants.value = await tenantApi.list()
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const form = reactive({ id: null, title: '', content: '', tenantRefId: null, channel: '站内信' })
const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入正文', trigger: 'blur' }]
}

function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑站内信' : '新增站内信'
  if (row) Object.assign(form, { id: row.id, title: row.title, content: row.content, tenantRefId: row.tenantRefId, channel: row.channel || '站内信' })
  else Object.assign(form, { id: null, title: '', content: '', tenantRefId: null, channel: '站内信' })
}
async function submit() {
  await formRef.value.validate()
  const data = { ...form, tenantRefId: form.tenantRefId || null }
  if (form.id) await messageApi.update(data)
  else await messageApi.add(data)
  ElMessage.success('保存成功')
  dialog.visible = false
  load()
}
async function send(id) {
  await messageApi.send(id)
  ElMessage.success('发送成功')
  load()
}
async function remove(id) {
  await messageApi.remove(id)
  ElMessage.success('删除成功')
  load()
}

onMounted(() => { load(); loadTenants() })
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
