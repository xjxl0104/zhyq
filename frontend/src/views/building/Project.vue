<template>
  <div class="page-container">
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="项目名称">
          <el-input v-model="query.name" placeholder="请输入" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="运营中" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="table-card">
      <div class="toolbar">
        <el-button type="primary" @click="openAdd()"><el-icon><Plus /></el-icon>新建项目</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column prop="code" label="项目编码" width="110" />
        <el-table-column prop="name" label="项目名称" min-width="160" />
        <el-table-column prop="type" label="类型" width="110" />
        <el-table-column prop="city" label="城市" width="100" />
        <el-table-column label="管理面积" width="120">
          <template #default="{ row }">{{ row.manageArea }} ㎡</template>
        </el-table-column>
        <el-table-column prop="manager" label="负责人" width="100" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '运营中' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next"
                     :total="total" v-model:current-page="query.pageNo"
                     v-model:page-size="query.pageSize" @change="load" />
    </div>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px">
      <el-form :model="form" label-width="90px" ref="formRef" :rules="rules">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="项目编码" prop="code"><el-input v-model="form.code" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="项目名称" prop="name"><el-input v-model="form.name" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="类型">
            <el-select v-model="form.type" style="width: 100%">
              <el-option label="产业园" value="产业园" /><el-option label="写字楼" value="写字楼" />
              <el-option label="公寓" value="公寓" /><el-option label="商业综合体" value="商业综合体" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="城市"><el-input v-model="form.city" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="地址"><el-input v-model="form.address" /></el-form-item>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="管理面积"><el-input-number v-model="form.manageArea" :min="0" :precision="2" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="建筑面积"><el-input-number v-model="form.buildArea" :min="0" :precision="2" style="width:100%" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="负责人"><el-input v-model="form.manager" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="状态">
            <el-radio-group v-model="form.status"><el-radio :value="1">运营中</el-radio><el-radio :value="0">停用</el-radio></el-radio-group>
          </el-form-item></el-col>
        </el-row>
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
import { projectApi } from '@/api/building'
import { useCrudPage } from '@/composables/useCrudPage'

const formRef = ref()
const blank = () => ({ id: null, code: '', name: '', type: '产业园', city: '', address: '', manageArea: 0, buildArea: 0, manager: '', status: 1 })
const rules = {
  code: [{ required: true, message: '请输入项目编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入项目名称', trigger: 'blur' }]
}

const {
  loading, list, total, query, load, reset,
  dialogVisible, dialogTitle, form,
  openAdd, openEdit, submit, remove
} = useCrudPage(projectApi, {
  defaultQuery: { name: '', status: null },
  emptyForm: blank,
  titles: { add: '新建项目', edit: '编辑项目' },
  beforeSubmit: () => formRef.value.validate()
})

onMounted(load)
</script>
<style scoped>.pager { margin-top: 16px; justify-content: flex-end; }</style>
