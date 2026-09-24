<template>
  <div class="page-container">
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="名称/手机"><el-input v-model="query.keyword" clearable style="width: 180px" /></el-form-item>
        <el-form-item label="评级">
          <el-select v-model="query.grade" placeholder="全部" clearable style="width: 100px">
            <el-option v-for="g in ['A','B','C','D']" :key="g" :label="g" :value="g" />
          </el-select>
        </el-form-item>
        <el-form-item label="仅有推荐人">
          <el-switch v-model="query.referredOnly" :active-value="1" :inactive-value="null" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="table-card">
      <div class="toolbar">
        <span class="hint">管理推荐归属、云仓承接和伙伴可见进度。客户资料可在「招商 › 意向客户」补充。</span>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="name" label="客户 / 联系方式" min-width="180">
          <template #default="{ row }"><div>{{ row.name }}</div><div class="project-note">{{ row.contact || '联系人未填写' }} · {{ row.phone || '电话未填写' }}</div><div class="project-note">{{ projectLabel(row.projectId) }}</div></template>
        </el-table-column>
        <el-table-column label="推荐伙伴" width="100">
          <template #default="{ row }">{{ row.referrerName || (row.referrerId ? `#${row.referrerId}` : '—') }}</template>
        </el-table-column>
        <el-table-column label="评级" width="65" align="center">
          <template #default="{ row }"><el-tag v-if="row.grade" :type="gradeType(row.grade)">{{ row.grade }}</el-tag><span v-else>—</span></template>
        </el-table-column>
        <el-table-column label="客户状态" width="100">
          <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ STATUS[row.status] || row.status }}</el-tag><div class="project-note">{{ BIZ[row.bizLine] || '业务待确认' }} · {{ SIGN[row.signMode] || '默认签约' }}</div></template>
        </el-table-column>
        <el-table-column label="归属锁定" width="100">
          <template #default="{ row }"><el-tag v-if="row.lockStatus" :type="lockType(row.lockStatus)" size="small">{{ LOCK[row.lockStatus] }}</el-tag><span v-else class="muted">未锁定</span><div v-if="[1, 2].includes(row.lockStatus) && row.lockDaysLeft != null" class="project-note">剩余 {{ row.lockDaysLeft }} 天</div></template>
        </el-table-column>
        <el-table-column label="云仓承接" min-width="150">
          <template #default="{ row }"><div>{{ row.assignedWarehouseName || '尚未分派' }}</div><div class="project-note">{{ ASSIGN[row.warehouseAssignmentStatus || 0] }}</div><div v-if="row.intendedWarehouseName" class="project-note">意向：{{ row.intendedWarehouseName }}</div></template>
        </el-table-column>
        <el-table-column prop="publicProgress" label="伙伴可见进度" min-width="155" show-overflow-tooltip />
        <el-table-column label="操作" width="245" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openAssignment(row)">分派云仓</el-button>
            <el-button link type="primary" @click="openProgress(row)">更新进度</el-button>
            <el-button link type="primary" @click="openLock(row)">锁定</el-button>
            <el-dropdown trigger="click" @command="handleMore($event, row)">
              <el-button link type="primary" class="more-action">更多</el-button>
              <template #dropdown><el-dropdown-menu><el-dropdown-item command="grade">客户评级</el-dropdown-item><el-dropdown-item command="referrer">设置推荐人</el-dropdown-item><el-dropdown-item command="sign">签约方式</el-dropdown-item><el-dropdown-item v-if="canEdit && row.status === 1" command="lose" divided>标记流失</el-dropdown-item><el-dropdown-item v-if="canEdit && row.status === 3" command="restore" divided>恢复跟进</el-dropdown-item></el-dropdown-menu></template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                     :total="total" v-model:current-page="query.pageNo"
                     v-model:page-size="query.pageSize" :page-sizes="[10,20,50]" @change="load" />
    </div>

    <el-dialog v-model="assignment.visible" title="分派承接云仓" width="500px">
      <el-alert type="info" :closable="false" title="分派后商家需要确认承接。已有生效合同的客户不能更换承接仓。" />
      <el-form label-width="90px" style="margin-top:20px">
        <el-form-item label="承接云仓"><el-select v-model="assignment.warehouseId" filterable placeholder="选择已上线云仓" style="width:100%"><el-option v-for="w in warehouses" :key="w.id" :value="w.id" :label="w.name" /></el-select></el-form-item>
        <el-form-item label="分派原因"><el-input v-model="assignment.reason" type="textarea" maxlength="500" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="assignment.visible = false">取消</el-button><el-button type="primary" :loading="assignment.busy" @click="assignWarehouse">确认分派</el-button></template>
    </el-dialog>
    <el-dialog v-model="progress.visible" title="更新伙伴可见进度" width="500px">
      <el-alert type="info" :closable="false" title="此内容会展示给推荐伙伴，请填写可对外说明的进度。" />
      <el-input v-model="progress.summary" type="textarea" :rows="4" maxlength="500" show-word-limit style="margin-top:20px" />
      <template #footer><el-button @click="progress.visible = false">取消</el-button><el-button type="primary" :loading="progress.busy" @click="saveProgress">发布进度</el-button></template>
    </el-dialog>
    <!-- 评级 -->
    <el-dialog v-model="grade.visible" title="客户评级" width="460px">
      <el-alert v-if="grade.suggest" type="info" :closable="false" class="mb">
        系统建议:<b>{{ grade.suggest.grade }}</b>({{ grade.suggest.basis }})
      </el-alert>
      <el-form label-width="80px">
        <el-form-item label="评级">
          <el-radio-group v-model="grade.value"><el-radio v-for="g in ['A','B','C','D']" :key="g" :value="g">{{ g }}</el-radio></el-radio-group>
        </el-form-item>
        <el-form-item label="依据" required><el-input v-model="grade.reason" type="textarea" placeholder="专员确认依据,写入审计" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="grade.visible = false">取消</el-button><el-button type="primary" @click="submitGrade">确定</el-button></template>
    </el-dialog>

    <!-- 推荐人 -->
    <el-dialog v-model="referrer.visible" title="设置推荐伙伴" width="460px">
      <el-form label-width="90px">
        <el-form-item label="伙伴邀请码" required><el-input v-model="referrer.inviteCode" maxlength="8" placeholder="8 位邀请码" /></el-form-item>
        <el-form-item label="原因"><el-input v-model="referrer.reason" type="textarea" placeholder="补归因原因(可选)" /></el-form-item>
      </el-form>
      <p class="hint">归因规则:先到先得;已有推荐人时改动会写审计。</p>
      <template #footer><el-button @click="referrer.visible = false">取消</el-button><el-button type="primary" @click="submitReferrer">确定</el-button></template>
    </el-dialog>

    <!-- 签约方式 -->
    <el-dialog v-model="sign.visible" title="签约方式" width="420px">
      <el-radio-group v-model="sign.value">
        <el-radio :value="1">园区签(客户 ↔ 园区,园区付云仓)</el-radio>
        <el-radio :value="2">云仓直签(客户 ↔ 云仓,云仓付园区平台费)</el-radio>
      </el-radio-group>
      <p class="hint">客户已有生效合同时不允许更改;新合同可另选。</p>
      <template #footer><el-button @click="sign.visible = false">取消</el-button><el-button type="primary" @click="submitSign">确定</el-button></template>
    </el-dialog>

    <!-- 锁定 -->
    <el-drawer v-model="lock.visible" :title="`锁定 · ${lock.row?.name || ''}`" size="520px">
      <template v-if="lock.data">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="状态">{{ LOCK[lock.data.status] }}</el-descriptions-item>
          <el-descriptions-item label="伙伴">#{{ lock.data.promoterId }}</el-descriptions-item>
          <el-descriptions-item label="预锁截止">{{ lock.data.prelockUntil || '-' }}</el-descriptions-item>
          <el-descriptions-item label="锁定截止">{{ lock.data.lockUntil || '-' }}</el-descriptions-item>
          <el-descriptions-item label="已延期">{{ lock.data.extendedCount || 0 }} 次</el-descriptions-item>
        </el-descriptions>
        <div class="lock-actions">
          <el-button v-if="lock.data.status === 1" type="success" @click="lockAct('confirmLock', '确认锁定')">专员确认 → 有效锁定</el-button>
          <el-button v-if="lock.data.status === 2" @click="lockReason('extendLock', '延期 +90 天')">延期</el-button>
          <el-button v-if="[1, 2].includes(lock.data.status)" type="warning" @click="lockReason('releaseLock', '释放到公海')">释放</el-button>
          <el-button v-if="[1, 2].includes(lock.data.status)" @click="lockTransfer">转移给其他伙伴</el-button>
        </div>
      </template>
      <template v-if="!lock.data || lock.data.status === 4">
        <el-empty :description="lock.data ? '该客户已释放，可以重新报备' : '当前没有有效锁定（公海）'">
          <el-form :inline="true">
            <el-form-item label="伙伴 ID"><el-input-number v-model="lock.newPromoterId" :min="1" /></el-form-item>
            <el-button type="primary" @click="lockPrelock">代伙伴报备(预锁 7 天)</el-button>
          </el-form>
        </el-empty>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { hasPermission } from '@/utils/permission'
