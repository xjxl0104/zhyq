<template>
  <div class="page-container">
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="单号"><el-input v-model="query.sourceNo" clearable style="width: 180px" /></el-form-item>
        <el-form-item label="来源">
          <el-select v-model="query.sourceType" placeholder="全部" clearable style="width: 150px">
            <el-option v-for="(t, v) in SRC" :key="v" :label="t" :value="Number(v)" />
          </el-select>
        </el-form-item>
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
        <el-upload :show-file-list="false" accept=".xlsx,.xls" :http-request="doImport">
          <el-button type="primary" :loading="importing"><el-icon><Upload /></el-icon>导入出库单(Excel)</el-button>
        </el-upload>
        <a :href="mktOrderApi.templateUrl" target="_blank"><el-button link type="primary">下载模板</el-button></a>
        <span class="hint">列:出库单号 · 客户手机号 · 件数 · 包裹数 · 发货时间 · 物流单号 · 云仓编码(可选) · 货值(可选)。服务费按客户合同单价表由园区自算,冻结 7 天后自动解冻。</span>
      </div>
      <el-alert v-if="importResult" :type="importResult.errors.length ? 'warning' : 'success'" :closable="true" class="mb" @close="importResult = null">
        导入 {{ importResult.imported }} 条,跳过 {{ importResult.skipped }} 条(重复单号)
        <template v-if="importResult.errors.length">,失败 {{ importResult.errors.length }} 条:
          <ul class="errs"><li v-for="(e, i) in importResult.errors" :key="i">{{ e }}</li></ul>
        </template>
      </el-alert>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="sourceNo" label="单号" width="170" />
        <el-table-column label="来源" width="110"><template #default="{ row }">{{ SRC[row.sourceType] }}</template></el-table-column>
        <el-table-column prop="customerName" label="客户" min-width="130" />
        <el-table-column prop="promoterName" label="成交伙伴" width="110" />
        <el-table-column prop="customerGrade" label="评级" width="60" align="center" />
        <el-table-column label="基数" width="110" align="right"><template #default="{ row }">{{ row.baseAmount }}</template></el-table-column>
        <el-table-column label="系数" width="90" align="right">
          <template #default="{ row }">{{ row.sourceType === 1 ? `${row.poolFactor} 月` : `${row.poolFactor}%` }}</template>
        </el-table-column>
        <el-table-column prop="poolAmount" label="佣金池" width="110" align="right" />
        <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="stType(row.status)">{{ ST[row.status] }}</el-tag></template></el-table-column>
        <el-table-column prop="eventTime" label="事件时间" width="160" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openSplits(row)">拆分</el-button>
            <el-button v-if="row.status === 2" link type="danger" @click="voidOrder(row)">作废</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                     :total="total" v-model:current-page="query.pageNo"
                     v-model:page-size="query.pageSize" :page-sizes="[10,20,50]" @change="load" />
    </div>

    <el-drawer v-model="splits.visible" :title="`佣金拆分 · ${splits.row?.sourceNo || ''}`" size="560px">
      <el-table :data="splits.list" size="small" border>
        <el-table-column prop="promoterId" label="伙伴" width="80" />
        <el-table-column prop="positionCode" label="岗位" width="70" />
        <el-table-column label="份额 / 级差" width="110"><template #default="{ row }">{{ row.sharePct }}% / {{ row.diffPct }}%</template></el-table-column>
        <el-table-column prop="amount" label="金额" width="110" align="right" />
        <el-table-column label="状态"><template #default="{ row }">{{ CST[row.status] }}</template></el-table-column>
        <el-table-column prop="unfreezeAt" label="解冻时间" width="160" />
      </el-table>
      <p class="hint">合计 {{ sum }} / 池 {{ splits.row?.poolAmount }};差额为园区留存(链未到顶格、退出或内部人员)。</p>
    </el-drawer>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { mktOrderApi } from '@/api/marketing'

const SRC = { 1: '租赁签约', 2: '出库单', 3: '平台费收款', 4: '签约奖', 5: '增值服务' }
const ST = { 1: '待确认', 2: '已确认', 3: '已退款', 4: '已取消', 5: '无归属' }
const CST = { 1: '冻结', 2: '可结算', 3: '已结算', 4: '已提现', 5: '作废' }
const stType = (s) => ({ 2: 'success', 3: 'danger', 4: 'info', 5: 'warning' }[s] || '')

const loading = ref(false)
const importing = ref(false)
const importResult = ref(null)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, sourceNo: '', sourceType: null, status: null })

async function load() {
  loading.value = true
  try {
    const res = await mktOrderApi.page(query)
    list.value = res.records; total.value = res.total
  } finally { loading.value = false }
}
function reset() { Object.assign(query, { pageNo: 1, sourceNo: '', sourceType: null, status: null }); load() }

async function doImport({ file }) {
  const fd = new FormData(); fd.append('file', file)
  importing.value = true
  try {
    importResult.value = await mktOrderApi.importExcel(fd)
    load()
  } finally { importing.value = false }
}
async function voidOrder(row) {
  const { value } = await ElMessageBox.prompt('作废原因(未结算佣金作废,已结算生成扣回)', '作废计佣订单', { inputPattern: /\S+/, inputErrorMessage: '原因必填' })
  await mktOrderApi.void(row.id, { reason: value }); ElMessage.success('已作废'); load()
}

const splits = reactive({ visible: false, row: null, list: [] })
async function openSplits(row) { splits.row = row; splits.list = await mktOrderApi.splits(row.id); splits.visible = true }
const sum = computed(() => splits.list.reduce((a, s) => a + Number(s.amount || 0), 0).toFixed(2))

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.hint { color: var(--el-text-color-secondary); font-size: 12px; }
.mb { margin-bottom: 12px; }
.errs { margin: 4px 0 0 16px; max-height: 120px; overflow: auto; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
