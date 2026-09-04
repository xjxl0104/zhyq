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
        <div class="stat-label">无效数</div>
        <div class="stat-value">{{ stats.invalid }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">月度转化率</div>
        <div class="stat-value">{{ stats.convertRate }}%</div>
      </el-card>
    </div>

    <!-- Tab 切换 -->
    <el-tabs v-model="activeTab" @tab-change="onTabChange" class="lead-tabs">
      <el-tab-pane label="我的线索" name="mine" />
      <el-tab-pane label="全部线索" name="all" />
      <el-tab-pane label="线索公海" name="pool" />
      <el-tab-pane label="审核中" name="review" />
    </el-tabs>

    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="联系人">
          <el-input v-model="query.contact" placeholder="请输入联系人" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="query.phone" placeholder="请输入电话" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="公司">
          <el-input v-model="query.company" placeholder="请输入公司" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 表格区 -->
    <div class="table-card">
      <div class="toolbar">
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增线索</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column prop="contact" label="联系人" min-width="100" />
        <el-table-column prop="phone" label="电话" min-width="130" />
        <el-table-column prop="company" label="公司" min-width="160" />
        <el-table-column prop="source" label="来源" width="100" />
        <el-table-column prop="demandArea" label="需求面积" width="110" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="nextFollow" label="下次跟进" width="170" />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button link type="primary" @click="openFollow(row)">跟进</el-button>
            <el-button link type="success" @click="convert(row)">转客户</el-button>
            <el-popconfirm title="确认删除?" @confirm="remove(row.id)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                     :total="total" v-model:current-page="query.pageNo"
                     v-model:page-size="query.pageSize" :page-sizes="[10,20,50]" @change="load" />
    </div>

    <!-- 表单弹窗 -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="560px">
      <el-form :model="form" label-width="90px" ref="formRef" :rules="rules">
        <el-form-item label="联系人" prop="contact"><el-input v-model="form.contact" /></el-form-item>
        <el-form-item label="电话" prop="phone"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="公司"><el-input v-model="form.company" /></el-form-item>
        <el-form-item label="来源">
          <el-select v-model="form.source" placeholder="请选择" clearable style="width: 100%">
            <el-option label="网页" value="网页" />
            <el-option label="小程序" value="小程序" />
            <el-option label="活动" value="活动" />
            <el-option label="渠道" value="渠道" />
            <el-option label="电话" value="电话" />
            <el-option label="人工" value="人工" />
          </el-select>
        </el-form-item>
        <el-form-item label="需求面积"><el-input v-model="form.demandArea" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" placeholder="请选择" style="width: 100%">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="下次跟进">
          <el-date-picker v-model="form.nextFollow" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                          placeholder="选择时间" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="附件">
          <FileUpload v-model="attachFiles" biz-type="crm_lead" :biz-id="form.id" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 跟进弹窗 -->
    <el-dialog v-model="followDialog.visible" title="跟进记录" width="600px">
      <div class="follow-header">
        <span>联系人:{{ followDialog.lead.contact }}</span>
        <span>公司:{{ followDialog.lead.company }}</span>
      </div>
      <el-timeline v-if="followList.length" class="follow-timeline">
        <el-timeline-item v-for="f in followList" :key="f.id" :timestamp="f.createTime" placement="top">
          <div class="follow-item">
            <el-tag size="small" v-if="f.type">{{ f.type }}</el-tag>
            <span class="follow-content">{{ f.content }}</span>
            <div class="follow-meta" v-if="f.nextFollow">下次跟进:{{ f.nextFollow }}</div>
          </div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无跟进记录" :image-size="80" />

      <el-divider>新增跟进</el-divider>
      <el-form :model="followForm" label-width="90px" ref="followFormRef" :rules="followRules">
        <el-form-item label="方式" prop="type">
          <el-select v-model="followForm.type" placeholder="请选择" style="width: 100%">
            <el-option label="电话" value="电话" />
            <el-option label="拜访" value="拜访" />
            <el-option label="微信" value="微信" />
            <el-option label="邮件" value="邮件" />
            <el-option label="会议" value="会议" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input v-model="followForm.content" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="下次跟进">
          <el-date-picker v-model="followForm.nextFollow" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                          placeholder="选择时间" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="followDialog.visible = false">关闭</el-button>
        <el-button type="primary" @click="submitFollow">提交跟进</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { leadApi, followApi, customerApi } from '@/api/crm'
import { fileApi } from '@/api/file'
import FileUpload from '@/components/FileUpload.vue'

const statusOptions = [
  { value: 1, label: '新建', type: 'warning' },
  { value: 2, label: '待分配', type: 'warning' },
  { value: 3, label: '跟进中', type: 'primary' },
  { value: 4, label: '意向', type: 'primary' },
  { value: 5, label: '已转化', type: 'success' },
  { value: 6, label: '无效', type: 'danger' },
  { value: 7, label: '公海', type: 'info' },
  { value: 8, label: '审核中', type: 'warning' }
]
function statusText(v) {
  const s = statusOptions.find(o => o.value === v)
  return s ? s.label : '-'
}
function statusType(v) {
  const s = statusOptions.find(o => o.value === v)
  return s ? s.type : 'info'
}

// tab -> status 过滤
const tabStatus = { mine: null, all: null, pool: 7, review: 8 }
const activeTab = ref('mine')

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, contact: '', phone: '', company: '', status: null })