const canEdit=hasPermission('crm:marketing:customer:edit')

import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { mktCustomerApi, mktWarehouseApi } from '@/api/marketing'
import { useProjectStore } from '@/stores/project'

const projectStore = useProjectStore()
const projectLabel = id => id == null ? '待分配园区' : (projectStore.projects.find(p => String(p.id) === String(id))?.name || `园区 #${id}`)
const ASSIGN = {0:'待分派',1:'待商家确认',2:'已承接',3:'已拒绝'}
const warehouses = ref([])
const assignment = reactive({visible:false,id:null,warehouseId:null,reason:'',busy:false})
const progress = reactive({visible:false,id:null,summary:'',busy:false})
async function openAssignment(row) {
  warehouses.value = await mktWarehouseApi.online()
  Object.assign(assignment,{visible:true,id:row.id,warehouseId:row.assignedWarehouseId || row.intendedWarehouseId,reason:'',busy:false})
}
async function assignWarehouse() {
  if (!assignment.warehouseId || !assignment.reason.trim()) return ElMessage.error('请选择云仓并填写分派原因')
  assignment.busy = true
  try { await mktCustomerApi.assignWarehouse(assignment.id,{warehouseId:assignment.warehouseId,reason:assignment.reason}); assignment.visible=false; ElMessage.success('已分派，等待商家确认'); await load() }
  finally { assignment.busy=false }
}
function openProgress(row) { Object.assign(progress,{visible:true,id:row.id,summary:row.publicProgress || '',busy:false}) }
async function saveProgress() {
  if (!progress.summary.trim()) return ElMessage.error('请填写进度')
  progress.busy=true
  try { await mktCustomerApi.progress(progress.id,{summary:progress.summary}); progress.visible=false; ElMessage.success('已发布'); await load() }
  finally { progress.busy=false }
}
const BIZ = { 1: '租赁', 2: '云仓' }
const SIGN = { 1: '园区签', 2: '云仓直签' }
const LOCK = { 1: '预锁', 2: '有效锁定', 3: '已成交', 4: '已释放' }
const STATUS = { 1: '跟进中', 2: '已签约', 3: '已流失' }
const statusType = (v) => ({ 1: 'primary', 2: 'success', 3: 'info' }[v] || 'info')
const gradeType = (g) => ({ A: 'danger', B: 'warning', C: '', D: 'info' }[g] || 'info')
const lockType = (s) => ({ 1: 'info', 2: 'success', 3: '' }[s] || 'info')

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, keyword: '', grade: null, referredOnly: null })

