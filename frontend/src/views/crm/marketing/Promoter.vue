<template>
  <div class="page-container">
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="姓名/手机">
          <el-input v-model="query.keyword" placeholder="姓名或手机号" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="岗位">
          <el-select v-model="query.positionCode" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="p in positions" :key="p.code" :label="p.name" :value="p.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="table-card">
      <div class="toolbar">
        <el-button type="primary" @click="openManual"><el-icon><Plus /></el-icon>录入伙伴</el-button>
        <span class="hint">阶段 A 尚无小程序,伙伴由运营在此手工录入;上线小程序后由伙伴扫码自助注册。</span>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="60" />
        <el-table-column prop="name" label="姓名" width="110" />
        <el-table-column label="手机" width="130">
          <template #default="{ row }">{{ maskPhone(row.phone) }}</template>
        </el-table-column>
        <el-table-column prop="inviteCode" label="邀请码" width="110" />
        <el-table-column label="岗位" width="110">
          <template #default="{ row }"><el-tag>{{ positionName(row.positionCode) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="parentId" label="上级 ID" width="90" align="center" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="内部" width="70" align="center">
          <template #default="{ row }"><el-tag v-if="row.isInternal === 1" type="warning" size="small">内部</el-tag></template>
        </el-table-column>
        <el-table-column prop="source" label="来源" width="80" />
        <el-table-column prop="createTime" label="注册时间" width="160" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" @click="openPosition(row)">调岗</el-button>
            <el-button link type="primary" @click="openParent(row)">改上级</el-button>
            <el-button v-if="row.status === 1" link type="warning" @click="freeze(row)">冻结</el-button>
            <el-button v-else-if="row.status === 2" link type="success" @click="unfreeze(row)">解冻</el-button>
            <el-button v-else-if="row.status === 3" link type="success" @click="audit(row, true)">审核通过</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                     :total="total" v-model:current-page="query.pageNo"
                     v-model:page-size="query.pageSize" :page-sizes="[10,20,50]" @change="load" />
    </div>

    <!-- 录入伙伴 -->
    <el-dialog v-model="manual.visible" title="录入伙伴" width="520px">
      <el-form ref="manualRef" :model="manual.form" :rules="manualRules" label-width="100px">
        <el-form-item label="姓名" prop="name"><el-input v-model="manual.form.name" /></el-form-item>
        <el-form-item label="手机号" prop="phone"><el-input v-model="manual.form.phone" maxlength="11" /></el-form-item>
        <el-form-item label="上级邀请码">
          <el-input v-model="manual.form.parentInviteCode" placeholder="留空 = 独立根节点" maxlength="8" />
        </el-form-item>
        <el-form-item label="岗位">
          <el-select v-model="manual.form.positionCode" style="width: 100%">
            <el-option v-for="p in positions" :key="p.code" :label="p.name" :value="p.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="manual.form.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="manual.visible = false">取消</el-button>
        <el-button type="primary" @click="submitManual">保存</el-button>
      </template>
    </el-dialog>

    <!-- 调岗 / 改上级 / 冻结:统一的带原因弹窗 -->
    <el-dialog v-model="act.visible" :title="act.title" width="460px">
      <el-form label-width="90px">
        <el-form-item v-if="act.type === 'position'" label="新岗位">
          <el-select v-model="act.value" style="width: 100%">
            <el-option v-for="p in positions" :key="p.code" :label="p.name" :value="p.code" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="act.type === 'parent'" label="新上级 ID">
          <el-input-number v-model="act.value" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="原因" required>
          <el-input v-model="act.reason" type="textarea" placeholder="必填,写入审计" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="act.visible = false">取消</el-button>
        <el-button type="primary" @click="submitAct">确定</el-button>
      </template>
    </el-dialog>

    <!-- 详情 Drawer -->
    <el-drawer v-model="detail.visible" :title="`伙伴详情 · ${detail.row?.name || ''}`" size="720px">
      <el-tabs v-model="detail.tab" @tab-change="loadTab">
        <el-tab-pane label="资料" name="info">
          <el-descriptions :column="2" border v-if="detail.row">
            <el-descriptions-item label="姓名">{{ detail.row.name }}</el-descriptions-item>
            <el-descriptions-item label="手机">{{ maskPhone(detail.row.phone) }}</el-descriptions-item>
            <el-descriptions-item label="邀请码">{{ detail.row.inviteCode }}</el-descriptions-item>
            <el-descriptions-item label="岗位">{{ positionName(detail.row.positionCode) }}</el-descriptions-item>
            <el-descriptions-item label="上级 ID">{{ detail.row.parentId || '—(根节点)' }}</el-descriptions-item>
            <el-descriptions-item label="路径">{{ detail.row.path }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ statusText(detail.row.status) }}</el-descriptions-item>
            <el-descriptions-item label="岗位起始">{{ detail.row.positionSince || '-' }}</el-descriptions-item>
            <el-descriptions-item label="备注" :span="2">{{ detail.row.remark || '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>
        <el-tab-pane label="团队(直属)" name="team">
          <el-table :data="detail.team" size="small" border>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="name" label="姓名" />
            <el-table-column label="岗位"><template #default="{ row }">{{ positionName(row.positionCode) }}</template></el-table-column>
            <el-table-column label="状态"><template #default="{ row }">{{ statusText(row.status) }}</template></el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="佣金" name="commissions">
          <el-table :data="detail.commissions" size="small" border>
            <el-table-column prop="referralOrderId" label="订单" width="80" />
            <el-table-column prop="positionCode" label="岗位" width="70" />
            <el-table-column label="份额/级差" width="100"><template #default="{ row }">{{ row.sharePct }}% / {{ row.diffPct }}%</template></el-table-column>
            <el-table-column prop="amount" label="金额" width="110" />
            <el-table-column label="状态"><template #default="{ row }">{{ commissionStatus(row.status) }}</template></el-table-column>
            <el-table-column prop="createTime" label="时间" width="160" />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="岗位史" name="history">
          <el-table :data="detail.history" size="small" border>
            <el-table-column prop="fromCode" label="从" width="70" />
            <el-table-column prop="toCode" label="到" width="70" />
            <el-table-column prop="reason" label="方式" width="80" />
            <el-table-column prop="operator" label="操作人" width="100" />
            <el-table-column prop="note" label="说明" />
            <el-table-column prop="createTime" label="时间" width="160" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-drawer>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { mktPromoterApi, mktPositionApi } from '@/api/marketing'

const statusOptions = [
  { value: 1, label: '正常' }, { value: 2, label: '冻结' }, { value: 3, label: '待审核' }, { value: 4, label: '已退出' }
]
const statusText = (v) => statusOptions.find(s => s.value === v)?.label || '-'
const statusType = (v) => ({ 1: 'success', 2: 'warning', 3: 'info', 4: 'danger' }[v] || 'info')
const commissionStatus = (v) => ({ 1: '冻结', 2: '可结算', 3: '已结算', 4: '已提现', 5: '作废' }[v] || '-')
const maskPhone = (p) => (p && p.length === 11 ? p.slice(0, 3) + '****' + p.slice(7) : p || '-')

const positions = ref([])
const positionName = (code) => positions.value.find(p => p.code === code)?.name || code

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, keyword: '', positionCode: null, status: null })

