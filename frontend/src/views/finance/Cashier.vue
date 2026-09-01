<template>
  <div class="page-container cashier">
    <div class="cashier-grid">
      <!-- 左侧:租客 + 未结账单 -->
      <div class="left-panel">
        <div class="panel-head">
          <div class="panel-title">选择租客</div>
          <!-- 档案里的每个租客都能选:欠款的排前面并带「N 笔 · ¥金额」徽标,没欠款的
               显示「无欠款」也照样可选。原先只列欠款租客,当月有应收的租客一旦口径
               没对上就整个从下拉里消失,收银员无从下手 -->
          <el-select v-model="tenantRefId" filterable placeholder="请选择租客(欠款的在前)" clearable
                     style="width: 340px" @change="loadBills">
            <el-option v-for="t in tenants" :key="t.tenantRefId"
                       :label="tenantOptionLabel(t)" :value="t.tenantRefId">
              <span>{{ tenantOptionLabel(t) }}</span>
              <span class="opt-owe" :class="{ clear: !hasOutstanding(t) }">{{ tenantOptionBadge(t) }}</span>
            </el-option>
          </el-select>
        </div>

        <el-table ref="tableRef" :data="bills" v-loading="loading" border stripe
                  @selection-change="onSelect" row-key="id" class="bill-table">
          <el-table-column type="selection" width="46" />
          <el-table-column prop="code" label="账单号" min-width="150" />
          <el-table-column prop="feeType" label="费用类型" width="100" />
          <!-- 账期与应收日:同一租客往往有多笔同类费用,不显示账期分不清在收哪一期 -->
          <el-table-column label="账期" width="180">
            <template #default="{ row }">{{ row.periodStart || '-' }} ~ {{ row.periodEnd || '-' }}</template>
          </el-table-column>
          <el-table-column prop="dueDate" label="应收日" width="110" />
          <el-table-column label="应收" width="120" align="right">
            <template #default="{ row }">¥{{ money(row.amount) }}</template>
          </el-table-column>
          <el-table-column label="已收" width="120" align="right">
            <template #default="{ row }">¥{{ money(row.paidAmount) }}</template>
          </el-table-column>
          <el-table-column label="欠款" width="130" align="right">
            <template #default="{ row }">
              <span class="owe">¥{{ money(owe(row)) }}</span>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="tenantRefId && !bills.length && !loading" class="empty-tip">该租客暂无未结账单</div>
      </div>

      <!-- 右侧:结算卡 -->
      <div class="settle-card">
        <div class="settle-title">收银结算</div>
        <div class="settle-body">
          <div class="settle-row">
            <span class="lbl">已选账单</span>
            <span class="val">{{ selected.length }} 张</span>
          </div>
          <div class="settle-row big">
            <span class="lbl">合计欠款</span>
            <span class="amount">¥{{ money(totalOwe) }}</span>
          </div>
          <el-divider style="margin: 14px 0" />
          <div class="field">
            <div class="field-lbl">收款金额</div>
            <el-input-number v-model="payAmount" :min="0" :max="totalOwe" :precision="2" :step="100"
                             controls-position="right" style="width: 100%" />
          </div>
          <div class="field">
            <div class="field-lbl">支付方式</div>
            <el-select v-model="payMethod" style="width: 100%">
              <el-option v-for="m in payMethods" :key="m" :label="m" :value="m" />
            </el-select>
          </div>
          <el-button type="primary" class="confirm-btn" :loading="paying"
                     :disabled="!selected.length || payAmount <= 0" @click="confirmPay">
            确认收款  ¥{{ money(payAmount) }}
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { billApi, paymentApi } from '@/api/finance'
import { hasOutstanding, tenantOptionBadge, tenantOptionLabel } from './cashierModel'

const PAYABLE = [3, 4, 6] // 待收付/部分结清/逾期

const tenants = ref([])
const tenantRefId = ref(null)
const bills = ref([])
const loading = ref(false)
const tableRef = ref()
const selected = ref([])
const payAmount = ref(0)
const payMethod = ref('现金')
const paying = ref(false)
const payMethods = ['现金', '转账', 'POS', '微信', '支付宝', '聚合']