async function load() {
  loading.value = true
  try {
    const res = await mktCustomerApi.page(query)
    list.value = res.records; total.value = res.total
  } finally { loading.value = false }
}
function search() { query.pageNo = 1; load() }
function reset() { Object.assign(query, { pageNo: 1, keyword: '', grade: null, referredOnly: null }); load() }

function handleMore(action, row) {
  if (action === 'grade') return openGrade(row)
  if (action === 'referrer') return openReferrer(row)
  if (action === 'sign') return openSignMode(row)
  if (action === 'lose' || action === 'restore') return changeCustomerStatus(row, action)
}
async function changeCustomerStatus(row, action) {
  const title = action === 'lose' ? '标记流失' : '恢复跟进'
  try {
    const { value } = await ElMessageBox.prompt('请填写原因，将保存到操作记录', title, { inputPattern: /\S+/, inputErrorMessage: '请填写原因' })
    await mktCustomerApi[action](row.id, { reason: value.trim() })
    ElMessage.success(action === 'lose' ? '已标记流失' : '已恢复跟进，请按需重新分派云仓')
    await load()
  } catch (error) { if (error !== 'cancel' && error !== 'close') throw error }
}

// 评级
const grade = reactive({ visible: false, row: null, value: 'D', reason: '', suggest: null })
async function openGrade(row) {
  Object.assign(grade, { visible: true, row, value: row.grade || 'D', reason: '', suggest: null })
  try { grade.suggest = await mktCustomerApi.suggestGrade(row.id) } catch (e) { /* 无建议也能评 */ }
}
async function submitGrade() {
  if (!grade.reason.trim()) return ElMessage.error('请填写依据')
  await mktCustomerApi.grade(grade.row.id, { grade: grade.value, reason: grade.reason })
  ElMessage.success('已评级'); grade.visible = false; load()
}

