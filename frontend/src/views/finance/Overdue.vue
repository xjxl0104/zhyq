<template>
  <div class="page-container">
    <div class="table-card">
      <div class="toolbar">
        <span class="title">逾期账单</span>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="code" label="账单号" min-width="150" />
        <el-table-column prop="tenantRefId" label="对方租客" width="100" />
        <el-table-column prop="feeType" label="费用类型" width="100" />
        <el-table-column label="应收" width="120" align="right">
          <template #default="{ row }">¥{{ money(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="实收" width="120" align="right">
          <template #default="{ row }">¥{{ money(row.paidAmount) }}</template>
        </el-table-column>
        <el-table-column label="滞纳金" width="120" align="right">
          <template #default="{ row }"><span style="color:#e5484d">¥{{ money(row.lateFee) }}</span></template>
        </el-table-column>
        <el-table-column prop="dueDate" label="应收日" width="120" />
        <el-table-column label="逾期天数" width="110" align="center">
          <template #default="{ row }">
            <el-tag type="danger" effect="dark">{{ row.overdueDays || 0 }} 天</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status]?.type || 'info'">
              {{ statusMap[row.status]?.label || row.status }}
            </el-tag>
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
import { billApi } from '@/api/finance'

const statusMap = {
  1: { label: '草稿', type: 'info' },
  2: { label: '待审核', type: 'warning' },
  3: { label: '待收付', type: 'primary' },
  4: { label: '部分结清', type: 'warning' },
  5: { label: '已结清', type: 'success' },
  6: { label: '逾期', type: 'danger' },
  7: { label: '退款中', type: 'warning' },
  8: { label: '作废', type: 'info' }
}

function money(v) {
  if (v == null) return '0.00'
  return Number(v).toFixed(2)
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10 })

async function load() {
  loading.value = true
  try {
    const res = await billApi.overdue(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.title { font-size: 15px; font-weight: 600; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
