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
async function onDrop(e, folderId) {
  dragOver.value = false
  const list = e.dataTransfer?.files
  if (list && list.length) await uploadFiles(list, folderId)
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
        name: f.name
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
const moveDialog = reactive({ visible: false, row: null, folderId: 0 })
const moveTreeData = computed(() => [{ value: 0, label: '根目录（不放进任何文件夹）' }, ...toSelect(folderTree.value)])
function toSelect(nodes) {
  return (nodes || []).map((n) => ({ value: n.id, label: n.name, children: toSelect(n.children) }))
}
function openMove(row) {
  Object.assign(moveDialog, { visible: true, row, folderId: row.folderId || 0 })
}
async function saveMove() {
  try {
    await archiveApi.move({ id: moveDialog.row.id, folderId: moveDialog.folderId })
    ElMessage.success('已移动')
    moveDialog.visible = false
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
  <div class="fm">
    <aside class="side">
      <div class="side-head">
        <span class="side-title">资料库</span>
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
        @node-click="(n) => selectFolder(n.id)"
      >
        <template #default="{ data }">
          <span class="tnode" @dragover.prevent.stop @drop.prevent.stop="onDrop($event, data.id)">
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

    <section class="main" @dragover.prevent="dragOver = true" @dragleave="onMainLeave" @drop.prevent="onDrop($event, currentFolderId)">
      <div class="crumb">
        <div class="crumb-path">
          <span class="cp" @click="selectFolder(null)">全部资料</span>
          <template v-for="b in breadcrumb" :key="b.id">
            <span class="sep">/</span>
            <span class="cp" @click="selectFolder(b.id)">{{ b.name }}</span>
          </template>
        </div>
        <div class="crumb-actions">
          <el-input v-model="keyword" size="small" placeholder="搜索资料" clearable style="width: 180px"
            @keyup.enter="search" @clear="search">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-button size="small" @click="openFolderDialog(currentFolderId)"><el-icon><Plus /></el-icon>新建文件夹</el-button>
          <el-button size="small" type="primary" :loading="uploading" @click="pickFiles"><el-icon><Upload /></el-icon>上传文件</el-button>
          <input ref="fileInput" type="file" multiple style="display: none" @change="onPickChange" />
        </div>
      </div>

      <div v-if="dragOver" class="drop-mask">
        <el-icon class="dm-ico"><UploadFilled /></el-icon>
        <div>松开鼠标，上传到「{{ currentFolderName }}」</div>
      </div>

      <div v-if="subFolders.length" class="folders">
        <div v-for="f in subFolders" :key="f.id" class="fold-card" @click="selectFolder(f.id)"
          @dragover.prevent.stop @drop.prevent.stop="onDrop($event, f.id)">
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
          <span class="fh-title">{{ currentFolderName }}</span>
          <span class="fh-count">共 {{ total }} 份资料</span>
          <span class="fh-hint">可直接把文件拖进来上传</span>
        </div>
        <el-table :data="files" v-loading="loading" border stripe empty-text="这个文件夹还是空的，把文件拖进来试试">
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

    <el-dialog v-model="moveDialog.visible" title="移动到文件夹" width="440px">
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
.fm { display: flex; gap: 16px; align-items: flex-start; }
.side { width: 268px; flex: none; background: var(--el-bg-color, #fff); border: 1px solid var(--el-border-color-lighter, #ebeef5); border-radius: 8px; padding: 12px 8px; max-height: calc(100vh - 130px); overflow: auto; }
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
.main { flex: 1; min-width: 0; position: relative; }
.crumb { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 14px; flex-wrap: wrap; }
.crumb-path { font-size: 14px; display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.cp { cursor: pointer; color: var(--el-color-primary, #409eff); }
.cp:hover { text-decoration: underline; }
.sep { color: var(--el-text-color-secondary, #c0c4cc); }
.crumb-actions { display: flex; gap: 8px; align-items: center; }
.drop-mask { position: absolute; inset: 0; z-index: 10; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 10px; background: rgba(64, 158, 255, 0.08); border: 2px dashed var(--el-color-primary, #409eff); border-radius: 10px; color: var(--el-color-primary, #409eff); font-size: 15px; pointer-events: none; }
.dm-ico { font-size: 40px; }
.folders { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 12px; margin-bottom: 16px; }
.fold-card { position: relative; display: flex; flex-direction: column; gap: 4px; padding: 14px 14px 12px; border: 1px solid var(--el-border-color-lighter, #ebeef5); border-radius: 10px; background: var(--el-bg-color, #fff); cursor: pointer; transition: border-color .15s, box-shadow .15s; }
.fold-card:hover { border-color: var(--el-color-primary-light-5, #a0cfff); box-shadow: 0 2px 8px rgba(64, 158, 255, .12); }
.fold-ico { font-size: 30px; color: #e6a23c; }
.fold-name { font-size: 14px; font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.fold-cnt { font-size: 12px; color: var(--el-text-color-secondary, #909399); }
.fold-more { position: absolute; top: 8px; right: 8px; color: var(--el-text-color-secondary, #909399); opacity: 0; }
.fold-card:hover .fold-more { opacity: 1; }
.files { background: var(--el-bg-color, #fff); border: 1px solid var(--el-border-color-lighter, #ebeef5); border-radius: 10px; padding: 14px; }
.files-head { display: flex; align-items: baseline; gap: 12px; margin-bottom: 10px; }
.fh-title { font-size: 15px; font-weight: 600; }
.fh-count { font-size: 13px; color: var(--el-text-color-secondary, #909399); }
.fh-hint { margin-left: auto; font-size: 12px; color: var(--el-text-color-placeholder, #a8abb2); }
.fname { display: inline-flex; align-items: center; gap: 6px; }
.fico { color: var(--el-color-primary, #409eff); }
.ext { color: var(--el-text-color-secondary, #909399); font-style: normal; }
.pager { margin-top: 14px; justify-content: flex-end; }
</style>
