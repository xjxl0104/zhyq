<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { archiveApi, folderApi, fmtSize } from '@/api/archive'
import { startFileDownload } from '@/utils/fileDownload'
import { useProjectStore } from '@/stores/project'

const project = useProjectStore()

const folderTree = ref([])
const currentFolderId = ref(null)
const breadcrumb = ref([])
const files = ref([])
const total = ref(0)
const loading = ref(false)
const uploading = ref(false)
const dragOver = ref(false)
const keyword = ref('')
const query = reactive({ pageNo: 1, pageSize: 20 })
const fileInput = ref()

const subFolders = computed(() => {
  if (currentFolderId.value === null) return folderTree.value
  return findNode(folderTree.value, currentFolderId.value)?.children || []
})
const currentFolderName = computed(() => {
  if (currentFolderId.value === null) return '全部资料'
  return breadcrumb.value.length ? breadcrumb.value[breadcrumb.value.length - 1].name : '文件夹'
})

function findNode(nodes, id) {
  for (const n of nodes || []) {
    if (n.id === id) return n
    const hit = findNode(n.children, id)
    if (hit) return hit
  }
  return null
}

async function loadTree() {
  folderTree.value = (await folderApi.tree(project.currentProjectId || undefined)) || []
}
async function loadBreadcrumb() {
  breadcrumb.value = currentFolderId.value === null ? [] : (await folderApi.breadcrumb(currentFolderId.value)) || []
}
async function loadFiles() {
  loading.value = true
  selected.value = []
  try {
    const params = { pageNo: query.pageNo, pageSize: query.pageSize, keyword: keyword.value || undefined }
    if (currentFolderId.value !== null) params.folderId = currentFolderId.value
    else if (!keyword.value) params.folderId = 0
    const res = await archiveApi.page(params)
    files.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}
async function selectFolder(id) {
  currentFolderId.value = id
  query.pageNo = 1
  await Promise.all([loadBreadcrumb(), loadFiles()])
}
async function refresh() {
  await loadTree()
  await loadFiles()
}
function search() {
  query.pageNo = 1
  loadFiles()
}

// ---------- 新建 / 重命名 / 删除 文件夹 ----------
const folderDialog = reactive({ visible: false, mode: 'create', targetId: null, id: null, name: '', title: '' })
function openFolderDialog(parentId) {
  Object.assign(folderDialog, { visible: true, mode: 'create', targetId: parentId ?? null, id: null, name: '', title: '新建文件夹' })
}
function openRenameFolder(node) {
  Object.assign(folderDialog, { visible: true, mode: 'rename', targetId: null, id: node.id, name: node.name, title: '重命名文件夹' })
}
async function saveFolder() {
  const name = (folderDialog.name || '').trim()
  if (!name) return ElMessage.warning('请填写文件夹名称')
  try {
    if (folderDialog.mode === 'create') {
      await folderApi.create({ parentId: folderDialog.targetId, name })
      ElMessage.success('已新建文件夹')
    } else {
      await folderApi.rename({ id: folderDialog.id, name })
      ElMessage.success('已重命名')
    }
    folderDialog.visible = false
    await loadTree()
    if (currentFolderId.value !== null) await loadBreadcrumb()
  } catch (e) {
    ElMessage.error(e?.message || '操作失败')
  }
}
async function removeFolder(node) {
  try {
    await ElMessageBox.confirm(`确认删除文件夹「${node.name}」？文件夹里必须没有子文件夹和资料。`, '删除文件夹', { type: 'warning' })
    await folderApi.remove(node.id)
    ElMessage.success('已删除')
    if (currentFolderId.value === node.id) await selectFolder(null)
    else await loadTree()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e?.message || '删除失败')
  }
}
function onNodeCmd(cmd, node) {
  if (cmd === 'sub') openFolderDialog(node.id)
  else if (cmd === 'rename') openRenameFolder(node)
  else if (cmd === 'delete') removeFolder(node)
}

// ---------- 文件夹拖动排序 / 拖动调整层级 ----------
const cardDragId = ref(null)

