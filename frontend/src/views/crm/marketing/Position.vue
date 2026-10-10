<template>
  <div class="page-container mkt-page">
    <div class="table-card">
      <div class="toolbar">
        <span class="title">角色称号与锁定上限</span>
        <el-button type="primary" @click="save" :loading="saving">保存设置</el-button>
      </div>
      <p class="rule-note">P4 拥有定价权；P1–P4 的按单金额均由 P4 按客户 / 品牌自定义，称号不绑定固定金额或比例。</p>
      <el-table :data="list" v-loading="loading" border>
        <el-table-column prop="code" label="角色" width="80" />
        <el-table-column label="称号" min-width="140">
          <template #default="{ row }"><el-input v-model="row.name" :aria-label="`${row.code} 称号`" size="small" /></template>
        </el-table-column>
        <el-table-column label="按单定价权限" width="150">
          <template #default="{ row }"><el-tag :type="row.code === 'P4' ? 'success' : 'info'">{{ row.code === 'P4' ? '可自定义金额' : '由 P4 设置金额' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="锁定客户上限（含预锁）" width="220">
          <template #default="{ row }"><el-input-number v-model="row.lockCap" :min="0" :max="2147483647" :step="1" :precision="0" :aria-label="`${row.code} 锁定客户上限`" size="small" controls-position="right" /></template>
        </el-table-column>
      </el-table>
      <p class="hint">自动晋升与降级已停用。P4 可在小程序中手动设置邀请体系内成员的 P1–P4 角色称号；设为 P4 将授予对应客户的定价权。实际每单金额在「客户管理 › 按单佣金」查看。</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { mktPositionApi } from '@/api/marketing'

const loading = ref(false)
const saving = ref(false)
const list = ref([])

async function load() {
  loading.value = true
  try {
    list.value = await mktPositionApi.list()
  } finally {
    loading.value = false
  }
}

function validate() {
  for (const p of list.value) {
    if (!p.name?.trim()) return `请填写 ${p.code} 的称号`
    if (!Number.isInteger(p.lockCap) || p.lockCap < 0 || p.lockCap > 2147483647) return `${p.code} 的锁定客户上限须为有效的非负整数`
  }
  return null
}

async function save() {
  const err = validate()
  if (err) return ElMessage.error(err)
  saving.value = true
  try {
    await mktPositionApi.update(list.value.map(({ id, name, lockCap }) => ({ id, name: name.trim(), lockCap })))
    ElMessage.success('已保存')
    load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.title { font-weight: 600; margin-right: auto; }
.rule-note { margin: 0 0 16px; color: var(--el-text-color-regular); font-size: 13px; line-height: 1.7; }
.hint { color: var(--el-text-color-secondary); font-size: 12px; margin-top: 12px; }
</style>
