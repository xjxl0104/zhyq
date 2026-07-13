<template>
  <div class="page-container">
    <div class="table-card setting-card">
      <div class="card-head">
        <div class="card-title">财务设置</div>
        <el-button type="primary" :loading="saving" @click="saveAll">
          <el-icon><Check /></el-icon>保存全部
        </el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="skey" label="配置键" width="220" />
        <el-table-column label="配置值" min-width="220">
          <template #default="{ row }">
            <el-input v-model="row.svalue" placeholder="配置值" />
          </template>
        </el-table-column>
        <el-table-column label="说明" min-width="240">
          <template #default="{ row }">
            <el-input v-model="row.remark" placeholder="说明" />
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!list.length && !loading" class="empty-tip">暂无财务配置项</div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { settingApi } from '@/api/finance'

const MODULE = 'finance'
const loading = ref(false)
const saving = ref(false)
const list = ref([])

async function load() {
  loading.value = true
  try {
    list.value = await settingApi.list(MODULE) || []
  } finally {
    loading.value = false
  }
}

async function saveAll() {
  saving.value = true
  try {
    await settingApi.batch(list.value)
    ElMessage.success('保存成功')
    load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.setting-card { max-width: 900px; }
.card-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.card-title { font-size: 15px; font-weight: 650; color: var(--text-title); }
.empty-tip { text-align: center; color: var(--text-secondary); padding: 30px 0; }
</style>
