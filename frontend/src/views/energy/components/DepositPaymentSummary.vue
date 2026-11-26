<template>
  <div class="deposit-summary">
    <!-- 收取单位分开提示卡片（昌记 / 数智云仓） -->
    <div v-if="config" class="payee-bar">
      <div class="payee-card rent">
        <div class="payee-head">
          <el-tag type="primary" size="small">租金保证金收取单位</el-tag>
          <strong>{{ config.rentPayeeName || '未设置（点下方「收取单位与抬头」配置）' }}</strong>
        </div>
        <div class="payee-acc">{{ config.rentPaymentAccount || '未填写租金保证金收款账户/方式' }}</div>
      </div>
      <div class="payee-card property">
        <div class="payee-head">
          <el-tag type="success" size="small">物业保证金收取单位</el-tag>
          <strong>{{ config.propertyPayeeName || '未设置（点下方「收取单位与抬头」配置）' }}</strong>
        </div>
        <div class="payee-acc">{{ config.propertyPaymentAccount || '未填写物业保证金收款账户/方式' }}</div>
      </div>
    </div>

    <div class="summary-grid">
      <div class="summary-item"><span>租金保证金通知单</span><strong>{{ rentNotices.length }} 张</strong></div>
      <div class="summary-item hl"><span>租金保证金（应收 / 已缴）</span><strong>¥{{ money(rentTotals.due) }} <small>/ ¥{{ money(rentTotals.paid) }}</small></strong></div>
      <div class="summary-item"><span>物业保证金通知单</span><strong>{{ propertyNotices.length }} 张</strong></div>
      <div class="summary-item hl-green"><span>物业保证金（应收 / 已缴）</span><strong>¥{{ money(propTotals.due) }} <small>/ ¥{{ money(propTotals.paid) }}</small></strong></div>
      <div class="summary-item total"><span>待收保证金余额</span><strong>¥{{ money(rentTotals.unpaid + propTotals.unpaid) }}</strong></div>
    </div>

    <div class="table-card">
      <div class="toolbar">
        <el-button @click="openHeader">收取单位与通知单抬头</el-button>
        <el-dropdown trigger="click" @command="onSealCommand">
          <el-button :type="anySealEnabled ? 'danger' : ''" plain>
            <span v-if="anySealEnabled" class="seal-dot" />
            电子印章
            <el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item disabled>── 租金保证金通知单印章（昌记） ──</el-dropdown-item>
              <el-dropdown-item v-for="s in seals" :key="'dr-' + s.id" :command="`RENT:${s.id}`">
                <div class="seal-drop-item">
                  <img v-if="s.imageData" :src="s.imageData" class="seal-drop-img" alt="" />
                  <span>{{ s.name }}</span>
                  <el-tag v-if="rentSeal?.enabled === 1 && String(rentSeal.sealId) === String(s.id)" size="small" type="danger" effect="plain">已应用</el-tag>
                </div>
              </el-dropdown-item>
              <el-dropdown-item command="RENT:design">调整租金保证金通知单印章位置/大小</el-dropdown-item>
              <el-dropdown-item divided disabled>── 物业保证金通知单印章（数智云仓） ──</el-dropdown-item>
              <el-dropdown-item v-for="s in seals" :key="'dp-' + s.id" :command="`PROPERTY:${s.id}`">
                <div class="seal-drop-item">
                  <img v-if="s.imageData" :src="s.imageData" class="seal-drop-img" alt="" />
                  <span>{{ s.name }}</span>
                  <el-tag v-if="propertySeal?.enabled === 1 && String(propertySeal.sealId) === String(s.id)" size="small" type="danger" effect="plain">已应用</el-tag>
                </div>
              </el-dropdown-item>
              <el-dropdown-item command="PROPERTY:design">调整物业保证金通知单印章位置/大小</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button type="warning" plain :loading="calibrating" @click="calibrate">按2个月租金/物管费校准</el-button>
        <el-button :disabled="!viewRentNotices.length" @click="printBatch(viewRentNotices)"><el-icon><Printer /></el-icon>打印全部租金保证金通知单</el-button>
        <el-button :disabled="!viewPropertyNotices.length" @click="printBatch(viewPropertyNotices)"><el-icon><Printer /></el-icon>打印全部物业保证金通知单</el-button>
      </div>
      <div class="toolbar-tip">
        保证金按合同 <strong>2个月租金</strong>（收取单位：{{ config?.rentPayeeName || '昌记' }}）与 <strong>2个月物业管理费</strong>（收取单位：{{ config?.propertyPayeeName || '数智云仓' }}）分开出单，同一租户多份合同自动合并在一张通知单内分行列示；保证金一次性收取、无需开具发票，缴纳后上传回执整行变绿。
      </div>

      <FeeFilterBar :model="filter" :companies="opts.companies" :floors="opts.floors">
        分租户汇总 {{ viewSummaryRows.length }} / {{ summaryRows.length }} 户 · 应收合计 ¥{{ money(viewSummaryRows.reduce((s, r) => s + r.totalDue, 0)) }} · 已缴 ¥{{ money(viewSummaryRows.reduce((s, r) => s + r.totalPaid, 0)) }}
      </FeeFilterBar>

      <!-- 表 1: 保证金缴费汇总（分租户） -->
      <h4 class="section-title">保证金缴费汇总（分租户）</h4>
      <el-table :data="viewSummaryRows" v-loading="loading" border size="small" show-summary :summary-method="tenantSummary">
        <el-table-column type="index" label="序号" width="55" align="center" />
        <el-table-column prop="tenantName" label="租户/公司名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="unitName" label="楼层/区域" min-width="190" show-overflow-tooltip />
        <el-table-column label="计租面积(㎡)" width="110" align="right">
          <template #default="{ row }">{{ num(row.leaseArea) }}</template>
        </el-table-column>
        <el-table-column label="租金保证金(应收)" width="135" align="right">
          <template #default="{ row }">¥{{ money(row.rentDue) }}</template>
        </el-table-column>
        <el-table-column label="租金保证金状态" width="105" align="center">
          <template #default="{ row }">
            <el-tag :type="stateMeta(row.rentDue, row.rentPaid, row.rentState).type" size="small">
              {{ stateMeta(row.rentDue, row.rentPaid, row.rentState).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="物业保证金(应收)" width="135" align="right">
          <template #default="{ row }">{{ row.propDue > 0 ? '¥' + money(row.propDue) : '-' }}</template>
        </el-table-column>
        <el-table-column label="物业保证金状态" width="105" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.propDue > 0" :type="stateMeta(row.propDue, row.propPaid, row.propState).type" size="small">
              {{ stateMeta(row.propDue, row.propPaid, row.propState).label }}
            </el-tag>
            <span v-else class="muted">无</span>
          </template>
        </el-table-column>
        <el-table-column label="保证金应收合计" width="135" align="right">
          <template #default="{ row }"><strong>¥{{ money(row.totalDue) }}</strong></template>
        </el-table-column>
        <el-table-column label="已缴合计" width="125" align="right">
          <template #default="{ row }"><span class="ok-num">¥{{ money(row.totalPaid) }}</span></template>
        </el-table-column>
        <el-table-column label="待收余额" width="125" align="right">
          <template #default="{ row }"><strong>¥{{ money(row.totalUnpaid) }}</strong></template>
        </el-table-column>
      </el-table>

      <!-- 表 2: 租金保证金缴费通知单（昌记） -->
      <div class="notice-group-head">
        <h4 class="section-title">
          租金保证金缴费通知单（{{ viewRentNotices.length }} 张）
          <el-tag type="primary" effect="plain" size="small" style="margin-left: 8px">收取单位：{{ config?.rentPayeeName || '待配置' }}</el-tag>
        </h4>
      </div>
      <el-table :data="viewRentNotices" border size="small" row-key="noticeNo" class="notice-table"
                :row-class-name="({ row }) => row.payState === 'PAID' ? 'notice-payment-paid' : ''"
                show-summary :summary-method="noticeSummary">
        <el-table-column type="index" label="序号" width="55" align="center" />
        <el-table-column prop="noticeNo" label="单据编号" width="185" />
        <el-table-column prop="tenantName" label="租户/公司名称" min-width="190" show-overflow-tooltip />
        <el-table-column prop="unitName" label="楼层/区域" min-width="175" show-overflow-tooltip />
        <el-table-column prop="payeeName" label="收取单位" min-width="165" show-overflow-tooltip />
        <el-table-column label="应收租金保证金" width="135" align="right"><template #default="{ row }">¥{{ money(row.totalDue) }}</template></el-table-column>
        <el-table-column label="已缴金额" width="120" align="right"><template #default="{ row }">¥{{ money(row.totalPaid) }}</template></el-table-column>
        <el-table-column label="待缴余额" width="125" align="right"><template #default="{ row }"><strong>¥{{ money(row.totalUnpaid) }}</strong></template></el-table-column>
        <el-table-column label="回执" width="82" align="center">
          <template #default="{ row }">{{ row.receiptCount > 0 ? `${row.receiptCount} 份` : '未传' }}</template>
        </el-table-column>
        <el-table-column label="缴纳状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="stateMeta(row.totalDue, row.totalPaid, row.payState).type" size="small">
              {{ stateMeta(row.totalDue, row.totalPaid, row.payState).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="success" @click="openPaymentForNotice(row)">缴纳 / 回执</el-button>
            <el-button link type="primary" @click="preview(row)">预览</el-button>
            <el-button link type="primary" @click="printBatch([row])">打印</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 表 3: 物业保证金缴费通知单（数智云仓） -->
      <div class="notice-group-head">
        <h4 class="section-title">
          物业保证金缴费通知单（{{ viewPropertyNotices.length }} 张）
          <el-tag type="success" effect="plain" size="small" style="margin-left: 8px">收取单位：{{ config?.propertyPayeeName || '待配置' }}</el-tag>
        </h4>
      </div>
      <el-table :data="viewPropertyNotices" border size="small" row-key="noticeNo" class="notice-table"
                :row-class-name="({ row }) => row.payState === 'PAID' ? 'notice-payment-paid' : ''"
                show-summary :summary-method="noticeSummary">
        <el-table-column type="index" label="序号" width="55" align="center" />
        <el-table-column prop="noticeNo" label="单据编号" width="185" />
        <el-table-column prop="tenantName" label="租户/公司名称" min-width="190" show-overflow-tooltip />
        <el-table-column prop="unitName" label="楼层/区域" min-width="175" show-overflow-tooltip />
        <el-table-column prop="payeeName" label="收取单位" min-width="165" show-overflow-tooltip />
        <el-table-column label="应收物业保证金" width="135" align="right"><template #default="{ row }">¥{{ money(row.totalDue) }}</template></el-table-column>
        <el-table-column label="已缴金额" width="120" align="right"><template #default="{ row }">¥{{ money(row.totalPaid) }}</template></el-table-column>
        <el-table-column label="待缴余额" width="125" align="right"><template #default="{ row }"><strong>¥{{ money(row.totalUnpaid) }}</strong></template></el-table-column>
        <el-table-column label="回执" width="82" align="center">
          <template #default="{ row }">{{ row.receiptCount > 0 ? `${row.receiptCount} 份` : '未传' }}</template>
        </el-table-column>
        <el-table-column label="缴纳状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="stateMeta(row.totalDue, row.totalPaid, row.payState).type" size="small">
              {{ stateMeta(row.totalDue, row.totalPaid, row.payState).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="success" @click="openPaymentForNotice(row)">缴纳 / 回执</el-button>
            <el-button link type="primary" @click="preview(row)">预览</el-button>
            <el-button link type="primary" @click="printBatch([row])">打印</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 收取单位与通知单抬头弹窗 -->
    <el-dialog v-model="headerDlg.visible" title="保证金收取单位与通知单抬头（昌记 / 数智云仓分开配置）" width="740px">
      <el-form :model="headerForm" label-width="140px" size="small">
        <el-form-item label="缴费单位">
          <el-select v-model="headerTarget" filterable style="width: 100%" @change="onHeaderTargetChange">
            <el-option value="" label="全部缴费单位（通用抬头）">
              <div class="header-opt-row">
                <span><strong>全部缴费单位（通用抬头）</strong></span>
                <el-tag size="small" type="info" effect="plain">通用</el-tag>
              </div>
            </el-option>
            <el-option v-for="t in headerTenantChoices" :key="t" :value="t" :label="`${t}（${headerProfilesMap[t] ? '定制' : '通用'}）`">
              <div class="header-opt-row">
                <span>{{ t }}</span>
                <el-tag size="small" :type="headerProfilesMap[t] ? 'warning' : 'info'" effect="plain">
                  {{ headerProfilesMap[t] ? '定制' : '通用' }}
                </el-tag>
              </div>
            </el-option>
          </el-select>
          <div class="header-scope-tip">
            <template v-if="headerTarget">
              当前为 <strong>{{ headerTarget }}</strong> 的{{ headerProfilesMap[headerTarget] ? '定制' : '独立' }}保证金抬头；保存后标记为「定制」，以后预览和打印该单位保证金通知单均自动沿用。
            </template>
            <template v-else>
              当前为全体未定制单位共用的「通用抬头」；如需给某家单位单独设置缴费截止日或收款账户，请从上方下拉选择具体缴费单位。
            </template>
          </div>
        </el-form-item>
        <el-divider />
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="园区名称"><el-input v-model="headerForm.parkName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="单据编号前缀"><el-input v-model="headerForm.noticePrefix" placeholder="如 DIPARK" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="开具日期"><el-date-picker v-model="headerForm.issueDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="缴费截止日"><el-date-picker v-model="headerForm.dueDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" placeholder="留空显示按合同约定" /></el-form-item></el-col>
        </el-row>

        <el-divider content-position="left">租金保证金收取单位与收款账户（昌记）</el-divider>
        <el-form-item label="租金保证金收取单位"><el-input v-model="headerForm.rentPayeeName" /></el-form-item>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="联系人"><el-input v-model="headerForm.rentContactName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="联系电话"><el-input v-model="headerForm.rentContactPhone" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="收款账户/方式"><el-input v-model="headerForm.rentPaymentAccount" type="textarea" :rows="3" /></el-form-item>

        <el-divider content-position="left">物业保证金收取单位与收款账户（数智云仓）</el-divider>
        <el-form-item label="物业保证金收取单位"><el-input v-model="headerForm.propertyPayeeName" /></el-form-item>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="联系人"><el-input v-model="headerForm.propertyContactName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="联系电话"><el-input v-model="headerForm.propertyContactPhone" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="收款账户/方式"><el-input v-model="headerForm.propertyPaymentAccount" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button v-if="headerTarget && headerProfilesMap[headerTarget]" type="warning" plain :loading="headerDlg.saving" @click="resetHeaderToGeneral">恢复通用</el-button>
        <el-button @click="headerDlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="headerDlg.saving" @click="saveHeader">
          {{ headerTarget ? '保存为该单位定制抬头' : '保存通用抬头' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 多合同租户选择哪份合同登记缴纳/回执 -->
    <el-dialog v-model="pickDlg.visible" :title="`选择合同登记保证金缴纳与回执 - ${pickDlg.notice?.tenantName || ''}`" width="760px">
      <el-table :data="pickDlg.contracts" border size="small">
        <el-table-column prop="unitName" label="楼层/区域" min-width="200" show-overflow-tooltip />
        <el-table-column label="应收金额" width="125" align="right">
          <template #default="{ row }">¥{{ money(pickDlg.isRent ? row.rentDeposit : row.propertyDeposit) }}</template>
        </el-table-column>
        <el-table-column label="已缴金额" width="125" align="right">
          <template #default="{ row }">¥{{ money(pickDlg.isRent ? row.rentDepositPaid : row.propertyDepositPaid) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="depTag(pickDlg.isRent ? row.rentDepositStatus : row.propertyDepositStatus).type" size="small">
              {{ depTag(pickDlg.isRent ? row.rentDepositStatus : row.propertyDepositStatus).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="chooseContract(row)">缴纳 / 回执</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 预览 -->
    <el-dialog v-model="previewDlg.visible" width="1100px" top="4vh" :title="previewDlg.title" destroy-on-close>
      <iframe class="preview-frame" :srcdoc="previewDlg.html" />
      <template #footer>
        <el-button @click="previewDlg.visible = false">关闭</el-button>
        <el-button type="primary" @click="printBatch([previewDlg.notice])">打印</el-button>
      </template>
    </el-dialog>

    <NoticeSealDesigner
      v-model="sealDesigner.visible"
      :template-key="sealDesigner.category === 'RENT' ? 'DEPOSIT_RENT_NOTICE' : 'DEPOSIT_PROPERTY_NOTICE'"
      :title="sealDesigner.category === 'RENT' ? '租金保证金缴费通知单 · 电子印章排版' : '物业保证金缴费通知单 · 电子印章排版'"
      :notice-markup="sealSampleMarkup"
      :seals="seals"
      :placement="sealDesigner.category === 'RENT' ? rentSeal : propertySeal"
      :initial-seal-id="sealDesigner.initialSealId"
      @saved="onSealSaved"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, Printer } from '@element-plus/icons-vue'
import { leaseFeeApi, noticeHeaderProfileApi } from '@/api/energy'
import { buildTenantChoices, mergeDepositConfigWithProfile } from '../noticeHeaderHelper'
import { sealApi } from '@/api/oa'
import { applyFilter, filterOptions, money, num } from '../feePeriod'
import { LEASE_NOTICE_CSS } from '../leaseNoticePrint'
import { depositNoticeHtml, printDepositNotices } from '../depositNoticePrint'
import FeeFilterBar from './FeeFilterBar.vue'
import NoticeSealDesigner from './NoticeSealDesigner.vue'

const props = defineProps({
  contracts: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false }
})
const emit = defineEmits(['open-deposit', 'calibrated'])

const config = ref(null)
const seals = ref([])
const rentSeal = ref(null)
const propertySeal = ref(null)
const calibrating = ref(false)
const sealDesigner = reactive({ visible: false, category: 'RENT', initialSealId: null })
const anySealEnabled = computed(() => rentSeal.value?.enabled === 1 || propertySeal.value?.enabled === 1)

const activeContracts = computed(() => props.contracts.filter((c) => c.status === 1))

function monthlyRentOf(c) {
  if (Number(c.fixedMonthlyRent || 0) > 0) return Number(c.fixedMonthlyRent)
  return Number(c.leaseArea || 0) * Number(c.rentUnitPrice || 0)
}
function monthlyPropOf(c) {
  return Number(c.leaseArea || 0) * Number(c.propertyUnitPrice || 0)
}

function stateOf(due, paid, statuses) {
  if (due <= 0) return 'NONE'
  if (statuses.every((s) => Number(s) === 2) || paid >= due - 0.005) return 'PAID'
  if (paid > 0 || statuses.some((s) => Number(s) > 0)) return 'PARTIAL'
  return 'UNPAID'
}

function stateMeta(due, paid, st) {
  if (st === 'PAID') return { label: '已缴纳', type: 'success' }
  if (st === 'PARTIAL') return { label: '部分缴纳', type: 'warning' }
  return { label: '未缴纳', type: 'info' }
}

function depTag(st) {
  const n = Number(st || 0)
  if (n === 2) return { label: '已缴纳', type: 'success' }
  if (n === 1) return { label: '部分缴纳', type: 'warning' }
  return { label: '未缴纳', type: 'info' }
}

// 按租户聚合成两类通知单（昌记-租金保证金 / 数智云仓-物业保证金），逻辑与缴费汇总完全一致
function buildNotices(category) {
  const isRent = category === 'RENT'
  const prefix = config.value?.noticePrefix || 'DIPARK'
  const payeeName = isRent ? (config.value?.rentPayeeName || '') : (config.value?.propertyPayeeName || '')
  const paymentAccount = isRent ? (config.value?.rentPaymentAccount || '') : (config.value?.propertyPaymentAccount || '')

  const groups = new Map()
  for (const c of activeContracts.value) {
    const due = Number(isRent ? c.rentDeposit : c.propertyDeposit) || 0
    if (due <= 0) continue
    const key = c.tenantName || '-'
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key).push(c)
  }

  let idx = 0
  const out = []
  for (const [tenantName, list] of groups.entries()) {
    idx++
    const items = list.map((c, i) => {
      const due = Number(isRent ? c.rentDeposit : c.propertyDeposit) || 0
      const paid = Number(isRent ? c.rentDepositPaid : c.propertyDepositPaid) || 0
      const monthly = isRent ? monthlyRentOf(c) : monthlyPropOf(c)
      return {
        seq: String(i + 1),
        contractId: c.id,
        feeItem: isRent ? '租金保证金' : '物业管理费保证金',
        unitName: c.unitName,
        leaseArea: Number(c.leaseArea || 0),
        unitPrice: Number(isRent ? c.rentUnitPrice : c.propertyUnitPrice),
        monthlyAmount: Math.round(monthly * 100) / 100,
        standardDesc: '2个月',
        depositDue: due,
        depositPaid: paid,
        depositUnpaid: Math.max(0, Math.round((due - paid) * 100) / 100),
        remark: c.depositTerms || c.depositRemark || ''
      }
    })
    const totalDue = Math.round(items.reduce((s, x) => s + x.depositDue, 0) * 100) / 100
    const totalPaid = Math.round(items.reduce((s, x) => s + x.depositPaid, 0) * 100) / 100
    const totalUnpaid = Math.max(0, Math.round((totalDue - totalPaid) * 100) / 100)
    const receiptCount = list.reduce((s, c) => s + Number(isRent ? c.rentReceiptCount : c.propertyReceiptCount || 0), 0)
    const payState = stateOf(totalDue, totalPaid, list.map((c) => (isRent ? c.rentDepositStatus : c.propertyDepositStatus)))
    const prof = headerProfilesMap.value[tenantName]
    const effCfg = mergeDepositConfigWithProfile(config.value, prof)
    const effPrefix = effCfg?.noticePrefix || prefix
    const effPayee = (isRent ? effCfg?.rentPayeeName : effCfg?.propertyPayeeName) || payeeName
    const effAcc = (isRent ? effCfg?.rentPaymentAccount : effCfg?.propertyPaymentAccount) || paymentAccount
    out.push({
      feeCategory: category,
      noticeNo: `${effPrefix}-BZJ-${isRent ? 'ZJ' : 'WY'}-${String(idx).padStart(2, '0')}`,
      tenantName,
      unitName: [...new Set(list.map((c) => c.unitName).filter(Boolean))].join('、'),
      agreementNo: [...new Set(list.map((c) => c.agreementNo).filter((v) => v && v !== '待补充'))].join('；'),
      leaseArea: Math.round(list.reduce((s, c) => s + Number(c.leaseArea || 0), 0) * 100) / 100,
      payeeName: effPayee,
      paymentAccount: effAcc,
      headerConfig: effCfg,
      totalDue,
      totalPaid,
      totalUnpaid,
      receiptCount,
      payState,
      items,
      contracts: list
    })
  }
  return out
}

const rentNotices = computed(() => buildNotices('RENT'))
const propertyNotices = computed(() => buildNotices('PROPERTY'))

const summaryRows = computed(() => {
  const map = new Map()
  for (const c of activeContracts.value) {
    const key = c.tenantName || '-'
    const cur = map.get(key) || {
      tenantName: key, units: new Set(), leaseArea: 0,
      rentDue: 0, rentPaid: 0, rentStatuses: [],
      propDue: 0, propPaid: 0, propStatuses: []
    }
    if (c.unitName) cur.units.add(c.unitName)
    cur.leaseArea += Number(c.leaseArea || 0)
    cur.rentDue += Number(c.rentDeposit || 0)
    cur.rentPaid += Number(c.rentDepositPaid || 0)
    if (Number(c.rentDeposit || 0) > 0) cur.rentStatuses.push(c.rentDepositStatus)
    cur.propDue += Number(c.propertyDeposit || 0)
    cur.propPaid += Number(c.propertyDepositPaid || 0)
    if (Number(c.propertyDeposit || 0) > 0) cur.propStatuses.push(c.propertyDepositStatus)
    map.set(key, cur)
  }
  return [...map.values()].map((r) => {
    const totalDue = Math.round((r.rentDue + r.propDue) * 100) / 100
    const totalPaid = Math.round((r.rentPaid + r.propPaid) * 100) / 100
    return {
      tenantName: r.tenantName,
      unitName: [...r.units].join('、'),
      leaseArea: Math.round(r.leaseArea * 100) / 100,
      rentDue: Math.round(r.rentDue * 100) / 100,
      rentPaid: Math.round(r.rentPaid * 100) / 100,
      rentState: stateOf(r.rentDue, r.rentPaid, r.rentStatuses),
      propDue: Math.round(r.propDue * 100) / 100,
      propPaid: Math.round(r.propPaid * 100) / 100,
      propState: stateOf(r.propDue, r.propPaid, r.propStatuses),
      totalDue,
      totalPaid,
      totalUnpaid: Math.max(0, Math.round((totalDue - totalPaid) * 100) / 100)
    }
  })
})

const filter = reactive({ company: '', floor: '' })
const opts = computed(() => filterOptions(summaryRows.value, (r) => r.tenantName, (r) => r.unitName))
const viewSummaryRows = computed(() => applyFilter(summaryRows.value, filter, (r) => r.tenantName, (r) => r.unitName))
const viewRentNotices = computed(() => applyFilter(rentNotices.value, filter, (r) => r.tenantName, (r) => r.unitName))
const viewPropertyNotices = computed(() => applyFilter(propertyNotices.value, filter, (r) => r.tenantName, (r) => r.unitName))

const sumNotices = (list) => {
  const due = list.reduce((s, n) => s + n.totalDue, 0)
  const paid = list.reduce((s, n) => s + n.totalPaid, 0)
  return { due, paid, unpaid: Math.max(0, due - paid) }
}
const rentTotals = computed(() => sumNotices(rentNotices.value))
const propTotals = computed(() => sumNotices(propertyNotices.value))

async function loadConfigAndSeals() {
  try {
    const [cfg, profs] = await Promise.all([
      leaseFeeApi.depositConfig(),
      noticeHeaderProfileApi.list('DEPOSIT')
    ])
    config.value = cfg
    headerProfiles.value = profs || []
  } catch { /* ignore */ }
  try {
    const [list, rSeal, pSeal] = await Promise.all([
      sealApi.list({ onlyEnabled: true }),
      sealApi.getPlacement('DEPOSIT_RENT_NOTICE'),
      sealApi.getPlacement('DEPOSIT_PROPERTY_NOTICE')
    ])
    seals.value = list || []
    rentSeal.value = rSeal || null
    propertySeal.value = pSeal || null
  } catch { /* ignore */ }
}

const headerDlg = reactive({ visible: false, saving: false })
const headerForm = reactive({})
const headerTarget = ref('')
const headerProfiles = ref([])
const headerProfilesMap = computed(() => {
  const m = {}
  for (const p of headerProfiles.value) {
    if (p?.tenantName) m[p.tenantName.trim()] = p
  }
  return m
})
const headerTenantChoices = computed(() =>
  buildTenantChoices(activeContracts.value, [], headerProfiles.value)
)

function fillHeaderFormFor(target) {
  const base = config.value || {}
  if (!target) {
    Object.assign(headerForm, { ...base })
    return
  }
  const merged = mergeDepositConfigWithProfile(base, headerProfilesMap.value[target])
  Object.assign(headerForm, merged)
}

function onHeaderTargetChange(val) {
  fillHeaderFormFor(val)
}

async function openHeader() {
  headerTarget.value = ''
  fillHeaderFormFor('')
  headerDlg.visible = true
  try {
    headerProfiles.value = (await noticeHeaderProfileApi.list('DEPOSIT')) || []
  } catch { /* ignore */ }
}

async function saveHeader() {
  headerDlg.saving = true
  try {
    if (!headerTarget.value) {
      config.value = await leaseFeeApi.saveDepositConfig({ ...headerForm })
      headerDlg.visible = false
      ElMessage.success('保证金通用收取单位与通知单抬头已保存')
    } else {
      await noticeHeaderProfileApi.save({
        scopeType: 'DEPOSIT',
        tenantName: headerTarget.value,
        parkName: headerForm.parkName,
        noticePrefix: headerForm.noticePrefix,
        issueDate: headerForm.issueDate,
        dueDate: headerForm.dueDate,
        rentPayeeName: headerForm.rentPayeeName,
        rentContactName: headerForm.rentContactName,
        rentContactPhone: headerForm.rentContactPhone,
        rentPaymentAccount: headerForm.rentPaymentAccount,
        propertyPayeeName: headerForm.propertyPayeeName,
        propertyContactName: headerForm.propertyContactName,
        propertyContactPhone: headerForm.propertyContactPhone,
        propertyPaymentAccount: headerForm.propertyPaymentAccount
      })
      headerProfiles.value = (await noticeHeaderProfileApi.list('DEPOSIT')) || []
      ElMessage.success(`已保存「${headerTarget.value}」的保证金定制抬头`)
    }
  } finally {
    headerDlg.saving = false
  }
}

async function resetHeaderToGeneral() {
  if (!headerTarget.value) return
  headerDlg.saving = true
  try {
    await noticeHeaderProfileApi.remove('DEPOSIT', headerTarget.value)
    headerProfiles.value = (await noticeHeaderProfileApi.list('DEPOSIT')) || []
    fillHeaderFormFor(headerTarget.value)
    ElMessage.success(`「${headerTarget.value}」已恢复为通用抬头`)
  } finally {
    headerDlg.saving = false
  }
}

async function calibrate() {
  await ElMessageBox.confirm(
    '将按「2个月租金（昌记）+ 2个月物业管理费（数智云仓）」重新核定所有在租合同的应收保证金（已登记的已缴金额和回执附件不会被清空）。确定执行？',
    '按2个月标准校准保证金',
    { type: 'warning', confirmButtonText: '确认校准', cancelButtonText: '取消' }
  )
  calibrating.value = true
  try {
    const r = await leaseFeeApi.calibrateDeposits()
    ElMessage.success(`已校准完成：共核对 ${r.total} 份合同，更新 ${r.updated} 份`)
    emit('calibrated')
  } finally {
    calibrating.value = false
  }
}

const sealSampleMarkup = computed(() => {
  const isRent = sealDesigner.category === 'RENT'
  const list = isRent ? rentNotices.value : propertyNotices.value
  const sample = list[0] || {
    feeCategory: sealDesigner.category,
    noticeNo: `${config.value?.noticePrefix || 'DIPARK'}-BZJ-${isRent ? 'ZJ' : 'WY'}-01`,
    tenantName: '示例租户有限公司',
    unitName: '1层 A区',
    leaseArea: 1200,
    totalDue: isRent ? 72000 : 4800,
    totalPaid: 0,
    totalUnpaid: isRent ? 72000 : 4800,
    items: [{ seq: '1', feeItem: isRent ? '租金保证金' : '物业管理费保证金', unitName: '1层 A区', leaseArea: 1200, unitPrice: isRent ? 30 : 2, monthlyAmount: isRent ? 36000 : 2400, standardDesc: '2个月', depositDue: isRent ? 72000 : 4800, depositPaid: 0, depositUnpaid: isRent ? 72000 : 4800, remark: '' }]
  }
  return depositNoticeHtml(config.value, sample, null)
})

async function onSealCommand(cmd) {
  await loadConfigAndSeals()
  const [cat, arg] = String(cmd).split(':')
  sealDesigner.category = cat === 'PROPERTY' ? 'PROPERTY' : 'RENT'
  if (arg && arg !== 'design') {
    sealDesigner.initialSealId = Number(arg)
  } else {
    const cur = sealDesigner.category === 'RENT' ? rentSeal.value : propertySeal.value
    sealDesigner.initialSealId = cur?.sealId || seals.value[0]?.id || null
  }
  sealDesigner.visible = true
}
function onSealSaved(saved) {
  if (sealDesigner.category === 'RENT') rentSeal.value = saved
  else propertySeal.value = saved
}

const pickDlg = reactive({ visible: false, notice: null, isRent: true, contracts: [] })
function openPaymentForNotice(notice) {
  const list = notice.contracts || []
  if (list.length === 1) {
    emit('open-deposit', list[0])
    return
  }
  pickDlg.notice = notice
  pickDlg.isRent = notice.feeCategory === 'RENT'
  pickDlg.contracts = list
  pickDlg.visible = true
}
function chooseContract(contract) {
  pickDlg.visible = false
  emit('open-deposit', contract)
}

const previewDlg = reactive({ visible: false, title: '', html: '', notice: null })
function preview(row) {
  previewDlg.notice = row
  previewDlg.title = `${row.feeCategory === 'RENT' ? '租金保证金' : '物业保证金'}通知单预览 - ${row.tenantName}`
  const placement = row.feeCategory === 'RENT' ? rentSeal.value : propertySeal.value
  previewDlg.html = `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><style>${LEASE_NOTICE_CSS}</style></head><body>${depositNoticeHtml(config.value, row, placement)}</body></html>`
  previewDlg.visible = true
}
function printBatch(list) {
  if (!list?.length) return
  if (!printDepositNotices(config.value, list, true, { RENT: rentSeal.value, PROPERTY: propertySeal.value })) {
    ElMessage.warning('浏览器拦截了打印窗口，请允许弹出窗口')
  }
}

function tenantSummary({ columns, data }) {
  const sum = (f) => data.reduce((s, r) => s + Number(f(r) || 0), 0)
  return columns.map((col, i) => {
    if (i === 0) return '合计'
    switch (col.label) {
      case '计租面积(㎡)': return num(sum((r) => r.leaseArea))
      case '租金保证金(应收)': return '¥' + money(sum((r) => r.rentDue))
      case '物业保证金(应收)': return '¥' + money(sum((r) => r.propDue))
      case '保证金应收合计': return '¥' + money(sum((r) => r.totalDue))
      case '已缴合计': return '¥' + money(sum((r) => r.totalPaid))
      case '待收余额': return '¥' + money(sum((r) => r.totalUnpaid))
      default: return ''
    }
  })
}

function noticeSummary({ columns, data }) {
  const sum = (f) => data.reduce((s, r) => s + Number(f(r) || 0), 0)
  return columns.map((col, i) => {
    if (i === 0) return '合计'
    switch (col.label) {
      case '应收租金保证金':
      case '应收物业保证金': return '¥' + money(sum((r) => r.totalDue))
      case '已缴金额': return '¥' + money(sum((r) => r.totalPaid))
      case '待缴余额': return '¥' + money(sum((r) => r.totalUnpaid))
      default: return ''
    }
  })
}

onMounted(loadConfigAndSeals)
</script>

<style scoped>
.payee-bar { display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 12px; margin-bottom: 12px; }
.payee-card { padding: 10px 14px; border-radius: 8px; border: 1px solid var(--el-border-color-lighter); background: var(--el-fill-color-blank); }
.payee-card.rent { border-left: 4px solid var(--el-color-primary); }
.payee-card.property { border-left: 4px solid var(--el-color-success); }
.payee-head { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; flex-wrap: wrap; }
.payee-acc { font-size: 12px; color: var(--el-text-color-secondary); white-space: pre-wrap; line-height: 1.5; }
.summary-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(175px, 1fr)); gap: 10px; margin-bottom: 12px; }
.summary-item { padding: 10px 14px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; display: flex; flex-direction: column; gap: 4px; background: var(--el-fill-color-blank); }
.summary-item span { font-size: 12px; color: var(--el-text-color-secondary); }
.summary-item strong { font-size: 16px; font-variant-numeric: tabular-nums; }
.summary-item strong small { font-size: 12px; font-weight: 500; color: var(--el-text-color-secondary); }
.summary-item.hl strong { color: var(--el-color-primary); }
.summary-item.hl-green strong { color: var(--el-color-success); }
.summary-item.total strong { color: var(--el-color-danger); }
.toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; flex-wrap: wrap; }
.toolbar-tip { font-size: 12px; color: var(--el-text-color-secondary); margin-bottom: 12px; line-height: 1.6; }
.section-title { margin: 16px 0 8px; font-size: 14px; display: flex; align-items: center; }
.ok-num { color: var(--el-color-success); font-weight: 600; }
.muted { color: var(--el-text-color-placeholder); font-size: 12px; }
.preview-frame { width: 100%; height: 72vh; border: 1px solid var(--el-border-color); border-radius: 6px; background: #fff; }
.notice-table :deep(.el-table__body tr.notice-payment-paid > td.el-table__cell) { background-color: var(--el-color-success-light-9) !important; }
.notice-table :deep(.el-table__body tr.notice-payment-paid:hover > td.el-table__cell) { background-color: var(--el-color-success-light-8) !important; }
.seal-dot { display: inline-block; width: 7px; height: 7px; border-radius: 50%; background: #dc2626; margin-right: 6px; }
.seal-drop-item { display: flex; align-items: center; gap: 8px; }
.seal-drop-img { width: 22px; height: 18px; object-fit: contain; }
.header-opt-row { display: flex; justify-content: space-between; align-items: center; gap: 12px; width: 100%; }
.header-scope-tip { margin-top: 4px; font-size: 12px; line-height: 1.5; color: var(--el-text-color-secondary); }
</style>
