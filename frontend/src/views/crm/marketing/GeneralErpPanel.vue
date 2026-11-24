<template>
  <div v-if="canQuery" class="general-erp-panel">
    <el-alert
      v-if="onboarding || warehouse?.joinStatus === 3"
      type="warning"
      show-icon
      :closable="false"
      class="mb"
    >
      <template #title>
        <div class="onboard-banner">
          <span>
            云仓入驻第 3 步（外部 ERP 接入）：① 签发凭证 → ② 切换至正式环境（env=2）并由外部 ERP 发送签名心跳使连接变为「已联通(正式)」 → ③ 确认联通进入签署协议环节。当前连接状态：
            <b>{{ ERP_STATUS_TEXT[warehouse?.erpStatus ?? 0] || '未对接' }}</b>
          </span>
          <el-button
            v-if="canAuditOnboarding"
            type="success"
            size="small"
            :loading="confirmingOnboarding"
            :disabled="warehouse?.erpStatus !== 2"
            @click="confirmOnboardingErp"
          >
            确认 ERP 已联通，继续入驻
          </el-button>
        </div>
      </template>
    </el-alert>

    <el-alert v-if="panelError" type="error" :closable="false" :title="panelError" class="mb" />

    <!-- a) 凭证与环境状态区 -->
    <section class="table-card erp-section" aria-labelledby="erp-cred-title">
      <div class="toolbar">
        <h2 id="erp-cred-title" class="title">凭证与环境状态</h2>
        <template v-if="credential">
          <el-tag :type="credential.env === 2 ? 'success' : 'warning'">
            {{ credential.env === 2 ? '正式环境 (env=2)' : '沙箱环境 (env=1)' }}
          </el-tag>
          <el-tag :type="credential.status === 1 ? 'success' : 'info'">
            {{ credential.status === 1 ? '凭证已启用' : '凭证已停用' }}
          </el-tag>
        </template>
        <el-tag v-else type="info">尚未签发凭证</el-tag>

        <div class="toolbar-actions">
          <el-button :loading="loading.cred" @click="loadCredential">刷新</el-button>
          <template v-if="canConfig">
            <el-button v-if="!credential" type="primary" :loading="busy.cred" @click="issueCredential">
              签发沙箱凭证
            </el-button>
            <template v-else>
              <el-button
                v-if="credential.env !== 2"
                type="success"
                :loading="busy.cred"
                @click="switchEnv(2)"
              >
                切换至正式环境 (env=2)
              </el-button>
              <el-button v-else plain :loading="busy.cred" @click="switchEnv(1)">
                切回沙箱环境
              </el-button>
              <el-button type="warning" plain :loading="busy.cred" @click="rotateSecret">
                轮换密钥
              </el-button>
              <el-button plain :loading="busy.cred" @click="toggleCredStatus">
                {{ credential.status === 1 ? '停用凭证' : '启用凭证' }}
              </el-button>
            </template>
          </template>
        </div>
      </div>

      <el-descriptions v-if="credential" :column="2" border size="small" class="mt8">
        <el-descriptions-item label="AppKey (App-Id)">
          <code>{{ credential.appId }}</code>
        </el-descriptions-item>
        <el-descriptions-item label="当前环境">
          {{ credential.env === 2 ? '正式环境（计入真实订单与佣金）' : '沙箱环境（仅联调校验，不计正式账务）' }}
        </el-descriptions-item>
        <el-descriptions-item label="最近签名心跳">
          {{ credential.lastHeartbeatAt || '暂无有效心跳（需外部 ERP 调用心跳或推送接口）' }}
        </el-descriptions-item>
        <el-descriptions-item label="最近事件同步">
          {{ credential.lastSyncAt || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="订单 Webhook 地址">
          <code>{{ credential.endpoint || '/api/open/v1/erp/order-event' }}</code>
        </el-descriptions-item>
        <el-descriptions-item label="签名心跳地址">
          <code>{{ credential.heartbeatEndpoint || '/api/open/v1/erp/heartbeat' }}</code>
        </el-descriptions-item>
      </el-descriptions>
      <el-empty v-else description="当前云仓尚未签发通用 ERP 凭证，点击右上角「签发沙箱凭证」生成 AppKey / AppSecret" :image-size="64" />
      <p class="hint">安全说明：AppSecret 仅在首次签发或轮换密钥时一次性明文展示；切换环境或轮换密钥后需由外部 ERP 重新发送签名心跳确认连接。</p>
    </section>

    <!-- b) 货主编码映射表 -->
    <section class="table-card erp-section" aria-labelledby="erp-map-title">
      <div class="toolbar">
        <h2 id="erp-map-title" class="title">货主编码映射（客户 ↔ 云仓 ERP 货主编码）</h2>
        <div class="toolbar-actions">
          <el-button :loading="loading.mappings" @click="loadMappings">刷新</el-button>
          <el-button v-if="canConfig" type="primary" @click="openMappingDialog()">新增货主映射</el-button>
        </div>
      </div>
      <el-table :data="mappings" v-loading="loading.mappings" border stripe size="small">
        <el-table-column label="本系统客户" min-width="180">
          <template #default="{ row }">
            <div>{{ row.customerName || `客户 #${row.customerId}` }}</div>
            <div class="sub">客户编号 #{{ row.customerId }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="customerCode" label="ERP 货主编码 (customerCode)" min-width="180">
          <template #default="{ row }"><code>{{ row.customerCode }}</code></template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '生效中' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="配置时间" width="170">
          <template #default="{ row }">{{ row.createTime || '—' }}</template>
        </el-table-column>
        <el-table-column v-if="canConfig" label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openMappingDialog(row)">修改编码</el-button>
            <el-button link type="danger" @click="removeMapping(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <p class="hint">Webhook 入站出库单仅按「云仓 + ERP 货主编码」匹配本系统客户；若先推单后建映射，保存映射后会自动将正式环境下的未映射积压单排入重放队列。</p>
    </section>

    <!-- c) 异常与未映射积压队列 + 同步日志 -->
    <section class="table-card erp-section" aria-labelledby="erp-exc-title">
      <div class="toolbar">
        <h2 id="erp-exc-title" class="title">异常积压与 Webhook 日志</h2>
        <el-radio-group v-model="queueTab" size="small" @change="onQueueTabChange">
          <el-radio-button value="exceptions">未映射积压与死信队列 ({{ exceptions.total }})</el-radio-button>
          <el-radio-button value="logs">最近同步日志</el-radio-button>
        </el-radio-group>
        <div class="toolbar-actions">
          <el-button :loading="loading.exceptions || loading.logs" @click="onQueueTabChange">刷新</el-button>
        </div>
      </div>

      <template v-if="queueTab === 'exceptions'">
        <el-table :data="exceptions.records" v-loading="loading.exceptions" border stripe size="small">
          <el-table-column prop="eventId" label="事件 ID" min-width="150" show-overflow-tooltip />
          <el-table-column prop="orderNo" label="外部单号" min-width="140" show-overflow-tooltip />
          <el-table-column prop="event" label="事件类型" width="130" />
          <el-table-column label="队列状态" width="130">
            <template #default="{ row }">
              <el-tag :type="excTagType(row.status)" size="small">{{ EXC_STATUS_TEXT[row.status] || row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="attempts" label="重试次数" width="85" align="right" />
          <el-table-column prop="lastError" label="阻塞 / 失败原因" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ row.lastError || '—' }}</template>
          </el-table-column>
          <el-table-column prop="occurredAt" label="事件时间" width="165">
            <template #default="{ row }">{{ row.occurredAt || '—' }}</template>
          </el-table-column>
          <el-table-column v-if="canConfig" label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 'WAITING_MAPPING'"
                link
                type="primary"
                @click="openMappingDialog()"
              >
                补齐映射
              </el-button>
              <el-button
                v-if="['WAITING_MAPPING', 'RETRY', 'DEAD'].includes(row.status)"
                link
                type="success"
                :disabled="credential?.env !== 2"
                :title="credential?.env !== 2 ? '请先切换至正式环境再重放历史事件' : ''"
                @click="openRetryDialog(row)"
              >
                {{ row.status === 'DEAD' ? '死信重试' : '重放事件' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          class="pager"
          small
          background
          layout="total, prev, pager, next"
          :total="exceptions.total"
          v-model:current-page="exceptions.pageNo"
          :page-size="exceptions.pageSize"
          @current-change="loadExceptions"
        />
      </template>

      <template v-else>
        <el-table :data="logs.records" v-loading="loading.logs" border stripe size="small">
          <el-table-column prop="orderNo" label="订单号" min-width="150" show-overflow-tooltip />
          <el-table-column prop="event" label="事件" width="130" />
          <el-table-column label="结果" width="90">
            <template #default="{ row }">
              <el-tag :type="row.ok === 1 ? 'success' : 'danger'" size="small">{{ row.ok === 1 ? '成功' : '失败' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="code" label="响应码" width="120" />
          <el-table-column prop="msg" label="说明" min-width="200" show-overflow-tooltip />
          <el-table-column prop="createTime" label="接收时间" width="165" />
        </el-table>
        <el-pagination
          class="pager"
          small
          background
          layout="total, prev, pager, next"
          :total="logs.total"
          v-model:current-page="logs.pageNo"
          :page-size="logs.pageSize"
          @current-change="loadLogs"
        />
      </template>
    </section>

    <!-- d) 周期强对账快照 -->
    <section class="table-card erp-section" aria-labelledby="erp-rec-title">
      <div class="toolbar">
        <h2 id="erp-rec-title" class="title">周期强对账快照（远端 ERP vs 本地有效出库单）</h2>
        <div class="toolbar-actions">
          <el-date-picker
            v-model="reconcile.period"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            :clearable="false"
            style="width: 250px"
            @change="loadReconciliation"
          />
          <el-button @click="setLastMonthPeriod">上月整月</el-button>
          <el-button :loading="loading.reconcile" @click="loadReconciliation">查询对账</el-button>
          <el-button v-if="canConfig" type="primary" @click="openSubmitReconcile">录入外部单数对账</el-button>
        </div>
      </div>

      <template v-if="reconcile.preview">
        <div class="rec-kpi-row">
          <div class="rec-kpi">
            <div class="rec-kpi-label">本地有效出库单数</div>
            <div class="rec-kpi-val">{{ reconcile.preview.calculation?.systemOrderCount ?? 0 }}</div>
            <div class="sub">唯一有效出库单</div>
          </div>
          <div class="rec-kpi">
            <div class="rec-kpi-label">已撤单 / 退款排除</div>
            <div class="rec-kpi-val">{{ reconcile.preview.calculation?.cancelledOrderCount ?? 0 }}</div>
            <div class="sub">不计入结算基数</div>
          </div>
          <div class="rec-kpi" :class="{ 'is-warn': (reconcile.preview.calculation?.blockingCount || 0) > 0 }">
            <div class="rec-kpi-label">阻塞事件数</div>
            <div class="rec-kpi-val">{{ reconcile.preview.calculation?.blockingCount ?? 0 }}</div>
            <div class="sub">未映射 {{ reconcile.preview.calculation?.unmappedCount ?? 0 }} · 待重试 {{ reconcile.preview.calculation?.retryCount ?? 0 }}</div>
          </div>
          <div class="rec-kpi">
            <div class="rec-kpi-label">最新快照差异率</div>
            <div class="rec-kpi-val">{{ latestSnapshot ? diffRateText(snapOf(latestSnapshot)) : '待对账' }}</div>
            <div class="sub">
              <template v-if="latestSnapshot">
                远端 {{ snapOf(latestSnapshot).externalOrderCount ?? 0 }} 单 / 差异 {{ snapOf(latestSnapshot).diffOrderCount ?? 0 }} 单
              </template>
              <template v-else>当前账期尚未提交外部单数</template>
            </div>
          </div>
        </div>

        <el-alert
          v-if="reconcile.preview.calculation?.blockingReasons?.length"
          type="warning"
          show-icon
          :closable="false"
          class="mb"
          :title="`存在阻塞结算的异常项：${reconcile.preview.calculation.blockingReasons.join('；')}`"
        />

        <el-table :data="reconcile.preview.history || []" v-loading="loading.reconcile" border stripe size="small">
          <el-table-column label="修订版" width="80">
            <template #default="{ row }">v{{ snapOf(row).revision ?? 1 }}</template>
          </el-table-column>
          <el-table-column label="快照状态" width="150">
            <template #default="{ row }">
              <el-tag :type="snapOf(row).state === 'CONFIRMED' ? 'success' : 'warning'" size="small">
                {{ snapOf(row).state === 'CONFIRMED' ? '已确认放行' : '草稿待确认' }}
              </el-tag>
              <el-tag v-if="row?.stale" type="danger" size="small" class="ml4">数据已变</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="远端单数" width="110" align="right">
            <template #default="{ row }">{{ snapOf(row).externalOrderCount ?? 0 }}</template>
          </el-table-column>
          <el-table-column label="本地单数" width="110" align="right">
            <template #default="{ row }">{{ snapOf(row).systemOrderCount ?? 0 }}</template>
          </el-table-column>
          <el-table-column label="差异单数 / 差异率" width="150" align="right">
            <template #default="{ row }">
              <span :class="{ 'diff-danger': Number(snapOf(row).diffOrderCount || 0) !== 0 }">
                {{ snapOf(row).diffOrderCount ?? 0 }} 单 ({{ diffRateText(snapOf(row)) }})
              </span>
            </template>
          </el-table-column>
          <el-table-column label="外部来源与凭据说明" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              <b>{{ snapOf(row).sourceName || '外部导入' }}</b>：{{ snapOf(row).evidence || '—' }}
            </template>
          </el-table-column>
          <el-table-column label="确认信息" min-width="160">
            <template #default="{ row }">
              <template v-if="snapOf(row).confirmedAt">
                {{ snapOf(row).confirmedBy || '—' }} · {{ snapOf(row).confirmedAt }}
              </template>
              <span v-else class="sub">{{ snapOf(row).reason || '待核对确认' }}</span>
            </template>
          </el-table-column>
          <el-table-column v-if="canConfig" label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row?.stale" link type="warning" @click="refreshReconciliation">刷新快照</el-button>
              <el-button v-if="row?.canConfirm" link type="success" @click="confirmReconciliation(snapOf(row))">确认无差异放行</el-button>
            </template>
          </el-table-column>
        </el-table>
        <p class="hint">{{ reconcile.preview.scope }}</p>
      </template>
    </section>

    <!-- 一次性展示新签发/轮换密钥弹窗 -->
    <el-dialog
      v-model="secretDialog.visible"
      title="请立即保存新签发的 Webhook 凭证（仅展示一次）"
      width="min(540px, 94vw)"
      :close-on-click-modal="false"
      @closed="clearSecretDialog"
    >
      <el-alert
        type="warning"
        show-icon
        :closable="false"
        title="出于安全合规要求，AppSecret 在关闭此弹窗后无法再次查看；若遗失只能重新轮换密钥（旧密钥立即失效）。"
        class="mb"
      />
      <el-descriptions :column="1" border>
        <el-descriptions-item label="云仓">{{ warehouseName || `#${warehouseId}` }}</el-descriptions-item>
        <el-descriptions-item label="AppKey (App-Id)">
          <div class="secret-line">
            <code>{{ secretDialog.appKey }}</code>
            <el-button link type="primary" @click="copyText(secretDialog.appKey, 'AppKey')">复制</el-button>
          </div>
        </el-descriptions-item>
        <el-descriptions-item label="AppSecret">
          <div class="secret-line">
            <code>{{ secretDialog.appSecret }}</code>
            <el-button link type="primary" @click="copyText(secretDialog.appSecret, 'AppSecret')">复制</el-button>
          </div>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="copyText(`AppKey: ${secretDialog.appKey}\nAppSecret: ${secretDialog.appSecret}`, '完整凭证')">
          一键复制完整凭证
        </el-button>
        <el-button type="primary" @click="secretDialog.visible = false">我已妥善保存</el-button>
      </template>
    </el-dialog>

    <!-- 新增/编辑货主映射弹窗 -->
    <el-dialog v-model="mappingDialog.visible" :title="mappingDialog.editing ? '修改货主编码映射' : '新增货主编码映射'" width="min(500px, 94vw)">
      <el-form label-position="top">
        <el-form-item label="选择本系统客户" required>
          <el-select
            v-model="mappingDialog.customerId"
            filterable
            remote
            reserve-keyword
            :disabled="mappingDialog.editing"
            :remote-method="searchCustomers"
            :loading="mappingDialog.searching"
            placeholder="输入客户名称或手机号远程搜索"
            style="width: 100%"
          >
            <el-option
              v-for="c in mappingDialog.customers"
              :key="c.id"
              :value="Number(c.id)"
              :label="`${c.name} (#${c.id})`"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="该云仓 ERP 货主编码 (customerCode)" required>
          <el-input v-model="mappingDialog.customerCode" maxlength="64" show-word-limit placeholder="例如：OWNER_HZ_001（1–64 字符）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="busy.mapping" @click="mappingDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="busy.mapping" @click="submitMapping">保存映射</el-button>
      </template>
    </el-dialog>

    <!-- 重放/重试弹窗 -->
    <el-dialog v-model="retryDialog.visible" title="重放 / 死信重试确认" width="min(480px, 94vw)">
      <p class="sub mb">事件单号：{{ retryDialog.row?.orderNo || retryDialog.row?.eventId }}（状态：{{ EXC_STATUS_TEXT[retryDialog.row?.status] || retryDialog.row?.status }}）</p>
      <el-form label-position="top">
        <el-form-item label="重试 / 重放原因" required>
          <el-input v-model="retryDialog.reason" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="例如：已补齐货主编码映射，申请重放出库事件" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="busy.retry" @click="retryDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="busy.retry" @click="submitRetry">确认排队重放</el-button>
      </template>
    </el-dialog>

    <!-- 提交外部单数对账弹窗 -->
    <el-dialog v-model="submitRecDialog.visible" title="录入远端 ERP 出库单数对账" width="min(520px, 94vw)">
      <p class="sub mb">对账账期：{{ reconcile.period?.[0] }} 至 {{ reconcile.period?.[1] }}（本地有效出库单：{{ reconcile.preview?.calculation?.systemOrderCount ?? 0 }} 单）</p>
      <el-form label-position="top">
        <el-form-item label="远端 ERP 有效出库单数" required>
          <el-input-number v-model="submitRecDialog.externalOrderCount" :min="0" :max="1000000000" :precision="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="外部数据来源名称" required>
          <el-input v-model="submitRecDialog.sourceName" maxlength="200" placeholder="例如：聚水潭 / 旺店通生产库导出报表" />
        </el-form-item>
        <el-form-item label="凭据及筛选口径说明" required>
          <el-input v-model="submitRecDialog.evidence" type="textarea" :rows="3" maxlength="1000" show-word-limit placeholder="填写导出文件名、筛选时间范围及排除退货/取消说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="busy.reconcile" @click="submitRecDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="busy.reconcile" @click="submitReconciliation">生成对账快照</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { mktCustomerApi, mktErpApi, mktWarehouseApi, mktYunshanApi } from '@/api/marketing'
import { erpReconciliationApi } from '@/api/erp-reconciliation'
import { hasPermission } from '@/utils/permission'

const props = defineProps({
  warehouseId: { type: [String, Number], required: true },
  warehouseName: { type: String, default: '' },
  warehouse: { type: Object, default: null },
  onboarding: { type: Boolean, default: false }
})

const emit = defineEmits(['updated'])

const ERP_STATUS_TEXT = { 0: '未对接', 1: '已联通(沙箱)', 2: '已联通(正式)', 3: '心跳超时断连' }
const EXC_STATUS_TEXT = {
  WAITING_MAPPING: '未映射积压',
  RETRY: '等待重试',
  DEAD: '死信队列',
  RECEIVED: '已排队重放',
  PROCESSING: '处理中'
}

const canQuery = computed(() => hasPermission('crm:marketing:erp:query'))
const canConfig = computed(() => hasPermission('crm:marketing:erp:config'))
const canAuditOnboarding = computed(() => hasPermission('crm:marketing:onboarding:audit'))

const panelError = ref('')
const credential = ref(null)
const mappings = ref([])
const queueTab = ref('exceptions')
const confirmingOnboarding = ref(false)

const loading = reactive({ cred: false, mappings: false, exceptions: false, logs: false, reconcile: false })
const busy = reactive({ cred: false, mapping: false, retry: false, reconcile: false })

const exceptions = reactive({ records: [], total: 0, pageNo: 1, pageSize: 10 })
const logs = reactive({ records: [], total: 0, pageNo: 1, pageSize: 10 })

function formatDate(d) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
function defaultPeriod() {
  const now = new Date()
  const first = new Date(now.getFullYear(), now.getMonth(), 1)
  return [formatDate(first), formatDate(now)]
}

const reconcile = reactive({ period: defaultPeriod(), preview: null })
const latestSnapshot = computed(() => reconcile.preview?.history?.[0] || null)
const snapOf = (entry) => (entry && typeof entry === 'object' ? (entry.snapshot || entry) : {})

const secretDialog = reactive({ visible: false, appKey: '', appSecret: '' })
const mappingDialog = reactive({ visible: false, editing: false, customerId: null, customerCode: '', customers: [], searching: false })
const retryDialog = reactive({ visible: false, row: null, reason: '' })
const submitRecDialog = reactive({ visible: false, externalOrderCount: 0, sourceName: '', evidence: '' })

let disposed = false
let scopeVersion = 0

const excTagType = (status) => ({
  WAITING_MAPPING: 'warning',
  RETRY: 'warning',
  DEAD: 'danger',
  RECEIVED: 'primary',
  PROCESSING: 'info'
}[status] || 'info')

function diffRateText(snapshot) {
  if (!snapshot) return '—'
  const ext = Number(snapshot.externalOrderCount || 0)
  const diff = Math.abs(Number(snapshot.diffOrderCount || 0))
  if (diff === 0) return '0.00%'
  if (ext <= 0) return '100.00%'
  return `${(diff / ext * 100).toFixed(2)}%`
}

async function loadCredential() {
  if (!canQuery.value || !props.warehouseId) return
  const ver = scopeVersion
  loading.cred = true
  try {
    const res = await mktErpApi.get(props.warehouseId)
    if (!disposed && ver === scopeVersion) credential.value = res || null
  } catch (e) {
    if (!disposed && ver === scopeVersion) panelError.value = e.message || 'ERP 凭证状态读取失败'
  } finally {
    if (!disposed && ver === scopeVersion) loading.cred = false
  }
}

async function loadMappings() {
  if (!canQuery.value || !props.warehouseId) return
  const ver = scopeVersion
  loading.mappings = true
  try {
    const res = await mktErpApi.mappings(props.warehouseId)
    if (!disposed && ver === scopeVersion) mappings.value = Array.isArray(res) ? res : []
  } catch (e) {
    if (!disposed && ver === scopeVersion) panelError.value = e.message || '货主编码映射读取失败'
  } finally {
    if (!disposed && ver === scopeVersion) loading.mappings = false
  }
}

async function loadExceptions() {
  if (!canQuery.value || !props.warehouseId) return
  const ver = scopeVersion
  loading.exceptions = true
  try {
    const res = await mktErpApi.exceptions(props.warehouseId, { pageNo: exceptions.pageNo, pageSize: exceptions.pageSize })
    if (!disposed && ver === scopeVersion) {
      exceptions.records = res?.records || []
      exceptions.total = Number(res?.total || 0)
    }
  } catch (e) {
    if (!disposed && ver === scopeVersion) panelError.value = e.message || '异常积压队列读取失败'
  } finally {
    if (!disposed && ver === scopeVersion) loading.exceptions = false
  }
}

async function loadLogs() {
  if (!canQuery.value || !props.warehouseId) return
  const ver = scopeVersion
  loading.logs = true
  try {
    const res = await mktErpApi.logs({ warehouseId: props.warehouseId, pageNo: logs.pageNo, pageSize: logs.pageSize })
    if (!disposed && ver === scopeVersion) {
      logs.records = res?.records || []
      logs.total = Number(res?.total || 0)
    }
  } catch (e) {
    if (!disposed && ver === scopeVersion) panelError.value = e.message || '同步日志读取失败'
  } finally {
    if (!disposed && ver === scopeVersion) loading.logs = false
  }
}

function onQueueTabChange() {
  if (queueTab.value === 'logs') return loadLogs()
  return loadExceptions()
}

async function loadReconciliation() {
  if (!canQuery.value || !props.warehouseId || !reconcile.period?.length) return
  const [periodStart, periodEnd] = reconcile.period
  const ver = scopeVersion
  loading.reconcile = true
  try {
    const res = await erpReconciliationApi.preview(props.warehouseId, { periodStart, periodEnd })
    if (!disposed && ver === scopeVersion) reconcile.preview = res || null
  } catch (e) {
    if (!disposed && ver === scopeVersion) panelError.value = e.message || '对账快照读取失败'
  } finally {
    if (!disposed && ver === scopeVersion) loading.reconcile = false
  }
}

function setLastMonthPeriod() {
  const now = new Date()
  const firstLastMonth = new Date(now.getFullYear(), now.getMonth() - 1, 1)
  const endLastMonth = new Date(now.getFullYear(), now.getMonth(), 0)
  reconcile.period = [formatDate(firstLastMonth), formatDate(endLastMonth)]
  loadReconciliation()
}

async function issueCredential() {
  if (!canConfig.value || busy.cred) return
  busy.cred = true
  panelError.value = ''
  try {
    const res = await mktErpApi.issue(props.warehouseId)
    credential.value = { ...res, secret: null }
    Object.assign(secretDialog, { visible: true, appKey: res.appId || '', appSecret: res.secret || '' })
    ElMessage.success('沙箱凭证已签发，请立即复制保存密钥')
    emit('updated')
  } catch (e) {
    panelError.value = e.message || '签发凭证失败'
  } finally {
    busy.cred = false
  }
}

async function rotateSecret() {
  if (!canConfig.value || busy.cred) return
  try {
    await ElMessageBox.confirm('轮换密钥后旧 AppSecret 将立即失效，外部 ERP 必须更换新密钥并重新发送签名心跳。确认继续？', '轮换 Webhook 密钥', { type: 'warning' })
  } catch { return }
  busy.cred = true
  panelError.value = ''
  try {
    const res = await mktErpApi.resetSecret(props.warehouseId)
    credential.value = { ...res, secret: null }
    Object.assign(secretDialog, { visible: true, appKey: res.appId || '', appSecret: res.secret || '' })
    ElMessage.success('密钥已轮换，请立即复制保存新密钥')
    emit('updated')
  } catch (e) {
    panelError.value = e.message || '轮换密钥失败'
  } finally {
    busy.cred = false
  }
}

function clearSecretDialog() {
  secretDialog.appKey = ''
  secretDialog.appSecret = ''
}

async function copyText(text, label) {
  if (!text) return
  try {
    await navigator.clipboard?.writeText(text)
    ElMessage.success(`${label} 已复制到剪贴板`)
  } catch {
    ElMessage.warning('当前环境不支持自动复制，请手动选中复制')
  }
}

async function switchEnv(targetEnv) {
  if (!canConfig.value || busy.cred || !credential.value) return
  const toLive = targetEnv === 2
  try {
    await ElMessageBox.confirm(
      toLive
        ? '切换至正式环境（env=2）后，外部 ERP 推送的已映射出库单将进入正式计佣与账单核算，且需外部 ERP 重新发送一次签名心跳确认正式联通。确认切换？'
        : '切回沙箱环境（env=1）后将暂停接收正式出库计佣事件。确认切换？',
      toLive ? '切换至正式环境 (env=2)' : '切回沙箱环境 (env=1)',
      { type: 'warning' }
    )
  } catch { return }
  busy.cred = true
  panelError.value = ''
  try {
    await mktErpApi.update(props.warehouseId, { env: targetEnv })
    ElMessage.success(toLive ? '已切换至正式环境 (env=2)' : '已切回沙箱环境 (env=1)')
    await loadCredential()
    emit('updated')
  } catch (e) {
    panelError.value = e.message || '切换环境失败'
  } finally {
    busy.cred = false
  }
}

async function toggleCredStatus() {
  if (!canConfig.value || busy.cred || !credential.value) return
  const nextStatus = credential.value.status === 1 ? 0 : 1
  busy.cred = true
  panelError.value = ''
  try {
    await mktErpApi.update(props.warehouseId, { status: nextStatus })
    ElMessage.success(nextStatus === 1 ? '凭证已启用' : '凭证已停用')
    await loadCredential()
    emit('updated')
  } catch (e) {
    panelError.value = e.message || '更新凭证状态失败'
  } finally {
    busy.cred = false
  }
}

async function confirmOnboardingErp() {
  if (!canAuditOnboarding.value || confirmingOnboarding.value) return
  confirmingOnboarding.value = true
  panelError.value = ''
  try {
    await mktWarehouseApi.confirmErp(props.warehouseId)
    ElMessage.success('正式 ERP 联通已确认，云仓已进入待签加盟协议阶段')
    emit('updated')
  } catch (e) {
    panelError.value = e.message || '确认 ERP 联通失败'
  } finally {
    confirmingOnboarding.value = false
  }
}

async function searchCustomers(keyword = '') {
  mappingDialog.searching = true
  try {
    const res = await mktCustomerApi.page({ pageNo: 1, pageSize: 20, keyword: String(keyword || '').trim() || undefined })
    mappingDialog.customers = res?.records || []
  } catch {
    try {
      const fallback = await mktYunshanApi.customers({ warehouseId: props.warehouseId, keyword: String(keyword || '').trim(), pageNo: 1, pageSize: 20 })
      mappingDialog.customers = fallback?.records || []
    } catch {
      mappingDialog.customers = []
    }
  } finally {
    mappingDialog.searching = false
  }
}

function openMappingDialog(row = null) {
  if (row) {
    Object.assign(mappingDialog, {
      visible: true,
      editing: true,
      customerId: Number(row.customerId),
      customerCode: row.customerCode || '',
      customers: [{ id: Number(row.customerId), name: row.customerName || `客户 #${row.customerId}` }]
    })
  } else {
    Object.assign(mappingDialog, { visible: true, editing: false, customerId: null, customerCode: '', customers: [] })
    searchCustomers('')
  }
}

async function submitMapping() {
  if (busy.mapping) return
  if (!mappingDialog.customerId) return ElMessage.error('请选择本系统客户')
  const code = mappingDialog.customerCode.trim()
  if (!code || code.length > 64) return ElMessage.error('请填写 1–64 字符的 ERP 货主编码')
  busy.mapping = true
  try {
    const res = await mktErpApi.saveMapping(props.warehouseId, {
      customerId: Number(mappingDialog.customerId),
      customerCode: code
    })
    mappingDialog.visible = false
    const queued = Number(res?.queued || 0)
    ElMessage.success(queued > 0 ? `货主映射已保存，已将 ${queued} 条未映射积压单排入重放队列` : '货主映射已保存')
    await Promise.all([loadMappings(), loadExceptions(), loadReconciliation()])
  } catch (e) {
    ElMessage.error(e.message || '保存货主映射失败')
  } finally {
    busy.mapping = false
  }
}

async function removeMapping(row) {
  try {
    await ElMessageBox.confirm(`确认删除客户「${row.customerName || '#' + row.customerId}」的货主编码「${row.customerCode}」映射？`, '删除货主映射', { type: 'warning' })
  } catch { return }
  await mktErpApi.deleteMapping(row.id)
  ElMessage.success('映射已删除')
  await Promise.all([loadMappings(), loadReconciliation()])
}

function openRetryDialog(row) {
  Object.assign(retryDialog, { visible: true, row, reason: row.status === 'WAITING_MAPPING' ? '已补齐货主编码映射，申请重放' : '' })
}

async function submitRetry() {
  if (busy.retry || !retryDialog.row) return
  const reason = retryDialog.reason.trim()
  if (!reason) return ElMessage.error('请填写重放/重试原因')
  busy.retry = true
  try {
    await mktErpApi.retry(props.warehouseId, retryDialog.row.id, { reason })
    retryDialog.visible = false
    ElMessage.success('已排入重放队列')
    await Promise.all([loadExceptions(), loadReconciliation()])
  } catch (e) {
    ElMessage.error(e.message || '重放请求失败')
  } finally {
    busy.retry = false
  }
}

function openSubmitReconcile() {
  Object.assign(submitRecDialog, {
    visible: true,
    externalOrderCount: reconcile.preview?.calculation?.systemOrderCount ?? 0,
    sourceName: latestSnapshot.value?.snapshot?.sourceName || '',
    evidence: ''
  })
}

async function submitReconciliation() {
  if (busy.reconcile || !reconcile.period?.length) return
  if (!submitRecDialog.sourceName.trim() || !submitRecDialog.evidence.trim()) {
    return ElMessage.error('请填写外部数据来源名称与凭据口径说明')
  }
  const [periodStart, periodEnd] = reconcile.period
  busy.reconcile = true
  try {
    await erpReconciliationApi.submit(props.warehouseId, {
      periodStart,
      periodEnd,
      externalOrderCount: Number(submitRecDialog.externalOrderCount || 0),
      sourceName: submitRecDialog.sourceName.trim(),
      evidence: submitRecDialog.evidence.trim()
    })
    submitRecDialog.visible = false
    ElMessage.success('对账草稿快照已生成')
    await loadReconciliation()
  } catch (e) {
    ElMessage.error(e.message || '提交对账快照失败')
  } finally {
    busy.reconcile = false
  }
}

async function refreshReconciliation() {
  if (!reconcile.period?.length) return
  const [periodStart, periodEnd] = reconcile.period
  await erpReconciliationApi.refresh(props.warehouseId, { periodStart, periodEnd })
  ElMessage.success('已按最新数据重新生成待确认快照')
  await loadReconciliation()
}

async function confirmReconciliation(snapshot) {
  let reason = ''
  try {
    const res = await ElMessageBox.prompt('双方有效出库单数一致且无阻塞事件。请填写对账确认说明（写入审计日志）：', '确认周期强对账快照', {
      inputPattern: /\S+/,
      inputErrorMessage: '确认说明必填'
    })
    reason = res.value.trim()
  } catch { return }
  await erpReconciliationApi.confirm(props.warehouseId, snapshot.id, reason)
  ElMessage.success('周期对账已确认锁定，可放行账单与结算')
  await loadReconciliation()
}

async function loadAll() {
  if (!canQuery.value || !props.warehouseId) return
  panelError.value = ''
  exceptions.pageNo = 1
  logs.pageNo = 1
  await Promise.all([
    loadCredential(),
    loadMappings(),
    loadExceptions(),
    loadReconciliation()
  ])
}

watch(() => props.warehouseId, () => {
  scopeVersion++
  credential.value = null
  mappings.value = []
  exceptions.records = []
  exceptions.total = 0
  logs.records = []
  logs.total = 0
  reconcile.preview = null
  loadAll()
}, { immediate: true })

onBeforeUnmount(() => {
  disposed = true
  scopeVersion++
})

defineExpose({
  credential,
  mappings,
  exceptions,
  reconcile,
  secretDialog,
  mappingDialog,
  retryDialog,
  submitRecDialog,
  loadAll,
  issueCredential,
  switchEnv,
  submitMapping,
  submitRetry,
  submitReconciliation,
  confirmReconciliation,
  confirmOnboardingErp
})
</script>

<style scoped>
.general-erp-panel { display: flex; flex-direction: column; gap: 12px; min-width: 0; }
.erp-section { min-width: 0; }
.toolbar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.title { margin: 0; font-size: 15px; font-weight: 600; }
.toolbar-actions { margin-left: auto; display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.onboard-banner { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; width: 100%; }
.secret-line { display: flex; align-items: center; justify-content: space-between; gap: 12px; overflow-wrap: anywhere; }
.rec-kpi-row { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin-bottom: 12px; }
.rec-kpi { padding: 12px 14px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; background: var(--el-bg-color); min-width: 0; }
.rec-kpi.is-warn { border-color: var(--el-color-warning-light-5); background: #fffaf0; }
.rec-kpi-label { font-size: 12px; color: var(--el-text-color-secondary); }
.rec-kpi-val { margin: 4px 0; font-size: 22px; font-weight: 600; font-variant-numeric: tabular-nums; }
.diff-danger { color: var(--el-color-danger); font-weight: 600; }
.sub { font-size: 12px; color: var(--el-text-color-secondary); }
.mb { margin-bottom: 12px; }
.mt8 { margin-top: 8px; }
.ml4 { margin-left: 4px; }
.pager { margin-top: 12px; justify-content: flex-end; }
@media (max-width: 900px) {
  .rec-kpi-row { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .toolbar-actions { margin-left: 0; width: 100%; }
}
</style>
