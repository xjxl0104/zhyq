<template>
  <div class="page-container">
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="动作"><el-input v-model="query.action" placeholder="如 commission.void" clearable style="width: 200px" /></el-form-item>
        <el-form-item label="对象类型"><el-input v-model="query.bizType" placeholder="promoter / service_contract …" clearable style="width: 200px" /></el-form-item>
        <el-form-item label="对象 ID"><el-input-number v-model="query.bizId" :min="1" controls-position="right" style="width: 130px" /></el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>
    <div class="table-card">
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="createTime" label="时间" width="160" />
        <el-table-column prop="operator" label="操作人" width="110" />
        <el-table-column prop="action" label="动作" width="200" />
        <el-table-column prop="bizType" label="对象" width="140" />
        <el-table-column prop="bizId" label="ID" width="80" />
        <el-table-column prop="reason" label="原因 / 说明" min-width="220" />
        <el-table-column label="变更" width="90">
          <template #default="{ row }">
            <el-button v-if="row.beforeJson || row.afterJson" link type="primary" @click="show(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                     :total="total" v-model:current-page="query.pageNo"
                     v-model:page-size="query.pageSize" :page-sizes="[20,50,100]" @change="load" />
    </div>
    <el-dialog v-model="diff.visible" title="变更前后" width="720px">
      <el-row :gutter="12">
        <el-col :span="12"><b>之前</b><pre class="json">{{ diff.before }}</pre></el-col>
        <el-col :span="12"><b>之后</b><pre class="json">{{ diff.after }}</pre></el-col>
      </el-row>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { mktAuditApi } from '@/api/marketing'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 20, action: '', bizType: '', bizId: null })
async function load() {
  loading.value = true
  try { const res = await mktAuditApi.page(query); list.value = res.records; total.value = res.total } finally { loading.value = false }
}
function search() { query.pageNo = 1; load() }
function reset() { Object.assign(query, { pageNo: 1, action: '', bizType: '', bizId: null }); load() }
const diff = reactive({ visible: false, before: '', after: '' })
const pretty = (s) => { try { return JSON.stringify(JSON.parse(s), null, 2) } catch (e) { return s || '' } }
function show(row) { diff.before = pretty(row.beforeJson); diff.after = pretty(row.afterJson); diff.visible = true }
onMounted(load)
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
.json { background: var(--el-fill-color-light); padding: 8px; border-radius: 4px; font-size: 12px; max-height: 400px; overflow: auto; }
</style>
