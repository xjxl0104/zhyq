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
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="min(980px, 96vw)" top="5vh" destroy-on-close>
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
                     placeholder="先选择角色" style="width: 100%">
            <el-option v-for="role in roles" :key="role.id" :value="role.id"
                       :label="role.code === 'admin' ? `${role.name}（超级管理员）` : role.name" />
          </el-select>
          <div class="form-tip">角色自动赋予 {{ inheritedMenuIds.length }} 项权限；调整角色时，用户权限会同步更新。</div>
        </el-form-item>
        <el-form-item label="额外授权" prop="menuIds">
          <PermissionSelector v-model="form.menuIds" :menus="menuFlat"
                              :locked-ids="lockedMenuIds" :inherited-ids="inheritedMenuIds" />
          <div class="form-tip">按左侧 01–10 目录添加额外权限。绿色标签表示角色已授予，无需重复勾选；要收回角色权限，请调整角色配置。“我的建议”默认开放。</div>
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
        <el-button type="primary" :disabled="dialog.loading || !dialog.ready || !menusReady" @click="submit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, reactive, ref, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { userApi, roleApi, menuApi } from '@/api/system'
import PermissionSelector from '@/components/PermissionSelector.vue'
import { normalizePermissionSelection } from '@/utils/permissionTree'

const loading = ref(false)
const list = ref([])
const roles = ref([])
const menuFlat = ref([])
const menusReady = ref(false)
const inheritedMenuIds = ref([])
let roleRequest = 0
let userRequest = 0
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, username: '', nickname: '', status: null })

// Only personal feedback is public; management remains an explicit grant.
const lockedMenuIds = computed(() => menuFlat.value.filter(menu =>
  menu.status === 1 && /^(\/)?suggestion\/mine$/.test(menu.path || '')
).map(menu => menu.id))

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
const dialog = reactive({ visible: false, title: '', loading: false, ready: false })
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
  menusReady.value = true
}

async function openDialog(row) {
  const request = ++userRequest
  dialog.loading = false
  dialog.visible = true
  dialog.ready = false
  dialog.title = row ? '编辑用户' : '新增用户'
  formRef.value?.clearValidate()
  Object.assign(form, emptyForm, { roleIds: [], menuIds: [] })
  await nextTick()
  if (!row) {
    // New user: default check locked menus
    form.menuIds = [...lockedMenuIds.value]
    dialog.ready = true
    return
  }
  dialog.loading = true
  try {
    const detail = await userApi.get(row.id)
    if (request !== userRequest) return
    Object.assign(form, detail.user, { password: '', roleIds: detail.roleIds || [], menuIds: detail.menuIds || [] })
    await nextTick()
    const checkedIds = [...new Set([...detail.menuIds || [], ...lockedMenuIds.value])]
    form.menuIds = checkedIds
    dialog.ready = true
  } finally {
    if (request === userRequest) dialog.loading = false
  }
}

async function submit() {
  if (dialog.loading || !dialog.ready || !menusReady.value) return
  await formRef.value.validate()
  form.menuIds = normalizePermissionSelection(menuFlat.value, [...form.menuIds, ...lockedMenuIds.value])
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
</style>