const stats = reactive({ total: 0, todayNew: 0, monthNew: 0, invalid: 0, convertRate: 0 })

async function loadStats() {
  const res = await leadApi.stats()
  Object.assign(stats, res)
}

async function load() {
  loading.value = true
  try {
    const res = await leadApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
function onTabChange(name) {
  query.pageNo = 1
  query.status = tabStatus[name]
  load()
}
function reset() {
  Object.assign(query, { pageNo: 1, contact: '', phone: '', company: '', status: tabStatus[activeTab.value] })
  load()
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const emptyForm = () => ({ id: null, contact: '', phone: '', company: '', source: '', demandArea: '', status: 1, nextFollow: null, remark: '' })
const form = reactive(emptyForm())
const attachFiles = ref([])
const rules = {
  contact: [{ required: true, message: '请输入联系人', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入电话', trigger: 'blur' }]
}

async function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑线索' : '新增线索'
  attachFiles.value = []
  if (row) {
    Object.assign(form, row)
    try { attachFiles.value = await fileApi.list('crm_lead', row.id) } catch (e) { /* 忽略 */ }
  } else {
    Object.assign(form, emptyForm())
  }
}
async function submit() {
  await formRef.value.validate()
  let leadId = form.id
  if (form.id) await leadApi.update(form)
  else leadId = await leadApi.add(form)
  const pendingIds = (attachFiles.value || []).filter(f => f && f.id && !f.bizId).map(f => f.id)
  if (leadId && pendingIds.length) {
    try { await fileApi.attach('crm_lead', leadId, pendingIds) } catch (e) { /* 忽略,不阻断保存 */ }
  }
  ElMessage.success('保存成功')
  dialog.visible = false
  load()
  loadStats()
}
async function remove(id) {
  await leadApi.remove(id)
  ElMessage.success('删除成功')
  load()
  loadStats()
}
async function convert(row) {
  await customerApi.fromLead(row.id)
  ElMessage.success('已转为意向客户')
  load()
  loadStats()
}

// 跟进
const followFormRef = ref()
const followDialog = reactive({ visible: false, lead: {} })
const followList = ref([])
const followForm = reactive({ leadId: null, type: '', content: '', nextFollow: null })
const followRules = {
  type: [{ required: true, message: '请选择跟进方式', trigger: 'change' }],
  content: [{ required: true, message: '请输入跟进内容', trigger: 'blur' }]
}

async function openFollow(row) {
  followDialog.visible = true
  followDialog.lead = row
  Object.assign(followForm, { leadId: row.id, type: '', content: '', nextFollow: null })
  await loadFollows(row.id)
}
async function loadFollows(leadId) {
  followList.value = await followApi.list(leadId)
}
async function submitFollow() {
  await followFormRef.value.validate()
  await followApi.add(followForm)
  ElMessage.success('跟进已提交')
  Object.assign(followForm, { type: '', content: '', nextFollow: null })
  loadFollows(followDialog.lead.id)
}

onMounted(() => {
  query.status = tabStatus[activeTab.value]
  load()
  loadStats()
})
</script>

<style scoped>
.stat-row { display: flex; gap: 16px; margin-bottom: 16px; }
.stat-card { flex: 1; }
.stat-label { color: var(--text-secondary); font-size: 13px; }
.stat-value { font-size: 26px; font-weight: 600; margin-top: 8px; }
.lead-tabs { margin-bottom: 8px; }
.follow-header { display: flex; gap: 24px; color: #606266; margin-bottom: 12px; }
.follow-timeline { max-height: 260px; overflow-y: auto; }
.follow-content { margin-left: 8px; }
.follow-meta { color: var(--text-secondary); font-size: 12px; margin-top: 4px; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
