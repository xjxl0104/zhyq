<template>
  <div class="page-container">
    <!-- 统计卡片 -->
    <div class="stat-row">
      <div class="stat-card">
        <div class="stat-label">已退租合同数</div>
        <div class="stat-value">{{ stats.contractCount || 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">保证金合计</div>
        <div class="stat-value warning">¥{{ money(stats.depositTotal) }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">租金实收合计</div>
        <div class="stat-value success">¥{{ money(stats.rentPaidTotal) }}</div>
      </div>
    </div>

    <!-- 表格区 -->
    <div class="table-card">
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column prop="code" label="合同编号" min-width="160" />
        <!-- 租客列显示名字而不是裸 id:账单口径的登记明细名优先,档案名兜底(后端拼好),
             兜底文案与收银台同一份 tenantOptionLabel -->
        <el-table-column label="租客" min-width="160">
          <template #default="{ row }">{{ tenantOptionLabel(row) }}</template>
        </el-table-column>
        <el-table-column prop="terminateDate" label="退租日期" width="130" />
        <el-table-column label="租金应收" width="140" align="right">
          <template #default="{ row }">¥{{ money(row.rentTotal) }}</template>
        </el-table-column>
        <el-table-column label="租金实收" width="140" align="right">
          <template #default="{ row }"><span class="paid">¥{{ money(row.rentPaid) }}</span></template>
        </el-table-column>
        <el-table-column label="保证金" width="140" align="right">
          <template #default="{ row }">¥{{ money(row.deposit) }}</template>
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
import { checkoutApi } from '@/api/finance'
import { tenantOptionLabel } from './cashierModel'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const stats = ref({})
const query = reactive({ pageNo: 1, pageSize: 10 })

function money(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function load() {
  loading.value = true
  try {
    const res = await checkoutApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
async function loadStats() {
  stats.value = await checkoutApi.stats() || {}
}

onMounted(() => { load(); loadStats() })
</script>

<style scoped>
.stat-row { display: flex; gap: 16px; margin-bottom: 16px; }
.stat-row .stat-card {
  flex: 1; background: var(--bg-card); border-radius: var(--radius);
  border: 1px solid var(--border); padding: 20px 22px;
  transition: border-color .18s, transform .18s;
}
.stat-row .stat-card:hover { border-color: var(--border-strong); transform: translateY(-1px); }
.stat-label { color: var(--text-secondary); font-size: 13px; margin-bottom: 8px; }
.stat-value {
  font-size: 25px; font-weight: 650; color: var(--text-title);
  letter-spacing: -0.5px; font-variant-numeric: tabular-nums;
}
.stat-value.success { color: #16a34a; }
.stat-value.warning { color: #ea9a13; }
.paid { color: #16a34a; font-weight: 600; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