async function load() {
  loading.value = true
  try {
    const res = await mktPromoterApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally { loading.value = false }
}
function reset() {
  Object.assign(query, { pageNo: 1, keyword: '', positionCode: null, status: null })
  load()
}

// ---- 录入 ----
const manualRef = ref()
const manual = reactive({ visible: false, form: emptyManual() })
function emptyManual() { return { name: '', phone: '', parentInviteCode: '', positionCode: 'P1', remark: '' } }
const manualRules = {
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  phone: [{ required: true, pattern: /^1\d{10}$/, message: '请输入 11 位手机号', trigger: 'blur' }]
}
function openManual() { Object.assign(manual.form, emptyManual()); manual.visible = true }
async function submitManual() {
  await manualRef.value.validate()
  await mktPromoterApi.manual(manual.form)
  ElMessage.success('已录入')
  manual.visible = false
  load()
}

// ---- 调岗 / 改上级 / 冻结 ----
const act = reactive({ visible: false, type: '', title: '', row: null, value: null, reason: '' })
function openPosition(row) { Object.assign(act, { visible: true, type: 'position', title: '手动调岗', row, value: row.positionCode, reason: '' }) }
function openParent(row) { Object.assign(act, { visible: true, type: 'parent', title: '改上级(终身绑定,仅运营可改)', row, value: row.parentId, reason: '' }) }
function freeze(row) { Object.assign(act, { visible: true, type: 'freeze', title: '冻结伙伴', row, value: null, reason: '' }) }
async function submitAct() {
  if (!act.reason.trim()) return ElMessage.error('请填写原因')
  const id = act.row.id
  if (act.type === 'position') await mktPromoterApi.position(id, { code: act.value, reason: act.reason })
  else if (act.type === 'parent') await mktPromoterApi.parent(id, { parentId: act.value, reason: act.reason })
  else if (act.type === 'freeze') await mktPromoterApi.freeze(id, { reason: act.reason })
  ElMessage.success('已处理')
  act.visible = false
  load()
}
async function unfreeze(row) { await mktPromoterApi.unfreeze(row.id); ElMessage.success('已解冻'); load() }
async function audit(row, pass) { await mktPromoterApi.audit(row.id, { pass, reason: pass ? '后台审核通过' : '' }); ElMessage.success('已审核'); load() }

// ---- 详情 ----
const detail = reactive({ visible: false, tab: 'info', row: null, team: [], commissions: [], history: [] })
async function openDetail(row) {
  detail.row = await mktPromoterApi.get(row.id)
  detail.tab = 'info'
  detail.team = []; detail.commissions = []; detail.history = []
  detail.visible = true
}
async function loadTab(name) {
  const id = detail.row.id
  if (name === 'team' && !detail.team.length) detail.team = await mktPromoterApi.team(id)
  if (name === 'commissions' && !detail.commissions.length) detail.commissions = (await mktPromoterApi.commissions(id, { pageNo: 1, pageSize: 50 })).records
  if (name === 'history' && !detail.history.length) detail.history = await mktPromoterApi.history(id)
}

onMounted(async () => {
  positions.value = await mktPositionApi.list()
  load()
})
</script>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 12px; }
.hint { color: var(--el-text-color-secondary); font-size: 12px; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
