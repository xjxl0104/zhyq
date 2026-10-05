<template>
  <div class="page-container">
    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="账号">
          <el-input v-model="query.username" placeholder="请输入账号" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="query.nickname" placeholder="请输入昵称" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="正常" :value="1" />
            <el-option label="停用" :value="0" />
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
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增用户</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column prop="username" label="账号" min-width="120" />
        <el-table-column prop="nickname" label="昵称" min-width="120" />
        <el-table-column prop="phone" label="手机号" min-width="130" />
        <el-table-column prop="email" label="邮箱" min-width="160" />
        <el-table-column label="角色" min-width="190">
          <template #default="{ row }">
            <div v-if="row.roleNames?.length" class="role-list">
              <el-tag v-for="name in row.roleNames" :key="name"
                      :type="name === '平台超级管理员' ? 'danger' : 'primary'">
                {{ name }}
              </el-tag>
            </div>
            <span v-else class="muted">未分配</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
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
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="760px" top="5vh">
      <el-form v-loading="dialog.loading" :model="form" label-width="90px" ref="formRef" :rules="rules">
        <el-form-item label="账号" prop="username">
          <el-input v-model="form.username" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password
                    :placeholder="form.id ? '留空则不修改密码' : '请设置初始密码'" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname"><el-input v-model="form.nickname" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
        <el-form-item label="角色权限">
          <el-select v-model="form.roleIds" multiple collapse-tags collapse-tags-tooltip
                     placeholder="先选择岗位角色" style="width: 100%">
            <el-option v-for="role in roles" :key="role.id" :value="role.id"
                       :label="role.code === 'admin' ? `${role.name}（超级管理员）` : role.name" />
          </el-select>
          <div class="form-tip">角色自动赋予 {{ inheritedMenuIds.length }} 项权限；调整角色时，用户权限会同步更新。</div>
        </el-form-item>
        <el-form-item label="额外授权" prop="menuIds">
          <div class="menu-tree-container">
            <div class="menu-tree-toolbar">
              <el-input v-model="permissionKeyword" placeholder="搜索模块或操作" clearable style="width: 220px" />
              <el-button link type="primary" size="small" @click="selectAllMenus">全选</el-button>
              <el-button link size="small" @click="clearMenus">清空</el-button>
            </div>
            <el-scrollbar max-height="320px">
              <el-tree ref="menuTreeRef" :data="permissionTree" node-key="id" show-checkbox
                       :filter-node-method="filterPermission"
                       :props="{ label: 'name', children: 'children' }">
                <template #default="{ data }">
                  <span :class="{ 'locked-menu': isLockedMenu(data.id) }">
                    {{ data.name }}
                    <el-tag v-if="isLockedMenu(data.id)" size="small" type="info" class="lock-tag">默认</el-tag>
                    <el-tag v-else-if="inheritedMenuIds.includes(data.id)" size="small" type="success" class="lock-tag">角色已授予</el-tag>
                  </span>
                </template>
              </el-tree>
            </el-scrollbar>
          </div>
          <div class="form-tip">按业务模块展开设置额外权限。角色已授予的权限无需重复勾选；要收回角色权限，请调整上方角色或角色配置。“建议与反馈”默认开放。</div>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">正常</el-radio>
            <el-radio :value="0">停用</el-radio>
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
import { computed, nextTick, reactive, ref, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { userApi, roleApi, menuApi } from '@/api/system'
import { buildPermissionTree, filterPermission } from '@/utils/permissionTree'

const loading = ref(false)
const list = ref([])
const roles = ref([])
const menuFlat = ref([])
const permissionKeyword = ref('')
const inheritedMenuIds = ref([])
let roleRequest = 0
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, username: '', nickname: '', status: null })

// "建议与反馈" locked menu IDs (resolved from menuFlat)
const lockedMenuIds = computed(() => {
  const suggestion = menuFlat.value.find(m => m.name === '建议与反馈' && m.parentId === 0)
  if (!suggestion) return []
  const ids = [suggestion.id]
  menuFlat.value.filter(m => m.parentId === suggestion.id).forEach(m => ids.push(m.id))
  return ids
})

