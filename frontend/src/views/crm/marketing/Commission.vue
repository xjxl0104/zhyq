<template>
  <div class="page-container">
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="伙伴 ID"><el-input-number v-model="query.promoterId" :min="1" controls-position="right" style="width: 140px" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="(t, v) in ST" :key="v" :label="t" :value="Number(v)" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="table-card">
      <div class="toolbar">
        <el-button type="primary" :disabled="!settleable.length" @click="settle">批量结算(已选 {{ settleable.length }} 条,合计 {{ settleSum }})</el-button>
        <el-button @click="batches.visible = true; loadBatches()">结算批次</el-button>
        <span class="hint">只有「可结算」状态的行能结算;冻结行等到账/解冻期满后自动转可结算。</span>
      </div>
      <el-table :data="list" v-loading="loading" border stripe @selection-change="onSelect">
        <el-table-column type="selection" width="45" :selectable="(row) => row.status === 2" />
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="referralOrderId" label="订单" width="80" />
        <el-table-column prop="promoterName" label="伙伴" width="110"><template #default="{ row }">{{ row.promoterName || '#' + row.promoterId }}</template></el-table-column>
        <el-table-column prop="positionCode" label="岗位" width="70" />
        <el-table-column label="份额 / 级差" width="110"><template #default="{ row }">{{ row.sharePct }}% / {{ row.diffPct }}%</template></el-table-column>
        <el-table-column prop="baseAmount" label="基数" width="110" align="right" />
        <el-table-column label="金额" width="110" align="right">
          <template #default="{ row }"><span :class="{ neg: row.sign === -1 }">{{ row.amount }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="stType(row.status)">{{ ST[row.status] }}</el-tag></template></el-table-column>
        <el-table-column prop="unfreezeAt" label="解冻时间" width="160" />
        <el-table-column prop="settleBatchId" label="批次" width="80" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button v-if="[1, 2].includes(row.status)" link type="danger" @click="voidOne(row)">作废</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                     :total="total" v-model:current-page="query.pageNo"
                     v-model:page-size="query.pageSize" :page-sizes="[10,20,50]" @change="load" />
    </div>

    <el-drawer v-model="batches.visible" title="结算批次" size="520px">
      <el-table :data="batches.list" size="small" border>
        <el-table-column prop="batchNo" label="批次号" width="180" />
        <el-table-column prop="cnt" label="条数" width="70" />
        <el-table-column prop="totalAmount" label="合计" width="110" align="right" />
        <el-table-column prop="operator" label="操作人" width="100" />
        <el-table-column prop="createTime" label="时间" />
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { mktCommissionApi } from '@/api/marketing'

const ST = { 1: '冻结', 2: '可结算', 3: '已结算', 4: '已提现', 5: '作废' }
const stType = (s) => ({ 2: 'success', 3: '', 4: 'info', 5: 'danger' }[s] || 'warning')

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 20, promoterId: null, status: null })
const selected = ref([])
const settleable = computed(() => selected.value.filter(r => r.status === 2))
const settleSum = computed(() => settleable.value.reduce((a, r) => a + Number(r.amount || 0), 0).toFixed(2))

async function load() {
  loading.value = true
  try {
    const res = await mktCommissionApi.page(query)
    list.value = res.records; total.value = res.total
  } finally { loading.value = false }
}
function reset() { Object.assign(query, { pageNo: 1, promoterId: null, status: null }); load() }
function onSelect(rows) { selected.value = rows }

async function settle() {
  await ElMessageBox.confirm(`将 ${settleable.value.length} 条可结算流水结算为「已结算」,合计 ${settleSum.value} 元?`, '批量结算', { type: 'warning' })
  const batchNo = await mktCommissionApi.settle({ ids: settleable.value.map(r => r.id) })
  ElMessage.success(`已结算,批次 ${batchNo}`); load()
}
async function voidOne(row) {
  const { value } = await ElMessageBox.prompt('作废原因', '作废佣金', { inputPattern: /\S+/, inputErrorMessage: '原因必填' })
  await mktCommissionApi.void(row.id, { reason: value }); ElMessage.success('已作废'); load()
}

const batches = reactive({ visible: false, list: [] })
async function loadBatches() { batches.list = (await mktCommissionApi.batches({ pageNo: 1, pageSize: 50 })).records }

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 12px; }
.hint { color: var(--el-text-color-secondary); font-size: 12px; }
.neg { color: var(--el-color-danger); }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
