<template>
  <div class="page-container">
    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="收据号">
          <el-input v-model="query.receiptNo" placeholder="请输入收据号" clearable style="width: 200px" />
        </el-form-item>
        <!-- 按租客翻收据是日常最常用的查法。后端 ReceiptController.page 早就支持
             tenantRefId,只是前端一直没接;下拉用租客档案全量,结清的租客也要能查到 -->
        <el-form-item label="对方租客">
          <el-select v-model="query.tenantRefId" placeholder="全部" clearable filterable style="width: 220px">
            <el-option v-for="t in tenants" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 表格区 -->
    <div class="table-card">
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column prop="receiptNo" label="收据号" min-width="170" />
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
        <el-table-column label="金额" width="140" align="right">
          <template #default="{ row }">¥{{ money(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="payee" label="收款人" width="120" />
        <el-table-column prop="printCount" label="打印次数" width="100" align="center" />
        <el-table-column prop="lastPrintTime" label="最后打印时间" width="180" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-popconfirm title="确认打印该收据?" @confirm="print(row.id)">
              <template #reference><el-button link type="primary">打印</el-button></template>
            </el-popconfirm>
            <el-button link type="info" @click="openLogs(row)">打印日志</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                     :total="total" v-model:current-page="query.pageNo"
                     v-model:page-size="query.pageSize" :page-sizes="[10,20,50]" @change="load" />
    </div>

    <!-- 打印日志抽屉 -->
    <el-drawer v-model="drawer.visible" :title="`打印日志 - ${drawer.receiptNo}`" size="480px">
      <el-table :data="logs" v-loading="drawer.loading" border stripe>
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column prop="operator" label="操作人" min-width="120" />
        <el-table-column prop="printTime" label="打印时间" min-width="180" />
      </el-table>
      <div v-if="!logs.length && !drawer.loading" class="empty-tip">暂无打印记录</div>
    </el-drawer>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { receiptApi } from '@/api/finance'
import { tenantApi } from '@/api/tenant'


const router = useRouter()

// 联动:从流水/收据/发票/通知点进所有账单页并定位到那一张账单。
// 走 query 而不是弹窗,用户可以在账单页继续做收款/开票等后续动作。
function gotoBill(billId) {
  router.push({ path: '/finance/bill', query: { billId } })
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const EMPTY_QUERY = { receiptNo: '', tenantRefId: null }
const query = reactive({ pageNo: 1, pageSize: 10, ...EMPTY_QUERY })
const tenants = ref([])

function money(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function load() {
  loading.value = true
  try {
    const res = await receiptApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
function reset() {
  Object.assign(query, { pageNo: 1, ...EMPTY_QUERY })
  load()
}

async function print(id) {
  await receiptApi.print(id)
  ElMessage.success('打印成功')
  load()
}

const drawer = reactive({ visible: false, receiptNo: '', loading: false })
const logs = ref([])
async function openLogs(row) {
  drawer.visible = true
  drawer.receiptNo = row.receiptNo
  drawer.loading = true
  logs.value = []
  try {
    logs.value = await receiptApi.logs(row.id) || []
  } finally {
    drawer.loading = false
  }
}

// 查询回第 1 页,否则换条件后停在旧页码多半是一屏空白
function search() {
  query.pageNo = 1
  return load()
}

onMounted(async () => {
  // 租客名册取不到只是少一个下拉,不该拖垮收据列表
  const [tenantList] = await Promise.allSettled([tenantApi.list(), load()])
  if (tenantList.status === 'fulfilled') tenants.value = tenantList.value || []
})
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
.empty-tip { text-align: center; color: var(--text-secondary); padding: 30px 0; }
</style>
