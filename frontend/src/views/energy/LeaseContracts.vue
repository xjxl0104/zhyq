<template>
  <div class="page-container">
    <div class="summary-grid">
      <div class="summary-item">
        <span>在租计费合同</span>
        <strong>{{ activeContracts.length }} 份</strong>
      </div>
      <div class="summary-item ok">
        <span>已关联招商正式合同</span>
        <strong>{{ linkedCount }} / {{ activeContracts.length }} 份</strong>
      </div>
      <div class="summary-item">
        <span>计租面积合计</span>
        <strong>{{ num(totalArea) }} ㎡</strong>
      </div>
      <div class="summary-item hl">
        <span>月标准租金合计</span>
        <strong>¥{{ money(totalMonthlyRent) }}</strong>
      </div>
      <div class="summary-item">
        <span>月标准物业费合计</span>
        <strong>¥{{ money(totalMonthlyProp) }}</strong>
      </div>
    </div>

    <div class="table-card">
      <div class="toolbar">
        <el-button type="primary" @click="openEdit(null)"><el-icon><Plus /></el-icon>新增合同</el-button>
        <el-button type="success" plain :loading="syncing" @click="syncFormal">同步完善正式合同</el-button>
        <el-input v-model="keyword" placeholder="搜索租户名称 / 楼层区域 / 合同编号" clearable style="width: 260px" />
        <el-checkbox v-model="showDisabled">显示停用合同</el-checkbox>
      </div>
      <div class="toolbar-tip">
        本台账是「缴费管理」下保证金、租金、物业费等所有收费模块的统一合同数据源。点击「同步完善正式合同」会以本台账的合同编号与条款为准，单向同步完善招商「正式合同」（不会改动本台账已有数据）；后续新租户在「招商租赁 → 正式合同」录入正式合同时，也会自动同步新增到本台账。
      </div>

      <el-table :data="viewRows" v-loading="loading" border size="small" row-key="id" show-summary :summary-method="tableSummary">
        <el-table-column prop="seqNo" label="序号" width="55" align="center" />
        <el-table-column prop="agreementNo" label="合同 / 协议编号" min-width="230" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="code-cell">
              <span>{{ row.agreementNo || '待补充' }}</span>
              <el-tag v-if="row.bizContractId" size="small" type="success" effect="plain">已关联正式合同</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="tenantName" label="租户名称" min-width="185" show-overflow-tooltip />
        <el-table-column prop="unitName" label="楼层/区域" min-width="175" show-overflow-tooltip />
        <el-table-column label="计租面积(㎡)" width="105" align="right">
          <template #default="{ row }">{{ num(row.leaseArea) }}</template>
        </el-table-column>
        <el-table-column label="起止日期" width="180" align="center">
          <template #default="{ row }">{{ row.startDate }} ~ {{ row.endDate }}</template>
        </el-table-column>
        <el-table-column label="月固定总价" width="128" align="right">
          <template #default="{ row }">
            <span v-if="Number(row.fixedMonthlyRent) > 0" class="fixed-rent">¥{{ money(row.fixedMonthlyRent) }}/月</span>
            <span v-else class="muted">按单价</span>
          </template>
        </el-table-column>
        <el-table-column label="基础租金单价" width="105" align="right">
          <template #default="{ row }">{{ num(row.rentUnitPrice, 4) }}</template>
        </el-table-column>
        <el-table-column label="基础物业单价" width="105" align="right">
          <template #default="{ row }">{{ num(row.propertyUnitPrice, 4) }}</template>
        </el-table-column>
        <el-table-column label="递增规则" width="110" align="center">
          <template #default="{ row }">每{{ row.escalationMonths || 36 }}月 +{{ Math.round(Number(row.escalationRate || 0) * 100) }}%</template>
        </el-table-column>
        <el-table-column prop="collectionTiming" label="约定收款时点" min-width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="68" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '在用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="editVisible" :title="form.id ? '编辑计费合同' : '新增计费合同'" width="820px" destroy-on-close>
      <el-form :model="form" label-width="145px" size="small">
        <el-row :gutter="14">
          <el-col :span="12"><el-form-item label="租户名称" required><el-input v-model="form.tenantName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="楼层/区域" required><el-input v-model="form.unitName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="合同序号"><el-input-number v-model="form.seqNo" :min="1" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="计租面积(㎡)" required><el-input-number v-model="form.leaseArea" :min="0" :precision="2" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="起租日" required><el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="到期日" required><el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>

          <el-col :span="12">
            <el-form-item label="月租金单价(元/㎡)" :required="!hasFixedRent">
              <el-input-number v-model="form.rentUnitPrice" :min="0" :precision="6" style="width: 100%"
                               :placeholder="hasFixedRent ? '已按月固定总价计费' : '元/㎡/月'" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="月物业单价(元/㎡)" required>
              <el-input-number v-model="form.propertyUnitPrice" :min="0" :precision="4" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="月固定总价(元/月)">
              <div class="fixed-rent-row">
                <el-input-number v-model="form.fixedMonthlyRent" :min="0" :precision="2" :controls="false"
                                 placeholder="选填，如 102800（填了则按月固定总价起始并递增）" style="width: 280px" />
                <span class="unit-label">元 / 月</span>
                <el-button v-if="hasFixedRent" link type="info" @click="form.fixedMonthlyRent = null">清空改按单价</el-button>
              </div>
              <div class="field-hint">
                选填。若租户按月固定总价签约，填写后起始租金按此总价计算，后续每期递增也直接按该总价递增{{ impliedUnitText }}；留空或填 0 则按「计租面积 × 月租金单价」计算。
              </div>
            </el-form-item>
          </el-col>

          <el-col :span="12"><el-form-item label="递增比例(0.1=10%)"><el-input-number v-model="form.escalationRate" :min="0" :max="1" :step="0.01" :precision="4" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="递增周期(月)"><el-input-number v-model="form.escalationMonths" :min="1" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="租金保证金(元)"><el-input-number v-model="form.rentDeposit" :min="0" :precision="2" :controls="false" placeholder="留空默认按2个月租金" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="物业保证金(元)"><el-input-number v-model="form.propertyDeposit" :min="0" :precision="2" :controls="false" placeholder="留空默认按2个月物管费" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="保证金缴纳约定"><el-input v-model="form.depositTerms" placeholder="如 签约3日内一次性缴纳 / 分期计划" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="合同 / 协议编号"><el-input v-model="form.agreementNo" placeholder="租金协议号 / 物业协议号（保存时同步回写招商正式合同编号）" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="约定收款时点"><el-input v-model="form.collectionTiming" placeholder="如 当月30日前收取下个月租金" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio :value="1">在用</el-radio>
                <el-radio :value="0">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="免租/优惠规则JSON">
              <el-input v-model="form.rulesJson" type="textarea" :rows="4" placeholder='{"periods":[{"from":"2026-06","to":"2026-07","rentFactor":0,"propertyFactor":0,"remark":"免租期"}]}' />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { leaseFeeApi } from '@/api/energy'
