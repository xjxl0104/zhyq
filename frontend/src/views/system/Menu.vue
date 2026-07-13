<template>
  <div class="page-container">
    <div class="table-card">
      <div class="toolbar">
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增菜单</el-button>
      </div>
      <el-table :data="tree" v-loading="loading" row-key="id" border
                :tree-props="{ children: 'children' }" default-expand-all>
        <el-table-column prop="name" label="菜单名称" min-width="200" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="typeMap[row.type]?.color || 'info'">{{ typeMap[row.type]?.label || row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="path" label="路由路径" min-width="160" />
        <el-table-column label="图标" width="90" align="center">
          <template #default="{ row }">
            <el-icon v-if="row.icon"><component :is="row.icon" /></el-icon>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="perm" label="权限标识" min-width="150" />
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.type !== 3" link type="primary" @click="openDialog(null, row.id)">新增下级</el-button>
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除?" @confirm="remove(row.id)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog.visible" :title="dialog.title" width="540px">
      <el-form :model="form" label-width="90px" ref="formRef" :rules="rules">
        <el-form-item label="上级菜单">
          <el-tree-select v-model="form.parentId" :data="selectTree" check-strictly
                          :props="{ label: 'name', value: 'id' }" style="width: 100%" placeholder="顶级菜单(0)" />
        </el-form-item>
        <el-form-item label="菜单类型">
          <el-radio-group v-model="form.type">
            <el-radio :value="1">目录</el-radio>
            <el-radio :value="2">菜单</el-radio>
            <el-radio :value="3">按钮</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="菜单名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item v-if="form.type !== 3" label="路由路径">
          <el-input v-model="form.path" placeholder="如 /system/user" />
        </el-form-item>
        <el-form-item v-if="form.type === 2" label="前端组件">
          <el-input v-model="form.component" placeholder="如 system/User" />
        </el-form-item>
        <el-form-item v-if="form.type !== 1" label="权限标识">
          <el-input v-model="form.perm" placeholder="如 system:user:add" />
        </el-form-item>
        <el-form-item v-if="form.type !== 3" label="图标">
          <el-input v-model="form.icon" placeholder="Element Plus 图标名,如 Setting" />
        </el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" /></el-form-item>
        <el-form-item v-if="form.type !== 3" label="是否可见">
          <el-radio-group v-model="form.visible">
            <el-radio :value="1">显示</el-radio><el-radio :value="0">隐藏</el-radio>
          </el-radio-group>
        </el-form-item>
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
import { menuApi } from '@/api/system'

const typeMap = {
  1: { label: '目录', color: 'warning' },
  2: { label: '菜单', color: 'primary' },
  3: { label: '按钮', color: 'info' }
}

const loading = ref(false)
const flat = ref([])
const tree = computed(() => buildTree(flat.value, 0))
const selectTree = computed(() => [{ id: 0, name: '顶级菜单', children: buildTree(flat.value.filter(x => x.type !== 3), 0) }])

function buildTree(arr, pid) {
  return arr.filter(x => x.parentId === pid).map(x => {
    const children = buildTree(arr, x.id)
    return children.length ? { ...x, children } : { ...x }
  })
}

async function load() {
  loading.value = true
  try { flat.value = await menuApi.list() } finally { loading.value = false }
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const emptyForm = { id: null, parentId: 0, name: '', type: 1, path: '', component: '', perm: '', icon: '', sort: 0, visible: 1, status: 1 }
const form = reactive({ ...emptyForm })
const rules = { name: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }] }

function openDialog(row, parentId) {
  dialog.visible = true
  dialog.title = row ? '编辑菜单' : '新增菜单'
  if (row) Object.assign(form, emptyForm, row)
  else Object.assign(form, emptyForm, { parentId: parentId ?? 0 })
}
async function submit() {
  await formRef.value.validate()
  form.id ? await menuApi.update(form) : await menuApi.add(form)
  ElMessage.success('保存成功'); dialog.visible = false; load()
}
async function remove(id) { await menuApi.remove(id); ElMessage.success('删除成功'); load() }
onMounted(load)
</script>
