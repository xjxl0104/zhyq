<template>
  <div class="page-container">
    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="标题">
          <el-input v-model="query.title" placeholder="请输入标题" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="Number(k)" />
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
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
        <el-table-column prop="tenantRefId" label="提交租客ID" width="100" />
        <el-table-column prop="contact" label="联系人" width="100" />
        <el-table-column prop="phone" label="电话" width="130" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status]?.type || 'info'">
              {{ statusMap[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="handler" label="处理人" width="100" />
        <el-table-column prop="reply" label="回复" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 1" link type="primary" @click="startProcessing(row)">开始处理</el-button>
            <el-button v-if="row.status === 2" link type="success" @click="openHandleDialog(row)">办结</el-button>
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

    <!-- 表单弹窗 -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="520px">
      <el-form :model="form" label-width="90px" ref="formRef" :rules="rules">
        <el-form-item label="标题" prop="title"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="内容"><el-input v-model="form.content" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="提交租客ID"><el-input-number v-model="form.tenantRefId" :min="0" style="width: 100%" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contact" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 办结弹窗 -->
    <el-dialog v-model="handleDialog.visible" title="处理办结" width="480px">
      <el-form :model="handleForm" label-width="80px">
        <el-form-item label="回复"><el-input v-model="handleForm.reply" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="处理人"><el-input v-model="handleForm.handler" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitHandle">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { feedbackApi } from '@/api/property'

const route = useRoute()

const statusMap = {
  1: { label: '待处理', type: 'warning' },
  2: { label: '处理中', type: 'primary' },
  3: { label: '已办结', type: 'success' }
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, title: '', status: null })

async function load() {
  loading.value = true
  try {
    const res = await feedbackApi.page({ ...query, ftype: route.meta.ftype })
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

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const form = reactive({ id: null, title: '', content: '', tenantRefId: null, contact: '', phone: '' })
const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }]
}

function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑' : '新增'
  if (row) Object.assign(form, row)
  else Object.assign(form, { id: null, title: '', content: '', tenantRefId: null, contact: '', phone: '' })
}
async function submit() {
  await formRef.value.validate()
  const data = { ...form, ftype: route.meta.ftype }
  if (form.id) await feedbackApi.update(data)
  else await feedbackApi.add(data)
  ElMessage.success('保存成功')
  dialog.visible = false
  load()
}
async function remove(id) {
  await feedbackApi.remove(id)
  ElMessage.success('删除成功')
  load()
}

async function startProcessing(row) {
  await feedbackApi.processing(row.id)
  ElMessage.success('已开始处理')
  load()
}

const handleDialog = reactive({ visible: false, row: null })
const handleForm = reactive({ reply: '', handler: '' })
function openHandleDialog(row) {
  handleDialog.visible = true
  handleDialog.row = row
  handleForm.reply = ''
  handleForm.handler = ''
}
async function submitHandle() {
  await feedbackApi.handle(handleDialog.row.id, { reply: handleForm.reply, handler: handleForm.handler })
  ElMessage.success('办结成功')
  handleDialog.visible = false
  load()
}

watch(() => route.meta.ftype, load)

onMounted(load)
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
