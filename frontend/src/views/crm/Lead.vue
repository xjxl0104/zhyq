<template>
  <div class="page-container">
    <!-- 统计卡 -->
    <div class="stat-row">
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">总线索</div>
        <div class="stat-value">{{ stats.total }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">当日新增</div>
        <div class="stat-value">{{ stats.todayNew }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">当月新增</div>
        <div class="stat-value">{{ stats.monthNew }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">已流失/暂缓</div>
        <div class="stat-value">{{ stats.invalid }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">月度成交率</div>
        <div class="stat-value">{{ stats.convertRate }}%</div>
      </el-card>
    </div>

    <!-- 查询区 -->
    <details class="search-bar" :open="!isMobile">
      <summary class="search-bar__summary">查询与筛选</summary>
      <el-form :inline="true" :model="query">
        <el-form-item label="客户编号">
          <el-input v-model="query.leadNo" placeholder="如 KH-0001" clearable style="width: 130px" />
        </el-form-item>
        <el-form-item label="客户姓名">
          <el-input v-model="query.contact" placeholder="请输入姓名" clearable style="width: 130px" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="query.phone" placeholder="请输入电话" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item label="公司/店铺">
          <el-input v-model="query.company" placeholder="请输入名称" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="客户类型">
          <el-select v-model="query.customerType" placeholder="全部" clearable style="width: 200px">
            <el-option v-for="t in CUSTOMER_TYPE_OPTIONS" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="客户等级">
          <el-select v-model="query.grade" placeholder="全部" clearable style="width: 150px">
            <el-option v-for="g in GRADE_OPTIONS" :key="g" :label="g" :value="g" />
          </el-select>
        </el-form-item>
        <el-form-item label="当前状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 150px">
            <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="query.ownerName" placeholder="跟进负责人" clearable style="width: 120px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </details>

    <!-- 表格区 -->
    <div class="table-card">
      <div class="toolbar">
        <span class="section-title">
          客户信息登记表
          <span class="hint">— 「最近跟进」「跟进次数」由跟进记录自动维护，不用手填</span>
        </span>
        <div>
          <el-button @click="importVisible = true"><el-icon><Upload /></el-icon>导入登记表</el-button>
          <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增线索</el-button>
        </div>
      </div>

      <MobileRecordList v-if="isMobile" :items="list" :loading="loading" empty-text="暂无线索">
        <template #title="{ row }">
          <span>{{ row.contact || row.company || `线索 #${row.id}` }}</span>
          <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
        <template #default="{ row }">
          <div class="mobile-record-summary">
            <strong>{{ row.leadNo || '未分配编号' }}</strong>
            <el-tag v-if="row.grade" :type="gradeType(row.grade)" size="small">{{ row.grade }}</el-tag>
          </div>
          <div class="mobile-record-meta">{{ row.phone || '未填写电话' }} · {{ row.company || '未填写公司/店铺' }}</div>
          <div class="mobile-record-meta">负责人 {{ row.ownerName || '未分配' }} · 下次跟进 {{ row.nextFollow || '—' }}</div>
          <details class="mobile-record-details">
            <summary :aria-label="`查看线索 ${row.leadNo || row.contact || row.company || row.id} 全部信息`">查看全部信息</summary>
            <dl class="mobile-record-fields">
              <div class="mobile-record-field"><dt>客户编号</dt><dd>{{ row.leadNo || '—' }}</dd></div>
              <div class="mobile-record-field"><dt>登记日期</dt><dd>{{ row.registerDate || '—' }}</dd></div>
              <div class="mobile-record-field"><dt>客户姓名</dt><dd>{{ row.contact || '—' }}</dd></div>
              <div class="mobile-record-field"><dt>联系电话</dt><dd>{{ row.phone || '—' }}</dd></div>
              <div class="mobile-record-field mobile-record-field--wide"><dt>公司/店铺</dt><dd>{{ row.company || '—' }}</dd></div>
              <div class="mobile-record-field"><dt>客户来源</dt><dd>{{ row.source || '—' }}</dd></div>
              <div class="mobile-record-field"><dt>客户类型</dt><dd>{{ row.customerType || '—' }}</dd></div>
              <div class="mobile-record-field"><dt>意向合作方式</dt><dd>{{ row.coopMode || '—' }}</dd></div>
              <div class="mobile-record-field"><dt>面积/库容(㎡)</dt><dd>{{ row.demandArea ?? '—' }}</dd></div>
              <div class="mobile-record-field"><dt>主营品类</dt><dd>{{ row.goodsType || '—' }}</dd></div>
              <div class="mobile-record-field"><dt>日均/月单量</dt><dd>{{ row.orderVolume ?? '—' }}</dd></div>
              <div class="mobile-record-field"><dt>意向合作周期</dt><dd>{{ row.coopPeriod || '—' }}</dd></div>
              <div class="mobile-record-field"><dt>心理价位</dt><dd>{{ row.budgetPrice ?? '—' }}</dd></div>
              <div class="mobile-record-field mobile-record-field--wide"><dt>意向园区/仓库</dt><dd>{{ row.intentPark || '—' }}</dd></div>
              <div class="mobile-record-field mobile-record-field--wide"><dt>所在地区</dt><dd>{{ row.region || '—' }}</dd></div>
              <div class="mobile-record-field"><dt>客户等级</dt><dd>{{ row.grade || '—' }}</dd></div>
              <div class="mobile-record-field"><dt>负责人</dt><dd>{{ row.ownerName || '—' }}</dd></div>
              <div class="mobile-record-field"><dt>当前状态</dt><dd>{{ statusText(row.status) }}</dd></div>
              <div class="mobile-record-field"><dt>最近跟进</dt><dd>{{ row.lastFollowDate || '—' }}</dd></div>
              <div class="mobile-record-field"><dt>跟进次数</dt><dd>{{ row.followCount ?? '—' }}</dd></div>
              <div class="mobile-record-field mobile-record-field--wide"><dt>下次跟进计划</dt><dd>{{ row.nextFollow || '—' }}</dd></div>
              <div class="mobile-record-field mobile-record-field--wide"><dt>跟进记录速记</dt><dd>{{ row.remark || '—' }}</dd></div>
            </dl>
          </details>
        </template>
        <template #actions="{ row }">
          <el-button :aria-label="`查看线索 ${row.leadNo || row.contact || row.company || row.id} 跟进记录`" link type="primary" @click="openFollow(row)">跟进记录</el-button>
          <el-button :aria-label="`编辑线索 ${row.leadNo || row.contact || row.company || row.id}`" link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-button v-if="row.status !== 5" :aria-label="`将线索 ${row.leadNo || row.contact || row.company || row.id} 转为客户`" link type="success" @click="convert(row)">转客户</el-button>
          <el-popconfirm title="确认删除该线索?" @confirm="remove(row.id)">
            <template #reference><el-button :aria-label="`删除线索 ${row.leadNo || row.contact || row.company || row.id}`" link type="danger">删除</el-button></template>
          </el-popconfirm>
        </template>
      </MobileRecordList>
      <el-table v-else :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column prop="leadNo" label="客户编号" width="110" fixed />
        <el-table-column prop="registerDate" label="登记日期" width="110" />
        <el-table-column prop="contact" label="客户姓名" width="100" fixed />
        <el-table-column prop="phone" label="联系电话" width="130" />
        <el-table-column prop="company" label="公司/店铺" min-width="150" show-overflow-tooltip />
        <el-table-column prop="source" label="客户来源" width="120" />
        <el-table-column prop="customerType" label="客户类型" min-width="150" show-overflow-tooltip />
        <el-table-column prop="coopMode" label="意向合作方式" min-width="150" show-overflow-tooltip />
        <el-table-column prop="demandArea" label="面积/库容(㎡)" width="120" />
        <el-table-column prop="goodsType" label="主营品类" width="110" show-overflow-tooltip />
        <el-table-column prop="orderVolume" label="日均/月单量" width="120" />
        <el-table-column prop="budgetPrice" label="心理价位" width="110" show-overflow-tooltip />
        <el-table-column prop="intentPark" label="意向园区/仓库" min-width="150" show-overflow-tooltip />
        <el-table-column prop="region" label="所在地区" min-width="140" show-overflow-tooltip />
        <el-table-column label="等级" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.grade" :type="gradeType(row.grade)" size="small">{{ row.grade.charAt(0) }}</el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column prop="ownerName" label="负责人" width="100" />
        <el-table-column label="当前状态" width="140">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastFollowDate" label="最近跟进" width="110" />
        <el-table-column prop="followCount" label="跟进次数" width="100" align="center" />
        <el-table-column prop="nextFollow" label="下次跟进计划" width="170" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openFollow(row)">跟进记录</el-button>
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button v-if="row.status !== 5" link type="success" @click="convert(row)">转客户</el-button>
            <el-popconfirm title="确认删除该线索?" @confirm="remove(row.id)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                     :total="total" v-model:current-page="query.pageNo"
                     v-model:page-size="query.pageSize" :page-sizes="[10, 20, 50]" @change="load" />
    </div>

    <!-- 新增/编辑 -->
    <el-dialog v-model="dialogVisible" :title="form.id ? `编辑线索 ${form.leadNo || ''}` : '新增线索'" width="880px">
      <el-form :model="form" label-width="130px" ref="formRef" :rules="rules">
        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="登记日期" prop="registerDate">
              <el-date-picker v-model="form.registerDate" type="date" value-format="YYYY-MM-DD"
                              placeholder="选择日期" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="客户来源">
              <el-select v-model="form.source" placeholder="请选择" clearable style="width: 100%">
                <el-option v-for="s in SOURCE_OPTIONS" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="客户姓名" prop="contact">
              <el-input v-model="form.contact" placeholder="如：陈总" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="联系电话">
              <el-input v-model="form.phone" placeholder="手机号" />
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="公司名称/店铺名称">
              <el-input v-model="form.company" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="客户类型">
              <el-select v-model="form.customerType" placeholder="请选择" clearable style="width: 100%">
                <el-option v-for="t in CUSTOMER_TYPE_OPTIONS" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="意向合作方式">
              <el-select v-model="form.coopMode" placeholder="请选择" clearable style="width: 100%">
                <el-option v-for="m in COOP_MODE_OPTIONS" :key="m" :label="m" :value="m" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">需求信息</el-divider>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="面积/库容(㎡)">
              <el-input v-model="form.demandArea" placeholder="如：5000" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="主营品类/货物">
              <el-input v-model="form.goodsType" placeholder="如：化妆品" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="日均/月发货量">
              <el-input v-model="form.orderVolume" placeholder="如：1万单" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="意向合作周期">
              <el-input v-model="form.coopPeriod" placeholder="如：1年" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="预算/心理价位">
              <el-input v-model="form.budgetPrice" placeholder="如：7元/㎡·月" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="意向园区/仓库">
              <el-input v-model="form.intentPark" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="客户所在地区">
              <el-input v-model="form.region" placeholder="如：广州市海珠区…" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">跟进管理</el-divider>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="客户等级">
              <el-select v-model="form.grade" placeholder="请选择" clearable style="width: 100%">
                <el-option v-for="g in GRADE_OPTIONS" :key="g" :label="g" :value="g" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="跟进负责人">
              <el-input v-model="form.ownerName" placeholder="如：丁超" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="当前状态" prop="status">
              <el-select v-model="form.status" style="width: 100%">
                <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="下次跟进计划">
              <el-date-picker v-model="form.nextFollow" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                              placeholder="选择时间" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="跟进记录速记">
              <el-input v-model="form.remark" type="textarea" :rows="2"
                        placeholder="随手记；逐次明细请用列表里的「跟进记录」" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="附件">
              <FileUpload v-model="attachFiles" biz-type="crm_lead" :biz-id="form.id" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 导入 -->
    <el-dialog v-model="importVisible" title="导入客户信息登记表" width="560px" @close="importResult = null">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 14px"
                title="按表头文字认列，不要求列顺序一致；只读取「客户信息登记表」这一页。同姓名+电话已存在的会自动跳过，重复导入不会产生副本。" />
      <el-upload drag :auto-upload="false" :limit="1" accept=".xlsx,.xls"
                 :on-change="onFileChange" :on-remove="() => (importFile = null)" :file-list="[]">
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">把 Excel 拖到这里，或<em>点击选择</em></div>
        <template #tip><div class="el-upload__tip">支持 .xlsx / .xls</div></template>
      </el-upload>
      <div v-if="importFile" class="picked">已选择：{{ importFile.name }}</div>

      <div v-if="importResult" class="import-result">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="导入成功">{{ importResult.imported }} 条</el-descriptions-item>
          <el-descriptions-item label="重复跳过">{{ importResult.skipped }} 条</el-descriptions-item>
          <el-descriptions-item label="失败">{{ importResult.errors.length }} 条</el-descriptions-item>
        </el-descriptions>
        <ul v-if="importResult.errors.length" class="err-list">
          <li v-for="(e, i) in importResult.errors" :key="i">{{ e }}</li>
        </ul>
      </div>

      <template #footer>
        <el-button @click="importVisible = false">关闭</el-button>
        <el-button type="primary" :loading="importing" :disabled="!importFile" @click="doImport">开始导入</el-button>
      </template>
    </el-dialog>

    <LeadFollowDrawer v-model="followVisible" :lead="currentLead" @saved="load" />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { leadApi } from '@/api/crm'
import { fileApi } from '@/api/file'
import FileUpload from '@/components/FileUpload.vue'
import LeadFollowDrawer from './LeadFollowDrawer.vue'
import MobileRecordList from '@/components/MobileRecordList.vue'
import { useResponsive } from '@/composables/useResponsive'
import {
  SOURCE_OPTIONS, CUSTOMER_TYPE_OPTIONS, COOP_MODE_OPTIONS, GRADE_OPTIONS, STATUS_OPTIONS,
  statusText, statusType, gradeType
} from './leadOptions'

const { isMobile } = useResponsive()

const loading = ref(false)
const list = ref([])
const total = ref(0)
const stats = ref({ total: 0, todayNew: 0, monthNew: 0, invalid: 0, convertRate: 0 })

const defaultQuery = () => ({
  pageNo: 1, pageSize: 10,
  leadNo: '', contact: '', phone: '', company: '',
  customerType: '', grade: '', status: null, ownerName: ''
})
const query = reactive(defaultQuery())

async function load() {
  loading.value = true
  try {
    const res = await leadApi.page(query)
    list.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}
async function loadStats() {
  try { stats.value = await leadApi.stats() } catch (e) { /* 统计失败不挡列表 */ }
}
function search() { query.pageNo = 1; load() }
function reset() { Object.assign(query, defaultQuery()); load() }

// 新增/编辑
const formRef = ref()
const dialogVisible = ref(false)
const saving = ref(false)
const today = () => new Date().toISOString().slice(0, 10)
const defaultForm = () => ({
  id: null, leadNo: '', registerDate: today(), source: '', contact: '', phone: '', company: '',
  customerType: '', coopMode: '', demandArea: '', goodsType: '', orderVolume: '', coopPeriod: '',
  budgetPrice: '', intentPark: '', region: '', grade: '', ownerName: '', status: 1,
  nextFollow: '', remark: ''
})
const form = reactive(defaultForm())
const attachFiles = ref([])
const rules = {
  contact: [{ required: true, message: '请填写客户姓名', trigger: 'blur' }],
  registerDate: [{ required: true, message: '请选择登记日期', trigger: 'change' }],
  status: [{ required: true, message: '请选择当前状态', trigger: 'change' }]
}

async function openDialog(row) {
  Object.assign(form, defaultForm())
  attachFiles.value = []
  if (row) {
    Object.assign(form, await leadApi.get(row.id))
    try { attachFiles.value = await fileApi.list('crm_lead', row.id) } catch (e) { /* 忽略 */ }
  }
  dialogVisible.value = true
}

async function submit() {
  await formRef.value.validate()
  saving.value = true
  try {
    let leadId = form.id
    if (form.id) {
      await leadApi.update(form)
      ElMessage.success('已保存')
    } else {
      leadId = await leadApi.add(form)
      ElMessage.success('新增成功，客户编号已自动生成')
    }
    // 先传后回填:新建时上传的附件此刻才拿到 bizId
    const pendingIds = (attachFiles.value || []).filter((f) => f && f.id && !f.bizId).map((f) => f.id)
    if (pendingIds.length > 0) {
      try {
        await fileApi.attach('crm_lead', leadId, pendingIds)
      } catch (e) {
        ElMessage.warning('线索已保存，但附件关联失败，请在编辑里重新上传')
      }
    }
    dialogVisible.value = false
    await Promise.all([load(), loadStats()])
  } finally {
    saving.value = false
  }
}

// 转客户:后端置为「已签约/已成交」,与意向客户页的既有流程衔接
async function convert(row) {
  await leadApi.convert(row.id)
  ElMessage.success('已转为客户，状态置为已签约/已成交')
  await Promise.all([load(), loadStats()])
}

async function remove(id) {
  await leadApi.remove(id)
  ElMessage.success('已删除')
  await Promise.all([load(), loadStats()])
}

// 跟进记录
const followVisible = ref(false)
const currentLead = ref(null)
function openFollow(row) {
  currentLead.value = row
  followVisible.value = true
}

// 导入
const importVisible = ref(false)
const importing = ref(false)
const importFile = ref(null)
const importResult = ref(null)
function onFileChange(f) {
  importFile.value = f.raw
  importResult.value = null
}
async function doImport() {
  importing.value = true
  try {
    const fd = new FormData()
    fd.append('file', importFile.value)
    importResult.value = await leadApi.importExcel(fd)
    ElMessage.success(`导入完成：成功 ${importResult.value.imported} 条`)
    importFile.value = null
    await Promise.all([load(), loadStats()])
  } finally {
    importing.value = false
  }
}

onMounted(() => { load(); loadStats() })
</script>

<style scoped>
.stat-row { display: flex; gap: 12px; margin-bottom: 14px; flex-wrap: wrap; }
.stat-card { flex: 1; min-width: 150px; }
.stat-label { font-size: 13px; color: #909399; }
.stat-value { font-size: 24px; font-weight: 700; color: #303133; margin-top: 4px; }
.section-title { font-size: 15px; font-weight: 600; color: #303133; }
.hint { font-size: 13px; font-weight: 400; color: #909399; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.pager { margin-top: 16px; justify-content: flex-end; }
.picked { margin-top: 10px; font-size: 13px; color: #606266; }
.import-result { margin-top: 14px; }
.err-list { margin: 10px 0 0; padding-left: 18px; color: #f56c6c; font-size: 13px; max-height: 160px; overflow: auto; }
</style>
