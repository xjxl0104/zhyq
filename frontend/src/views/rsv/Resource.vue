<template>
  <div class="page-container">
    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="资源类型">
          <el-select v-model="query.type" placeholder="全部类型" clearable style="width: 160px" @change="load">
            <el-option label="会议室" value="MEETING" />
            <el-option label="场地" value="SITE" />
            <el-option label="工位" value="DESK" />
          </el-select>
        </el-form-item>
        <el-form-item label="资源名称">
          <el-input v-model="query.name" placeholder="请输入资源名称" clearable style="width: 180px" />
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
        <el-button type="primary" @click="openAdd()"><el-icon><Plus /></el-icon>新增资源</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="55" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">{{ typeLabel(row.type) }}</template>
        </el-table-column>
        <el-table-column prop="name" label="资源名称" min-width="160" />
        <el-table-column prop="capacity" label="容量" width="90" align="center" />
        <el-table-column label="单价/小时" width="110" align="right">
          <template #default="{ row }">{{ row.pricePerHour != null ? `¥${row.pricePerHour}` : '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '可用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
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
        <el-form-item label="资源类型" prop="type">
          <el-select v-model="form.type" placeholder="请选择资源类型" style="width: 100%">
            <el-option label="会议室" value="MEETING" />
            <el-option label="场地" value="SITE" />
            <el-option label="工位" value="DESK" />
          </el-select>
        </el-form-item>
        <el-form-item label="资源名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="容量">
          <el-input-number v-model="form.capacity" :min="0" style="width: 200px" />
        </el-form-item>
        <el-form-item label="单价/小时">
          <el-input-number v-model="form.pricePerHour" :min="0" :precision="2" style="width: 200px" />
          <span style="margin-left: 6px">元</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">可用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
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
import { resourceApi } from '@/api/rsv'
import { useCrudPage } from '@/composables/useCrudPage'

const formRef = ref()
const blank = () => ({ id: null, type: 'MEETING', name: '', capacity: null, pricePerHour: null, status: 1, remark: '' })
const rules = {
  type: [{ required: true, message: '请选择资源类型', trigger: 'change' }],
  name: [{ required: true, message: '请输入资源名称', trigger: 'blur' }]
}

const typeLabel = (type) => ({ MEETING: '会议室', SITE: '场地', DESK: '工位' }[type] || type)

const {
  loading, list, total, query, load, reset,
  dialogVisible, dialogTitle, form,
  openAdd, openEdit, submit, remove
} = useCrudPage(resourceApi, {
  defaultQuery: { type: null, name: '' },
  emptyForm: blank,
  titles: { add: '新增资源', edit: '编辑资源' },
  beforeSubmit: () => formRef.value.validate()
})

onMounted(() => load())
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