// 推荐人
const referrer = reactive({ visible: false, row: null, inviteCode: '', reason: '' })
function openReferrer(row) { Object.assign(referrer, { visible: true, row, inviteCode: '', reason: '' }) }
async function submitReferrer() {
  if (!referrer.inviteCode.trim()) return ElMessage.error('请输入邀请码')
  await mktCustomerApi.referrer(referrer.row.id, { inviteCode: referrer.inviteCode.trim(), reason: referrer.reason })
  ElMessage.success('已设置'); referrer.visible = false; load()
}

// 签约方式
const sign = reactive({ visible: false, row: null, value: 1 })
function openSignMode(row) { Object.assign(sign, { visible: true, row, value: row.signMode || 1 }) }
async function submitSign() {
  await mktCustomerApi.signMode(sign.row.id, { signMode: sign.value })
  ElMessage.success('已设置'); sign.visible = false; load()
}

// 锁定
const lock = reactive({ visible: false, row: null, data: null, newPromoterId: null })
async function openLock(row) {
  lock.row = row; lock.newPromoterId = row.referrerId || null
  lock.data = await mktCustomerApi.lock(row.id)
  lock.visible = true
}
async function lockAct(fn, okMsg) { await mktCustomerApi[fn](lock.data.id); ElMessage.success(okMsg); await openLock(lock.row); load() }
async function lockReason(fn, title) {
  const { value } = await ElMessageBox.prompt('请填写原因', title, { inputPattern: /\S+/, inputErrorMessage: '原因必填' })
  await mktCustomerApi[fn](lock.data.id, { reason: value }); ElMessage.success('已处理'); await openLock(lock.row); load()
}
async function lockTransfer() {
  const { value: to } = await ElMessageBox.prompt('目标伙伴 ID', '转移锁定', { inputPattern: /^\d+$/, inputErrorMessage: '请输入数字 ID' })
  const { value: reason } = await ElMessageBox.prompt('转移原因', '转移锁定', { inputPattern: /\S+/, inputErrorMessage: '原因必填' })
  await mktCustomerApi.transferLock(lock.data.id, { toPromoterId: Number(to), reason })
  ElMessage.success('已转移'); await openLock(lock.row); load()
}
async function lockPrelock() {
  if (!lock.newPromoterId) return ElMessage.error('请输入伙伴 ID')
  await mktCustomerApi.prelock(lock.row.id, { promoterId: lock.newPromoterId })
  ElMessage.success('已预锁'); await openLock(lock.row); load()
}

onMounted(load)
</script>

<style scoped>
.more-action { margin-left: 10px; }
.project-note { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.5; margin-top: 4px; overflow-wrap: anywhere; }
.pager { margin-top: 16px; justify-content: flex-end; }
.hint { color: var(--el-text-color-secondary); font-size: 12px; }
.muted { color: var(--el-text-color-placeholder); }
.mb { margin-bottom: 12px; }
.lock-actions { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 16px; }
</style>
