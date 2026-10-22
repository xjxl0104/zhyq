<template>
  <div class="org-page" v-if="canQuery">
    <div class="org-layout">
      <aside class="org-side table-card">
        <div class="org-search">
          <el-input v-model="keyword" placeholder="搜索姓名" clearable />
          <el-button :loading="loading" @click="reload">刷新</el-button>
        </div>
        <el-alert v-if="error" type="error" :closable="false" :title="error" class="mb" />
        <el-tree ref="treeRef" v-loading="loading" :data="roots" node-key="id" :props="{ label: 'name', children: 'children' }"
                 default-expand-all highlight-current :expand-on-click-node="false" :filter-node-method="filterNode"
                 empty-text="暂无伙伴" @node-click="select">
          <template #default="{ data }">
            <span class="org-node">
              <el-tag size="small" :type="tagType(data.positionCode)">{{ data.positionCode }}</el-tag>
              <span class="org-name">{{ data.name }}</span>
              <span class="org-count">客户 {{ data.customers }} · 已签约 {{ data.signedCustomers }}</span>
              <el-tag v-if="data.status !== 1" size="small" type="info">非正常</el-tag>
            </span>
          </template>
        </el-tree>
        <p class="hint">P4 发展的人为 P3，P3 发展的人为 P2。点击成员查看他推荐的客户和下一层级推荐的客户。</p>
      </aside>

      <section class="org-main table-card">
        <el-empty v-if="!current" description="点击左侧成员，查看他推荐的已签约客户，以及下一层级推荐的已签约客户" />
        <template v-else>
          <div class="org-head">
            <h3 class="org-title">
              <el-tag :type="tagType(current.positionCode)">{{ current.positionCode }}</el-tag> {{ current.name }}
            </h3>
            <span class="org-meta">推荐客户 {{ current.customers }} 个 · 已签约 {{ current.signedCustomers }} 个 · 直属下级 {{ current.children?.length || 0 }} 人</span>
            <el-button v-if="canAdjust" class="org-adjust" type="primary" plain @click="openAdjust">调整层级</el-button>
          </div>
          <div class="org-filter"><el-checkbox v-model="onlySigned">只看已签约客户</el-checkbox></div>
          <el-alert v-if="customersError" type="error" :closable="false" :title="customersError" class="mb" />
          <el-tabs v-model="tab">
            <el-tab-pane :label="`本人推荐的客户（${selfRows.length}）`" name="self">
              <el-table :data="selfRows" v-loading="customersLoading" size="small" border empty-text="暂无客户">
                <el-table-column prop="name" label="客户" min-width="160" />
                <el-table-column label="签约" width="90" align="center"><template #default="{ row }"><el-tag :type="row.signed ? 'success' : 'info'" size="small">{{ row.signed ? '已签约' : '未签约' }}</el-tag></template></el-table-column>
                <el-table-column label="合同" min-width="190"><template #default="{ row }">{{ row.contractNo || '—' }}<el-tag v-if="row.signMode" size="small" class="ml4" :type="row.signMode === 2 ? 'warning' : 'info'">{{ SIGN[row.signMode] }}</el-tag></template></el-table-column>
              </el-table>
            </el-tab-pane>
            <el-tab-pane :label="`下一层级推荐的客户（${nextRows.length}）`" name="next">
              <el-table :data="nextRows" v-loading="customersLoading" size="small" border empty-text="下一层级暂无推荐客户">
                <el-table-column prop="referrerName" label="推荐人" width="130" />
                <el-table-column prop="name" label="客户" min-width="160" />
                <el-table-column label="签约" width="90" align="center"><template #default="{ row }"><el-tag :type="row.signed ? 'success' : 'info'" size="small">{{ row.signed ? '已签约' : '未签约' }}</el-tag></template></el-table-column>
                <el-table-column label="合同" min-width="190"><template #default="{ row }">{{ row.contractNo || '—' }}<el-tag v-if="row.signMode" size="small" class="ml4" :type="row.signMode === 2 ? 'warning' : 'info'">{{ SIGN[row.signMode] }}</el-tag></template></el-table-column>
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </template>
      </section>
    </div>

    <el-dialog v-model="adjust.visible" title="调整层级" width="min(520px, 94vw)" :close-on-click-modal="false">
      <el-form label-position="top">
        <el-form-item label="成员">{{ current?.name }}（当前 {{ current?.positionCode }}）</el-form-item>
        <el-form-item label="调整为" required>
          <el-radio-group v-model="adjust.code" @change="loadPreview">
            <el-radio-button v-for="code in adjustOptions" :key="code" :value="code">{{ code }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <el-alert v-if="adjust.error" type="error" :closable="false" :title="adjust.error" class="mb" />
      <div v-loading="adjust.loading" class="adjust-preview">
        <template v-if="adjust.preview">
          <p v-if="adjust.preview.parentChanges">调整后 <strong>{{ adjust.preview.name }}</strong> 的上级由 <strong>{{ adjust.preview.currentParentName || '无' }}</strong> 改为 <strong>{{ adjust.preview.newParentName }}</strong>，他的下级跟着他。</p>
          <p v-else>调整后上级关系不变（上级：{{ adjust.preview.newParentName || '无' }}）。</p>
          <p class="hint">调整前已签的合同，如果已保存过佣金分配，会继续按原分配（含原上级分成）结算；调整后新增的客户按新链路分成，不再与原上级分成。</p>
          <el-alert v-if="adjust.preview.contractsWithoutAllocation > 0" type="warning" :closable="false" show-icon
                    :title="`他成交的合同中有 ${adjust.preview.contractsWithoutAllocation} 份还没有保存过佣金分配，调整后会按新链路分成。如需保留原上级分成，请先到“结算 → 计佣订单 → 佣金分配”保存这些合同的分配。`" />
        </template>
      </div>
      <template #footer>
        <el-button @click="adjust.visible = false">取消</el-button>
        <el-button type="primary" :loading="adjust.saving" :disabled="!adjust.preview || adjust.loading" @click="saveAdjust">确认调整</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { mktOrgApi } from '@/api/marketing'
import { hasPermission } from '@/utils/permission'

const SIGN = { 1: '园区签', 2: '云仓直签' }
const canQuery = computed(() => hasPermission('crm:marketing:promoter:query'))
const canEdit = computed(() => hasPermission('crm:marketing:promoter:edit'))
const rank = code => Number(String(code || '').slice(1)) || 0
const tagType = code => ({ P4: 'danger', P3: 'warning', P2: 'success' }[code] || 'info')

const loading = ref(false), error = ref(''), nodes = ref([]), keyword = ref(''), treeRef = ref(null)
const current = ref(null), tab = ref('self'), onlySigned = ref(false)
const customersLoading = ref(false), customersError = ref(''), customerData = ref({ self: [], next: [] })
let loadNo = 0, customersNo = 0

// 按 parentId 组树；上级不在列表里的当作根（P4）
function buildTree(list) {
  const map = new Map(list.map(item => [String(item.id), { ...item, children: [] }]))
  const roots = []
  for (const node of map.values()) {
    const parent = node.parentId == null ? null : map.get(String(node.parentId))
    if (parent && parent !== node) parent.children.push(node); else roots.push(node)
  }
  const sort = items => { items.sort((a, b) => rank(b.positionCode) - rank(a.positionCode) || Number(a.id) - Number(b.id)); items.forEach(i => sort(i.children)) }
  sort(roots)
  return roots
}
const roots = computed(() => buildTree(nodes.value))
const filterNode = (value, data) => !value || String(data.name || '').includes(value)
watch(keyword, value => treeRef.value?.filter(value))

const selfRows = computed(() => (customerData.value.self || []).filter(row => !onlySigned.value || row.signed))
const nextRows = computed(() => (customerData.value.next || []).filter(row => !onlySigned.value || row.signed))

async function reload() {
  const no = ++loadNo
  loading.value = true; error.value = ''
  try {
    const rows = await mktOrgApi.tree()
    if (no !== loadNo) return
    nodes.value = rows || []
    if (current.value) {
      const fresh = findNode(roots.value, current.value.id)
      if (fresh) { current.value = fresh; await loadCustomers(fresh.id) } else current.value = null
    }
  } catch (e) { if (no === loadNo) error.value = e.message || '组织架构加载失败，请重试' }
  finally { if (no === loadNo) loading.value = false }
}
function findNode(list, id) {
  for (const item of list) {
    if (String(item.id) === String(id)) return item
    const hit = findNode(item.children || [], id)
    if (hit) return hit
  }
  return null
}
async function select(node) {
  current.value = node
  tab.value = 'self'
  await loadCustomers(node.id)
}
async function loadCustomers(id) {
  const no = ++customersNo
  customersLoading.value = true; customersError.value = ''; customerData.value = { self: [], next: [] }
  try {
    const result = await mktOrgApi.customers(id)
    if (no === customersNo) customerData.value = { self: result?.self || [], next: result?.next || [] }
  } catch (e) { if (no === customersNo) customersError.value = e.message || '客户加载失败，请重试' }
  finally { if (no === customersNo) customersLoading.value = false }
}

// ---- 调整层级：晋升到更高层级（P2→P3 等）；P4 本身不在此调整
const canAdjust = computed(() => canEdit.value && !!current.value && rank(current.value.positionCode) > 0 && rank(current.value.positionCode) < 3)
const adjust = reactive({ visible: false, code: '', loading: false, saving: false, preview: null, error: '' })
const adjustOptions = computed(() => ['P2', 'P3'].filter(code => current.value && rank(code) > rank(current.value.positionCode)))
async function openAdjust() {
  if (!canAdjust.value) return
  Object.assign(adjust, { visible: true, code: adjustOptions.value[0] || '', loading: false, saving: false, preview: null, error: '' })
  await loadPreview()
}
async function loadPreview() {
  if (!current.value || !adjust.code) return
  const code = adjust.code, id = current.value.id
  adjust.loading = true; adjust.preview = null; adjust.error = ''
  try {
    const preview = await mktOrgApi.positionPreview(id, { code })
    if (adjust.code === code && current.value?.id === id) adjust.preview = preview
  } catch (e) { if (adjust.code === code) adjust.error = e.message || '预览失败，请重试' }
  finally { adjust.loading = false }
}
async function saveAdjust() {
  if (!adjust.preview || adjust.saving || !current.value) return
  adjust.saving = true; adjust.error = ''
  try {
    await mktOrgApi.position(current.value.id, { code: adjust.code })
    ElMessage.success(`已将 ${current.value.name} 调整为 ${adjust.code}`)
    adjust.visible = false
    await reload()
  } catch (e) { adjust.error = e.message || '调整失败，请重试' }
  finally { adjust.saving = false }
}

onMounted(reload)
defineExpose({ buildTree })
</script>

<style scoped>
.org-layout { display: grid; grid-template-columns: minmax(300px, 380px) minmax(0, 1fr); gap: 16px; align-items: start; }
.org-side, .org-main { min-width: 0; }
.org-search { display: flex; gap: 8px; margin-bottom: 12px; }
.org-node { display: inline-flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.org-name { font-weight: 500; }
.org-count { color: var(--el-text-color-secondary); font-size: 12px; }
.org-head { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; }
.org-title { margin: 0; font-size: 16px; display: flex; align-items: center; gap: 8px; }
.org-meta { color: var(--el-text-color-regular); font-size: 13px; }
.org-adjust { margin-left: auto; }
.org-filter { margin: 8px 0; }
.adjust-preview { min-height: 48px; line-height: 1.7; }
.hint { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.6; }
.mb { margin-bottom: 12px; }
.ml4 { margin-left: 4px; }
@media (max-width: 900px) { .org-layout { grid-template-columns: minmax(0, 1fr); } }
</style>