import { money, num } from './feePeriod'

const loading = ref(false)
const syncing = ref(false)
const saving = ref(false)
const contracts = ref([])
const keyword = ref('')
const showDisabled = ref(false)
const editVisible = ref(false)
const form = reactive({})

const activeContracts = computed(() => contracts.value.filter((c) => c.status === 1))
const linkedCount = computed(() => activeContracts.value.filter((c) => c.bizContractId).length)
const totalArea = computed(() => activeContracts.value.reduce((s, c) => s + Number(c.leaseArea || 0), 0))
const totalMonthlyRent = computed(() => activeContracts.value.reduce((s, c) => {
  const m = Number(c.fixedMonthlyRent || 0) > 0 ? Number(c.fixedMonthlyRent) : Number(c.leaseArea || 0) * Number(c.rentUnitPrice || 0)
  return s + m
}, 0))
const totalMonthlyProp = computed(() => activeContracts.value.reduce((s, c) => s + Number(c.leaseArea || 0) * Number(c.propertyUnitPrice || 0), 0))

const viewRows = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return contracts.value.filter((r) => {
    if (!showDisabled.value && r.status !== 1) return false
    if (!kw) return true
    return `${r.tenantName || ''} ${r.unitName || ''} ${r.agreementNo || ''}`.toLowerCase().includes(kw)
  })
})