function isLockedMenu(id) {
  return lockedMenuIds.value.includes(id)
}

const permissionTree = computed(() => {
  const markLocked = nodes => nodes.map(node => ({
    ...node, disabled: isLockedMenu(node.id),
    ...(node.children ? { children: markLocked(node.children) } : {})
  }))
  return markLocked(buildPermissionTree(menuFlat.value))
})

async function load() {
  loading.value = true
  try {
    const res = await userApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
function reset() {
  Object.assign(query, { pageNo: 1, username: '', nickname: '', status: null })
  load()
}

const formRef = ref()
const menuTreeRef = ref()
const dialog = reactive({ visible: false, title: '', loading: false })
const emptyForm = {
  id: null, username: '', password: '', nickname: '', phone: '', email: '', status: 1, roleIds: [], menuIds: []
}
const form = reactive({ ...emptyForm })
watch(() => [...form.roleIds], async (ids) => {
  const request = ++roleRequest
  try {
    const menus = await Promise.all(ids.map(id => roleApi.menuIds(id)))
    if (request === roleRequest) inheritedMenuIds.value = [...new Set(menus.flat())]
  } catch {
    if (request === roleRequest) inheritedMenuIds.value = []
  }
})
watch(permissionKeyword, value => menuTreeRef.value?.filter(value))
const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{
    validator: (rule, value, callback) => {
      if (!form.id && !value) callback(new Error('请设置初始密码'))
      else if (value && value.length < 6) callback(new Error('密码至少 6 位'))
      else callback()
    }, trigger: 'blur'
  }],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }]
}

async function loadRoles() {
  roles.value = await roleApi.list()
}

async function loadMenus() {
  menuFlat.value = await menuApi.list()
}

function selectAllMenus() {
  const allIds = menuFlat.value.filter(m => m.status === 1).map(m => m.id)
  menuTreeRef.value?.setCheckedKeys(allIds, false)
}

function clearMenus() {
  // Keep locked menus checked
  menuTreeRef.value?.setCheckedKeys(lockedMenuIds.value, false)
}

async function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑用户' : '新增用户'
  formRef.value?.clearValidate()
  Object.assign(form, emptyForm, { roleIds: [], menuIds: [] })
  permissionKeyword.value = ''
  await nextTick()
  if (!row) {
    // New user: default check locked menus
    menuTreeRef.value?.setCheckedKeys(lockedMenuIds.value, false)
    return
  }
  dialog.loading = true
  try {
    const detail = await userApi.get(row.id)
    Object.assign(form, detail.user, { password: '', roleIds: detail.roleIds || [], menuIds: detail.menuIds || [] })
    await nextTick()
    const checkedIds = [...new Set([...detail.menuIds || [], ...lockedMenuIds.value])]
    menuTreeRef.value?.setCheckedKeys(checkedIds, false)
  } finally {
    dialog.loading = false
  }
}

async function submit() {
  await formRef.value.validate()
  // Collect checked menu IDs from tree (ensure locked menus are included)
  const validIds = new Set(menuFlat.value.filter(menu => menu.status === 1).map(menu => menu.id))
  const treeChecked = (menuTreeRef.value?.getCheckedKeys(false) || []).filter(id => validIds.has(id))
  const finalMenuIds = [...new Set([...treeChecked, ...lockedMenuIds.value])]
  form.menuIds = finalMenuIds
  if (form.id) await userApi.update(form)
  else await userApi.add(form)
  ElMessage.success('保存成功')
  dialog.visible = false
  load()
}

async function remove(id) {
  await userApi.remove(id)
  ElMessage.success('删除成功')
  load()
}

onMounted(() => Promise.all([load(), loadRoles(), loadMenus()]))
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
.role-list { display: flex; flex-wrap: wrap; gap: 6px; }
.muted { color: var(--el-text-color-secondary); }
.admin-tag { margin-left: 8px; }
.form-tip { margin-top: 6px; color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.4; }
.menu-tree-container {
  width: 100%;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  padding: 8px;
}
.menu-tree-toolbar {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-bottom: 8px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.locked-menu { color: var(--el-text-color-secondary); }
.lock-tag { margin-left: 6px; }
</style>
