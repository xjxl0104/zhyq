<template>
  <div class="page-container">
    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="流水号">
          <el-input v-model="query.flowNo" placeholder="支持模糊匹配" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="对方租客">
          <el-select v-model="query.tenantRefId" placeholder="全部" clearable filterable style="width: 200px">
            <el-option v-for="t in tenants" :key="t.tenantRefId"
                       :label="t.tenantName || `租客 #${t.tenantRefId}`" :value="t.tenantRefId" />
          </el-select>
        </el-form-item>
        <el-form-item label="方向">
          <el-select v-model="query.direction" placeholder="全部" clearable style="width: 120px">
            <el-option label="收入" :value="1" />
            <el-option label="支出" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="匹配状态">
          <el-select v-model="query.matchStatus" placeholder="全部" clearable style="width: 120px">
            <el-option label="已匹配" :value="1" />
            <el-option label="未匹配" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="流水时间">
          <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD"
                          range-separator="至" start-placeholder="开始日" end-placeholder="结束日"
                          style="width: 260px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
      <!-- 当前生效的筛选一目了然:原先点「重置」什么都不显示变化,用户以为按钮坏了。
           两个分支必须走 v-if/v-else:把 v-for 夹在两个独立 v-if 中间时,清空瞬间
           Vue 会漏掉 v-for 片段的卸载,重置后旧标签还挂在那(实测踩到) -->
      <div class="filter-summary">
        <template v-if="activeFilters.length">
          <span>当前筛选：</span>
          <el-tag v-for="f in activeFilters" :key="f" size="small" type="info" effect="plain" class="filter-tag">{{ f }}</el-tag>
        </template>
        <span v-else class="filter-none">未设置筛选条件，显示全部流水</span>
      </div>
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
          <!-- 撤销收款走会计红冲:同为收入方向、金额取负。标红提醒这是一笔退回 -->
          <template #default="{ row }">
            <span :class="{ reversal: Number(row.amount) < 0 }">¥{{ row.amount }}</span>
          </template>
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
import { computed, reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { billApi, flowApi } from '@/api/finance'


const router = useRouter()

// 联动:从流水/收据/发票/通知点进所有账单页并定位到那一张账单。
// 走 query 而不是弹窗,用户可以在账单页继续做收款/开票等后续动作。
function gotoBill(billId) {
  router.push({ path: '/finance/bill', query: { billId } })
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const tenants = ref([])
const dateRange = ref(null)
const EMPTY_QUERY = { flowNo: '', tenantRefId: null, direction: null, matchStatus: null }
const query = reactive({ pageNo: 1, pageSize: 10, ...EMPTY_QUERY })

const directionLabel = { 1: '收入', 2: '支出' }
const matchLabel = { 1: '已匹配', 0: '未匹配' }
// 重置到底清掉了什么,得让用户看见 —— 这正是「点重置没反应」的根源
const activeFilters = computed(() => {
  const items = []
  if (query.flowNo) items.push(`流水号 ${query.flowNo}`)
  if (query.tenantRefId != null) {
    const hit = tenants.value.find(t => t.tenantRefId === query.tenantRefId)
    items.push(`租客 ${hit?.tenantName || '#' + query.tenantRefId}`)
  }
  if (query.direction != null) items.push(directionLabel[query.direction])
  if (query.matchStatus != null) items.push(matchLabel[query.matchStatus])
  if (dateRange.value?.length === 2) items.push(`${dateRange.value[0]} ~ ${dateRange.value[1]}`)
  return items
})

async function load() {
  loading.value = true
  try {
    const res = await flowApi.page({
      ...query,
      startDate: dateRange.value?.[0] || undefined,
      endDate: dateRange.value?.[1] || undefined
    })
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

// 查询永远从第 1 页开始:留在第 3 页查新条件,大概率是一屏空白
function search() {
  query.pageNo = 1
  return load()
}

function reset() {
  const hadFilters = activeFilters.value.length > 0
  Object.assign(query, { pageNo: 1, ...EMPTY_QUERY })
  dateRange.value = null
  // 没有反馈的重置和坏掉的按钮长得一模一样,明确说一句
  ElMessage.success(hadFilters ? '已清空筛选条件，显示全部流水' : '当前没有筛选条件，已刷新列表')
  return load()
}

onMounted(async () => {
  // 租客下拉复用收银台那份「全部租客」,口径与账单页一致;取不到不该拖垮流水列表
  const [tenantResult] = await Promise.allSettled([billApi.payableTenants(), load()])
  if (tenantResult.status === 'fulfilled') tenants.value = tenantResult.value || []
})
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
.filter-summary { margin-top: 4px; font-size: 12px; color: var(--el-text-color-secondary); }
.filter-tag { margin-right: 6px; }
.filter-none { color: var(--el-text-color-placeholder); }
/* 红冲流水金额为负,标红区分于正常收入 */
.reversal { color: #e5484d; font-weight: 600; }
</style>
