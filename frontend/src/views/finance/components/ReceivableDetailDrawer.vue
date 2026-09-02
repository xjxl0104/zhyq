<template>
  <el-drawer :model-value="modelValue" title="应收明细详情" size="78%" @close="$emit('update:modelValue', false)">
    <div v-loading="loading" v-if="detail?.register">
      <el-tabs>
        <el-tab-pane label="基础资料">
          <el-descriptions :column="3" border>
            <el-descriptions-item v-for="column in receivableColumns" :key="column.prop" :label="column.label">
              {{ formatReceivableCell(detail.register, column) }}
            </el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>
        <el-tab-pane label="计费规则">
          <el-table :data="detail.rules || []" border>
            <el-table-column label="费用" width="110">
              <template #default="{ row }">{{ feeTypeLabels[row.feeType] || row.feeType }}</template>
            </el-table-column>
            <el-table-column label="规则类型" width="120">
              <template #default="{ row }">{{ ruleTypeLabels[row.ruleType] || row.ruleType }}</template>
            </el-table-column>
            <el-table-column label="单价" width="130">
              <template #default="{ row }">
                {{ row.rateValue != null ? `${row.rateValue} ${rateUnitLabels[row.rateUnit] || row.rateUnit || ''}` : '-' }}
              </template>
            </el-table-column>
            <el-table-column label="月金额" width="120" align="right">
              <template #default="{ row }">{{ row.fixedAmount != null ? `¥${row.fixedAmount}` : '-' }}</template>
            </el-table-column>
            <el-table-column label="生效区间" width="200">
              <template #default="{ row }">
                {{ row.effectiveStart ? `${row.effectiveStart} ~ ${row.effectiveEnd || '合同止'}` : '整个合同期' }}
              </template>
            </el-table-column>
            <el-table-column prop="rawText" label="规则说明(来源条款)" min-width="220" />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="保证金与收款">
          <el-table :data="detail.deposits || []" border>
            <el-table-column prop="depositType" label="保证金类型" /><el-table-column prop="requiredAmount" label="应收" />
            <el-table-column prop="confirmedReceivedAmount" label="已确认收款" /><el-table-column prop="differenceAmount" label="差额" />
          </el-table>
          <el-divider>收款账户</el-divider>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="租金收款账户">
              <span>{{ revealed.rent || detail.register.rentAccountMasked || '-' }}</span>
              <el-button
                v-if="canViewAccount && detail.register.rentAccountId && !revealed.rent"
                link type="primary" size="small" class="reveal-btn"
                :loading="revealing.rent"
                @click="reveal('rent', detail.register.rentAccountId)"
              >查看完整账户</el-button>
            </el-descriptions-item>
            <el-descriptions-item label="物业管理、水电收款账户">
              <span>{{ revealed.property || detail.register.propertyAccountMasked || '-' }}</span>
              <el-button
                v-if="canViewAccount && detail.register.propertyAccountId && !revealed.property"
                link type="primary" size="small" class="reveal-btn"
                :loading="revealing.property"
                @click="reveal('property', detail.register.propertyAccountId)"
              >查看完整账户</el-button>
            </el-descriptions-item>
          </el-descriptions>
          <el-divider>已生成账单</el-divider>
          <!-- 账单号可点:跳到所有账单页并定位该单,与流水/收据页的联动同一套路 -->
          <el-table :data="detail.bills || []" border>
            <el-table-column prop="code" label="账单号" min-width="150">
              <template #default="{ row }">
                <!-- el-link 无 href 时不进 tab 序,手动补键盘可达性 -->
                <el-link type="primary" tabindex="0" @click="gotoBill(row)"
                         @keydown.enter="gotoBill(row)">{{ row.code }}</el-link>
              </template>
            </el-table-column>
            <el-table-column prop="feeType" label="费用" /><el-table-column prop="amount" label="金额" /><el-table-column prop="dueDate" label="应收日" />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="来源与变更">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="导入批次">{{ detail.sourceBatch?.id || '-' }}</el-descriptions-item>
            <el-descriptions-item label="源文件">{{ detail.sourceBatch?.fileName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="工作表/行">{{ detail.sourceRow ? `${detail.sourceRow.sheetName} / ${detail.sourceRow.rowNo}` : '-' }}</el-descriptions-item>
            <el-descriptions-item label="确认人">{{ detail.sourceBatch?.confirmedBy || '-' }}</el-descriptions-item>
          </el-descriptions>
          <pre class="source-json">{{ detail.sourceRow?.rawJson || '手工录入，无原始行快照' }}</pre>
        </el-tab-pane>
      </el-tabs>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { receivableApi } from '@/api/receivable'
import { formatReceivableCell, receivableColumns } from '../receivableModel'
const props = defineProps({ modelValue: Boolean, registerId: [String, Number], canViewAccount: Boolean })
const emit = defineEmits(['update:modelValue'])
// 计费规则页签的展示字典:后端返回的是与出账同源的规则枚举
const feeTypeLabels = { RENT: '租金', PROPERTY: '物业管理费' }
const ruleTypeLabels = {
  AUTHORITATIVE_MONTHLY: '按月计费',
  BASE_RATE: '基础单价',
  ESCALATION: '递增',
  WAIVER: '免租/免缴',
  RECURRING_WAIVER: '循环免租',
  DISCOUNT: '折扣',
  OFFSET: '补助抵扣',
  FIXED_ADJUSTMENT: '固定调整'
}
const rateUnitLabels = { MONTH_SQM: '元/㎡·月', DAY_SQM: '元/㎡·日', MONTH_FIXED: '元/月' }
const router = useRouter()
function gotoBill(bill) {
  emit('update:modelValue', false)
  router.push({ path: '/finance/bill', query: { billId: bill.id } })
}
const loading = ref(false); const detail = ref(null)
const revealed = ref({ rent: '', property: '' })
const revealing = ref({ rent: false, property: false })
watch(() => [props.modelValue, props.registerId], async ([open, id]) => {
  if (!open || !id) return
  revealed.value = { rent: '', property: '' }
  loading.value = true
  try { detail.value = await receivableApi.get(id) } finally { loading.value = false }
}, { immediate: true })
async function reveal(which, accountId) {
  revealing.value[which] = true
  try {
    const res = await receivableApi.revealAccount(accountId)
    revealed.value[which] = res?.accountNo || ''
    if (!res?.accountNo) ElMessage.warning('未获取到完整账户')
  } catch (e) {
    /* request.js 已统一弹错误提示 */
  } finally {
    revealing.value[which] = false
  }
}
</script>
<style scoped>.source-json{margin-top:16px;padding:14px;max-height:320px;overflow:auto;background:#f5f7fa;border-radius:6px;white-space:pre-wrap;word-break:break-all}.reveal-btn{margin-left:12px}</style>
