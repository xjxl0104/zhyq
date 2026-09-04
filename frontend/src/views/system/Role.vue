<template>
  <div class="page-container">
    <div class="table-card">
      <div class="toolbar">
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增角色</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column prop="name" label="角色名称" min-width="180">
          <template #default="{ row }">
            <span>{{ row.name }}</span>
            <el-tag v-if="isProtected(row)" type="danger" size="small" class="protected-tag">系统保护</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="code" label="角色编码" min-width="140" />
        <el-table-column label="数据范围" width="150">
          <template #default="{ row }">{{ scopeText(row.dataScope) }}</template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="success" :disabled="isProtected(row)" @click="openPermission(row)">配置权限</el-button>
            <el-button link type="primary" :disabled="isProtected(row)" @click="openDialog(row)">编辑</el-button>
            <template v-if="isProtected(row)">
              <el-button link type="danger" disabled>删除</el-button>
            </template>
            <el-popconfirm v-else title="确认删除?" @confirm="remove(row.id)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next"
                     :total="total" v-model:current-page="query.pageNo"
                     v-model:page-size="query.pageSize" @change="load" />
    </div>

    <el-dialog v-model="dialog.visible" :title="dialog.title" width="500px">
      <el-form :model="form" label-width="90px" ref="formRef" :rules="rules">
        <el-form-item label="角色名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="角色编码" prop="code"><el-input v-model="form.code" /></el-form-item>
        <el-form-item label="数据范围">
          <el-select v-model="form.dataScope" style="width: 100%">
            <el-option label="全部数据" :value="1" />
            <el-option label="本部门及下级" :value="2" />
            <el-option label="本部门" :value="3" />
            <el-option label="仅本人" :value="4" />
          </el-select>
        </el-form-item>
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

    <el-dialog v-model="permission.visible" :title="`配置权限：${permission.roleName}`" width="680px">
      <div class="permission-toolbar">
        <span class="permission-tip">勾选菜单和按钮权限；保存后，相关用户重新登录即可生效。</span>
        <div>
          <el-button link type="primary" @click="selectAllPermissions">全选</el-button>
          <el-button link @click="clearPermissions">清空</el-button>
        </div>
      </div>
      <div v-loading="permission.loading" class="permission-tree-wrap">
        <el-tree ref="permissionTreeRef" :data="menuTree" node-key="id" show-checkbox
                 check-strictly default-expand-all
                 :props="{ label: 'name', children: 'children', disabled: 'disabled' }">
          <template #default="{ data }">
            <div class="permission-node">
              <span>{{ data.name }}</span>
              <el-tag size="small" :type="menuTypeMap[data.type]?.color || 'info'">
                {{ menuTypeMap[data.type]?.label || '未知' }}
              </el-tag>
              <span v-if="data.perm" class="permission-code">{{ data.perm }}</span>
              <el-tag v-if="data.status !== 1" size="small" type="info">已停用</el-tag>
            </div>
          </template>
        </el-tree>
      </div>
      <template #footer>
        <el-button @click="permission.visible = false">取消</el-button>
        <el-button type="primary" :loading="permission.saving" @click="savePermissions">保存权限</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { menuApi, roleApi } from '@/api/system'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10 })
const scopeMap = { 1: '全部数据', 2: '本部门及下级', 3: '本部门', 4: '仅本人' }
const scopeText = (v) => scopeMap[v] || '-'
const isProtected = (role) => role?.code === 'admin'

async function load() {
  loading.value = true
  try {
    const res = await roleApi.page(query)
    list.value = res.records; total.value = res.total
  } finally { loading.value = false }
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const form = reactive({ id: null, name: '', code: '', dataScope: 1, sort: 0, status: 1 })
const rules = {
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入角色编码', trigger: 'blur' }]
}
function openDialog(row) {
  if (isProtected(row)) return
  dialog.visible = true
  dialog.title = row ? '编辑角色' : '新增角色'
  if (row) Object.assign(form, row)
  else Object.assign(form, { id: null, name: '', code: '', dataScope: 1, sort: 0, status: 1 })
}
async function submit() {
  await formRef.value.validate()
  form.id ? await roleApi.update(form) : await roleApi.add(form)
  ElMessage.success('保存成功'); dialog.visible = false; load()
}
async function remove(id) { await roleApi.remove(id); ElMessage.success('删除成功'); load() }

const menuTypeMap = {
  1: { label: '目录', color: 'warning' },
  2: { label: '菜单', color: 'primary' },
  3: { label: '按钮', color: 'info' }
}
const menuFlat = ref([])
const permissionTreeRef = ref()
const permission = reactive({
  visible: false,
  loading: false,
  saving: false,
  roleId: null,
  roleName: ''
})
const menuTree = computed(() => buildMenuTree(menuFlat.value, 0))
const enabledMenuIds = computed(() => menuFlat.value.filter(menu => menu.status === 1).map(menu => menu.id))

function buildMenuTree(menus, parentId) {
  return menus.filter(menu => menu.parentId === parentId).map(menu => {
    const children = buildMenuTree(menus, menu.id)
    const node = { ...menu, disabled: menu.status !== 1 }
    return children.length ? { ...node, children } : node
  })
}

async function openPermission(role) {
  if (isProtected(role)) return
  permission.visible = true
  permission.loading = true
  permission.roleId = role.id
  permission.roleName = role.name
  try {
    const [menus, selectedIds] = await Promise.all([menuApi.list(), roleApi.menuIds(role.id)])
    menuFlat.value = menus
    await nextTick()
    permissionTreeRef.value?.setCheckedKeys(selectedIds || [], false)
  } finally {
    permission.loading = false
  }
}

function selectAllPermissions() {
  permissionTreeRef.value?.setCheckedKeys(enabledMenuIds.value, false)
}

function clearPermissions() {
  permissionTreeRef.value?.setCheckedKeys([], false)
}

async function savePermissions() {
  const enabled = new Set(enabledMenuIds.value)
  const menuIds = (permissionTreeRef.value?.getCheckedKeys(false) || []).filter(id => enabled.has(id))
  permission.saving = true
  try {
    await roleApi.saveMenuIds(permission.roleId, menuIds)
    ElMessage.success('角色权限保存成功')
    permission.visible = false
  } finally {
    permission.saving = false
  }
}
onMounted(load)
</script>
<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
.protected-tag { margin-left: 8px; }
.permission-toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.permission-tip { color: var(--el-text-color-secondary); font-size: 13px; }
.permission-tree-wrap { min-height: 280px; max-height: 520px; padding: 12px; overflow: auto; border: 1px solid var(--el-border-color); border-radius: 6px; }
.permission-node { display: flex; align-items: center; gap: 8px; }
.permission-code { color: var(--el-text-color-secondary); font-family: monospace; font-size: 12px; }
</style>
