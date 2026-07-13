<template>
  <div class="page-container">
    <div class="table-card">
      <div class="toolbar">
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增部门</el-button>
        <el-button @click="expandAll">展开/折叠</el-button>
      </div>
      <el-table :data="tree" v-loading="loading" row-key="id" border
                :tree-props="{ children: 'children' }" ref="tableRef" default-expand-all>
        <el-table-column prop="name" label="部门名称" min-width="200" />
        <el-table-column prop="leader" label="负责人" width="120" />
        <el-table-column prop="phone" label="联系电话" width="140" />
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(null, row.id)">新增下级</el-button>
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除?" @confirm="remove(row.id)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog.visible" :title="dialog.title" width="500px">
      <el-form :model="form" label-width="90px" ref="formRef" :rules="rules">
        <el-form-item label="上级部门">
          <el-tree-select v-model="form.parentId" :data="selectTree" check-strictly
                          :props="{ label: 'name', value: 'id' }" style="width: 100%" placeholder="顶级部门(0)" />
        </el-form-item>
        <el-form-item label="部门名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="负责人"><el-input v-model="form.leader" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" /></el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio><el-radio :value="0">停用</el-radio>
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
import { reactive, ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { deptApi } from '@/api/system'

const loading = ref(false)
const flat = ref([])
const tree = computed(() => buildTree(flat.value, 0))
const selectTree = computed(() => [{ id: 0, name: '顶级部门', children: buildTree(flat.value, 0) }])

function buildTree(arr, pid) {
  return arr.filter(x => x.parentId === pid).map(x => {
    const children = buildTree(arr, x.id)
    return children.length ? { ...x, children } : { ...x }
  })
}

async function load() {
  loading.value = true
  try { flat.value = await deptApi.list() } finally { loading.value = false }
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const form = reactive({ id: null, parentId: 0, name: '', leader: '', phone: '', sort: 0, status: 1 })
const rules = { name: [{ required: true, message: '请输入部门名称', trigger: 'blur' }] }

function openDialog(row, parentId) {
  dialog.visible = true
  dialog.title = row ? '编辑部门' : '新增部门'
  if (row) Object.assign(form, row)
  else Object.assign(form, { id: null, parentId: parentId ?? 0, name: '', leader: '', phone: '', sort: 0, status: 1 })
}
async function submit() {
  await formRef.value.validate()
  form.id ? await deptApi.update(form) : await deptApi.add(form)
  ElMessage.success('保存成功'); dialog.visible = false; load()
}
async function remove(id) { await deptApi.remove(id); ElMessage.success('删除成功'); load() }
function expandAll() {}
onMounted(load)
</script>
