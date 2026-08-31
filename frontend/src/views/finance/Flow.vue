<template>
  <div class="page-container">
    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="方向">
          <el-select v-model="query.direction" placeholder="全部" clearable style="width: 120px">
            <el-option label="收入" :value="1" />
            <el-option label="支出" :value="2" />
          </el-select>
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
        <el-table-column prop="flowNo" label="流水号" min-width="160" />
        <el-table-column label="方向" width="90">
          <template #default="{ row }">
            <el-tag :type="row.direction === 1 ? 'success' : 'danger'">
              {{ row.direction === 1 ? '收入' : '支出' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额" min-width="120" align="right">
          <template #default="{ row }">¥{{ row.amount }}</template>
        </el-table-column>
        <!-- 联动:后端按登记明细口径填好租客名与账单号,点账单号跳到所有账单页定位该单 -->
        <el-table-column prop="tenantName" label="对方租客" min-width="160">
          <template #default="{ row }">{{ row.tenantName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="billCode" label="关联账单" min-width="160">
          <template #default="{ row }">
            <el-button v-if="row.billId" link type="primary" @click="gotoBill(row.billId)">
              {{ row.billCode || `#${row.billId}` }}
            </el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="feeType" label="费用类型" width="100">
          <template #default="{ row }">{{ row.feeType || '-' }}</template>
        </el-table-column>
        <el-table-column label="匹配状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.matchStatus === 1 ? 'success' : 'warning'">
              {{ row.matchStatus === 1 ? '已匹配' : '未匹配' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="flowTime" label="流水时间" width="170" />
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                     :total="total" v-model:current-page="query.pageNo"
                     v-model:page-size="query.pageSize" :page-sizes="[10,20,50]" @change="load" />
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { flowApi } from '@/api/finance'


const router = useRouter()

// 联动:从流水/收据/发票/通知点进所有账单页并定位到那一张账单。
// 走 query 而不是弹窗,用户可以在账单页继续做收款/开票等后续动作。
function gotoBill(billId) {
  router.push({ path: '/finance/bill', query: { billId } })
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, direction: null })

async function load() {
  loading.value = true
  try {
    const res = await flowApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
function reset() {
  Object.assign(query, { pageNo: 1, direction: null })
  load()
}

onMounted(load)
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
