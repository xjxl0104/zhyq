<template>
  <div class="page-container">
    <el-tabs v-model="activeTab" @tab-change="onTabChange" class="archive-tabs">
      <el-tab-pane label="已到期" name="expired" />
      <el-tab-pane label="已终止" name="terminated" />
      <el-tab-pane label="已归档" name="archived" />
    </el-tabs>

    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="合同编号">
          <el-input v-model="query.code" placeholder="请输入合同编号" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 表格区 -->
    <div class="table-card">
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="code" label="合同编号" min-width="160" />
        <el-table-column prop="tenantRefId" label="租客ID" width="100" />
        <el-table-column label="起止日期" width="220">
          <template #default="{ row }">{{ row.startDate || '-' }} ~ {{ row.endDate || '-' }}</template>
        </el-table-column>
        <el-table-column prop="terminateDate" label="退租时间" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-popconfirm v-if="activeTab !== 'archived'" title="确认归档该合同?" @confirm="archive(row.id)">
              <template #reference><el-button link type="primary">归档</el-button></template>
            </el-popconfirm>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                     :total="total" v-model:current-page="query.pageNo"
                     v-model:page-size="query.pageSize" :page-sizes="[10,20,50]" @change="load" />
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { contractApi } from '@/api/contract'

const tabStatus = { expired: 8, terminated: 9, archived: 10 }
const activeTab = ref('expired')

function statusText(v) {
  if (v === 8) return '已到期'
  if (v === 9) return '已终止'
  if (v === 10) return '已归档'
  return v
}
function statusType(v) {
  if (v === 8) return 'warning'
  if (v === 9) return 'danger'
  if (v === 10) return 'info'
  return 'info'
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, code: '', status: tabStatus[activeTab.value] })

async function load() {
  loading.value = true
  try {
    const res = await contractApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
function onTabChange(name) {
  query.pageNo = 1
  query.status = tabStatus[name]
  load()
}
function reset() {
  Object.assign(query, { pageNo: 1, code: '', status: tabStatus[activeTab.value] })
  load()
}

async function archive(id) {
  await contractApi.archive(id)
  ElMessage.success('归档成功')
  load()
}

onMounted(load)
</script>

<style scoped>
.archive-tabs { margin-bottom: 8px; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
