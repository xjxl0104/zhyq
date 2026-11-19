<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { archiveApi, archiveSettingApi, SECRET_OPTIONS, secretOf } from '@/api/archive'

const tab = ref('category')
const docTypes = ref([])

// ---------- 分类 ----------
const cats = ref([])
const catLoading = ref(false)
const catTree = computed(() => {
  const tops = cats.value.filter((c) => !c.parentCode)
  return tops.map((t) => ({ ...t, children: cats.value.filter((c) => c.parentCode === t.code) }))
})
const subOptions = computed(() => cats.value.filter((c) => c.parentCode && c.enabled === 1))
async function loadCats() {
  catLoading.value = true
  try { cats.value = await archiveSettingApi.categories() } finally { catLoading.value = false }
}
const catDialog = reactive({ visible: false, isNew: true })
const catForm = reactive({ id: null, code: '', name: '', sort: 0, defaultSecret: 2, enabled: 1, remark: '' })
function openCat(row, parentCode) {
  catDialog.isNew = !row
  Object.assign(catForm, row ? { ...row } : { id: null, code: parentCode ? `${parentCode}.` : '', name: '', sort: 0, defaultSecret: 2, enabled: 1, remark: '' })
  catDialog.visible = true
}
async function saveCat() {
  if (catDialog.isNew) await archiveSettingApi.addCategory({ ...catForm })
  else await archiveSettingApi.updateCategory({ ...catForm })
  ElMessage.success('已保存')
  catDialog.visible = false
  loadCats()
}
async function removeCat(row) {
  await archiveSettingApi.removeCategory(row.id)
  ElMessage.success('已删除')
  loadCats()
}
async function toggleCat(row) {
  await archiveSettingApi.updateCategory({ ...row, enabled: row.enabled === 1 ? 0 : 1 })
  loadCats()
}

// ---------- 规则 ----------
const rules = ref([])
const ruleTotal = ref(0)
const ruleLoading = ref(false)
const rq = reactive({ pageNo: 1, pageSize: 20, keyword: '', categoryCode: '' })
async function loadRules() {
  ruleLoading.value = true
  try {
    const res = await archiveSettingApi.rulePage({ ...rq, categoryCode: rq.categoryCode || undefined })
    rules.value = res.records
    ruleTotal.value = res.total
  } finally { ruleLoading.value = false }
}
function nameOf(code) {
  const c = cats.value.find((x) => x.code === code)
  return c ? `${c.code} ${c.name}` : code || '-'
}
const ruleDialog = reactive({ visible: false, isNew: true })
const ruleForm = reactive({ id: null, keyword: '', scope: 1, categoryCode: null, docType: null, secretLevel: null, weight: 8, enabled: 1, remark: '' })
function openRule(row) {
  ruleDialog.isNew = !row
  Object.assign(ruleForm, row ? { ...row } : { id: null, keyword: '', scope: 1, categoryCode: rq.categoryCode || null, docType: null, secretLevel: null, weight: 8, enabled: 1, remark: '' })
  ruleDialog.visible = true
}
async function saveRule() {
  if (ruleDialog.isNew) await archiveSettingApi.addRule({ ...ruleForm })
  else await archiveSettingApi.updateRule({ ...ruleForm })
  ElMessage.success('已保存')
  ruleDialog.visible = false
  loadRules()
}
async function removeRule(row) {
  await archiveSettingApi.removeRule(row.id)
  ElMessage.success('已删除')
  loadRules()
}
async function toggleRule(row) {
  await archiveSettingApi.updateRule({ ...row, enabled: row.enabled === 1 ? 0 : 1 })
  loadRules()
}

// ---------- 归类试验 ----------
const trial = reactive({ fileName: '', sourcePath: '', text: '' })
const trialResult = ref(null)
async function runTrial() {
  trialResult.value = await archiveSettingApi.test({ ...trial })
}

onMounted(async () => {
  docTypes.value = (await archiveApi.meta()).docTypes || []
  await loadCats()
  loadRules()
})
</script>