/** 拖的是不是电脑里的文件(而不是在拖文件夹节点) */
function hasExternalFiles(e) {
  const types = e.dataTransfer?.types
  return !!types && Array.from(types).includes('Files')
}
function isDescendantOf(ancestorId, nodeId) {
  const root = findNode(folderTree.value, ancestorId)
  return !!findNode(root?.children, nodeId)
}
function allowDrop(draggingNode, dropNode, type) {
  const from = draggingNode?.data
  const to = dropNode?.data
  if (!from || !to || from.id === to.id) return false
  // 拖进文件夹里面 = 换个位置放;不能拖进自己的子孙
  if (type === 'inner') return !isDescendantOf(from.id, to.id)
  // 前后插入 = 排序,只在同一层级内允许,避免不小心改了上级
  return from.parentId === to.parentId
}
async function onNodeDrop(draggingNode, dropNode, dropType) {
  const dragId = draggingNode?.data?.id
  if (!dragId) return
  try {
    if (dropType === 'inner') {
      await folderApi.move({ id: dragId, parentId: dropNode.data.id })
      ElMessage.success(`已移入「${dropNode.data.name}」`)
    } else {
      // el-tree 已把节点挪到新位置,直接取同级的最终顺序落库
      const parent = dropNode.parent
      const ids = (parent?.childNodes || []).map((n) => n.data?.id).filter((i) => i != null)
      if (!ids.length) return
      await folderApi.sort({ parentId: parent?.data?.id ?? null, ids })
    }
    await loadTree()
    if (currentFolderId.value !== null) await loadBreadcrumb()
  } catch (e) {
    ElMessage.error(e?.message || '调整顺序失败')
    await loadTree()
  }
}
/** 树节点上的拖放:只接「从电脑拖文件进来上传」,文件夹排序交给 el-tree 自己处理 */
function onNodeDragOver(e) {
  if (hasExternalFiles(e)) {
    e.preventDefault()
    e.stopPropagation()
  }
}
async function onNodeItemDrop(e, node) {
  if (!hasExternalFiles(e)) return
  e.preventDefault()
  e.stopPropagation()
  await onDrop(e, node.id)
}

// ---------- 文件夹卡片拖动排序 ----------
function onCardDragStart(e, f) {
  cardDragId.value = f.id
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move'
    e.dataTransfer.setData('text/plain', String(f.id))
  }
}
function onCardDragOver(e) {
  if (hasExternalFiles(e) || !cardDragId.value) return
  e.preventDefault()
  e.stopPropagation()
}
async function onCardDrop(e, target) {
  if (hasExternalFiles(e)) {
    await onDrop(e, target.id)
    return
  }
  e.preventDefault()
  e.stopPropagation()
  const fromId = cardDragId.value
  cardDragId.value = null
  if (!fromId || fromId === target.id) return
  const ids = subFolders.value.map((f) => f.id)
  const from = ids.indexOf(fromId)
  const to = ids.indexOf(target.id)
  if (from < 0 || to < 0) return
  ids.splice(to, 0, ids.splice(from, 1)[0])
  try {
    await folderApi.sort({ parentId: currentFolderId.value, ids })
    await loadTree()
  } catch (err) {
    ElMessage.error(err?.message || '排序失败')
  }
}

// ---------- 上传(拖拽 / 选择) ----------
function pickFiles() {
  fileInput.value?.click()
}
function onPickChange(e) {
  uploadFiles(e.target.files, currentFolderId.value)
  e.target.value = ''
}
function onMainLeave(e) {
  if (e.target === e.currentTarget) dragOver.value = false
}
/** 拖动文件夹卡片做排序时,不要亮起「上传到这里」的遮罩 */
function onMainDragOver(e) {
  if (cardDragId.value) return
  e.preventDefault()
  dragOver.value = true
}
async function onDrop(e, folderId) {
  dragOver.value = false
  const list = e.dataTransfer?.files
  if (list && list.length) await uploadFiles(list, folderId)
}
/** 去掉扩展名:列表会把 fileExt 单独拼在名称后面,名称里再带一遍就会重复 */
function stripExt(name) {
  const s = name || ''
  const i = s.lastIndexOf('.')
  return i > 0 ? s.slice(0, i) : s
}
async function uploadFiles(list, folderId) {
  const arr = Array.from(list || [])
  if (!arr.length) return
  uploading.value = true
  let ok = 0
  for (const f of arr) {
    try {
      const sf = await archiveApi.upload(f)
      await archiveApi.add({
        folderId: folderId ?? null,
        fileId: sf.id,
        name: stripExt(f.name)
      })
      ok++
    } catch (err) {
      ElMessage.error(`「${f.name}」上传失败：${err?.message || ''}`)
    }
  }
  uploading.value = false
  if (ok) {
    ElMessage.success(`已上传 ${ok} 个文件`)
    await refresh()
  }
}

