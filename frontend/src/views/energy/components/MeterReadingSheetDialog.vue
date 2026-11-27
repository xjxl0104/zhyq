<template>
  <el-dialog
    :model-value="modelValue"
    title="水电表抄表单（预览 / 调整内容 / 打印下载）"
    width="1240px"
    top="3vh"
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <!-- 顶部控制栏：范围、模式、视图切换、打印与下载 -->
    <div class="sheet-toolbar">
      <div class="left-controls">
        <el-radio-group v-model="scope" size="small">
          <el-radio-button value="BOTH">电表 + 水表</el-radio-button>
          <el-radio-button value="ELECTRIC">仅电表</el-radio-button>
          <el-radio-button value="WATER">仅水表</el-radio-button>
        </el-radio-group>

        <el-radio-group v-model="fillMode" size="small">
          <el-radio-button value="BLANK">现场空白抄表单</el-radio-button>
          <el-radio-button value="FILLED">含本期读数核对单</el-radio-button>
        </el-radio-group>

        <el-radio-group v-model="activeView" size="small">
          <el-radio-button value="preview">打印版式预览</el-radio-button>
          <el-radio-button value="edit">调整表单内容</el-radio-button>
        </el-radio-group>
      </div>

      <div class="right-controls">
        <el-button size="small" @click="resetFromSystem">重置为系统数据</el-button>
        <el-button size="small" type="primary" plain :loading="downloading" :disabled="!visibleRows.length" @click="onDownloadPdf">
          <el-icon><Download /></el-icon>下载 PDF
        </el-button>
        <el-button size="small" type="primary" :disabled="!visibleRows.length" @click="onPrint">
          <el-icon><Printer /></el-icon>打印抄表单
        </el-button>
      </div>
    </div>

    <div v-loading="loading" class="sheet-body">
      <!-- 视图 1：所见即所得 A4 打印版式预览 -->
      <div v-if="activeView === 'preview'" class="preview-wrap">
        <div class="preview-tip">
          已选 <strong>{{ visibleRows.length }}</strong> 块表计（可在上方切到「调整表单内容」修改标题、抄表人、勾选表计、调整任意行或临时加行，修改仅作用于本次打印/下载，不影响系统账期数据）。
        </div>
        <iframe class="preview-iframe" :srcdoc="previewDoc" />
      </div>

      <!-- 视图 2：可视化编辑表头与明细内容 -->
      <div v-else class="edit-wrap">
        <el-form :model="meta" label-width="88px" size="small" class="meta-form">
          <el-row :gutter="12">
            <el-col :span="10"><el-form-item label="表单标题"><el-input v-model="meta.title" /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="园区项目"><el-input v-model="meta.parkName" /></el-form-item></el-col>
            <el-col :span="6"><el-form-item label="抄表日期"><el-date-picker v-model="meta.readDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
            <el-col :span="10"><el-form-item label="计费账期"><el-input v-model="meta.periodText" /></el-form-item></el-col>
            <el-col :span="7"><el-form-item label="抄表人"><el-input v-model="meta.reader" placeholder="选填，留空则手写签字" /></el-form-item></el-col>
            <el-col :span="7"><el-form-item label="复核人"><el-input v-model="meta.reviewer" placeholder="选填，留空则手写签字" /></el-form-item></el-col>
            <el-col :span="24">
              <el-form-item label="表底说明">
                <el-input v-model="meta.notes" type="textarea" :rows="2" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>

        <div class="edit-table-bar">
          <span class="sub">勾选要打印的表计，可直接在表格里微调文字或临时加行（不影响系统原数据）：</span>
          <div class="bar-btns">
            <el-button size="small" @click="toggleAll(true)">全选</el-button>
            <el-button size="small" @click="toggleAll(false)">全不选</el-button>
            <el-button size="small" type="primary" plain @click="addTempRow('电')">+ 临时电表行</el-button>
            <el-button size="small" type="primary" plain @click="addTempRow('水')">+ 临时水表行</el-button>
          </div>
        </div>

        <el-table :data="scopedRows" border size="small" max-height="410">
          <el-table-column label="打印" width="54" align="center">
            <template #default="{ row }"><el-checkbox v-model="row.included" /></template>
          </el-table-column>
          <el-table-column label="类型" width="70" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.energyType === '电' ? 'warning' : 'primary'" effect="plain">
                {{ row.energyType }}表
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="区域/楼层" width="110">
            <template #default="{ row }"><el-input v-model="row.area" size="small" /></template>
          </el-table-column>
          <el-table-column label="租户 / 使用单位" min-width="190">
            <template #default="{ row }"><el-input v-model="row.tenantDisplay" size="small" /></template>
          </el-table-column>
          <el-table-column label="表计编号" width="125">
            <template #default="{ row }"><el-input v-model="row.code" size="small" /></template>
          </el-table-column>
          <el-table-column label="倍率" width="76">
            <template #default="{ row }"><el-input-number v-model="row.ratio" :min="1" :controls="false" size="small" style="width: 100%" /></template>
          </el-table-column>
          <el-table-column label="上期读数" width="105">
            <template #default="{ row }"><el-input v-model="row.prevReading" size="small" /></template>
          </el-table-column>
          <el-table-column v-if="fillMode === 'FILLED'" label="本期读数" width="105">
            <template #default="{ row }"><el-input v-model="row.currReading" size="small" /></template>
          </el-table-column>
          <el-table-column label="备注" min-width="140">
            <template #default="{ row }"><el-input v-model="row.remark" size="small" placeholder="铅封/表况说明" /></template>
          </el-table-column>
          <el-table-column label="操作" width="64" align="center">
            <template #default="{ row }">
              <el-button link type="danger" size="small" @click="removeRow(row)">移除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Printer } from '@element-plus/icons-vue'
