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
      <p class="hint">改参数只影响之后的计佣/锁定;已生成的流水与锁定不受影响。岗位数(ladder_depth)请在「岗位与份额」页切换,那里有合规提示与二次确认。</p>
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
async function load() { loading.value = true; try { list.value = await mktSettingApi.all() } finally { loading.value = false } }
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
