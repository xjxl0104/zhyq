<template>
  <div class="page-container">
    <div class="table-card">
      <div class="toolbar">
        <span class="title">规则参数(biz_setting · module = marketing)</span>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border>
        <el-table-column prop="skey" label="参数" width="220" />
        <el-table-column label="值" width="220">
          <template #default="{ row }"><el-input v-model="row.svalue" size="small" /></template>
        </el-table-column>
        <el-table-column prop="remark" label="说明" min-width="300" />
      </el-table>
      <p class="hint">云仓按单佣金由 P4 在小程序按客户 / 品牌自定义，可在「客户管理 › 按单佣金」查看。此处管理业务参数，已生成流水与锁定记录不受影响。</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { mktSettingApi } from '@/api/marketing'

const loading = ref(false)
const saving = ref(false)
const list = ref([])
async function load() { loading.value = true; try { list.value = (await mktSettingApi.all()).filter(row => row.skey !== 'ladder_depth') } finally { loading.value = false } }
async function save() {
  saving.value = true
  try { await mktSettingApi.update(list.value.map(r => ({ skey: r.skey, svalue: r.svalue }))); ElMessage.success('已保存'); load() } finally { saving.value = false }
}
onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; }
.title { font-weight: 600; margin-right: auto; }
.hint { color: var(--el-text-color-secondary); font-size: 12px; margin-top: 12px; }
</style>
