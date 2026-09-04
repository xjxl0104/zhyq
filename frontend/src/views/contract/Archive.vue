<template>
  <div class="page-container">
    <!-- 合同档案库:所有合同都在册,不只是走完生命周期的。
         执行中的合同也要能查到,否则新签约的一批在这里永远是空白 -->
    <el-tabs v-model="activeTab" @tab-change="onTabChange" class="archive-tabs">
      <el-tab-pane label="全部" name="all" />
      <el-tab-pane label="在租中" name="running" />
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
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column prop="code" label="合同编号" min-width="160" />
        <!-- 与合同列表同口径:后端已填好 tenantName,页面上不再露裸 ID -->
        <el-table-column prop="tenantName" label="租客" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.tenantName || `租客 #${row.tenantRefId ?? '-'}` }}</template>
        </el-table-column>
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
            <!-- 与后端守卫同口径:仅已到期(8)/已终止(9)可归档,执行中的不给按钮 -->
            <el-popconfirm v-if="[8, 9].includes(row.status)" title="确认归档该合同?" @confirm="archive(row.id)">
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

// null = 不按状态过滤(全部)。档案库要能查到所有合同,不只走完生命周期的那些
const tabStatus = { all: null, running: 5, expired: 8, terminated: 9, archived: 10 }
const activeTab = ref('all')

const statusMap = {
  1: { label: '草稿', type: 'info' },
  2: { label: '待审核', type: 'warning' },
  3: { label: '待签署', type: 'warning' },
  4: { label: '待执行', type: 'warning' },
  5: { label: '在租中', type: 'success' },
  6: { label: '变更中', type: 'warning' },
  7: { label: '退租中', type: 'warning' },
  8: { label: '已到期', type: 'warning' },
  9: { label: '已终止', type: 'danger' },
  10: { label: '已归档', type: 'info' }
}
function statusText(v) { return statusMap[v]?.label ?? v }
function statusType(v) { return statusMap[v]?.type ?? 'info' }

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
