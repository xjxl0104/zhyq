<template>
  <div class="page-container">
    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="所属项目">
          <el-select v-model="query.projectId" placeholder="全部项目" clearable style="width: 180px" @change="load">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="楼宇名称">
          <el-input v-model="query.name" placeholder="请输入楼宇名称" clearable style="width: 180px" />
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
        <el-button type="primary" @click="openAdd()"><el-icon><Plus /></el-icon>新增楼宇</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column prop="code" label="楼宇编码" min-width="110" />
        <el-table-column prop="name" label="楼宇名称" min-width="140" />
        <el-table-column label="所属项目" min-width="150">
          <template #default="{ row }">{{ projectMap[row.projectId] || row.projectId }}</template>
        </el-table-column>
        <el-table-column prop="floorCount" label="层数" width="80" align="center" />
        <el-table-column label="建筑面积" min-width="110" align="right">
          <template #default="{ row }">{{ row.buildArea }}㎡</template>
        </el-table-column>
        <el-table-column prop="usageType" label="用途" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '运营中' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
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
        <el-form-item label="所属项目" prop="projectId">
          <el-select v-model="form.projectId" placeholder="请选择项目" style="width: 100%">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="楼宇编码" prop="code"><el-input v-model="form.code" /></el-form-item>
        <el-form-item label="楼宇名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="层数">
          <el-input-number v-model="form.floorCount" :min="1" style="width: 200px" />
        </el-form-item>
        <el-form-item label="建筑面积">
          <el-input-number v-model="form.buildArea" :min="0" :precision="2" style="width: 200px" />
          <span style="margin-left: 6px">㎡</span>
        </el-form-item>
        <el-form-item label="用途">
          <el-select v-model="form.usageType" placeholder="请选择用途" style="width: 200px">
            <el-option label="办公" value="办公" />
            <el-option label="商业" value="商业" />
            <el-option label="厂房" value="厂房" />
            <el-option label="仓储" value="仓储" />
            <el-option label="综合" value="综合" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">运营中</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
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
import { ref, computed, onMounted } from 'vue'
import { buildingApi, projectApi } from '@/api/building'
import { useCrudPage } from '@/composables/useCrudPage'

const projects = ref([])
const projectMap = computed(() => Object.fromEntries(projects.value.map((p) => [p.id, p.name])))

const formRef = ref()
const blank = () => ({ id: null, projectId: null, code: '', name: '', floorCount: 1, buildArea: 0, usageType: '办公', status: 1, sort: 0 })
const rules = {
  projectId: [{ required: true, message: '请选择所属项目', trigger: 'change' }],
  code: [{ required: true, message: '请输入楼宇编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入楼宇名称', trigger: 'blur' }]
}

const {
  loading, list, total, query, load, reset,
  dialogVisible, dialogTitle, form,
  openAdd, openEdit, submit, remove
} = useCrudPage(buildingApi, {
  defaultQuery: { projectId: null, name: '' },
  emptyForm: blank,
  titles: { add: '新增楼宇', edit: '编辑楼宇' },
  beforeSubmit: () => formRef.value.validate()
})

onMounted(async () => {
  projects.value = await projectApi.list()
  load()
})
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
