<template>
  <el-dialog :model-value="modelValue" title="导入历史收款" width="860px" top="6vh" @update:model-value="emit('update:modelValue', $event)" @closed="reset">
    <div class="import-steps">
      <ol>
        <li><b>下载模板</b>，按「合同编号 + 费用类型 + 收款日期 + 金额」每笔一行填写（可选：支付方式、账期、备注）。</li>
        <li><b>上传并预览</b>：系统先计算每笔会冲抵哪些账单，此时不会写入任何数据。</li>
        <li><b>确认导入</b>：整份文件没有错误才能写入；每笔等同一次正式收款，可在账单里红冲撤销，重复上传同一文件不会重复入账。</li>
      </ol>
      <el-button text type="primary" @click="downloadTemplate">下载 CSV 模板</el-button>
    </div>
    <el-upload class="import-upload" drag :auto-upload="false" :limit="1" accept=".xlsx,.xls,.csv" :file-list="fileList"
               :on-change="onFile" :on-exceed="onExceed" :on-remove="reset">
      <div class="el-upload__text">拖拽 Excel / CSV 到这里，或 <em>点击选择</em></div>
      <template #tip><div class="el-upload__tip">一次最多 2000 行；金额按账期从早到晚冲抵该合同该费用类型的未收账单。</div></template>
    </el-upload>

    <template v-if="report">
      <div class="report-summary" role="status">
        <span class="chip ok">可导入 <b>{{ report.okCount }}</b> 笔 · ¥{{ money(report.totalAmount) }} · 冲抵 {{ report.billCount }} 张账单</span>
        <span v-if="report.skippedCount" class="chip skip">已导入过 <b>{{ report.skippedCount }}</b> 笔（自动跳过）</span>
        <span v-if="report.errorCount" class="chip error">有问题 <b>{{ report.errorCount }}</b> 行（修正后重新上传）</span>
      </div>
      <div class="report-filter" role="group" aria-label="筛选预览行">
        <button type="button" :class="{ active: filter === 'all' }" @click="filter = 'all'">全部 {{ report.totalRows }}</button>
        <button type="button" :class="{ active: filter === 'error' }" :disabled="!report.errorCount" @click="filter = 'error'">有问题 {{ report.errorCount }}</button>
        <button type="button" :class="{ active: filter === 'ok' }" :disabled="!report.okCount" @click="filter = 'ok'">可导入 {{ report.okCount }}</button>
      </div>
      <el-table :data="visibleRows" size="small" max-height="320" row-key="rowNo" class="report-table">
        <el-table-column prop="rowNo" label="行" width="56" />
        <el-table-column prop="contractCode" label="合同编号" min-width="120" show-overflow-tooltip />
        <el-table-column prop="feeType" label="费用类型" width="100" />
        <el-table-column prop="payDate" label="收款日期" width="110" />
        <el-table-column label="金额" width="110" align="right"><template #default="{ row }">{{ row.amount == null ? '-' : money(row.amount) }}</template></el-table-column>
        <el-table-column label="结果" min-width="260">
          <template #default="{ row }">
            <el-tag :type="tagType(row.status)" size="small" effect="light">{{ statusText(row.status) }}</el-tag>
            <span class="row-message">{{ row.message }}</span>
            <div v-if="row.allocations.length" class="row-alloc">{{ row.allocations.map(item => `${item.billCode}（${item.periodStart || '—'}）¥${money(item.amount)}`).join('；') }}</div>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
      <el-button :disabled="!file" :loading="loading === 'preview'" @click="runPreview">上传并预览</el-button>
      <el-button type="primary" :disabled="!canConfirm" :loading="loading === 'confirm'" @click="runConfirm">确认导入 {{ report?.okCount || '' }} 笔</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { paymentApi } from '@/api/finance'
import { money } from '@/utils/format'

defineProps({ modelValue: { type: Boolean, default: false } })
const emit = defineEmits(['update:modelValue', 'done'])

const file = ref(null)
const fileList = ref([])
const report = ref(null)
const loading = ref('')
const filter = ref('all')

const visibleRows = computed(() => (report.value?.rows || []).filter(row => filter.value === 'all' || row.status === filter.value))
const canConfirm = computed(() => !!file.value && !!report.value && report.value.errorCount === 0 && report.value.okCount > 0 && !loading.value)

function onFile(uploadFile) { file.value = uploadFile.raw; fileList.value = [uploadFile]; report.value = null; filter.value = 'all' }
function onExceed() { ElMessage.warning('一次只能处理一个文件，请先移除当前文件') }
function reset() { file.value = null; fileList.value = []; report.value = null; filter.value = 'all'; loading.value = '' }
function statusText(status) { return { ok: '可导入', skipped: '已导入过', error: '有问题' }[status] || status }
function tagType(status) { return { ok: 'success', skipped: 'info', error: 'danger' }[status] || 'info' }
function form() { const data = new FormData(); data.append('file', file.value); return data }

async function runPreview() {
  loading.value = 'preview'
  try {
    report.value = await paymentApi.previewHistory(form())
    filter.value = report.value.errorCount ? 'error' : 'all'
  } finally { loading.value = '' }
}
async function runConfirm() {
  loading.value = 'confirm'
  try {
    const result = await paymentApi.confirmHistory(form())
    ElMessage.success(`已登记 ${result.okCount} 笔历史收款，冲抵 ${result.billCount} 张账单`)
    emit('done')
    emit('update:modelValue', false)
  } finally { loading.value = '' }
}
function downloadTemplate() {
  const csv = '﻿合同编号,费用类型,收款日期,金额,支付方式,账期,备注\nHT-2026-001,租金,2026-03-15,12000.00,转账,2026-03,示例行请删除\n'
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url
  link.download = '历史收款导入模板.csv'
  link.click()
  URL.revokeObjectURL(url)
}
</script>

<style scoped>
.import-steps { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 14px; padding: 12px 14px; color: #68708a; background: #f8f9fc; border-radius: 10px; font-size: 13px; line-height: 1.7; }
.import-steps ol { margin: 0; padding-left: 18px; }
.import-steps b { color: var(--text-title); }
.import-upload { width: 100%; }
.report-summary { display: flex; flex-wrap: wrap; gap: 8px; margin: 14px 0 10px; }
.chip { padding: 4px 11px; border-radius: 999px; font-size: 13px; }
.chip.ok { color: #1c7f55; background: #e9f7f0; }
.chip.skip { color: #5d6480; background: #f0f1f6; }
.chip.error { color: #c2413a; background: #fff1f0; }
.report-filter { display: flex; gap: 6px; margin-bottom: 8px; }
.report-filter button { padding: 4px 12px; color: #68708a; background: #f4f5f9; border: 0; border-radius: 8px; font-size: 13px; cursor: pointer; }
.report-filter button.active { color: var(--text-title); background: #fff; box-shadow: 0 0 0 1px #c3cbfa; }
.report-filter button:disabled { opacity: .45; cursor: not-allowed; }
.row-message { margin-left: 8px; color: #374151; }
.row-alloc { margin-top: 2px; color: #68708a; font-size: 12px; }
@media (max-width: 680px) { .import-steps { flex-direction: column; } }
</style>