// ---------- 文件操作 ----------
async function download(row) {
  try {
    await startFileDownload(row.fileId, `${row.name}${row.fileExt ? '.' + row.fileExt : ''}`)
    ElMessage.success('已交给浏览器下载')
  } catch (e) {
    ElMessage.error(e?.message || '下载失败')
  }
}
const renameDocDialog = reactive({ visible: false, row: null, name: '' })
function openRenameDoc(row) {
  Object.assign(renameDocDialog, { visible: true, row, name: row.name })
}
async function saveRenameDoc() {
  const name = (renameDocDialog.name || '').trim()
  if (!name) return ElMessage.warning('请填写名称')
  try {
    await archiveApi.update({ ...renameDocDialog.row, name })
    ElMessage.success('已重命名')
    renameDocDialog.visible = false
    loadFiles()
  } catch (e) {
    ElMessage.error(e?.message || '重命名失败')
  }
}
// ---------- 批量选择 ----------
const selected = ref([])
const tableRef = ref()
function onSelChange(rows) {
  selected.value = rows || []
}
function clearSelection() {
  tableRef.value?.clearSelection()
  selected.value = []
}

// ---------- 移动到文件夹(单个 / 批量) ----------
const moveDialog = reactive({ visible: false, rows: [], folderId: null })
const moveTreeData = computed(() => [{ value: 0, label: '根目录（不放进任何文件夹）' }, ...toSelect(folderTree.value)])
const moveTitle = computed(() => (moveDialog.rows.length > 1
  ? `批量移动到文件夹（已选 ${moveDialog.rows.length} 份）`
  : '移动到文件夹'))
function toSelect(nodes) {
  return (nodes || []).map((n) => ({ value: n.id, label: n.name, children: toSelect(n.children) }))
}
function openMove(row) {
  Object.assign(moveDialog, { visible: true, rows: [row], folderId: row.folderId || 0 })
}
function openBulkMove() {
  if (!selected.value.length) return ElMessage.warning('请先勾选资料')
  Object.assign(moveDialog, { visible: true, rows: [...selected.value], folderId: null })
}
async function saveMove() {
  if (moveDialog.folderId === null || moveDialog.folderId === undefined) {
    return ElMessage.warning('请选择目标文件夹')
  }
  try {
    await archiveApi.move({ ids: moveDialog.rows.map((r) => r.id), folderId: moveDialog.folderId })
    ElMessage.success(`已移动 ${moveDialog.rows.length} 份资料`)
    moveDialog.visible = false
    clearSelection()
    await refresh()
  } catch (e) {
    ElMessage.error(e?.message || '移动失败')
  }
}
async function removeDoc(row) {
  try {
    await ElMessageBox.confirm(`确认删除「${row.name}」？文件也会一并删除。`, '删除资料', { type: 'warning' })
    await archiveApi.remove(row.id)
    ElMessage.success('已删除')
    await refresh()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e?.message || '删除失败')
  }
}

function fmtTime(t) {
  return (t || '').replace('T', ' ').slice(0, 16)
}

watch(() => project.currentProjectId, () => { selectFolder(null) })
onMounted(async () => {
  await loadTree()
  await loadFiles()
})
</script>