import { feePeriodApi } from '@/api/energy'
import { periodRange } from '../feePeriod'
import { buildReadingSheetDocument, downloadReadingSheetPdf, printReadingSheet } from '../meterReadingPrint'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  period: { type: Object, default: null },
  defaultEnergyType: { type: String, default: '电' }
})
defineEmits(['update:modelValue'])

const DEFAULT_NOTES = '抄表须知：1. 上期读数取自系统已锁定记录；2. 现场抄表请同步核对表计编号、互感器倍率及铅封完好情况，如表计异常请在备注栏注明；3. 抄表完成后须由抄表人、复核人及租户现场确认签字，作为本月水电费核算原始凭据。'

const loading = ref(false)
const downloading = ref(false)
const scope = ref('BOTH') // BOTH | ELECTRIC | WATER
const fillMode = ref('BLANK') // BLANK | FILLED
const activeView = ref('preview') // preview | edit
const allRows = ref([])
let rowUid = 1

const meta = reactive({
  title: '',
  parkName: '',
  periodText: '',
  readDate: '',
  reader: '',
  reviewer: '',
  notes: DEFAULT_NOTES
})

const scopedRows = computed(() => {
  if (scope.value === 'ELECTRIC') return allRows.value.filter((r) => r.energyType === '电')
  if (scope.value === 'WATER') return allRows.value.filter((r) => r.energyType === '水')
  return allRows.value
})

const visibleRows = computed(() => scopedRows.value.filter((r) => r.included))

const previewDoc = computed(() =>
  buildReadingSheetDocument(meta, visibleRows.value, { showFilled: fillMode.value === 'FILLED' })
)

function defaultTitleFor(sc) {
  const park = props.period?.parkName || 'DI PARK数智云仓产业园'
  if (sc === 'ELECTRIC') return `${park} 电表抄表记录单`
  if (sc === 'WATER') return `${park} 水表抄表记录单`
  return `${park} 水电表抄表记录单`
}

watch(scope, (sc, old) => {
  if (!meta.title || meta.title === defaultTitleFor(old)) {
    meta.title = defaultTitleFor(sc)
  }
})

