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
            <el-button link type="primary" @click="print(row)">打印</el-button>
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
import { money } from '@/utils/format'
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

async function print(row) {
  // 必须在用户点击的同步阶段开窗口，否则浏览器会把异步打开当成弹窗拦截。
  const printWindow = window.open('', '_blank')
  if (!printWindow) {
    ElMessage.error('浏览器拦截了打印窗口，请允许本站弹窗后重试')
    return
  }
  printWindow.opener = null
  try {
    const voucher = await receiptApi.voucher(row.id)
    // 后端确认收据有效后才留下打印审计记录；已作废收据不会增加打印次数。
    await receiptApi.print(row.id)
    printWindow.document.write(voucherDocument(voucher))
    printWindow.document.close()
    window.setTimeout(() => {
      printWindow.focus()
      printWindow.print()
    }, 150)
    ElMessage.success('已打开收据凭单，请在系统打印窗口选择打印机或另存为 PDF')
    load()
  } catch (e) {
    printWindow.close()
  }
}

function voucherDocument(voucher) {
  const value = (item) => escapeHtml(item ?? '-')
  const amount = money(voucher.amount)
  const receivedDate = receiptDate(voucher.receivedAt)
  return `<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><title>收据-${value(voucher.receiptNo)}</title>
<style>
  @page { size: A4; margin: 12mm 15mm; }
  * { box-sizing: border-box; } body { margin: 0; color: #111; font-family: "Microsoft YaHei", "SimSun", sans-serif; font-size: 15px; }
  .voucher { min-height: 268mm; padding: 3mm 5mm; }
  .date { min-height: 24px; text-align: right; font-size: 13px; }
  .title { margin: 4mm 0 12mm; text-align: center; font-size: 27px; font-weight: 700; letter-spacing: 14px; }
  .receipt-no { margin-top: -8mm; margin-bottom: 7mm; text-align: right; color: #444; font-size: 12px; }
  .row { display: flex; align-items: baseline; min-height: 42px; line-height: 30px; }
  .label { flex: 0 0 68px; white-space: nowrap; }
  .fill { flex: 1; min-height: 30px; padding: 0 6px; border-bottom: 1px solid #111; word-break: break-all; }
  .pair { display: grid; grid-template-columns: 1fr 1.18fr; gap: 28px; }
  .pair .row { min-width: 0; } .pair .label { flex-basis: 82px; }
  .currency { font-weight: 600; } .currency-prefix { flex: 0 0 auto; margin-right: 12px; }
  .methods { display: flex; flex-wrap: wrap; gap: 22px; padding-left: 6px; }
  .method { white-space: nowrap; }
  .signatures { display: grid; grid-template-columns: .8fr .8fr 1.45fr .8fr 1fr; gap: 18px; margin-top: 48px; font-size: 14px; }
  .signature { min-height: 44px; white-space: nowrap; } .seal { min-height: 88px; position: relative; }
  .seal-image { display: block; width: 126px; max-height: 68px; object-fit: contain; margin: 3px 0 0 10px; mix-blend-mode: multiply; }
  @media print { .voucher { min-height: 0; } }
</style></head><body><main class="voucher">
  <div class="date">${value(receivedDate)}</div><div class="title">收 据</div>
  <div class="receipt-no">收据号：${value(voucher.receiptNo)}</div>
  <div class="row"><span class="label">单位</span><span class="fill">${value(voucher.issuerName)}</span></div>
  <div class="row"><span class="label">兹收到</span><span class="fill">${value(voucher.payerName)}</span></div>
  <div class="row"><span class="label">交 来</span><span class="fill">${value(voucher.feeType)}</span></div>
  <div class="pair"><div class="row"><span class="label">合同/协议编号</span><span class="fill">${value(voucher.contractNo)}</span></div><div class="row"><span class="label">租赁地址</span><span class="fill">${value(voucher.leaseAddress)}</span></div></div>
  <div class="row"><span class="label">关联账单</span><span class="fill">${value(voucher.billCode)}</span></div>
  <div class="row"><span class="label">金额（大写）</span><span class="currency-prefix">人民币：</span><span class="fill currency">${value(voucher.amountUppercase)}</span></div>
  <div class="row"><span class="label">金额（小写）</span><span class="fill currency">¥ ${value(amount)} 元</span></div>
  <div class="row"><span class="label">收款方式</span><div class="methods"><span class="method">□ 转账</span><span class="method">□ 现金</span><span class="method">□ 支票</span><span class="method">□ 微信</span></div></div>
  <div class="signatures"><div class="signature">核准：</div><div class="signature">会计：</div><div class="signature seal">单位盖章：<img class="seal-image" src="${RECEIPT_SEAL_URL}" alt="收款专用章"></div><div class="signature">出纳：</div><div class="signature">收款人：${value(voucher.payee)}</div></div>
</main></body></html>`
}

const RECEIPT_SEAL_URL = `${window.location.origin}/receipt-seal.jpg`

function receiptDate(raw) {
  const match = String(raw || '').match(/^(\d{4})-(\d{2})-(\d{2})/)
  return match ? `${match[1]}年${Number(match[2])}月${Number(match[3])}日` : '-'
}

function escapeHtml(value) {
  return String(value).replace(/[&<>'"]/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[char])
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
