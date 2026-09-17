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
  const receivedAt = voucher.receivedAt ? String(voucher.receivedAt).replace('T', ' ') : '-'
  return `<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><title>${value(voucher.title)}-${value(voucher.receiptNo)}</title>
<style>
  @page { size: A4; margin: 14mm; }
  * { box-sizing: border-box; } body { margin: 0; color: #1f2937; font-family: "Microsoft YaHei", sans-serif; }
  .voucher { min-height: 245mm; border: 2px solid #253fb8; padding: 18mm 16mm; }
  .title { text-align: center; font-size: 28px; font-weight: 700; letter-spacing: 6px; color: #182b8f; }
  .issuer { margin-top: 10px; text-align: center; font-size: 15px; } .number { margin-top: 22px; text-align: right; font-size: 13px; }
  .line { display: grid; grid-template-columns: 1fr 1fr; gap: 22px; padding: 13px 0; border-bottom: 1px solid #cbd5e1; font-size: 15px; }
  .line.full { display: block; } .label { color: #475569; } .amount { font-size: 26px; font-weight: 700; color: #b42318; }
  .note { margin-top: 26px; color: #64748b; font-size: 12px; line-height: 1.8; }
  .signatures { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 24px; margin-top: 54px; font-size: 14px; }
  .signature { border-bottom: 1px solid #64748b; min-height: 28px; } @media print { .voucher { min-height: 0; } }
</style></head><body><main class="voucher">
  <div class="title">${value(voucher.title)}</div><div class="issuer">收款单位：${value(voucher.issuerName)}</div>
  <div class="number">收据编号：${value(voucher.receiptNo)}</div>
  <div class="line"><div><span class="label">交款单位/个人：</span>${value(voucher.payerName)}</div><div><span class="label">收款日期：</span>${value(receivedAt)}</div></div>
  <div class="line"><div><span class="label">关联账单：</span>${value(voucher.billCode)}</div><div><span class="label">费用项目：</span>${value(voucher.feeType)}</div></div>
  <div class="line full"><span class="label">收款金额（小写）：</span><span class="amount">¥${value(amount)}</span></div>
  <div class="line full"><span class="label">收款金额（大写）：</span>${value(voucher.amountUppercase)}</div>
  <div class="line"><div><span class="label">收款方式：</span>${value(voucher.payMethod)}</div><div><span class="label">备注：</span>${value(voucher.remark)}</div></div>
  <div class="note">说明：本收据为收款凭证，不作为税务发票使用；请妥善保管。已作废收据不可打印。</div>
  <div class="signatures"><div class="signature">交款人签字：</div><div class="signature">收款经办：${value(voucher.payee)}</div><div class="signature">财务复核：</div></div>
</main></body></html>`
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
