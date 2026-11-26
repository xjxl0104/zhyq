<template>
  <div class="ai-box">
    <div class="ai-head">
      <strong>租户缴费回执与发票批量上传（AI 自动识别核准）</strong>
      <span>支持点击或直接拖拽多个文件到下方对应区域；核对完全一致将自动归入对应通知单并更新状态，存在差异或识别不清将拦截并提醒人工核对。</span>
    </div>

    <div class="ai-grid">
      <!-- 租户缴费回执上传区 -->
      <div
        class="drop-card receipt"
        :class="{ 'is-dragging': draggingType === 'RECEIPT', 'is-disabled': !periodId || busy }"
        @dragenter.prevent="onDragEnter('RECEIPT')"
        @dragover.prevent="onDragEnter('RECEIPT')"
        @dragleave.prevent="onDragLeave($event, 'RECEIPT')"
        @drop.prevent="onDrop($event, 'RECEIPT')"
      >
        <div class="card-top">
          <div class="card-title">
            <el-tag size="small" type="success" effect="dark">回执核准</el-tag>
            <span>租户缴费回执上传</span>
          </div>
          <el-upload
            :show-file-list="false"
            :http-request="(opt) => handleOne('RECEIPT', opt.file)"
            :disabled="!periodId || busy"
            accept=".pdf,.jpg,.jpeg,.png,.webp"
            multiple
          >
            <el-button size="small" type="success" plain :loading="uploadingType === 'RECEIPT'">
              <el-icon><Upload /></el-icon>选择回执
            </el-button>
          </el-upload>
        </div>
        <div class="card-desc">
          {{ draggingType === 'RECEIPT'
            ? '松开鼠标即可开始 AI 自动识别核对回执...'
            : '拖拽银行电子回单（推荐 PDF）或点击上传。AI 自动核对：支付单位名称、接收单位名称及账号、支付金额；完全一致后自动上传至对应通知单「缴费」并标记已缴费。' }}
        </div>
      </div>

      <!-- 租户发票上传区 -->
      <div
        class="drop-card invoice"
        :class="{ 'is-dragging': draggingType === 'INVOICE', 'is-disabled': !periodId || busy }"
        @dragenter.prevent="onDragEnter('INVOICE')"
        @dragover.prevent="onDragEnter('INVOICE')"
        @dragleave.prevent="onDragLeave($event, 'INVOICE')"
        @drop.prevent="onDrop($event, 'INVOICE')"
      >
        <div class="card-top">
          <div class="card-title">
            <el-tag size="small" type="primary" effect="dark">发票核准</el-tag>
            <span>租户发票上传</span>
          </div>
          <el-upload
            :show-file-list="false"
            :http-request="(opt) => handleOne('INVOICE', opt.file)"
            :disabled="!periodId || busy"
            accept=".pdf,.ofd,.jpg,.jpeg,.png,.webp"
            multiple
          >
            <el-button size="small" type="primary" plain :loading="uploadingType === 'INVOICE'">
              <el-icon><Upload /></el-icon>选择发票
            </el-button>
          </el-upload>
        </div>
        <div class="card-desc">
          {{ draggingType === 'INVOICE'
            ? '松开鼠标即可开始 AI 自动识别核对发票...'
            : '拖拽数智云仓/昌记开具的电子发票（推荐 PDF / OFD）或点击上传。AI 自动核对：购买方、销售方、发票含税金额；完全一致后自动上传至对应通知单「发票」并标记已开票。' }}
        </div>
      </div>
    </div>

    <!-- AI 核准结果明细 -->
    <div v-if="results.length" class="result-panel">
      <div class="result-head">
        <span>
          本次 AI 核准记录（共 {{ results.length }} 份：
          <strong class="ok-txt">自动通过 {{ approvedCount }} 份</strong>，
          <strong class="warn-txt">需人工核对 {{ manualCount }} 份</strong>）
        </span>
        <el-button link type="info" size="small" @click="results = []">清空记录</el-button>
      </div>
      <div v-for="(r, idx) in results" :key="idx" class="result-row" :class="r.status === 'AUTO_APPROVED' ? 'ok' : 'warn'">
        <div class="result-main">
          <el-tag size="small" :type="r.docType === 'RECEIPT' ? 'success' : 'primary'" effect="plain">
            {{ r.docType === 'RECEIPT' ? '缴费回执' : '发票' }}
          </el-tag>
          <span class="fname" :title="r.fileName">{{ r.fileName }}</span>
          <el-tag size="small" :type="r.status === 'AUTO_APPROVED' ? 'success' : 'danger'">
            {{ r.status === 'AUTO_APPROVED' ? 'AI核准通过 · 已自动归档' : '拦截 · 请人工核对' }}
          </el-tag>
          <span v-if="r.matchedNoticeNo" class="matched-info">
            → {{ r.matchedNoticeNo }}（{{ r.matchedTenantName }}）
          </span>
        </div>
        <div class="result-reason">{{ r.reason }}</div>
        <div v-if="r.checks && r.checks.length" class="check-chips">
          <el-tag
            v-for="(c, ci) in r.checks"
            :key="ci"
            size="small"
            :type="c.passed ? 'success' : 'danger'"
            effect="plain"
          >
            {{ c.passed ? '✓' : '✗' }} {{ c.label }}：{{ c.actual }}
            <template v-if="!c.passed">（通知单：{{ c.expected }}）</template>
          </el-tag>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import { noticeAiApi } from '@/api/energy'