<template>
  <div class="page-container">
    <el-tabs v-model="tab" class="table-card">
      <el-tab-pane label="分类架构" name="category">
        <div class="toolbar">
          <el-button type="primary" @click="openCat(null, '')"><el-icon><Plus /></el-icon>新增门类</el-button>
          <span class="hint">两层:门类(01)+子类(01.01)。编码一旦建立不能修改;不用的分类请「停用」而不是删除。</span>
        </div>
        <el-table :data="catTree" v-loading="catLoading" row-key="id" border default-expand-all>
          <el-table-column prop="code" label="编码" width="120" />
          <el-table-column prop="name" label="名称" min-width="220" />
          <el-table-column label="默认密级" width="100">
            <template #default="{ row }"><el-tag :type="secretOf(row.defaultSecret).type" size="small">{{ secretOf(row.defaultSecret).label }}</el-tag></template>
          </el-table-column>
          <el-table-column label="状态" width="80">
            <template #default="{ row }"><el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">{{ row.enabled === 1 ? '启用' : '停用' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" width="260">
            <template #default="{ row }">
              <el-button v-if="!row.parentCode && row.code !== '99'" link type="primary" @click="openCat(null, row.code)">加子类</el-button>
              <el-button link type="primary" @click="openCat(row)">编辑</el-button>
              <el-button link type="warning" @click="toggleCat(row)">{{ row.enabled === 1 ? '停用' : '启用' }}</el-button>
              <el-popconfirm title="确认删除?" @confirm="removeCat(row)">
                <template #reference><el-button link type="danger">删除</el-button></template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="自动归类规则" name="rule">
        <el-form :inline="true" :model="rq" @submit.prevent="loadRules">
          <el-form-item label="关键词"><el-input v-model="rq.keyword" clearable style="width: 180px" @keyup.enter="loadRules" /></el-form-item>
          <el-form-item label="分类">
            <el-select v-model="rq.categoryCode" clearable filterable placeholder="全部" style="width: 220px" @change="() => { rq.pageNo = 1; loadRules() }">
              <el-option v-for="c in subOptions" :key="c.code" :label="`${c.code} ${c.name}`" :value="c.code" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="() => { rq.pageNo = 1; loadRules() }"><el-icon><Search /></el-icon>查询</el-button>
            <el-button type="primary" plain @click="openRule()"><el-icon><Plus /></el-icon>新增规则</el-button>
          </el-form-item>
        </el-form>
        <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px"
          title="规则 = 关键词 + 倾向的分类/类型/密级 + 权重。文件名里出现关键词得分最高(×3),所在文件夹次之,正文最弱;各规则得分累加,最高的分类胜出。没有足够把握时进「待归类」。" />
        <el-table :data="rules" v-loading="ruleLoading" border stripe>
          <el-table-column prop="keyword" label="关键词" width="160" />
          <el-table-column label="匹配范围" width="110"><template #default="{ row }">{{ row.scope === 2 ? '文件正文' : '文件名/文件夹' }}</template></el-table-column>
          <el-table-column label="倾向分类" min-width="200"><template #default="{ row }">{{ row.categoryCode ? nameOf(row.categoryCode) : '-' }}</template></el-table-column>
          <el-table-column prop="docType" label="倾向类型" width="100"><template #default="{ row }">{{ row.docType || '-' }}</template></el-table-column>
          <el-table-column label="密级" width="80"><template #default="{ row }">{{ row.secretLevel ? secretOf(row.secretLevel).label : '-' }}</template></el-table-column>
          <el-table-column prop="weight" label="权重" width="70" />
          <el-table-column label="状态" width="80"><template #default="{ row }"><el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">{{ row.enabled === 1 ? '启用' : '停用' }}</el-tag></template></el-table-column>
          <el-table-column label="操作" width="190">
            <template #default="{ row }">
              <el-button link type="primary" @click="openRule(row)">编辑</el-button>
              <el-button link type="warning" @click="toggleRule(row)">{{ row.enabled === 1 ? '停用' : '启用' }}</el-button>
              <el-popconfirm title="确认删除?" @confirm="removeRule(row)">
                <template #reference><el-button link type="danger">删除</el-button></template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination v-model:current-page="rq.pageNo" v-model:page-size="rq.pageSize" class="pager" background
          layout="total, sizes, prev, pager, next" :total="ruleTotal" :page-sizes="[20, 50, 100]" @change="loadRules" />
      </el-tab-pane>

      <el-tab-pane label="归类试验" name="trial">
        <el-form label-width="110px" style="max-width: 720px">
          <el-form-item label="文件名"><el-input v-model="trial.fileName" placeholder="如:公众责任险保单-昌记-正式保单.pdf" /></el-form-item>
          <el-form-item label="所在文件夹"><el-input v-model="trial.sourcePath" placeholder="可不填,如:10、物业相关合同/3、园区保险合同" /></el-form-item>
          <el-form-item label="正文片段"><el-input v-model="trial.text" type="textarea" :rows="3" placeholder="可不填" /></el-form-item>
          <el-form-item><el-button type="primary" :disabled="!trial.fileName" @click="runTrial">试一试</el-button></el-form-item>
        </el-form>
        <el-descriptions v-if="trialResult" :column="2" border style="max-width: 720px">
          <el-descriptions-item label="规范化名称" :span="2">{{ trialResult.name }}</el-descriptions-item>
          <el-descriptions-item label="分类">{{ nameOf(trialResult.categoryCode) }}</el-descriptions-item>
          <el-descriptions-item label="把握度">{{ trialResult.confidence }}%</el-descriptions-item>
          <el-descriptions-item label="类型">{{ trialResult.docType }}</el-descriptions-item>
          <el-descriptions-item label="密级">{{ secretOf(trialResult.secretLevel).label }}</el-descriptions-item>
          <el-descriptions-item label="期间">{{ trialResult.period || '-' }}</el-descriptions-item>
          <el-descriptions-item label="版本">{{ trialResult.versionLabel || '-' }}</el-descriptions-item>
          <el-descriptions-item label="依据" :span="2">{{ (trialResult.reasons || []).join(';') || '-' }}</el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>

      <el-tab-pane label="命名与编号规则" name="doc">
        <div class="doc-text">
          <h4>规范化名称:主题-期间-版本</h4>
          <p>例:物业服务合同-五矿-2026-盖章版 · 缴费通知单-中印物流-2026年10月 · 消防隐患检查表-3楼-2026年9月。对象、期间、版本识别不到就省略,系统不会编造。</p>
          <p>自动清理:文件名开头的「1、」「01-」、结尾的「(1)」「副本」、微信图片/哈希等无意义原名(这类改用所在文件夹名)。</p>
          <h4>档号:子类编码-年度-流水号</h4>
          <p>例:03.04-2026-0001,表示 2026 年归档的第 1 份「保险合同与保单」。年度取文件日期的年份,没有则取归档当年;档号一经生成不再变化,删除后不重复使用。</p>
          <h4>密级</h4>
          <p>公开=可对外;内部=员工可看(默认);机密=身份证、车牌人脸、合同价款、财务账务等,建议仅指定人员接触。当前版本做标注与筛选,不改变账号权限。</p>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="catDialog.visible" :title="catDialog.isNew ? '新增分类' : '编辑分类'" width="480px">
      <el-form :model="catForm" label-width="90px">
        <el-form-item label="编码"><el-input v-model="catForm.code" :disabled="!catDialog.isNew" placeholder="门类 03,子类 03.07" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="catForm.name" maxlength="64" /></el-form-item>
        <el-form-item label="默认密级">
          <el-select v-model="catForm.defaultSecret"><el-option v-for="s in SECRET_OPTIONS" :key="s.value" :label="s.label" :value="s.value" /></el-select>
        </el-form-item>
        <el-form-item label="排序"><el-input-number v-model="catForm.sort" :min="0" :max="9999" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="catForm.remark" maxlength="255" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="catDialog.visible = false">取消</el-button><el-button type="primary" @click="saveCat">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="ruleDialog.visible" :title="ruleDialog.isNew ? '新增规则' : '编辑规则'" width="520px">
      <el-form :model="ruleForm" label-width="100px">
        <el-form-item label="关键词"><el-input v-model="ruleForm.keyword" maxlength="64" placeholder="如:保单" /></el-form-item>
        <el-form-item label="匹配范围">
          <el-radio-group v-model="ruleForm.scope"><el-radio :value="1">文件名/文件夹</el-radio><el-radio :value="2">文件正文</el-radio></el-radio-group>
        </el-form-item>
        <el-form-item label="倾向分类">
          <el-select v-model="ruleForm.categoryCode" clearable filterable placeholder="不指定" style="width: 100%">
            <el-option v-for="c in subOptions" :key="c.code" :label="`${c.code} ${c.name}`" :value="c.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="倾向类型">
          <el-select v-model="ruleForm.docType" clearable placeholder="不指定" style="width: 100%"><el-option v-for="t in docTypes" :key="t" :label="t" :value="t" /></el-select>
        </el-form-item>
        <el-form-item label="密级">
          <el-select v-model="ruleForm.secretLevel" clearable placeholder="不指定" style="width: 100%"><el-option v-for="s in SECRET_OPTIONS" :key="s.value" :label="s.label" :value="s.value" /></el-select>
        </el-form-item>
        <el-form-item label="权重"><el-input-number v-model="ruleForm.weight" :min="1" :max="20" /><span class="hint">1~20,越大越有把握</span></el-form-item>
      </el-form>
      <template #footer><el-button @click="ruleDialog.visible = false">取消</el-button><el-button type="primary" @click="saveRule">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.hint { margin-left: 12px; color: var(--el-text-color-secondary, #909399); font-size: 13px; }
.pager { margin-top: 16px; justify-content: flex-end; }
.doc-text { max-width: 760px; line-height: 1.8; }
.doc-text h4 { margin: 14px 0 4px; }
.doc-text p { margin: 0; color: var(--el-text-color-regular, #606266); }
</style>
