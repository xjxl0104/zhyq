<template>
  <div class="page-container">
    <!-- 统计卡 -->
    <div class="stat-row">
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">中介总数</div>
        <div class="stat-value">{{ stats.total }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">合作中</div>
        <div class="stat-value">{{ stats.active }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">A级核心中介</div>
        <div class="stat-value">{{ stats.gradeA }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">当月新增</div>
        <div class="stat-value">{{ stats.monthNew }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">累计推荐 / 成交</div>
        <div class="stat-value">{{ stats.referral }} / {{ stats.deal }}</div>
      </el-card>
    </div>

    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="中介名称">
          <el-input v-model="query.name" placeholder="请输入中介名称" clearable style="width: 170px" />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="query.contact" placeholder="请输入联系人" clearable style="width: 130px" />
        </el-form-item>
        <el-form-item label="中介类型">
          <el-select v-model="query.agencyType" placeholder="全部" clearable style="width: 150px">
            <el-option v-for="t in AGENCY_TYPE_OPTIONS" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="合作等级">
          <el-select v-model="query.grade" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="g in AGENCY_GRADE_OPTIONS" :key="g" :label="g" :value="g" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="query.ownerName" placeholder="对接负责人" clearable style="width: 120px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="合作中" :value="1" />
            <el-option label="暂停合作" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 表格区 -->
    <div class="table-card">
      <div class="toolbar">
        <span class="section-title">
          中介登记表
          <span class="hint">— 「最近跟进」「跟进次数」由跟进记录自动维护，不用手填</span>
        </span>
        <div>
          <el-button @click="importVisible = true"><el-icon><Upload /></el-icon>导入登记表</el-button>
          <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增中介</el-button>
        </div>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="agencyNo" label="中介编号" width="100">
          <template #default="{ row }">{{ row.agencyNo || '—' }}</template>
        </el-table-column>
        <el-table-column prop="name" label="中介名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="agencyType" label="类型" width="120" />
        <el-table-column prop="contact" label="联系人" width="100" />
        <el-table-column prop="phone" label="电话" width="130" />
        <el-table-column label="合作等级" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.grade" :type="agencyGradeType(row.grade)" size="small">{{ row.grade }}</el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="佣金比例" width="90" align="right">
          <template #default="{ row }">{{ row.commissionRate ?? 0 }}%</template>
        </el-table-column>
        <el-table-column label="协议" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.agreementSigned === 1 ? 'success' : 'info'" size="small">
              {{ row.agreementSigned === 1 ? '已签' : '未签' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="推荐/成交" width="95" align="center">
          <template #default="{ row }">{{ row.referralCount ?? 0 }} / {{ row.dealCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column prop="ownerName" label="负责人" width="90" />
        <el-table-column label="最近跟进" width="110">
          <template #default="{ row }">{{ row.lastFollowDate || '—' }}</template>
        </el-table-column>
        <el-table-column label="跟进次数" width="85" align="center">
          <template #default="{ row }">{{ row.followCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="下次跟进" width="160">
          <template #default="{ row }">{{ row.nextFollow || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="95">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '合作中' : '暂停合作' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openFollow(row)">跟进记录</el-button>
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
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
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="760px">
      <el-form :model="form" label-width="110px" ref="formRef" :rules="rules">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="中介名称" prop="name">
              <el-input v-model="form.name" placeholder="公司名或经纪人姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="中介类型" prop="agencyType">
              <el-select v-model="form.agencyType" placeholder="请选择" style="width: 100%">
                <el-option v-for="t in AGENCY_TYPE_OPTIONS" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系人"><el-input v-model="form.contact" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话"><el-input v-model="form.phone" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="微信号"><el-input v-model="form.wechat" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所在地区"><el-input v-model="form.region" placeholder="如：杭州余杭" /></el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="办公地址"><el-input v-model="form.address" /></el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="擅长业务/资源">
              <el-input v-model="form.resourceDesc" placeholder="如：电商卖家资源多，擅长 500-2000㎡ 仓库租赁" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="合作等级">
              <el-select v-model="form.grade" placeholder="请选择" clearable style="width: 100%">
                <el-option v-for="g in AGENCY_GRADE_OPTIONS" :key="g" :label="g" :value="g" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="对接负责人"><el-input v-model="form.ownerName" placeholder="园区招商人员" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="佣金比例">
              <el-input-number v-model="form.commissionRate" :min="0" :max="100" :precision="2" :step="1" />
              <span class="unit">%</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="合作协议">
              <el-radio-group v-model="form.agreementSigned">
                <el-radio :value="1">已签</el-radio>
                <el-radio :value="0">未签</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="累计推荐客户">
              <el-input-number v-model="form.referralCount" :min="0" :step="1" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="累计成交客户">
              <el-input-number v-model="form.dealCount" :min="0" :step="1" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio :value="1">合作中</el-radio>
                <el-radio :value="0">暂停合作</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 导入 -->
    <el-dialog v-model="importVisible" title="导入中介登记表" width="580px" @close="importResult = null">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 14px"
                title="按表头文字认列，不要求列顺序；表头至少要有「中介名称」或「公司名称」列。同名称+电话已存在的会自动跳过，重复导入不会产生副本。" />
      <el-upload drag :auto-upload="false" :limit="1" :accept="IMPORT_ACCEPT"
                 :on-change="onFileChange" :on-remove="() => (importFile = null)" :file-list="[]">
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">把文件拖到这里，或<em>点击选择</em></div>
        <template #tip>
          <div class="el-upload__tip">
            支持 Excel(.xlsx / .xls / WPS .et)、CSV / TXT、Word(.docx 中的表格)
            <el-button link type="primary" @click.stop="downloadTemplate">下载模板</el-button>
          </div>
        </template>
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

    <ChannelFollowDrawer v-model="followVisible" :agency="followAgency" @saved="load" />
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { channelApi } from '@/api/crm'
import ChannelFollowDrawer from './ChannelFollowDrawer.vue'
import { AGENCY_TYPE_OPTIONS, AGENCY_GRADE_OPTIONS, agencyGradeType } from './agencyOptions'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const emptyQuery = () => ({ name: '', contact: '', agencyType: '', grade: '', ownerName: '', status: null })
const query = reactive({ pageNo: 1, pageSize: 10, ...emptyQuery() })

async function load() {
  loading.value = true
  try {
    const res = await channelApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
function search() {
  query.pageNo = 1
  load()
}
function reset() {
  Object.assign(query, { pageNo: 1, ...emptyQuery() })
  load()
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const emptyForm = () => ({
  id: null, version: null, name: '', agencyType: '中介公司', contact: '', phone: '', wechat: '',
  region: '', address: '', resourceDesc: '', grade: '', ownerName: '', commissionRate: 0,
  agreementSigned: 0, referralCount: 0, dealCount: 0, status: 1, remark: ''
})
const form = reactive(emptyForm())
const rules = {
  name: [{ required: true, message: '请输入中介名称', trigger: 'blur' }],
  agencyType: [{ required: true, message: '请选择中介类型', trigger: 'change' }]
}

function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑中介' : '新增中介'
  Object.assign(form, emptyForm())
  if (row) {
    // 只取表单字段，编号与跟进统计由后端维护
    Object.keys(form).forEach((k) => { if (row[k] !== undefined && row[k] !== null) form[k] = row[k] })
  }
}
async function submit() {
  await formRef.value.validate()
  if (form.id) await channelApi.update(form)
  else await channelApi.add(form)
  ElMessage.success('保存成功')
  dialog.visible = false
  load()
  loadStats()
}
async function remove(id) {
  await channelApi.remove(id)
  ElMessage.success('删除成功')
  load()
  loadStats()
}

const followVisible = ref(false)
const followAgency = ref(null)
function openFollow(row) {
  followAgency.value = row
  followVisible.value = true
}

const stats = reactive({ total: 0, active: 0, gradeA: 0, monthNew: 0, referral: 0, deal: 0 })
async function loadStats() {
  Object.assign(stats, await channelApi.stats())
}

// 导入
const IMPORT_ACCEPT = '.xlsx,.xls,.et,.csv,.txt,.tsv,.docx'
const TEMPLATE_HEADERS = ['中介名称', '中介类型', '联系人', '联系电话', '微信号', '所在地区', '办公地址',
  '擅长业务/资源', '合作等级', '对接负责人', '佣金比例', '合作协议', '累计推荐客户', '累计成交客户', '状态', '备注']
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
    importResult.value = await channelApi.importFile(fd)
    ElMessage.success(`导入完成：成功 ${importResult.value.imported} 条`)
    importFile.value = null
    await Promise.all([load(), loadStats()])
  } finally {
    importing.value = false
  }
}
function downloadTemplate() {
  const sample = ['示例中介公司', '中介公司', '张三', '13800000000', 'zhangsan', '杭州余杭', '',
    '电商卖家资源多', 'A-核心合作', '小林', '2', '已签', '0', '0', '合作中', '']
  // 带 BOM，Excel 双击打开不乱码
  const csv = String.fromCharCode(0xfeff) +[TEMPLATE_HEADERS, sample].map((r) => r.join(',')).join('\r\n')
  const a = document.createElement('a')
  a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
  a.download = '中介登记表模板.csv'
  a.click()
  URL.revokeObjectURL(a.href)
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
.picked { margin-top: 10px; font-size: 13px; color: #606266; }
.import-result { margin-top: 14px; }
.err-list { margin: 10px 0 0; padding-left: 18px; color: #f56c6c; font-size: 13px; max-height: 160px; overflow: auto; }
.unit { margin-left: 8px; color: #909399; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