function mapSheetRows(list, energyType) {
  return (list || []).map((r) => ({
    _uid: rowUid++,
    included: true,
    energyType,
    meterRole: r.meterRole || 'TENANT',
    area: r.area || '',
    tenantDisplay: r.tenantName || r.customerName || (r.meterRole === 'PROPERTY' ? '物业自用表' : '-'),
    code: r.code || '',
    ratio: Number(r.ratio || 1),
    prevReading: r.prevReading == null ? '' : String(r.prevReading),
    currReading: r.currReading == null ? '' : String(r.currReading),
    remark: r.meterRole === 'PROPERTY' ? '物业表' : (!r.sharePublic ? '不参与公摊' : '')
  }))
}

function initMeta() {
  const p = props.period
  const ym = p?.period ? `${p.period.slice(0, 4)}年${Number(p.period.slice(5))}月` : ''
  const range = p ? periodRange(p) : ''
  meta.parkName = p?.parkName || 'DI PARK数智云仓产业园'
  meta.title = defaultTitleFor(scope.value)
  meta.periodText = range && range !== p?.period ? `${ym}（${range}）` : ym
  meta.readDate = p?.periodEnd || new Date().toISOString().slice(0, 10)
  meta.reader = ''
  meta.reviewer = ''
  meta.notes = DEFAULT_NOTES
}

async function loadData() {
  if (!props.period?.id) return
  loading.value = true
  try {
    const [elec, water] = await Promise.all([
      feePeriodApi.sheet(props.period.id, '电'),
      feePeriodApi.sheet(props.period.id, '水')
    ])
    allRows.value = [
      ...mapSheetRows(elec?.rows, '电'),
      ...mapSheetRows(water?.rows, '水')
    ]
    initMeta()
  } finally {
    loading.value = false
  }
}

watch(() => props.modelValue, (open) => {
  if (!open) return
  scope.value = 'BOTH'
  fillMode.value = 'BLANK'
  activeView.value = 'preview'
  loadData()
})

function resetFromSystem() {
  loadData()
  ElMessage.success('已重新载入系统最新表计数据')
}

function toggleAll(flag) {
  scopedRows.value.forEach((r) => { r.included = flag })
}

function addTempRow(energyType) {
  allRows.value.push({
    _uid: rowUid++,
    included: true,
    energyType,
    meterRole: 'TENANT',
    area: '',
    tenantDisplay: '',
    code: '',
    ratio: 1,
    prevReading: '',
    currReading: '',
    remark: ''
  })
}

function removeRow(row) {
  allRows.value = allRows.value.filter((r) => r._uid !== row._uid)
}

function onPrint() {
  if (!visibleRows.value.length) return ElMessage.warning('请至少勾选一条表计记录')
  printReadingSheet(meta, visibleRows.value, { showFilled: fillMode.value === 'FILLED' })
}

async function onDownloadPdf() {
  if (!visibleRows.value.length) return ElMessage.warning('请至少勾选一条表计记录')
  downloading.value = true
  try {
    const ym = props.period?.period || '抄表单'
    const tag = scope.value === 'ELECTRIC' ? '电表抄表单' : (scope.value === 'WATER' ? '水表抄表单' : '水电表抄表单')
    await downloadReadingSheetPdf(meta, visibleRows.value, { showFilled: fillMode.value === 'FILLED' }, `${ym}_${tag}.pdf`)
    ElMessage.success('抄表单 PDF 已下载')
  } finally {
    downloading.value = false
  }
}
</script>

<style scoped>
.sheet-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.left-controls, .right-controls {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.sheet-body { min-height: 520px; }
.preview-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 8px;
}
.preview-iframe {
  width: 100%;
  height: 66vh;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  background: #fff;
}
.meta-form {
  padding: 10px 12px 2px;
  background: var(--el-fill-color-lighter);
  border-radius: 8px;
  margin-bottom: 10px;
}
.edit-table-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.edit-table-bar .sub { font-size: 12px; color: var(--el-text-color-secondary); }
.bar-btns { display: flex; gap: 6px; flex-wrap: wrap; }
</style>
