<template>
  <div class="page-container">
    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="分类">
          <el-input v-model="query.category" placeholder="请输入分类" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 130px">
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
        <el-button type="primary" @click="openAdd()"><el-icon><Plus /></el-icon>新增资产</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="assetNo" label="资产编号" min-width="150" />
        <el-table-column prop="name" label="资产名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="category" label="分类" width="110" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status]?.type || 'info'">
              {{ statusMap[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="holder" label="持有人" width="110" />
        <el-table-column prop="spaceId" label="所在空间" width="100" />
        <el-table-column label="购置价" width="120" align="right">
          <template #default="{ row }">
            {{ row.price != null ? `¥${Number(row.price).toLocaleString()}` : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <template v-if="row.status === 1">
              <el-button link type="warning" @click="doCheckout(row)">签出</el-button>
              <el-button link type="info" @click="doRepair(row)">报修</el-button>
            </template>
            <el-button v-if="row.status === 2" link type="success" @click="doCheckin(row)">签入</el-button>
            <el-button v-if="row.status === 3" link type="success" @click="doRepairDone(row)">维修完成</el-button>
            <el-button v-if="row.status !== 4" link type="primary" @click="doInventory(row)">盘点</el-button>
            <el-popconfirm v-if="row.status !== 4" title="确认报废该资产?" @confirm="doScrap(row)">
              <template #reference><el-button link type="danger">报废</el-button></template>
            </el-popconfirm>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                     :total="total" v-model:current-page="query.pageNo"
                     v-model:page-size="query.pageSize" :page-sizes="[10,20,50]" @change="load" />
    </div>

    <!-- 表单弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px">
      <el-form :model="form" label-width="90px" ref="formRef" :rules="rules">
        <el-form-item label="资产编号" prop="assetNo"><el-input v-model="form.assetNo" /></el-form-item>
        <el-form-item label="资产名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="分类"><el-input v-model="form.category" /></el-form-item>
        <el-form-item label="所在空间">
          <el-input-number v-model="form.spaceId" :min="0" :controls="false" style="width: 200px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">在库</el-radio>
            <el-radio :value="2">领用中</el-radio>
            <el-radio :value="3">维修</el-radio>
            <el-radio :value="4">报废</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="持有人"><el-input v-model="form.holder" /></el-form-item>
        <el-form-item label="购置日期">
          <el-date-picker v-model="form.purchaseDate" type="date" value-format="YYYY-MM-DD" style="width: 200px" />
        </el-form-item>
        <el-form-item label="购置价">
          <el-input-number v-model="form.price" :min="0" :precision="2" :step="100" style="width: 200px" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="附件">
          <FileUpload v-model="attachFiles" biz-type="asset" :biz-id="form.id" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { assetApi } from '@/api/am'
import { fileApi } from '@/api/file'
import FileUpload from '@/components/FileUpload.vue'
import { useCrudPage } from '@/composables/useCrudPage'

const formRef = ref()
const blank = () => ({
  id: null, assetNo: '', name: '', category: '', spaceId: null,
  status: 1, holder: '', purchaseDate: '', price: null, remark: ''
})
const rules = {
  assetNo: [{ required: true, message: '请输入资产编号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入资产名称', trigger: 'blur' }]
}

const statusMap = {
  1: { label: '在库', type: 'success' },
  2: { label: '领用中', type: 'warning' },
  3: { label: '维修', type: 'info' },
  4: { label: '报废', type: 'danger' }
}

const {
  loading, list, total, query, load, reset,
  dialogVisible, dialogTitle, form,
  openAdd: crudOpenAdd, openEdit: crudOpenEdit, remove
} = useCrudPage(assetApi, {
  defaultQuery: { category: '', status: null },
  emptyForm: blank,
  titles: { add: '新增资产', edit: '编辑资产' },
  beforeSubmit: () => formRef.value.validate()
})

const attachFiles = ref([])

function openAdd() {
  crudOpenAdd()
  attachFiles.value = []
}
async function openEdit(row) {
  crudOpenEdit(row)
  attachFiles.value = []
  try { attachFiles.value = await fileApi.list('asset', row.id) } catch (e) { /* 忽略 */ }
}
async function submit() {
  await formRef.value.validate()
  let newId = form.id
  if (form.id) await assetApi.update(form)
  else newId = await assetApi.add(form)
  const pendingIds = (attachFiles.value || []).filter(f => f && f.id && !f.bizId).map(f => f.id)
  if (newId && pendingIds.length) {
    try { await fileApi.attach('asset', newId, pendingIds) } catch (e) { /* 忽略,不阻断保存 */ }
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  await load()
}

async function doCheckout(row) {
  const { value: holder } = await ElMessageBox.prompt('请输入领用人', '签出', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputValue: row.holder || ''
  }).catch(() => ({ value: undefined }))
  if (holder === undefined) return
  await assetApi.checkout(row.id, holder)
  ElMessage.success('签出成功')
  await load()
}

async function doCheckin(row) {
  await assetApi.checkin(row.id)
  ElMessage.success('签入成功')
  await load()
}

async function doRepair(row) {
  await assetApi.repair(row.id)
  ElMessage.success('已送修')
  await load()
}

async function doRepairDone(row) {
  await assetApi.repairDone(row.id)
  ElMessage.success('维修完成')
  await load()
}

async function doScrap(row) {
  await assetApi.scrap(row.id)
  ElMessage.success('报废成功')
  await load()
}

async function doInventory(row) {
  const { value: spaceId } = await ElMessageBox.prompt('请输入所在空间 ID', '盘点', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputValue: row.spaceId != null ? String(row.spaceId) : ''
  }).catch(() => ({ value: undefined }))
  if (spaceId === undefined) return
  await assetApi.inventory(row.id, spaceId)
  ElMessage.success('盘点成功')
  await load()
}

onMounted(() => load())
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