<template>
  <div class="page-container archive-page">
    <div class="page-header">
      <div class="ph-title">
        <h2>资料库</h2>
        <p>像电脑文件夹一样管理园区资料：新建文件夹、拖拽上传、自动编号</p>
      </div>
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="搜索资料" clearable style="width: 200px"
          @keyup.enter="search" @clear="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button @click="openFolderDialog(currentFolderId)"><el-icon><Plus /></el-icon>新建文件夹</el-button>
        <el-button type="primary" :loading="uploading" @click="pickFiles"><el-icon><Upload /></el-icon>上传文件</el-button>
        <input ref="fileInput" type="file" multiple style="display: none" @change="onPickChange" />
      </div>
    </div>

    <div class="fm">
    <aside class="side">
      <div class="side-head">
        <span class="side-title">文件夹</span>
        <el-tooltip content="新建根文件夹" placement="top">
          <el-button size="small" circle @click="openFolderDialog(null)"><el-icon><Plus /></el-icon></el-button>
        </el-tooltip>
      </div>
      <div class="all" :class="{ on: currentFolderId === null }" @click="selectFolder(null)">
        <el-icon><FolderOpened /></el-icon><span>全部资料</span>
      </div>
      <el-tree
        :data="folderTree"
        node-key="id"
        :props="{ label: 'name', children: 'children' }"
        :expand-on-click-node="false"
        highlight-current
        :current-node-key="currentFolderId"
        draggable
        :allow-drop="allowDrop"
        @node-click="(n) => selectFolder(n.id)"
        @node-drop="onNodeDrop"
      >
        <template #default="{ data }">
          <span class="tnode" @dragover="onNodeDragOver" @drop="onNodeItemDrop($event, data)">
            <el-icon class="tico"><Folder /></el-icon>
            <span class="tname">{{ data.name }}</span>
            <span v-if="data.count" class="cnt">{{ data.count }}</span>
            <el-dropdown trigger="click" @command="(c) => onNodeCmd(c, data)" @click.stop>
              <el-icon class="more" @click.stop><MoreFilled /></el-icon>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="sub">新建子文件夹</el-dropdown-item>
                  <el-dropdown-item command="rename">重命名</el-dropdown-item>
                  <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </span>
        </template>
      </el-tree>
    </aside>

    <section class="main" @dragover="onMainDragOver" @dragleave="onMainLeave" @drop.prevent="onDrop($event, currentFolderId)">
      <div class="crumb-bar">
        <div class="crumb-path">
          <span class="cp root" :class="{ cur: currentFolderId === null }" @click="selectFolder(null)">全部资料</span>
          <template v-for="b in breadcrumb" :key="b.id">
            <span class="sep">/</span>
            <span class="cp" @click="selectFolder(b.id)">{{ b.name }}</span>
          </template>
        </div>
        <span class="crumb-meta">{{ subFolders.length }} 个文件夹 · {{ total }} 份资料</span>
      </div>

      <div v-if="dragOver" class="drop-mask">
        <el-icon class="dm-ico"><UploadFilled /></el-icon>
        <div>松开鼠标，上传到「{{ currentFolderName }}」</div>
      </div>

      <div v-if="subFolders.length" class="folders">
        <div v-for="f in subFolders" :key="f.id" class="fold-card"
          draggable="true"
          @click="selectFolder(f.id)"
          @dragstart="onCardDragStart($event, f)"
          @dragend="cardDragId = null"
          @dragover="onCardDragOver"
          @drop="onCardDrop($event, f)">
          <el-icon class="fold-ico"><FolderFilled /></el-icon>
          <div class="fold-name" :title="f.name">{{ f.name }}</div>
          <div class="fold-cnt">{{ f.count || 0 }} 个文件</div>
          <el-dropdown class="fold-more" trigger="click" @command="(c) => onNodeCmd(c, f)" @click.stop>
            <el-icon @click.stop><MoreFilled /></el-icon>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="sub">新建子文件夹</el-dropdown-item>
                <el-dropdown-item command="rename">重命名</el-dropdown-item>
                <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>

      <div class="files">
        <div class="files-head">
          <span class="fh-title">资料列表</span>
          <span class="fh-hint">把文件直接拖进这个区域即可上传</span>
        </div>
        <div v-if="selected.length" class="bulk">
          <span class="bulk-txt">已勾选 {{ selected.length }} 份资料</span>
          <el-button size="small" type="primary" @click="openBulkMove">
            <el-icon><Sort /></el-icon>批量移动
          </el-button>
          <el-button size="small" text @click="clearSelection">取消选择</el-button>
        </div>
        <el-table ref="tableRef" :data="files" v-loading="loading" border stripe
          empty-text="这个文件夹还是空的，把文件拖进来试试" @selection-change="onSelChange">
          <el-table-column type="selection" width="46" />
          <el-table-column prop="archiveNo" label="编号" width="130" />
          <el-table-column label="名称" min-width="300" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="fname"><el-icon class="fico"><Document /></el-icon>{{ row.name }}<em v-if="row.fileExt" class="ext">.{{ row.fileExt }}</em></span>
            </template>
          </el-table-column>
          <el-table-column label="大小" width="100">
            <template #default="{ row }">{{ row.fileSize ? fmtSize(row.fileSize) : '-' }}</template>
          </el-table-column>
          <el-table-column label="上传时间" width="150">
            <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="240" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="download(row)">下载</el-button>
              <el-button link type="primary" @click="openRenameDoc(row)">重命名</el-button>
              <el-button link type="primary" @click="openMove(row)">移动</el-button>
              <el-button link type="danger" @click="removeDoc(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination v-model:current-page="query.pageNo" v-model:page-size="query.pageSize" class="pager" background
          layout="total, sizes, prev, pager, next" :total="total" :page-sizes="[20, 50, 100]" @change="loadFiles" />
      </div>
    </section>
    </div>

    <el-dialog v-model="folderDialog.visible" :title="folderDialog.title" width="440px">
      <el-input v-model="folderDialog.name" placeholder="文件夹名称，如：2026年10月" maxlength="100" @keyup.enter="saveFolder" />
      <template #footer>
        <el-button @click="folderDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="saveFolder">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="renameDocDialog.visible" title="重命名文件" width="440px">
      <el-input v-model="renameDocDialog.name" maxlength="200" @keyup.enter="saveRenameDoc" />
      <template #footer>
        <el-button @click="renameDocDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="saveRenameDoc">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="moveDialog.visible" :title="moveTitle" width="460px">
      <div v-if="moveDialog.rows.length > 1" class="move-tip">
        将把 {{ moveDialog.rows.length }} 份资料一起移动，原有编号不变。
      </div>
      <el-tree-select v-model="moveDialog.folderId" :data="moveTreeData" check-strictly :render-after-expand="false"
        placeholder="选择目标文件夹" style="width: 100%" />
      <template #footer>
        <el-button @click="moveDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="saveMove">移动</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.archive-page { display: flex; flex-direction: column; gap: 14px; }
