<template>
  <div class="page-container">
    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="证件号">
          <el-input v-model="query.cardNo" placeholder="请输入证件号" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="持有人">
          <el-input v-model="query.holder" placeholder="请输入持有人" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.cardType" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="t in cardTypeOptions" :key="t" :label="t" :value="t" />
          </el-select>
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
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增出入证</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="cardNo" label="证件号" min-width="110" />
        <el-table-column prop="holder" label="持有人" min-width="100" />
        <el-table-column prop="phone" label="电话" min-width="130" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag>{{ row.cardType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="validStart" label="有效期起" width="120" />
        <el-table-column prop="validEnd" label="有效期止" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusMap[row.status] || '未知' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-popconfirm v-if="row.status === 1" title="确认挂失?" @confirm="loss(row.id)">
              <template #reference><el-button link type="warning">挂失</el-button></template>
            </el-popconfirm>
            <el-popconfirm v-if="row.status !== 3" title="确认注销?" @confirm="cancelCard(row.id)">
              <template #reference><el-button link type="info">注销</el-button></template>
            </el-popconfirm>
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
        <el-form-item label="证件号" prop="cardNo"><el-input v-model="form.cardNo" /></el-form-item>
        <el-form-item label="持有人" prop="holder"><el-input v-model="form.holder" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="类型" prop="cardType">
          <el-select v-model="form.cardType" style="width: 100%">
            <el-option v-for="t in cardTypeOptions" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="有效期起">
          <el-date-picker v-model="form.validStart" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="有效期止">
          <el-date-picker v-model="form.validEnd" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
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
import { passCardApi } from '@/api/service'

const cardTypeOptions = ['员工', '临时', '车辆']

// 1有效 2已挂失 3已注销
const statusMap = { 1: '有效', 2: '已挂失', 3: '已注销' }

function statusTagType(status) {
  const map = { 1: 'success', 2: 'warning', 3: 'info' }
  return map[status] || 'info'
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, cardNo: '', holder: '', cardType: '', status: null })

async function load() {
  loading.value = true
  try {
    const res = await passCardApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
function reset() {
  Object.assign(query, { pageNo: 1, cardNo: '', holder: '', cardType: '', status: null })
  load()
}

async function loss(id) {
  await passCardApi.loss(id)
  ElMessage.success('已挂失')
  load()
}
async function cancelCard(id) {
  await passCardApi.cancel(id)
  ElMessage.success('已注销')
  load()
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const defaultForm = () => ({ id: null, cardNo: '', holder: '', phone: '', cardType: '员工', validStart: null, validEnd: null })
const form = reactive(defaultForm())
const rules = {
  cardNo: [{ required: true, message: '请输入证件号', trigger: 'blur' }],
  holder: [{ required: true, message: '请输入持有人', trigger: 'blur' }],
  cardType: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑出入证' : '新增出入证'
  if (row) Object.assign(form, row)
  else Object.assign(form, defaultForm())
}
async function submit() {
  await formRef.value.validate()
  if (form.id) await passCardApi.update(form)
  else await passCardApi.add(form)
  ElMessage.success('保存成功')
  dialog.visible = false
  load()
}
async function remove(id) {
  await passCardApi.remove(id)
  ElMessage.success('删除成功')
  load()
}

onMounted(load)
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
.toolbar { margin-bottom: 12px; }
</style>
