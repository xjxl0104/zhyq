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
          <el-table-column label="应收" width="110" align="right">
            <template #default="{ row }">¥{{ money(row.amount) }}</template>
          </el-table-column>
          <!-- 滞纳金计入欠款口径,不单列出来的话"欠款 > 应收-已收"会让收银员一头雾水 -->
          <el-table-column label="滞纳金" width="100" align="right">
            <template #default="{ row }">
              <span :class="{ 'late-fee': Number(row.lateFee) > 0 }">¥{{ money(row.lateFee) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="已收" width="110" align="right">
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
import { billOwe, billOweCents, buildPaymentPlan, hasOutstanding, tenantOptionBadge, tenantOptionLabel } from './cashierModel'

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
// 欠款 = 本金 + 滞纳金 - 实收,与后端 BillMetrics.outstandingOf 同口径
const owe = billOwe

// 整数分求和再除回元:浮点直接累加会带出 e-14 级残差
const totalOwe = computed(() =>
  selected.value.reduce((s, r) => s + billOweCents(r), 0) / 100
)

watch(totalOwe, (v) => { payAmount.value = v })

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

// 幂等键按「账单 + 金额(分)」缓存:同一笔未完成的收款重试复用同一个 payNo
// (后端撞同 payNo 直接返回首次的支付单,不会重复入账);金额变了就是一笔新收款,
// 换新键走后端全量校验 —— 只按账单缓存的话,首笔实际入账但响应丢失后,刷新会把
// 默认金额改小,同键重试被后端当重放吞掉,前端还误报"收款成功"。
// 确认成功后丢弃;之前每次点击都现生成 payNo,超时重试等于全新收款,幂等键形同虚设
const payNos = new Map() // billId -> { payNo, cents }
function payNoFor(billId, cents) {
  const cached = payNos.get(billId)
  if (cached && cached.cents === cents) return cached.payNo
  const payNo = 'SK' + Date.now() + Math.random().toString(36).slice(2, 10)
  payNos.set(billId, { payNo, cents })
  return payNo
}
// 换租客清空:keep-alive 下组件实例常驻,旧租客的中断记录不该一直攒着
watch(tenantRefId, () => payNos.clear())

async function confirmPay() {
  if (!selected.value.length) return
  // 整数分拆单,永不产生 0 元支付请求
  const plan = buildPaymentPlan(selected.value, payAmount.value)
  if (!plan.length) {
    ElMessage.warning('没有可收款的金额,请检查收款金额与所选账单')
    return
  }
  paying.value = true
  let paidCount = 0
  let failed = false
  try {
    for (const item of plan) {
      const cents = Math.round(item.amount * 100)
      await paymentApi.pay({
        billId: item.billId,
        amount: item.amount,
        payMethod: payMethod.value,
        payNo: payNoFor(item.billId, cents)
      })
      payNos.delete(item.billId)
      paidCount++
    }
  } catch (e) {
    // request.js 已经弹过具体错误;这里只负责别让循环把刷新逻辑一起带走
    failed = true
  }
  // 无论成败都要刷新:把服务端真实入账状态拉回来。中断时界面停在旧欠款上,
  // 用户拿着旧数字再点一次就是重复收款。刷新自己失败也不能吞掉下面的结果提示
  try {
    await Promise.all([loadBills(), loadTenants()])
  } catch (e) {
    // 刷新失败 request.js 已提示
  } finally {
    paying.value = false
  }
  if (!failed) {
    // 收款已在后端一笔事务里落了:支付单 + 账单实收 + 收支流水 + 收据
    ElMessage.success(`收款成功,共 ${paidCount} 张账单,已生成收据与收支流水`)
  } else if (paidCount > 0) {
    ElMessage.warning(`前 ${paidCount} 张已收款成功,其余中断;欠款已刷新,可直接重试`)
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
.owe { color: var(--el-color-danger); font-weight: 650; font-variant-numeric: tabular-nums; }
.late-fee { color: var(--el-color-warning); font-weight: 600; }
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
