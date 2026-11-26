<template>
  <div class="page-container">
    <template v-if="!month && !leaseMonth">
      <FeeMonthPicker ref="picker" kind="summary" @select="(m) => (month = m)" />
      <div class="lease-picker-section">
        <LeaseMonthPicker ref="leasePicker" kind="summary" @select="(m) => (leaseMonth = m)" />
      </div>
    </template>

    <LeaseFeeSummaryDetail v-else-if="leaseMonth" :month="leaseMonth" @back="leaseMonth = null" />

    <template v-else>
      <div class="detail-head">
        <el-button @click="back"><el-icon><ArrowLeft /></el-icon>返回年月表</el-button>
        <h3>{{ monthLabel }} · 水电费、垃圾处理费缴费汇总</h3>
        <el-tag v-if="period" :type="locked ? 'success' : 'info'">{{ locked ? '已确认出账' : '草稿' }}</el-tag>
        <span v-if="period?.generatedTime" class="gen-time">通知单生成于 {{ period.generatedTime }}</span>
      </div>

      <el-alert v-if="period?.noticeStale && !locked" type="warning" show-icon :closable="false" style="margin-bottom: 10px"
                title="生成通知单之后，读数、总量、垃圾总额或区域/用户又有改动，下面的通知单是旧的。请重新生成后再核对、确认出账。">
        <el-button type="warning" size="small" :loading="generating" @click="generate">立即重新生成</el-button>
      </el-alert>

      <FeeBillFiles v-if="period" :period-id="period.id" :kinds="[BILL_KINDS.electric, BILL_KINDS.water, BILL_KINDS.garbage]" />

      <div v-if="period" class="summary-grid">
        <div class="summary-item"><span>电:南网 + 光伏</span><strong>{{ num(Number(period.electricGridUsage || 0) + Number(period.electricSolarUsage || 0)) }} 度</strong></div>
        <div class="summary-item"><span>电:公摊度数</span><strong>{{ num(period.electricPublicUsage) }}</strong></div>
        <div class="summary-item"><span>水:总表 / 公摊</span><strong>{{ num(period.waterUsedTotal) }} / {{ num(period.waterPublicUsage) }}</strong></div>
        <div class="summary-item"><span>垃圾:生活 / 工业</span><strong>¥{{ money(period.domesticGarbageAmount) }} / ¥{{ money(period.industrialGarbageAmount) }}</strong></div>
        <div class="summary-item"><span>通知单</span><strong>{{ notices.length }} 张</strong></div>
        <div class="summary-item total"><span>本期应收合计</span><strong>¥{{ money(allTotals.totalDue) }}</strong></div>
        <div v-if="filterActive" class="summary-item hl"><span>当前检索合计（{{ viewNotices.length }} 张）</span><strong>¥{{ money(totals.totalDue) }}</strong></div>
      </div>

      <div class="table-card">
        <div class="toolbar">
          <el-button type="primary" :disabled="locked" :loading="generating" @click="generate">
            {{ notices.length ? '重新生成通知单' : '生成通知单' }}
          </el-button>
          <el-button @click="openHeader">通知单抬头</el-button>
          <el-dropdown trigger="click" @command="onSealCommand">
            <el-button :type="sealPlacement?.enabled === 1 ? 'danger' : ''" plain>
              <span v-if="sealPlacement?.enabled === 1" class="seal-dot" />
              电子印章{{ sealPlacement?.enabled === 1 && sealPlacement.sealName ? `（${shortSealName(sealPlacement.sealName)}）` : '' }}
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-for="s in seals" :key="s.id" :command="`seal:${s.id}`">
                  <div class="seal-drop-item">
                    <img v-if="s.imageData" :src="s.imageData" class="seal-drop-img" alt="" />
                    <span>{{ s.name }}</span>
                    <el-tag v-if="sealPlacement?.enabled === 1 && String(sealPlacement.sealId) === String(s.id)" size="small" type="danger" effect="plain">已应用</el-tag>
                  </div>
                </el-dropdown-item>
                <el-dropdown-item v-if="!seals.length" disabled>暂未上传印章，请先到「印章管理」上传</el-dropdown-item>
                <el-dropdown-item divided command="design">打开通知单模板排版（拖动位置/缩放）</el-dropdown-item>
                <el-dropdown-item v-if="sealPlacement?.enabled === 1" command="disable">取消通知单电子印章</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button :disabled="!notices.length" @click="exportExcel"><el-icon><Download /></el-icon>导出 Excel</el-button>
          <el-button :disabled="!notices.length" @click="printAll"><el-icon><Printer /></el-icon>{{ filterActive ? "打印检索结果" : "打印全部" }}</el-button>
          <el-button :disabled="!selected.length" @click="printSelected">打印选中</el-button>
          <el-button type="danger" plain :disabled="!selected.length" @click="deleteSelected">
            <el-icon><Delete /></el-icon>删除选中<span v-if="selected.length">（{{ selected.length }}）</span>
          </el-button>
          <el-button type="danger" plain :disabled="!notices.length" @click="deleteAll">一键删除全部</el-button>
          <el-button v-if="!locked" type="success" :disabled="!notices.length || period?.noticeStale" @click="confirm">确认出账</el-button>
          <el-button v-else type="warning" @click="revoke">撤销确认</el-button>
        </div>
        <div class="toolbar-tip">先在「电费 / 水费 / 垃圾处理费」填好总量与读数，这里按租户生成通知单（一个租户一张，名下各区域的水电垃圾费按区域分行列在同一张单上）、核对、打印；确认出账后每个租户生成一张能源费账单，可在财务收款。删掉的通知单重新生成时会按读数重新出现，要永久去掉某个区域/用户请到电费、水费页删除对应区域。</div>
        <el-alert v-for="(w, i) in warnings" :key="i" :title="w" type="warning" :closable="false" show-icon style="margin-bottom: 8px" />

        <!-- 检索:按公司、楼层;下面的明细表、通知单列表和各合计都跟着检索结果走 -->
        <FeeFilterBar :model="filter" :companies="opts.companies" :floors="opts.floors">
          检索结果 {{ viewNotices.length }} / {{ notices.length }} 张通知单 · 应收合计 ¥{{ money(totals.totalDue) }}
        </FeeFilterBar>

        <!-- 应收收费明细表:行 = 费用项目,列 = 每户 -->
        <el-table :data="matrix" v-loading="loading || listLoading" border size="small" class="matrix">
          <el-table-column prop="seq" label="序号" width="60" fixed />
          <el-table-column prop="label" label="费用项目" width="130" fixed>
            <template #default="{ row }"><span :class="{ strong: row.strong }">{{ row.label }}</span></template>
          </el-table-column>
          <el-table-column v-for="n in viewNotices" :key="n.id" :min-width="150" align="right">
            <template #header>
              <div class="col-head" :title="n.tenantName">
                <div class="col-main">{{ shortName(n.tenantName) }}</div>
                <div class="col-sub">{{ n.unitName }}</div>
              </div>
            </template>
            <template #default="{ row }">
              <span :class="{ strong: row.strong }">{{ money(n[row.key]) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="合计金额/元" width="140" align="right" fixed="right">
            <template #default="{ row }"><strong>{{ money(totals[row.key]) }}</strong></template>
          </el-table-column>
        </el-table>
        <div v-if="!loading && !listLoading && !notices.length" class="empty-tip">本月还没有生成通知单（或已全部删除）。</div>

        <!-- 各户通知单 -->
        <h4 class="section-title">缴费通知单（{{ viewNotices.length }}{{ filterActive ? ` / ${notices.length}` : '' }} 张）</h4>
        <el-table ref="noticeTable" :data="viewNotices" border size="small" row-key="id" class="notice-table" :row-class-name="({ row }) => noticeRowClass(row)"
                  show-summary :summary-method="noticeSummary" @selection-change="(rows) => (selected = rows)">
          <el-table-column type="selection" width="45" />
          <el-table-column type="index" label="序号" width="55" />
          <el-table-column prop="noticeNo" label="单据编号" width="190" />
          <el-table-column prop="unitName" label="厂房/单元" min-width="170" show-overflow-tooltip />
          <el-table-column prop="tenantName" label="租户/公司名称" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.tenantName }}
              <el-tag v-if="!row.tenantRefId" size="small" type="warning" effect="plain" style="margin-left: 4px">未关联合同</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="参与公摊" width="85" align="center">
            <template #default="{ row }">{{ row.sharePublic ? '是' : '否' }}</template>
          </el-table-column>
          <el-table-column label="费用小计" width="120" align="right"><template #default="{ row }">¥{{ money(row.subtotal) }}</template></el-table-column>
          <el-table-column label="已付" width="100" align="right"><template #default="{ row }">{{ money(row.paidAmount) }}</template></el-table-column>
          <el-table-column label="前期欠费" width="100" align="right"><template #default="{ row }">{{ money(row.priorArrears) }}</template></el-table-column>
          <el-table-column label="本期应收合计" width="130" align="right"><template #default="{ row }"><strong>¥{{ money(row.totalDue) }}</strong></template></el-table-column>
          <el-table-column label="账单" width="80" align="center">
            <template #default="{ row }"><el-tag v-if="row.billId" size="small" type="success" effect="plain">已出账</el-tag><span v-else>-</span></template>
          </el-table-column>
          <el-table-column label="缴费状态" width="88" align="center">
            <template #default="{ row }"><el-tag :type="Number(row.paymentStatus) === 1 ? 'success' : 'info'" size="small">{{ Number(row.paymentStatus) === 1 ? '已缴费' : '未缴费' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="开票状态" width="94" align="center">
            <template #default="{ row }">
              <span v-if="Number(row.invoiceStatus) === 1" class="rainbow-badge">已开票</span>
              <el-tag v-else type="info" size="small">未开票</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="335" fixed="right">
            <template #default="{ row }">
              <el-button link type="success" @click="openPayment(row)">缴费</el-button>
              <el-button link class="invoice-btn" @click="openInvoice(row)">发票</el-button>
              <el-button link type="primary" @click="preview(row)">预览</el-button>
              <el-button link type="primary" @click="printOne(row)">打印</el-button>
              <el-button link type="primary" :disabled="locked" @click="edit(row)">调整</el-button>
              <el-button link type="danger" @click="deleteOne(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>

    <!-- 通知单抬头 -->
    <el-dialog v-model="headerDialog.visible" title="通知单抬头与计费期间" width="660px">
      <el-form :model="headerForm" label-width="120px">
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
              当前为 <strong>{{ headerTarget }}</strong> 的{{ headerProfilesMap[headerTarget] ? '定制' : '独立' }}抬头；保存后标记为「定制」，以后每月生成水电/垃圾费通知单均自动沿用（如独立缴费截止日）。
            </template>
            <template v-else>
              当前为全体未定制单位共用的「通用抬头」；如需给某家单位单独设置缴费截止日或收款信息，请从上方下拉选择具体缴费单位。
            </template>
          </div>
        </el-form-item>
        <el-divider />
        <el-form-item label="园区名称"><el-input v-model="headerForm.parkName" /></el-form-item>
        <el-form-item label="单据编号前缀"><el-input v-model="headerForm.noticePrefix" placeholder="如 DIPARK" style="width: 200px" /></el-form-item>
        <el-form-item label="计费期间">
          <el-date-picker v-model="headerForm.periodStart" type="date" value-format="YYYY-MM-DD" style="width: 160px" />
          <span style="margin: 0 8px">~</span>
          <el-date-picker v-model="headerForm.periodEnd" type="date" value-format="YYYY-MM-DD" style="width: 160px" />
        </el-form-item>
        <el-form-item label="开具日期"><el-date-picker v-model="headerForm.issueDate" type="date" value-format="YYYY-MM-DD" style="width: 160px" /></el-form-item>
        <el-form-item label="缴费截止日"><el-date-picker v-model="headerForm.dueDate" type="date" value-format="YYYY-MM-DD" style="width: 160px" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="headerForm.contactName" style="width: 200px" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="headerForm.contactPhone" style="width: 200px" /></el-form-item>
        <el-form-item label="收款账户/方式"><el-input v-model="headerForm.paymentAccount" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button v-if="headerTarget && headerProfilesMap[headerTarget]" type="warning" plain :disabled="locked" :loading="headerSaving" @click="resetHeaderToGeneral">恢复通用</el-button>
        <el-button @click="headerDialog.visible = false">取消</el-button>
        <el-button type="primary" :disabled="locked" :loading="state.saving || headerSaving" @click="saveHeader">
          {{ headerTarget ? '保存为该单位定制抬头' : '保存通用抬头' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 调整通知单:通知单抬头 + 基本信息 + 各行备注 + 新增收费项,一个弹窗改完 -->
    <el-dialog v-model="editDialog.visible" :title="`调整通知单 - ${editForm.unitName || ''}`" width="1040px" top="4vh" destroy-on-close>
      <el-form :model="editForm" label-width="110px" size="small" class="edit-form">
        <el-divider content-position="left">通知单抬头与基本信息（仅对本张通知单生效）</el-divider>
        <el-row :gutter="14">
          <el-col :span="12"><el-form-item label="园区名称"><el-input v-model="editForm.parkName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="单据编号"><el-input v-model="editForm.noticeNo" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="租户/公司名称"><el-input v-model="editForm.tenantName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="厂房/单元"><el-input v-model="editForm.unitName" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="收费期间">
              <div style="display:flex;align-items:center;gap:6px;width:100%">
                <el-date-picker v-model="editForm.periodStart" type="date" value-format="YYYY-MM-DD" style="flex:1" />
                <span>~</span>
                <el-date-picker v-model="editForm.periodEnd" type="date" value-format="YYYY-MM-DD" style="flex:1" />
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="6"><el-form-item label="开具日期" label-width="76px"><el-date-picker v-model="editForm.issueDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="缴费截止日" label-width="84px"><el-date-picker v-model="editForm.dueDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="联系人"><el-input v-model="editForm.contactName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="联系电话"><el-input v-model="editForm.contactPhone" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="收款账户/方式"><el-input v-model="editForm.paymentAccount" type="textarea" :rows="2" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="已付金额"><el-input-number v-model="editForm.paidAmount" :min="0" :precision="2" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="前期欠费"><el-input-number v-model="editForm.priorArrears" :min="0" :precision="2" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="editForm.remark" type="textarea" :rows="2" placeholder="印在通知单表头「备注」一栏" /></el-form-item></el-col>
        </el-row>
      </el-form>

      <div class="items-head">
        <strong>收费明细</strong>
        <span class="form-tip">水费、电费、垃圾费由读数自动算出，金额不能改，备注可改；下面可以随时新增收费项（维修费、违约金、减免等），金额可为负数。</span>
        <el-button type="primary" size="small" @click="addManual"><el-icon><Plus /></el-icon>新增收费项</el-button>
      </div>
      <el-table :data="editItems" border size="small" class="items-table" :row-class-name="({ row }) => (row.auto ? 'item-auto' : 'item-manual')">
        <el-table-column label="序号" width="58" align="center">
          <template #default="{ row }">{{ row.seq }}</template>
        </el-table-column>
        <el-table-column label="费用项目" min-width="170">
          <template #default="{ row }">
            <span v-if="row.auto" :class="{ 'sub-line': row.detail }">{{ row.detail ? '　' : '' }}{{ row.feeItem }}</span>
            <el-input v-else v-model="row.feeItem" size="small" placeholder="如 设备维修费" maxlength="64" />
          </template>
        </el-table-column>
        <el-table-column label="数量" width="110" align="right">
          <template #default="{ row }">
            <span v-if="row.auto">{{ row.quantity == null ? '' : num(row.quantity, 4) }}</span>
            <el-input-number v-else v-model="row.quantity" size="small" :precision="2" :controls="false" style="width: 96px" @change="calcAmount(row)" />
          </template>
        </el-table-column>
        <el-table-column label="单位" width="80" align="center">
          <template #default="{ row }">
            <span v-if="row.auto">{{ row.unit }}</span>
            <el-input v-else v-model="row.unit" size="small" placeholder="元" maxlength="16" />
          </template>
        </el-table-column>
        <el-table-column label="单价(元)" width="110" align="right">
          <template #default="{ row }">
            <span v-if="row.auto">{{ row.unitPrice == null ? '' : num(row.unitPrice, 4) }}</span>
            <el-input-number v-else v-model="row.unitPrice" size="small" :precision="4" :controls="false" style="width: 96px" @change="calcAmount(row)" />
          </template>
        </el-table-column>
        <el-table-column label="金额(元)" width="125" align="right">
          <template #default="{ row }">
            <span v-if="row.auto">{{ row.detail || row.amount == null ? '' : money(row.amount) }}</span>
            <el-input-number v-else v-model="row.amount" size="small" :precision="2" :controls="false" style="width: 110px" />
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="240">
          <template #default="{ row }">
            <span v-if="row.detail" class="sub-line">{{ row.remark }}</span>
            <el-input v-else v-model="row.remark" size="small" maxlength="255" placeholder="备注" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70" align="center">
          <template #default="{ row, $index }">
            <el-button v-if="!row.auto" link type="danger" size="small" @click="editItems.splice($index, 1)">删除</el-button>
            <span v-else class="muted">自动</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="edit-total">
        <span>费用小计 <strong>¥{{ money(editSubtotal) }}</strong></span>
        <span>已付 − ¥{{ money(editForm.paidAmount) }}</span>
        <span>前期欠费 + ¥{{ money(editForm.priorArrears) }}</span>
        <span class="due">本期应收合计 <strong>¥{{ money(editDue) }}</strong></span>
      </div>
      <template #footer>
        <el-button @click="editDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="editDialog.saving" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 预览 -->
    <el-dialog v-model="previewDialog.visible" width="1100px" top="4vh" :title="previewDialog.title" destroy-on-close>
      <iframe class="preview-frame" :srcdoc="previewDialog.html" />
      <template #footer>
        <el-button @click="previewDialog.visible = false">关闭</el-button>
        <el-button type="primary" @click="printOne(previewDialog.notice)">打印</el-button>
      </template>
    </el-dialog>
    <FeeNoticePayment v-model="paymentDialog.visible" :notice="paymentDialog.notice" @saved="paymentSaved" />
    <NoticeInvoiceDialog v-model="invoiceDialog.visible" :notice="invoiceDialog.notice" mode="utility" @saved="invoiceSaved" />
    <NoticeSealDesigner
      v-model="sealDesigner.visible"
      template-key="UTILITY_NOTICE"
      title="水电/垃圾费缴费通知单 · 电子印章模板排版"
      :notice-markup="sealSampleMarkup"
      :seals="seals"
      :placement="sealPlacement"
      :initial-seal-id="sealDesigner.initialSealId"
      @saved="(p) => (sealPlacement = p)"
    />
  </div>
</template>

<script setup>
import { computed, onActivated, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, ArrowLeft, Delete, Download, Plus, Printer } from '@element-plus/icons-vue'
import { feePeriodApi, leaseFeeApi, noticeHeaderProfileApi } from '@/api/energy'
import { buildTenantChoices, resolveProfileDateForMonth } from './noticeHeaderHelper'
import { sealApi } from '@/api/oa'
import { useFeePeriod, money, num, downloadBlob, BILL_KINDS, applyFilter, filterOptions, floorBandClass, shortName } from './feePeriod'
import FeeFilterBar from './components/FeeFilterBar.vue'
import { noticeHtml, NOTICE_CSS, parseHeaderOverride, printNotices } from './noticePrint'
import FeeMonthPicker from './components/FeeMonthPicker.vue'
import FeeBillFiles from './components/FeeBillFiles.vue'
import FeeNoticePayment from './components/FeeNoticePayment.vue'
import NoticeInvoiceDialog from './components/NoticeInvoiceDialog.vue'
import NoticeSealDesigner from './components/NoticeSealDesigner.vue'
import LeaseMonthPicker from './components/LeaseMonthPicker.vue'
import LeaseFeeSummaryDetail from './components/LeaseFeeSummaryDetail.vue'

const leaseMonth = ref(null)
const leasePicker = ref(null)

const notices = ref([])
const selected = ref([])
const warnings = ref([])
const listLoading = ref(false)
const generating = ref(false)
const picker = ref(null)
const noticeTable = ref(null)
const seals = ref([])
const sealPlacement = ref(null)
const sealDesigner = reactive({ visible: false, initialSealId: null })
const { month, period, loading, state, save, reload, back } = useFeePeriod(loadNotices)
const locked = computed(() => period.value?.status === 'CONFIRMED')
const monthLabel = computed(() => (month.value ? `${month.value.slice(0, 4)}年${Number(month.value.slice(5))}月` : ''))

async function loadSealData() {
  try {
    const [list, placement] = await Promise.all([
      sealApi.list({ onlyEnabled: true }),
      sealApi.getPlacement('UTILITY_NOTICE')
    ])
    seals.value = list || []
    sealPlacement.value = placement || null
  } catch {
    // 无印章数据或测试环境忽略
  }
}

async function loadNotices() {
  if (!period.value) return
  listLoading.value = true
  try {
    notices.value = await feePeriodApi.notices(period.value.id) || []
    selected.value = []
    await loadSealData()
  } finally {
    listLoading.value = false
  }
}
watch(month, (v) => { filter.company = ''; filter.floor = ''; if (!v) { notices.value = []; warnings.value = [] } })

let firstActivation = true
onActivated(async () => {
  if (firstActivation) { firstActivation = false; return }
  if (!month.value && !leaseMonth.value) { picker.value?.refresh(); leasePicker.value?.refresh() }
  else if (month.value) { await reload(); await loadNotices() }
})

const MATRIX_ROWS = [
  { seq: '1', key: 'waterFee', label: '水费' },
  { seq: '2', key: 'waterPublicFee', label: '公摊水费' },
  { seq: '3', key: 'electricFee', label: '电费' },
  { seq: '4', key: 'electricPublicFee', label: '公摊电费' },
  { seq: '5', key: 'domesticGarbageFee', label: '生活垃圾清运费' },
  { seq: '6', key: 'industrialGarbageFee', label: '工业垃圾清运费' },
  { seq: '7', key: 'otherFee', label: '其他费用' },
  { seq: '一', key: 'subtotal', label: '费用小计', strong: true },
  { seq: '二', key: 'paidAmount', label: '已付金额' },
  { seq: '三', key: 'priorArrears', label: '前期欠费' },
  { seq: '四', key: 'totalDue', label: '本期应收合计', strong: true }
]
// 检索:按公司、楼层筛通知单;每一层交替深浅色。明细表、合计、通知单列表都用筛出来的这批
const filter = reactive({ company: '', floor: '' })
const companyOf = (n) => n.tenantName
const unitOf = (n) => n.unitName
const opts = computed(() => filterOptions(notices.value, companyOf, unitOf))
const filterActive = computed(() => !!(filter.company || filter.floor))
const viewNotices = computed(() => applyFilter(notices.value, filter, companyOf, unitOf))
const bandClass = computed(() => floorBandClass(notices.value, (n) => n.noticeNo))
const paymentDialog = reactive({ visible: false, notice: null })
const invoiceDialog = reactive({ visible: false, notice: null })
function noticeRowClass(row) {
  if (Number(row.invoiceStatus) === 1) return 'notice-invoice-issued'
  return Number(row.paymentStatus) === 1 ? 'notice-payment-paid' : bandClass.value(row)
}
function openPayment(row) {
  paymentDialog.notice = row
  paymentDialog.visible = true
}
function paymentSaved({ id, paymentStatus }) {
  const row = notices.value.find(item => String(item.id) === String(id))
  if (row) row.paymentStatus = paymentStatus
}
function openInvoice(row) {
  invoiceDialog.notice = row
  invoiceDialog.visible = true
}
function invoiceSaved({ id, invoiceStatus }) {
  const row = notices.value.find(item => String(item.id) === String(id))
  if (row) row.invoiceStatus = invoiceStatus
}
const matrix = computed(() => (viewNotices.value.length ? MATRIX_ROWS : []))
// 合计列 = 各户金额逐个相加(分为单位取整,避免浮点尾数),与通知单列表、导出 Excel 同源
const sumOf = (list) => {
  const t = {}
  for (const r of MATRIX_ROWS) {
    t[r.key] = list.reduce((s, n) => s + Math.round(Number(n[r.key] || 0) * 100), 0) / 100
  }
  return t
}
const totals = computed(() => sumOf(viewNotices.value))
const allTotals = computed(() => sumOf(notices.value))
// 通知单列表底部合计行(跟着检索走)
function noticeSummary({ columns, data }) {
  const t = sumOf(data)
  return columns.map((col, i) => {
    if (i === 1) return '合计'
    switch (col.label) {
      case '费用小计': return '¥' + money(t.subtotal)
      case '已付': return money(t.paidAmount)
      case '前期欠费': return money(t.priorArrears)
      case '本期应收合计': return '¥' + money(t.totalDue)
      default: return ''
    }
  })
}
async function generate() {
  generating.value = true
  try {
    const r = await feePeriodApi.generate(period.value.id)
    warnings.value = r.warnings || []
    ElMessage.success(`已生成 ${r.notices?.length || 0} 张通知单`)
    await reload()
    await loadNotices()
  } finally {
    generating.value = false
  }
}

const headerDialog = reactive({ visible: false })
const headerForm = reactive({})
const headerTarget = ref('')
const headerSaving = ref(false)
const headerProfiles = ref([])
const headerLedgerContracts = ref([])
const headerProfilesMap = computed(() => {
  const m = {}
  for (const p of headerProfiles.value) {
    if (p?.tenantName) m[p.tenantName.trim()] = p
  }
  return m
})
const headerTenantChoices = computed(() =>
  buildTenantChoices(headerLedgerContracts.value, notices.value, headerProfiles.value)
)

function fillHeaderFormFor(target) {
  const p = period.value || {}
  if (!target) {
    Object.assign(headerForm, {
      parkName: p.parkName, noticePrefix: p.noticePrefix, periodStart: p.periodStart, periodEnd: p.periodEnd,
      issueDate: p.issueDate, dueDate: p.dueDate, contactName: p.contactName, contactPhone: p.contactPhone,
      paymentAccount: p.paymentAccount
    })
    return
  }
  const prof = headerProfilesMap.value[target]
  const ym = p.period
  Object.assign(headerForm, {
    parkName: prof?.parkName || p.parkName || '',
    noticePrefix: prof?.noticePrefix || p.noticePrefix || '',
    periodStart: (prof?.periodStart && ym && String(prof.periodStart).startsWith(ym)) ? prof.periodStart : p.periodStart,
    periodEnd: (prof?.periodEnd && ym && String(prof.periodEnd).startsWith(ym)) ? prof.periodEnd : p.periodEnd,
    issueDate: resolveProfileDateForMonth(prof?.issueDate, prof?.issueMonthOffset, prof?.issueDay, ym) || p.issueDate,
    dueDate: resolveProfileDateForMonth(prof?.dueDate, prof?.dueMonthOffset, prof?.dueDay, ym) || p.dueDate,
    contactName: prof?.contactName || p.contactName || '',
    contactPhone: prof?.contactPhone || p.contactPhone || '',
    paymentAccount: prof?.paymentAccount || p.paymentAccount || ''
  })
}

function onHeaderTargetChange(val) {
  fillHeaderFormFor(val)
}

async function openHeader() {
  headerTarget.value = ''
  fillHeaderFormFor('')
  headerDialog.visible = true
  try {
    const [profs, contracts] = await Promise.all([
      noticeHeaderProfileApi.list('UTILITY'),
      leaseFeeApi.contracts(false)
    ])
    headerProfiles.value = profs || []
    headerLedgerContracts.value = contracts || []
  } catch { /* ignore */ }
}

async function saveHeader() {
  if (!headerTarget.value) {
    await save({ ...headerForm })
    headerDialog.visible = false
    ElMessage.success(notices.value.length ? '通用抬头已保存；单据编号前缀与计费期间要点「重新生成通知单」才会更新到各户' : '通用抬头已保存')
    return
  }
  headerSaving.value = true
  try {
    await noticeHeaderProfileApi.save({
      scopeType: 'UTILITY',
      tenantName: headerTarget.value,
      referencePeriod: period.value?.period,
      periodId: period.value?.id,
      parkName: headerForm.parkName,
      noticePrefix: headerForm.noticePrefix,
      periodStart: headerForm.periodStart,
      periodEnd: headerForm.periodEnd,
      issueDate: headerForm.issueDate,
      dueDate: headerForm.dueDate,
      contactName: headerForm.contactName,
      contactPhone: headerForm.contactPhone,
      paymentAccount: headerForm.paymentAccount
    })
    headerProfiles.value = (await noticeHeaderProfileApi.list('UTILITY')) || []
    await loadNotices()
    ElMessage.success(`已保存「${headerTarget.value}」的定制抬头，后续生成通知单将自动沿用`)
  } finally {
    headerSaving.value = false
  }
}

async function resetHeaderToGeneral() {
  if (!headerTarget.value) return
  headerSaving.value = true
  try {
    await noticeHeaderProfileApi.remove('UTILITY', headerTarget.value, period.value?.id)
    headerProfiles.value = (await noticeHeaderProfileApi.list('UTILITY')) || []
    fillHeaderFormFor(headerTarget.value)
    await loadNotices()
    ElMessage.success(`「${headerTarget.value}」已恢复为通用抬头`)
  } finally {
    headerSaving.value = false
  }
}

const editDialog = reactive({ visible: false, saving: false })
const editForm = reactive({})
// 弹窗里的明细行:自动行(只读金额,可改备注)+ 手工行(整行可改);detail = 分表子行,只读
const editItems = ref([])
let originalRemarks = {}
function edit(row) {
  const p = period.value || {}
  const ov = parseHeaderOverride(row.headerOverrideJson)
  Object.assign(editForm, {
    id: row.id, noticeNo: row.noticeNo || '', tenantName: row.tenantName, unitName: row.unitName,
    parkName: ov.parkName || p.parkName || '',
    periodStart: ov.periodStart || p.periodStart || null,
    periodEnd: ov.periodEnd || p.periodEnd || null,
    issueDate: ov.issueDate || p.issueDate || null,
    dueDate: ov.dueDate || p.dueDate || null,
    contactName: ov.contactName ?? p.contactName ?? '',
    contactPhone: ov.contactPhone ?? p.contactPhone ?? '',
    paymentAccount: ov.paymentAccount || p.paymentAccount || '',
    paidAmount: Number(row.paidAmount || 0),
    priorArrears: Number(row.priorArrears || 0), remark: row.remark || ''
  })
  originalRemarks = {}
  editItems.value = (row.items || []).map((it) => {
    const manual = it.manualItem || it.itemType === 'OTHER' || it.itemType === 'CUSTOM'
    if (!manual) originalRemarks[it.id] = it.remark || ''
    return {
      id: it.id, auto: !manual, detail: /_DETAIL$/.test(it.itemType), seq: it.seq, feeItem: it.feeItem,
      quantity: it.quantity ?? null, unit: it.unit || '', unitPrice: it.unitPrice ?? null,
      amount: it.amount == null ? null : Number(it.amount), remark: it.remark || ''
    }
  })
  editDialog.visible = true
}
function addManual() {
  editItems.value.push({ id: null, auto: false, detail: false, seq: '', feeItem: '', quantity: null, unit: '元', unitPrice: null, amount: null, remark: '' })
}
// 填了数量和单价就自动算金额;之后仍可直接改金额
function calcAmount(row) {
  if (row.quantity != null && row.unitPrice != null) row.amount = Math.round(Number(row.quantity) * Number(row.unitPrice) * 100) / 100
}
const cents = (v) => Math.round(Number(v || 0) * 100)
const editSubtotal = computed(() => editItems.value.reduce((s, r) => (r.detail ? s : s + cents(r.amount)), 0) / 100)
const editDue = computed(() => (cents(editSubtotal.value) - cents(editForm.paidAmount) + cents(editForm.priorArrears)) / 100)
// 序号:手工行接在自动行后面顺延
watch(() => editItems.value.length, () => {
  const top = editItems.value.filter((r) => r.auto && !r.detail).length
  editItems.value.filter((r) => !r.auto).forEach((r, i) => { r.seq = String(top + i + 1) })
})
async function saveEdit() {
  const manual = editItems.value.filter((r) => !r.auto)
  for (const [i, r] of manual.entries()) {
    if (!String(r.feeItem || '').trim()) return ElMessage.warning(`第 ${i + 1} 个新增收费项还没填费用项目`)
    if (r.amount == null && !(r.quantity != null && r.unitPrice != null)) return ElMessage.warning(`新增收费项「${r.feeItem}」还没填金额（或数量 × 单价）`)
  }
  const itemRemarks = {}
  editItems.value.filter((r) => r.auto && !r.detail && (r.remark || '') !== originalRemarks[r.id]).forEach((r) => { itemRemarks[r.id] = r.remark || '' })
  editDialog.saving = true
  try {
    const headerOverrideJson = JSON.stringify({
      parkName: editForm.parkName || '',
      periodStart: editForm.periodStart || null,
      periodEnd: editForm.periodEnd || null,
      issueDate: editForm.issueDate || null,
      dueDate: editForm.dueDate || null,
      contactName: editForm.contactName || '',
      contactPhone: editForm.contactPhone || '',
      paymentAccount: editForm.paymentAccount || ''
    })
    await feePeriodApi.editNotice({
      id: editForm.id, noticeNo: editForm.noticeNo, tenantName: editForm.tenantName, unitName: editForm.unitName,
      paidAmount: editForm.paidAmount, priorArrears: editForm.priorArrears, remark: editForm.remark,
      headerOverrideJson, itemRemarks,
      manualItems: manual.map((r) => ({ feeItem: r.feeItem, unit: r.unit, quantity: r.quantity, unitPrice: r.unitPrice, amount: r.amount, remark: r.remark }))
    })
    editDialog.visible = false
    ElMessage.success('已保存')
    await reload()
    await loadNotices()
  } finally {
    editDialog.saving = false
  }
}

const shortSealName = (name) => (name && name.length > 8 ? `${name.slice(0, 8)}…` : name || '')

const sealSampleMarkup = computed(() => {
  const p = period.value || { parkName: 'DIPARK数智云仓产业园', period: month.value || '2026-11' }
  const sample = notices.value[0] || {
    noticeNo: `${p.noticePrefix || 'DIPARK'}-202611-001`,
    tenantName: '示例租户有限公司',
    unitName: '1层 A区',
    subtotal: 12580,
    paidAmount: 0,
    priorArrears: 0,
    totalDue: 12580,
    items: [
      { seq: '1', feeItem: '水费', prevReading: 1200, currReading: 1320, quantity: 120, unit: '吨', unitPrice: 4.5, amount: 540, remark: '' },
      { seq: '2', feeItem: '电费', prevReading: 25600, currReading: 37600, quantity: 12000, unit: '度', unitPrice: 0.92, amount: 11040, remark: '' },
      { seq: '3', feeItem: '生活垃圾清运费', quantity: 1, unit: '月', unitPrice: 1000, amount: 1000, remark: '' }
    ]
  }
  return noticeHtml(p, sample, null)
})

async function onSealCommand(cmd) {
  if (cmd === 'disable') {
    const saved = await sealApi.savePlacement('UTILITY_NOTICE', {
      ...(sealPlacement.value || {}),
      enabled: 0
    })
    sealPlacement.value = saved
    ElMessage.success('已取消水电/垃圾费通知单电子印章')
    return
  }
  await loadSealData()
  if (cmd.startsWith('seal:')) {
    sealDesigner.initialSealId = Number(cmd.slice(5))
  } else {
    sealDesigner.initialSealId = sealPlacement.value?.sealId || seals.value[0]?.id || null
  }
  sealDesigner.visible = true
}

const previewDialog = reactive({ visible: false, title: '', html: '', notice: null })
function preview(row) {
  previewDialog.notice = row
  previewDialog.title = `${row.noticeNo}  ${row.tenantName}（${row.unitName}）`
  previewDialog.html = `<!doctype html><html><head><meta charset="utf-8"><style>${NOTICE_CSS}</style></head><body>${noticeHtml(period.value, row, sealPlacement.value)}</body></html>`
  previewDialog.visible = true
}
function doPrint(list) {
  if (!printNotices(period.value, list, true, sealPlacement.value)) ElMessage.warning('浏览器拦截了打印窗口，请允许弹出窗口后重试')
}
const printOne = (row) => doPrint([row])
const printAll = () => doPrint(viewNotices.value)
const printSelected = () => doPrint(selected.value)
async function exportExcel() {
  const res = await feePeriodApi.export(period.value.id)
  downloadBlob(res, `${period.value.period}应收收费明细表.xlsx`)
}

// ---- 删除通知单:单张 / 多选 / 一键全部
function deleteHint() {
  return locked.value
    ? '本期已确认出账：删除通知单会同时删除它生成的账单（已收过款的会被拦下，需先红冲）。'
    : '删掉的通知单重新生成时会按读数重新出现；要永久去掉某个区域/用户请到电费、水费页删除对应区域。'
}
async function afterDelete(r) {
  ElMessage.success(`已删除 ${r.deleted} 张通知单${r.bills ? `，并删除 ${r.bills} 张账单` : ''}`)
  await reload()
  await loadNotices()
  noticeTable.value?.clearSelection()
}
async function deleteOne(row) {
  await ElMessageBox.confirm(`确定删除「${row.unitName} ${row.tenantName}」的通知单 ${row.noticeNo}？\n${deleteHint()}`,
    '删除通知单', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' })
  await afterDelete(await feePeriodApi.deleteNotices(period.value.id, [row.id]))
}
async function deleteSelected() {
  await ElMessageBox.confirm(`确定删除选中的 ${selected.value.length} 张通知单？\n${deleteHint()}`,
    '删除选中的通知单', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' })
  await afterDelete(await feePeriodApi.deleteNotices(period.value.id, selected.value.map((n) => n.id)))
}
async function deleteAll() {
  await ElMessageBox.prompt(`将删除 ${monthLabel.value} 的全部 ${notices.value.length} 张通知单，无法恢复。\n${deleteHint()}\n请输入“删除”二字确认：`,
    '一键删除全部通知单', { type: 'error', confirmButtonText: '全部删除', cancelButtonText: '取消', inputPattern: /^删除$/, inputErrorMessage: '请输入“删除”二字' })
  await afterDelete(await feePeriodApi.deleteAllNotices(period.value.id))
}

async function confirm() {
  await ElMessageBox.confirm(
    `将按本期 ${notices.value.length} 张通知单给每个租户各生成一张能源费账单（金额 = 费用小计；没有关联在租合同的户会跳过），账期随后冻结。确定出账？`,
    '确认出账', { type: 'warning', confirmButtonText: '确认出账', cancelButtonText: '取消' })
  const r = await feePeriodApi.confirm(period.value.id)
  ElMessage.success(`已出账 ${r.inserted} 张，跳过 ${r.skipped} 张`)
  await reload()
  await loadNotices()
}
async function revoke() {
  await ElMessageBox.confirm('将删除本期由通知单生成且尚未收款的账单，账期回到草稿以便重算。已收过款的会直接拦下。', '撤销确认', { type: 'warning' })
  const r = await feePeriodApi.revoke(period.value.id)
  ElMessage.success(`已撤销 ${r.removed} 张账单`)
  await reload()
  await loadNotices()
}
</script>

<style scoped>
.lease-picker-section { margin-top: 26px; padding-top: 22px; border-top: 1px solid var(--el-border-color-lighter); }
.detail-head { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; flex-wrap: wrap; }
.detail-head h3 { margin: 0; font-size: 16px; }
.toolbar { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; flex-wrap: wrap; }
.toolbar-tip { font-size: 12px; color: var(--el-text-color-secondary); margin-bottom: 12px; line-height: 1.6; }
.gen-time { font-size: 12px; color: var(--el-text-color-secondary); }
.summary-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 10px; margin-bottom: 12px; }
.summary-item { padding: 10px 14px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; display: flex; flex-direction: column; gap: 4px; background: var(--el-fill-color-blank); }
.summary-item span { font-size: 12px; color: var(--el-text-color-secondary); }
.summary-item strong { font-size: 16px; font-variant-numeric: tabular-nums; }
.summary-item.total strong { color: var(--el-color-danger); }
.matrix :deep(td) { font-variant-numeric: tabular-nums; }
.col-head { line-height: 1.3; text-align: center; }
.col-main { font-weight: 600; }
.col-sub { font-size: 11px; font-weight: 400; color: var(--el-text-color-secondary); white-space: normal; word-break: break-all; max-width: 150px; margin: 0 auto; }
.strong { font-weight: 700; }
.section-title { margin: 18px 0 8px; font-size: 14px; }
.empty-tip { text-align: center; color: var(--el-text-color-secondary); padding: 20px 0; font-size: 13px; }
.form-tip { margin-top: 4px; font-size: 12px; line-height: 1.5; color: var(--el-text-color-secondary); }
.edit-form { margin-bottom: 4px; }
.items-head { display: flex; align-items: center; gap: 12px; margin: 6px 0 10px; flex-wrap: wrap; }
.items-head .form-tip { flex: 1; margin: 0; min-width: 260px; }
.items-table :deep(tr.item-manual) { background-color: var(--el-color-warning-light-9); }
.sub-line { color: var(--el-text-color-secondary); font-size: 12px; }
.muted { color: var(--el-text-color-placeholder); font-size: 12px; }
.edit-total { display: flex; justify-content: flex-end; align-items: baseline; gap: 22px; margin-top: 12px; font-size: 13px; font-variant-numeric: tabular-nums; }
.edit-total .due strong { font-size: 18px; color: var(--el-color-danger); }
.preview-frame { width: 100%; height: 70vh; border: 1px solid var(--el-border-color-lighter); background: #fff; }
.notice-table :deep(.el-table__body tr.notice-payment-paid > td.el-table__cell) { background-color: var(--el-color-success-light-9) !important; }
.notice-table :deep(.el-table__body tr.notice-payment-paid.hover-row > td.el-table__cell),
.notice-table :deep(.el-table__body tr.notice-payment-paid:hover > td.el-table__cell) { background-color: var(--el-color-success-light-8) !important; }
@keyframes rainbow-flow {
  0% { background-position: 0% 50%; }
  50% { background-position: 100% 50%; }
  100% { background-position: 0% 50%; }
}
.notice-table :deep(.el-table__body tr.notice-invoice-issued) {
  background: linear-gradient(115deg, #ffd1e8 0%, #ffe0b5 14%, #fff6a8 28%, #baf7d0 42%, #b5e4ff 56%, #d9c2ff 70%, #ffc2e2 84%, #ffd1e8 100%) !important;
  background-size: 320% 100% !important;
  animation: rainbow-flow 4.5s linear infinite !important;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.8), inset 0 -1px 0 rgba(168, 85, 247, 0.28);
}
.notice-table :deep(.el-table__body tr.notice-invoice-issued > td.el-table__cell),
.notice-table :deep(.el-table__body tr.notice-invoice-issued.hover-row > td.el-table__cell),
.notice-table :deep(.el-table__body tr.notice-invoice-issued:hover > td.el-table__cell) {
  background: transparent !important;
  color: #111827;
}
.notice-table :deep(.el-table__body tr.notice-invoice-issued > td.el-table-fixed-column--right) {
  background: linear-gradient(115deg, rgba(255, 209, 232, 0.96) 0%, rgba(255, 246, 168, 0.96) 25%, rgba(186, 247, 208, 0.96) 50%, rgba(181, 228, 255, 0.96) 75%, rgba(217, 194, 255, 0.96) 100%) !important;
  background-size: 320% 100% !important;
  animation: rainbow-flow 4.5s linear infinite !important;
}
.rainbow-badge {
  display: inline-flex; align-items: center; justify-content: center;
  padding: 2px 9px; border-radius: 999px; font-size: 12px; font-weight: 700; color: #fff;
  background: linear-gradient(115deg, #ff4d8d, #ff9900, #10b981, #3b82f6, #8b5cf6, #ec4899);
  background-size: 260% 100%;
  animation: rainbow-flow 3.6s linear infinite;
  box-shadow: 0 2px 8px rgba(139, 92, 246, 0.35);
}
.invoice-btn { color: #9333ea !important; font-weight: 600; }
.invoice-btn:hover { color: #db2777 !important; }
.seal-dot { display: inline-block; width: 7px; height: 7px; border-radius: 50%; background: #dc2626; margin-right: 6px; }
.seal-drop-item { display: flex; align-items: center; gap: 8px; }
.seal-drop-img { width: 22px; height: 18px; object-fit: contain; }
.header-opt-row { display: flex; justify-content: space-between; align-items: center; gap: 12px; width: 100%; }
.header-scope-tip { margin-top: 4px; font-size: 12px; line-height: 1.5; color: var(--el-text-color-secondary); }
</style>
