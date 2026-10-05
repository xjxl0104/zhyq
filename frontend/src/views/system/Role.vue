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

    <el-dialog v-model="permission.visible" :title="`配置权限：${permission.roleName}`" width="min(900px, 96vw)" destroy-on-close>
      <p class="permission-tip">按左侧 01–10 目录配置权限。保存后，相关用户重新登录即可生效。</p>
      <div v-loading="permission.loading">
        <PermissionSelector v-model="selectedMenuIds" :menus="menuFlat" />
      </div>
      <template #footer>
        <el-button @click="permission.visible = false">取消</el-button>
        <el-button type="primary" :loading="permission.saving" :disabled="permission.loading || !permission.ready" @click="savePermissions">保存权限</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { menuApi, roleApi } from '@/api/system'
import PermissionSelector from '@/components/PermissionSelector.vue'
import { normalizePermissionSelection } from '@/utils/permissionTree'

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

let permissionRequest = 0
const menuFlat = ref([])
const selectedMenuIds = ref([])
const permission = reactive({
  visible: false,
  loading: false,
  ready: false,
  saving: false,
  roleId: null,
  roleName: ''
})
async function openPermission(role) {
  if (isProtected(role)) return
  const request = ++permissionRequest
  selectedMenuIds.value = []
  permission.visible = true
  permission.loading = true
  permission.ready = false
  permission.roleId = role.id
  permission.roleName = role.name
  try {
    const [menus, selectedIds] = await Promise.all([menuApi.list(), roleApi.menuIds(role.id)])
    if (request !== permissionRequest) return
    menuFlat.value = menus
    selectedMenuIds.value = normalizePermissionSelection(menus, selectedIds || [])
    permission.ready = true
  } finally {
    if (request === permissionRequest) permission.loading = false
  }
}

async function savePermissions() {
  if (permission.loading || !permission.ready || permission.saving) return
  const menuIds = normalizePermissionSelection(menuFlat.value, selectedMenuIds.value)
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
.permission-tip { color: var(--el-text-color-secondary); font-size: 13px; margin-bottom: 12px; }
</style>