.page-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; }
.ph-title h2 { margin: 0 0 6px; font-size: 18px; font-weight: 600; color: var(--el-text-color-primary, #303133); }
.ph-title p { margin: 0; font-size: 13px; color: var(--el-text-color-secondary, #909399); }
.toolbar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.fm { display: flex; gap: 14px; align-items: flex-start; min-width: 0; }
.side { width: 260px; flex: none; background: var(--el-bg-color, #fff); border: 1px solid var(--el-border-color-lighter, #ebeef5); border-radius: 8px; padding: 10px 6px; position: sticky; top: 0; max-height: calc(100vh - 172px); overflow: auto; }
.side-head { display: flex; align-items: center; justify-content: space-between; padding: 0 8px 10px; }
.side-title { font-weight: 600; }
.all { display: flex; align-items: center; gap: 6px; padding: 7px 8px; margin-bottom: 4px; border-radius: 4px; cursor: pointer; font-size: 14px; }
.all:hover, .all.on { background: var(--el-color-primary-light-9, #ecf5ff); color: var(--el-color-primary, #409eff); }
.all .cnt { margin-left: auto; font-size: 12px; color: var(--el-text-color-secondary, #909399); }
.tnode { display: flex; align-items: center; gap: 6px; width: 100%; padding-right: 4px; font-size: 13px; }
.tnode .tico { color: #e6a23c; }
.tname { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tnode .cnt { font-size: 12px; color: var(--el-text-color-secondary, #909399); }
.tnode .more { display: none; color: var(--el-text-color-secondary, #909399); }
.tnode:hover .more { display: inline-flex; }
.main { flex: 1; min-width: 0; position: relative; display: flex; flex-direction: column; gap: 12px; }
.crumb-bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; padding: 10px 14px; background: var(--el-bg-color, #fff); border: 1px solid var(--el-border-color-lighter, #ebeef5); border-radius: 8px; }
.crumb-path { font-size: 13px; display: flex; align-items: center; gap: 6px; flex-wrap: wrap; min-width: 0; }
.cp { cursor: pointer; color: var(--el-color-primary, #409eff); }
.cp.root { color: var(--el-text-color-regular, #606266); font-weight: 500; }
.cp.root.cur { color: var(--el-text-color-primary, #303133); cursor: default; }
.cp:hover { text-decoration: underline; }
.cp.cur:hover { text-decoration: none; }
.sep { color: var(--el-text-color-placeholder, #c0c4cc); }
.crumb-meta { font-size: 12px; color: var(--el-text-color-secondary, #909399); white-space: nowrap; }
.drop-mask { position: absolute; inset: 0; z-index: 10; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 10px; background: rgba(64, 158, 255, 0.08); border: 2px dashed var(--el-color-primary, #409eff); border-radius: 10px; color: var(--el-color-primary, #409eff); font-size: 15px; pointer-events: none; }
.dm-ico { font-size: 40px; }
.folders { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 12px; }
.fold-card { position: relative; display: flex; flex-direction: column; gap: 4px; padding: 14px 14px 12px; border: 1px solid var(--el-border-color-lighter, #ebeef5); border-radius: 10px; background: var(--el-bg-color, #fff); cursor: grab; transition: border-color .15s, box-shadow .15s; }
.fold-card:active { cursor: grabbing; }
.fold-card:hover { border-color: var(--el-color-primary-light-5, #a0cfff); box-shadow: 0 2px 8px rgba(64, 158, 255, .12); }
.fold-ico { font-size: 30px; color: #e6a23c; }
.fold-name { font-size: 14px; font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.fold-cnt { font-size: 12px; color: var(--el-text-color-secondary, #909399); }
.fold-more { position: absolute; top: 8px; right: 8px; color: var(--el-text-color-secondary, #909399); opacity: 0; }
.fold-card:hover .fold-more { opacity: 1; }
.files { background: var(--el-bg-color, #fff); border: 1px solid var(--el-border-color-lighter, #ebeef5); border-radius: 10px; padding: 14px; }
.files-head { display: flex; align-items: baseline; gap: 12px; margin-bottom: 10px; }
.bulk { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; padding: 8px 12px; border-radius: 6px; background: var(--el-color-primary-light-9, #ecf5ff); border: 1px solid var(--el-color-primary-light-7, #c6e2ff); }
.bulk-txt { font-size: 13px; color: var(--el-color-primary, #409eff); }
.move-tip { margin-bottom: 10px; font-size: 13px; color: var(--el-text-color-regular, #606266); }
.fh-title { font-size: 14px; font-weight: 600; }
.fh-hint { margin-left: auto; font-size: 12px; color: var(--el-text-color-placeholder, #a8abb2); }
.fname { display: inline-flex; align-items: center; gap: 6px; }
.fico { color: var(--el-color-primary, #409eff); }
.ext { color: var(--el-text-color-secondary, #909399); font-style: normal; }
.pager { margin-top: 14px; justify-content: flex-end; }

@media (max-width: 900px) {
  .page-header { flex-direction: column; align-items: stretch; }
  .toolbar { flex-wrap: wrap; }
  .toolbar :deep(.el-input) { width: 100% !important; }
  .fm { flex-direction: column; }
  .side { width: 100%; max-height: 260px; }
}
</style>