const hasFixedRent = computed(() => Number(form.fixedMonthlyRent) > 0)
const impliedUnitText = computed(() => {
  if (!hasFixedRent.value || !(Number(form.leaseArea) > 0)) return ''
  return `（折算约 ${(Number(form.fixedMonthlyRent) / Number(form.leaseArea)).toFixed(4)} 元/㎡/月）`
})

async function load() {
  loading.value = true
  try {
    contracts.value = await leaseFeeApi.contracts(true) || []
  } finally {
    loading.value = false
  }
}

async function syncFormal() {
  syncing.value = true
  try {
    const r = await leaseFeeApi.syncFormalContracts()
    ElMessage.success(`已按基础台账完善正式合同：关联 ${r.linked} 份，更新 ${r.updated || 0} 份，补建 ${r.formalCreated || 0} 份`)
    await load()
  } finally {
    syncing.value = false
  }
}

function openEdit(row) {
  Object.assign(form, row ? {
    ...row,
    fixedMonthlyRent: row.fixedMonthlyRent == null ? null : Number(row.fixedMonthlyRent),
    rentDeposit: row.rentDeposit == null ? null : Number(row.rentDeposit),
    propertyDeposit: row.propertyDeposit == null ? null : Number(row.propertyDeposit)
  } : {
    id: null, seqNo: contracts.value.length + 1, tenantName: '', unitName: '', agreementNo: '',
    leaseArea: 0, startDate: '', endDate: '', rentUnitPrice: 0, fixedMonthlyRent: null, propertyUnitPrice: 2,
    escalationRate: 0.1, escalationMonths: 36, rentDeposit: null, propertyDeposit: null, depositTerms: '',
    collectionTiming: '', rulesJson: '', status: 1
  })
  editVisible.value = true
}

async function save() {
  if (!form.tenantName?.trim() || !form.unitName?.trim()) return ElMessage.warning('请填写租户名称和楼层/区域')
  if (!form.startDate || !form.endDate) return ElMessage.warning('请填写起租日和到期日')
  if (form.rulesJson?.trim()) {
    try { JSON.parse(form.rulesJson) } catch { return ElMessage.warning('免租/优惠规则 JSON 格式不正确') }
  }
  saving.value = true
  try {
    await leaseFeeApi.saveContract({
      ...form,
      fixedMonthlyRent: Number(form.fixedMonthlyRent) > 0 ? Number(form.fixedMonthlyRent) : null
    })
    ElMessage.success('合同已保存，缴费模块草稿月份已按新规则重算')
    editVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  await ElMessageBox.confirm(`确定删除合同「${row.tenantName} - ${row.unitName}」？`, '删除合同', { type: 'warning' })
  await leaseFeeApi.deleteContract(row.id)
  ElMessage.success('已删除')
  await load()
}

function tableSummary({ columns, data }) {
  const sumArea = data.reduce((s, r) => s + Number(r.leaseArea || 0), 0)
  return columns.map((col, i) => {
    if (i === 0) return '合计'
    if (col.label === '计租面积(㎡)') return num(sumArea)
    return ''
  })
}

onMounted(load)
</script>

<style scoped>
.summary-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 10px; margin-bottom: 12px; }
.summary-item { padding: 12px 14px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; display: flex; flex-direction: column; gap: 4px; background: var(--el-fill-color-blank); }
.summary-item span { font-size: 12px; color: var(--el-text-color-secondary); }
.summary-item strong { font-size: 18px; font-variant-numeric: tabular-nums; }
.summary-item.ok strong { color: var(--el-color-success); }
.summary-item.hl strong { color: var(--el-color-primary); }
.toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; flex-wrap: wrap; }
.toolbar-tip { font-size: 12px; color: var(--el-text-color-secondary); margin-bottom: 12px; line-height: 1.5; }
.code-cell { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.fixed-rent { font-weight: 600; color: var(--el-color-primary); font-variant-numeric: tabular-nums; }
.muted { color: var(--el-text-color-placeholder); font-size: 12px; }
.fixed-rent-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.unit-label { font-size: 12px; color: var(--el-text-color-secondary); }
.field-hint { font-size: 12px; color: var(--el-text-color-secondary); line-height: 1.5; margin-top: 4px; }
</style>
