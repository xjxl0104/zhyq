<template>
  <div class="page-container">
    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="厂商名称">
          <el-input v-model="query.name" placeholder="请输入厂商名称" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="(label, val) in statusMap" :key="val" :label="label" :value="Number(val)" />
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
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增厂商</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column prop="name" label="厂商" min-width="130" />
        <el-table-column prop="platform" label="平台" min-width="140" />
        <el-table-column prop="apiUrl" label="API地址" min-width="220" show-overflow-tooltip />
        <el-table-column prop="appKey" label="AppKey" min-width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusMap[row.status] || '未知' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button link type="success" @click="testConn(row)">连测</el-button>
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
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="520px">
      <el-alert
        title="演示环境密钥明文存储,生产须加密"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      />
      <el-form :model="form" label-width="90px" ref="formRef" :rules="rules">
        <el-form-item label="厂商" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="平台"><el-input v-model="form.platform" /></el-form-item>
        <el-form-item label="API地址"><el-input v-model="form.apiUrl" /></el-form-item>
        <el-form-item label="AppKey"><el-input v-model="form.appKey" /></el-form-item>
        <el-form-item label="AppSecret">
          <el-input v-model="form.appSecret" type="password" show-password />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option v-for="(label, val) in statusMap" :key="val" :label="label" :value="Number(val)" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
        <el-form-item label="附件">
          <FileUpload v-model="attachFiles" biz-type="vendor" :biz-id="form.id" />
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
import { vendorApi } from '@/api/iot'
import { fileApi } from '@/api/file'
import FileUpload from '@/components/FileUpload.vue'

// 1启用 0停用
const statusMap = { 1: '启用', 0: '停用' }
function statusTagType(status) {
  return status === 1 ? 'success' : 'info'
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, name: '', status: null })

async function load() {
  loading.value = true
  try {
    const res = await vendorApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
function reset() {
  Object.assign(query, { pageNo: 1, name: '', status: null })
  load()
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const form = reactive({ id: null, name: '', platform: '', apiUrl: '', appKey: '', appSecret: '', status: 1, remark: '' })
const attachFiles = ref([])
const rules = {
  name: [{ required: true, message: '请输入厂商名称', trigger: 'blur' }]
}

async function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑厂商' : '新增厂商'
  attachFiles.value = []
  if (row) {
    Object.assign(form, row)
    try { attachFiles.value = await fileApi.list('vendor', row.id) } catch (e) { /* 忽略 */ }
  } else {
    Object.assign(form, { id: null, name: '', platform: '', apiUrl: '', appKey: '', appSecret: '', status: 1, remark: '' })
  }
}
async function submit() {
  await formRef.value.validate()
  let vendorId = form.id
  if (form.id) await vendorApi.update(form)
  else vendorId = await vendorApi.add(form)
  const pendingIds = (attachFiles.value || []).filter(f => f && f.id && !f.bizId).map(f => f.id)
  if (vendorId && pendingIds.length) {
    try { await fileApi.attach('vendor', vendorId, pendingIds) } catch (e) { /* 忽略,不阻断保存 */ }
  }
  ElMessage.success('保存成功')
  dialog.visible = false
  load()
}
async function remove(id) {
  await vendorApi.remove(id)
  ElMessage.success('删除成功')
  load()
}
async function testConn(row) {
  const res = await vendorApi.test(row.id)
  ElMessage.success(res || '连接成功(模拟)')
}

onMounted(() => {
  load()
})
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
.toolbar { margin-bottom: 12px; }
</style>
