<template>
  <div class="page-container">
    <div class="table-card">
      <div class="toolbar">
        <span class="title">逾期账单</span>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column prop="code" label="账单号" min-width="150" />
        <!-- 与所有账单页同一口径:后端已按登记明细优先填好 tenantName。
             这里原先渲染的是 tenantRefId,界面上就是一个裸数字"2",看不出是谁 -->
        <el-table-column prop="tenantName" label="对方租客" min-width="160">
          <template #default="{ row }">{{ row.tenantName || `租客 #${row.tenantRefId || '-'}` }}</template>
        </el-table-column>
        <el-table-column prop="agreementNo" label="协议编号" min-width="140">
          <template #default="{ row }">{{ row.agreementNo || '-' }}</template>
        </el-table-column>
        <el-table-column prop="feeType" label="费用类型" width="100" />
        <el-table-column label="应收" width="120" align="right">
          <template #default="{ row }">¥{{ money(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="实收" width="120" align="right">
          <template #default="{ row }">¥{{ money(row.paidAmount) }}</template>
        </el-table-column>
        <el-table-column label="滞纳金" width="150" align="right">
          <template #default="{ row }">
            <span style="color:#e5484d">¥{{ money(row.lateFee) }}</span>
            <!-- 人工调整过的要一眼可辨:它不参与每日自动重算,数字与系统口径可能不同 -->
            <el-tooltip v-if="row.lateFeeManual === 1" :content="row.lateFeeRemark || '已人工调整，不参与自动重算'">
              <el-tag type="warning" size="small" effect="plain" class="manual-tag">人工</el-tag>
            </el-tooltip>
          </template>
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
        <el-table-column v-if="canAdjust" label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openAdjust(row)">调整滞纳金</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                     :total="total" v-model:current-page="query.pageNo"
                     v-model:page-size="query.pageSize" :page-sizes="[10,20,50]" @change="load" />
    </div>
    <LateFeeAdjustDialog v-model="adjustVisible" :bill="adjustBill" @saved="load" />
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { billApi } from '@/api/finance'
import LateFeeAdjustDialog from './components/LateFeeAdjustDialog.vue'

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
const adjustVisible = ref(false)
const adjustBill = ref(null)
// 与后端 @PreAuthorize('finance:bill:lateFee:adjust') 同一权限点,没权限的不显示入口
const canAdjust = ref(false)
function openAdjust(row) { adjustBill.value = row; adjustVisible.value = true }

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

onMounted(async () => {
  const [caps] = await Promise.allSettled([billApi.capabilities(), load()])
  canAdjust.value = caps.status === 'fulfilled' && !!caps.value?.lateFeeAdjust
})
</script>

<style scoped>
.title { font-size: 15px; font-weight: 600; }
.manual-tag { margin-left: 6px; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
