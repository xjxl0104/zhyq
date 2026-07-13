<template>
  <div class="page-container">
    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="抬头">
          <el-input v-model="query.title" placeholder="请输入发票抬头" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="(s, k) in statusMap" :key="k" :label="s.label" :value="Number(k)" />
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
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增发票</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="title" label="发票抬头" min-width="160" show-overflow-tooltip />
        <el-table-column prop="taxNo" label="税号" min-width="160" />
        <el-table-column label="金额" min-width="110" align="right">
          <template #default="{ row }">¥{{ row.amount }}</template>
        </el-table-column>
        <el-table-column prop="invoiceType" label="类型" width="90" />
        <el-table-column prop="billId" label="关联账单ID" width="110" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status]?.type || 'info'">
              {{ statusMap[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm v-if="row.status === 1" title="确认审核通过?" @confirm="changeStatus(row, 2)">
              <template #reference><el-button link type="primary">审核通过</el-button></template>
            </el-popconfirm>
            <el-popconfirm v-if="row.status === 2" title="确认开票?" @confirm="changeStatus(row, 3)">
              <template #reference><el-button link type="success">开票</el-button></template>
            </el-popconfirm>
            <el-popconfirm v-if="row.status === 3" title="确认红冲该发票?" @confirm="changeStatus(row, 4)">
              <template #reference><el-button link type="danger">红冲</el-button></template>
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
        <el-form-item label="发票抬头" prop="title"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="税号"><el-input v-model="form.taxNo" /></el-form-item>
        <el-form-item label="金额" prop="amount">
          <el-input-number v-model="form.amount" :min="0" :precision="2" style="width: 200px" />
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="form.invoiceType">
            <el-radio value="普票">普票</el-radio>
            <el-radio value="专票">专票</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="关联账单ID"><el-input v-model="form.billId" placeholder="账单ID(可选)" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
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
import { invoiceApi } from '@/api/finance'

const statusMap = {
  1: { label: '申请中', type: 'warning' },
  2: { label: '已审核', type: 'primary' },
  3: { label: '已开票', type: 'success' },
  4: { label: '已红冲', type: 'danger' },
  5: { label: '已作废', type: 'info' }
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, title: '', status: null })

async function load() {
  loading.value = true
  try {
    const res = await invoiceApi.page(query)
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
const blank = { id: null, title: '', taxNo: '', amount: 0, invoiceType: '普票', billId: null, remark: '', status: 1 }
const form = reactive({ ...blank })
const rules = {
  title: [{ required: true, message: '请输入发票抬头', trigger: 'blur' }],
  amount: [{ required: true, message: '请输入金额', trigger: 'blur' }]
}

function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑发票' : '新增发票'
  if (row) Object.assign(form, row)
  else Object.assign(form, blank)
}
async function submit() {
  await formRef.value.validate()
  if (form.id) await invoiceApi.update(form)
  else await invoiceApi.add(form)
  ElMessage.success('保存成功')
  dialog.visible = false
  load()
}
async function changeStatus(row, status) {
  await invoiceApi.update({ id: row.id, status })
  ElMessage.success('操作成功')
  load()
}

onMounted(load)
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