const props = defineProps({
  periodId: { type: Number, default: null },
  /** utility = 水电/垃圾费缴费汇总 | lease = 租金/物业管理费缴费汇总 */
  mode: { type: String, default: 'utility' }
})
const emit = defineEmits(['matched'])

const draggingType = ref('')
const uploadingType = ref('')
const pendingCount = ref(0)
const results = ref([])

const busy = computed(() => pendingCount.value > 0)
const approvedCount = computed(() => results.value.filter((r) => r.status === 'AUTO_APPROVED').length)
const manualCount = computed(() => results.value.filter((r) => r.status !== 'AUTO_APPROVED').length)

function onDragEnter(type) {
  if (!props.periodId || busy.value) return
  draggingType.value = type
}

function onDragLeave(e, type) {
  if (draggingType.value !== type) return
  const rect = e.currentTarget.getBoundingClientRect()
  if (e.clientX <= rect.left || e.clientX >= rect.right || e.clientY <= rect.top || e.clientY >= rect.bottom) {
    draggingType.value = ''
  }
}

async function onDrop(e, type) {
  draggingType.value = ''
  if (!props.periodId || busy.value) return
  const dropped = Array.from(e.dataTransfer?.files || [])
  if (!dropped.length) return
  let anyApproved = false
  for (const f of dropped) {
    const ok = await handleOne(type, f, false)
    if (ok) anyApproved = true
  }
  if (anyApproved) emit('matched')
}

async function handleOne(docType, file, emitNow = true) {
  if (!props.periodId || !file) return false
  uploadingType.value = docType
  pendingCount.value++
  try {
    const fd = new FormData()
    fd.append('file', file)
    fd.append('mode', props.mode)
    fd.append('docType', docType)
    fd.append('periodId', props.periodId)
    const res = await noticeAiApi.verifyUpload(fd)
    results.value.unshift(res)
    if (res.status === 'AUTO_APPROVED') {
      ElMessage.success(`「${file.name}」AI核准通过，已自动归档到 ${res.matchedNoticeNo}（${res.matchedTenantName}）`)
      if (emitNow) emit('matched')
      return true
    } else {
      ElMessage.warning(`「${file.name}」未完全匹配，已拦截并提醒人工核对`)
      return false
    }
  } catch (err) {
    results.value.unshift({
      status: 'MANUAL_REVIEW',
      fileName: file.name,
      docType,
      reason: err?.message || '识别请求失败，请人工核对后手动上传',
      checks: []
    })
    return false
  } finally {
    pendingCount.value = Math.max(0, pendingCount.value - 1)
    if (pendingCount.value === 0) uploadingType.value = ''
  }
}
</script>

<style scoped>
.ai-box {
  margin-bottom: 12px;
  padding: 12px 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-blank);
}
.ai-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}
.ai-head span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.ai-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(340px, 1fr));
  gap: 12px;
}
.drop-card {
  border: 1.5px dashed var(--el-border-color);
  border-radius: 8px;
  padding: 10px 12px;
  transition: border-color 0.2s, background-color 0.2s;
}
.drop-card.receipt.is-dragging {
  border-color: var(--el-color-success);
  background: var(--el-color-success-light-9);
}
.drop-card.invoice.is-dragging {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.drop-card.is-disabled {
  opacity: 0.65;
}
.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
}
.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 13px;
}
.card-desc {
  font-size: 12px;
  line-height: 1.55;
  color: var(--el-text-color-secondary);
}
.result-panel {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid var(--el-border-color-lighter);
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.ok-txt { color: var(--el-color-success); }
.warn-txt { color: var(--el-color-danger); }
.result-row {
  padding: 8px 10px;
  border-radius: 6px;
  border: 1px solid var(--el-border-color-lighter);
  font-size: 12px;
}
.result-row.ok {
  background: var(--el-color-success-light-9);
  border-color: var(--el-color-success-light-7);
}
.result-row.warn {
  background: var(--el-color-warning-light-9);
  border-color: var(--el-color-warning-light-7);
}
.result-main {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.fname {
  font-weight: 600;
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.matched-info {
  color: var(--el-color-success-dark-2);
  font-weight: 600;
}
.result-reason {
  margin-top: 4px;
  color: var(--el-text-color-regular);
  line-height: 1.5;
}
.check-chips {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
</style>
