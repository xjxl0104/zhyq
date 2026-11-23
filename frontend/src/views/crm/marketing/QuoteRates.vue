<template>
  <el-card shadow="never">
    <el-form inline @submit.prevent="search">
      <el-form-item label="加盟云仓"><el-input v-model="keyword" clearable placeholder="名称或编码" @clear="search" /></el-form-item>
      <el-button native-type="submit" type="primary">查询</el-button>
    </el-form>
    <el-alert v-if="failure" type="error" :title="failure" :closable="false" />
    <el-table v-loading="loading" :data="rows" empty-text="当前园区没有符合条件的已上线云仓">
      <el-table-column prop="code" label="编码" />
      <el-table-column prop="name" label="云仓名称" />
      <el-table-column prop="region" label="区域" />
      <el-table-column label="全国发货报价"><template #default="{row}"><el-button link type="primary" @click="selected = row">查看价卡</el-button></template></el-table-column>
    </el-table>
    <el-pagination v-if="total > pageSize" :current-page="page" :page-size="pageSize" :total="total" layout="prev,pager,next,total" @current-change="changePage" />
    <el-drawer :model-value="!!selected" size="min(800px, 96vw)" :title="selected?.name + ' · 报价体系'" @close="selected=null" destroy-on-close>
      <QuoteCardEditor v-if="selected" :key="projectId + '/' + selected.id" :warehouse-id="selected.id" :project-id="projectId" />
    </el-drawer>
  </el-card>
</template>
<script setup>
import { computed, ref, watch } from 'vue'
import { mktWarehouseApi } from '@/api/marketing'
import { useProjectStore } from '@/stores/project'
import QuoteCardEditor from './QuoteCardEditor.vue'

const project = useProjectStore()
const projectId = computed(() => project.currentProjectId)
const keyword = ref(''), rows = ref([]), total = ref(0), page = ref(1), pageSize = 20
const loading = ref(false), selected = ref(null), failure = ref('')
let sequence = 0
async function load() {
  const ticket = ++sequence
  failure.value = ''
  rows.value = []
  total.value = 0
  loading.value = !!projectId.value
  if (!projectId.value) return
  try {
    const result = await mktWarehouseApi.page({projectId:projectId.value, keyword:keyword.value.trim(), pageNo:page.value, pageSize, joinStatus:5})
    if (ticket === sequence) { rows.value = result.records; total.value = result.total }
  } catch (error) {
    if (ticket === sequence) failure.value = error.message || '读取加盟云仓失败'
  } finally { if (ticket === sequence) loading.value = false }
}
function search() { page.value = 1; load() }
function changePage(value) { page.value = value; load() }
watch(projectId, () => { selected.value = null; search() }, {immediate:true})
</script>