function money(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
function owe(row) {
  return Number(row.amount || 0) - Number(row.paidAmount || 0)
}

const totalOwe = computed(() =>
  selected.value.reduce((s, r) => s + owe(r), 0)
)

watch(totalOwe, (v) => { payAmount.value = Number(v.toFixed(2)) })

async function loadTenants() {
  // 全部租客,欠款的在前。收款成功后要重新拉一次:结清的租客徽标变「无欠款」并沉到列表后面
  tenants.value = (await billApi.payableTenants()) || []
}

async function loadBills() {
  selected.value = []
  bills.value = []
  if (!tenantRefId.value) return
  loading.value = true
  try {
    // status 不传,前端过滤可收款状态
    const res = await billApi.page({ pageNo: 1, pageSize: 100, tenantRefId: tenantRefId.value, direction: 1 })
    bills.value = (res.records || []).filter(b => PAYABLE.includes(b.status) && owe(b) > 0)
  } finally {
    loading.value = false
  }
}

function onSelect(rows) {
  selected.value = rows
}

async function confirmPay() {
  if (!selected.value.length) return
  paying.value = true
  let budget = Number(payAmount.value)
  let paidCount = 0
  try {
    // 按欠款顺序逐张收款,金额不足整单时只收部分
    const rows = [...selected.value]
    for (const row of rows) {
      if (budget <= 0) break
      const rowOwe = owe(row)
      if (rowOwe <= 0) continue
      const pay = Math.min(budget, rowOwe)
      const payNo = 'SK' + Date.now() + Math.floor(Math.random() * 100000)
      await paymentApi.pay({
        billId: row.id,
        amount: Number(pay.toFixed(2)),
        payMethod: payMethod.value,
        payNo
      })
      budget -= pay
      paidCount++
    }
    // 收款已在后端一笔事务里落了:支付单 + 账单实收 + 收支流水 + 收据。
    // 这四处分别在 收支流水 / 收据记录 页面可查,账单页的「详情」能看到本次收款记录。
    ElMessage.success(`收款成功,共 ${paidCount} 张账单,已生成收据与收支流水`)
    // 租客下拉要一起刷:这个租客要是结清了,就该从"有欠款的租客"里消失
    await Promise.all([loadBills(), loadTenants()])
  } finally {
    paying.value = false
  }
}

onMounted(loadTenants)
</script>

<style scoped>
.cashier-grid { display: grid; grid-template-columns: 1fr 360px; gap: 18px; align-items: start; }
.left-panel {
  background: var(--bg-card); border: 1px solid var(--border);
  border-radius: var(--radius); padding: 18px;
}
.panel-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.panel-title { font-size: 15px; font-weight: 650; color: var(--text-title); }
.bill-table { --el-table-row-hover-bg-color: var(--bg-hover, #f5f7fa); }
.owe { color: #e5484d; font-weight: 650; font-variant-numeric: tabular-nums; }
.opt-owe { float: right; color: var(--el-color-danger); font-size: 12px; margin-left: 20px; }
.opt-owe.clear { color: var(--text-secondary); }
.empty-tip { text-align: center; color: var(--text-secondary); padding: 30px 0; }

.settle-card {
  background: var(--bg-card); border: 1px solid var(--border);
  border-radius: var(--radius); overflow: hidden;
  position: sticky; top: 16px;
}
.settle-title {
  background: var(--brand, #3b6cff); color: #fff;
  padding: 16px 20px; font-size: 16px; font-weight: 650; letter-spacing: .5px;
}
.settle-body { padding: 20px; }
.settle-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
.settle-row .lbl { color: var(--text-secondary); font-size: 13px; }
.settle-row .val { color: var(--text-title); font-weight: 600; }
.settle-row.big .amount {
  font-size: 30px; font-weight: 700; color: #e5484d;
  letter-spacing: -0.5px; font-variant-numeric: tabular-nums;
}
.field { margin-bottom: 16px; }
.field-lbl { font-size: 13px; color: var(--text-secondary); margin-bottom: 6px; }
.confirm-btn {
  width: 100%; height: 52px; font-size: 17px; font-weight: 650;
  margin-top: 8px; letter-spacing: 1px;
}
</style>
