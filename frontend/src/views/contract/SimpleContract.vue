<template>
  <div class="page-container">
    <!-- 顶部概览 -->
    <div class="summary-grid">
      <div class="summary-item">
        <span>简易合同总数</span>
        <strong>{{ list.length }} 份</strong>
      </div>
      <div class="summary-item ok">
        <span>生效中（在册公司）</span>
        <strong>{{ activeCount }} 份</strong>
      </div>
      <div class="summary-item">
        <span>覆盖楼层</span>
        <strong>{{ usedFloorCount }} 层</strong>
      </div>
      <div class="summary-item">
        <span>已上传附件</span>
        <strong>{{ withAttachCount }} 份</strong>
      </div>
    </div>

    <!-- 按楼层分栏(1楼~8楼 + 已有自定义楼层) -->
    <div class="floor-bar">
      <button type="button" class="floor-pill" :class="{ active: floorFilter === 'all' }" @click="selectFloor('all')">
        <span>全部楼层</span>
        <span class="pill-count">{{ list.length }}</span>
      </button>
      <button v-for="f in floorTabs" :key="f" type="button" class="floor-pill"
              :class="{ active: floorFilter === f }" @click="selectFloor(f)">
        <span>{{ f }}楼（{{ f }}01~）</span>
        <span class="pill-count">{{ floorCounts[f] || 0 }}</span>
      </button>
    </div>

    <!-- 按楼层分组的自编号房号分布墙(X01, X02...) -->
    <div v-if="floorGroups.length" class="floor-board">
      <div v-for="g in floorGroups" :key="g.floor" class="floor-group">
        <div class="floor-group__head">
          <div class="floor-group__title">
            <strong>{{ g.floor }} 楼</strong>
            <span class="muted">自编号房号 {{ g.floor }}01 / {{ g.floor }}02 ...（共 {{ g.items.length }} 间）</span>
          </div>
          <el-button type="primary" plain size="small" @click="openDialog(null, g.floor)">
            <el-icon><Plus /></el-icon>新增 {{ g.floor }} 楼下一房号
          </el-button>
        </div>
        <div class="room-wall">
          <button v-for="item in g.items" :key="item.id" type="button" class="room-chip"
                  :class="chipClass(item.status)" :title="`${item.roomCode} · ${item.companyName}`"
                  @click="openDialog(item)">
            <span class="room-chip__code">{{ item.roomCode }}</span>
            <span class="room-chip__name">{{ item.companyName }}</span>
            <span v-if="Number(item.attachmentCount) > 0" class="room-chip__att">附{{ item.attachmentCount }}</span>
          </button>
        </div>
      </div>
    </div>

    <!-- 查询与列表 -->
    <div class="table-card">
      <div class="toolbar">
        <el-button type="primary" @click="openDialog(null, floorFilter === 'all' ? 1 : floorFilter)">
          <el-icon><Plus /></el-icon>新增简易合同
        </el-button>
        <el-input v-model="keyword" placeholder="搜索房号(如 101) / 注册公司 / 合同号 / 联系人" clearable style="width: 300px" />
        <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 130px">
          <el-option label="生效中" :value="1" />
          <el-option label="已到期" :value="2" />
          <el-option label="已注销" :value="3" />
        </el-select>
        <span class="toolbar-tip">主要用于公司工商注册挂靠，按楼层自动顺延自编号房号（X01、X02...），支持上传简易合同扫描件与营业执照。</span>
      </div>

      <el-table :data="viewRows" v-loading="loading" border stripe size="small" row-key="id">
        <el-table-column type="index" label="序号" width="55" align="center" />
        <el-table-column label="楼层" width="76" align="center">
          <template #default="{ row }">
            <el-tag effect="plain" size="small">{{ row.floorNo }}楼</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="自编号房号" width="105" align="center">
          <template #default="{ row }">
            <strong class="room-code">{{ row.roomCode }}</strong>
          </template>
        </el-table-column>
        <el-table-column prop="contractNo" label="简易合同编号" min-width="140" show-overflow-tooltip />
        <el-table-column prop="companyName" label="注册公司名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="regAddress" label="注册地址" min-width="210" show-overflow-tooltip />
        <el-table-column label="联系人 / 电话" min-width="145" show-overflow-tooltip>
          <template #default="{ row }">
            {{ [row.contactName, row.contactPhone].filter(Boolean).join(' / ') || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="起止日期" width="180" align="center">
          <template #default="{ row }">{{ row.startDate || '-' }} ~ {{ row.endDate || '-' }}</template>
        </el-table-column>
        <el-table-column label="费用(元)" width="100" align="right">
          <template #default="{ row }">{{ Number(row.annualFee || 0) ? `¥${money(row.annualFee)}` : '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="84" align="center">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.status).type" size="small">{{ statusMeta(row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="合同附件" width="100" align="center">
          <template #default="{ row }">
            <el-button link :type="Number(row.attachmentCount) > 0 ? 'success' : 'primary'" @click="openAttachments(row)">
              {{ Number(row.attachmentCount) > 0 ? `附件 (${row.attachmentCount})` : '上传附件' }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openAttachments(row)">附件</el-button>
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除该简易合同?" @confirm="remove(row)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新增/编辑简易合同弹窗 -->
    <el-dialog v-model="dialog.visible" :title="form.id ? `编辑简易合同 · ${form.roomCode}` : '新增简易合同（注册公司）'"
               width="740px" top="5vh" destroy-on-close :close-on-click-modal="false">
      <el-form :model="form" label-width="115px" size="small">
        <el-row :gutter="14">
          <el-col :span="12">
            <el-form-item label="所属楼层" required>
              <el-select v-model="form.floorNo" style="width: 100%" @change="onFloorChange">
                <el-option v-for="f in floorChoices" :key="f" :label="`${f}楼（自编号 ${f}01、${f}02...）`" :value="f" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="自编号房号" required>
              <div class="room-input-row">
                <el-input v-model="form.roomCode" placeholder="如 101、102、201" />
                <el-button v-if="!form.id" plain @click="fetchNextRoom(form.floorNo)">自动取号</el-button>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="注册公司名称" required>
              <el-input v-model="form.companyName" placeholder="请输入工商注册公司全称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="简易合同编号">
              <el-input v-model="form.contractNo" placeholder="留空自动生成如 JY-2026-101" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="统一信用代码">
              <el-input v-model="form.creditCode" placeholder="选填" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="法人/联系人">
              <el-input v-model="form.contactName" placeholder="选填" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话">
              <el-input v-model="form.contactPhone" placeholder="选填" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="注册地址">
              <el-input v-model="form.regAddress" placeholder="留空自动生成：DI PARK数智云仓产业园X层X01室" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="起始日期">
              <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="到期日期">
              <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="费用(元)">
              <el-input-number v-model="form.annualFee" :min="0" :precision="2" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio :value="1">生效中</el-radio>
                <el-radio :value="2">已到期</el-radio>
                <el-radio :value="3">已注销</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="可填写关联正式租户、工商注册进度等" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="合同附件">
              <FileUpload v-model="formFiles" biz-type="simple_contract" :biz-id="form.id || undefined"
                          accept=".pdf,.doc,.docx,.xls,.xlsx,.jpg,.jpeg,.png,.zip,.rar" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 简易合同附件独立查看/上传弹窗 -->
    <el-dialog v-model="attachDialog.visible" :title="`简易合同附件 · ${attachDialog.row?.roomCode || ''} ${attachDialog.row?.companyName || ''}`"
               width="580px" @closed="onAttachClosed">
      <el-alert type="info" :closable="false" show-icon>
        可上传简易合同扫描件、营业执照、场地使用证明等资料；上传即保存，点击下方可下载查看。
      </el-alert>
      <div class="attach-uploader">
        <FileUpload v-model="attachFiles" biz-type="simple_contract" :biz-id="attachDialog.row?.id"
                    accept=".pdf,.doc,.docx,.xls,.xlsx,.jpg,.jpeg,.png,.zip,.rar" />
      </div>
      <div v-if="attachFiles.length" class="attach-downloads">
        <div v-for="file in attachFiles" :key="file.id" class="attach-download">
          <span class="attach-name" :title="file.originalName || file.name">{{ file.originalName || file.name }}</span>
          <el-button link type="primary" :loading="downloadingId === file.id" @click="downloadFile(file)">下载查看</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { simpleContractApi } from '@/api/contract'
import { fileApi } from '@/api/file'
import { startFileDownload } from '@/utils/fileDownload'
import { money } from '@/utils/format'
import FileUpload from '@/components/FileUpload.vue'

const DEFAULT_FLOORS = [1, 2, 3, 4, 5, 6, 7, 8]
const loading = ref(false)
const list = ref([])
const floorFilter = ref('all')
const keyword = ref('')
const statusFilter = ref(null)

const floorTabs = computed(() => {
  const set = new Set(DEFAULT_FLOORS)
  list.value.forEach((r) => { if (r.floorNo) set.add(Number(r.floorNo)) })
  return [...set].sort((a, b) => a - b)
})
const floorChoices = computed(() => {
  const set = new Set([...DEFAULT_FLOORS, 9, 10, 11, 12])
  list.value.forEach((r) => { if (r.floorNo) set.add(Number(r.floorNo)) })
  return [...set].sort((a, b) => a - b)
})
const floorCounts = computed(() => {
  const map = {}
  for (const r of list.value) map[r.floorNo] = (map[r.floorNo] || 0) + 1
  return map
})

const activeCount = computed(() => list.value.filter((r) => r.status === 1).length)
const usedFloorCount = computed(() => new Set(list.value.map((r) => r.floorNo)).size)
const withAttachCount = computed(() => list.value.filter((r) => Number(r.attachmentCount) > 0).length)

const viewRows = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return list.value.filter((r) => {
    if (floorFilter.value !== 'all' && Number(r.floorNo) !== Number(floorFilter.value)) return false
    if (statusFilter.value != null && Number(r.status) !== Number(statusFilter.value)) return false
    if (!kw) return true
    return `${r.roomCode || ''} ${r.companyName || ''} ${r.contractNo || ''} ${r.contactName || ''} ${r.creditCode || ''}`
      .toLowerCase().includes(kw)
  })
})

const floorGroups = computed(() => {
  const map = new Map()
  for (const r of viewRows.value) {
    const f = Number(r.floorNo || 1)
    if (!map.has(f)) map.set(f, [])
    map.get(f).push(r)
  }
  // 当选中单个楼层且该楼层暂时为空时，也展示快捷新增头
  if (floorFilter.value !== 'all' && !map.has(Number(floorFilter.value))) {
    map.set(Number(floorFilter.value), [])
  }
  return [...map.entries()].sort((a, b) => a[0] - b[0]).map(([floor, items]) => ({ floor, items }))
})

function selectFloor(f) { floorFilter.value = f }
function statusMeta(v) {
  if (v === 1) return { label: '生效中', type: 'success' }
  if (v === 2) return { label: '已到期', type: 'warning' }
  return { label: '已注销', type: 'info' }
}
function chipClass(v) {
  if (v === 1) return 'is-active'
  if (v === 2) return 'is-expired'
  return 'is-closed'
}

async function load() {
  loading.value = true
  try {
    list.value = await simpleContractApi.list() || []
  } finally {
    loading.value = false
  }
}

const dialog = reactive({ visible: false, saving: false })
const form = reactive({})
const formFiles = ref([])

async function fetchNextRoom(floorNo) {
  try {
    const res = await simpleContractApi.nextRoom(floorNo)
    form.roomCode = res.roomCode || `${floorNo}01`
    form.contractNo = res.contractNo || ''
    form.regAddress = res.regAddress || ''
  } catch { /* ignore */ }
}

function onFloorChange(val) {
  if (!form.id) fetchNextRoom(val)
}

async function openDialog(row, presetFloor = 1) {
  formFiles.value = []
  if (row) {
    Object.assign(form, { ...row, annualFee: Number(row.annualFee || 0) })
    dialog.visible = true
    try { formFiles.value = await fileApi.list('simple_contract', row.id) || [] } catch { /* ignore */ }
  } else {
    const f = Number(presetFloor || 1)
    Object.assign(form, {
      id: null, floorNo: f, roomCode: '', contractNo: '', companyName: '', creditCode: '',
      contactName: '', contactPhone: '', regAddress: '', startDate: '', endDate: '',
      annualFee: 0, status: 1, remark: ''
    })
    dialog.visible = true
    await fetchNextRoom(f)
  }
}

async function save() {
  if (!form.floorNo) return ElMessage.warning('请选择所属楼层')
  if (!form.roomCode?.trim()) return ElMessage.warning('请填写自编号房号')
  if (!form.companyName?.trim()) return ElMessage.warning('请填写注册公司名称')
  dialog.saving = true
  try {
    const saved = form.id
      ? await simpleContractApi.update({ ...form })
      : await simpleContractApi.add({ ...form })
    const savedId = saved?.id || form.id
    const pendingIds = (formFiles.value || []).filter((f) => f && f.id && !f.bizId).map((f) => f.id)
    if (savedId && pendingIds.length) {
      await fileApi.attachVerified('simple_contract', savedId, pendingIds)
    }
    ElMessage.success('保存成功')
    dialog.visible = false
    await load()
  } finally {
    dialog.saving = false
  }
}

async function remove(row) {
  await simpleContractApi.remove(row.id)
  ElMessage.success('已删除')
  await load()
}

const attachDialog = reactive({ visible: false, row: null })
const attachFiles = ref([])
const downloadingId = ref(null)

async function openAttachments(row) {
  attachDialog.row = row
  attachDialog.visible = true
  attachFiles.value = []
  try {
    attachFiles.value = await fileApi.list('simple_contract', row.id) || []
  } catch {
    ElMessage.error('附件加载失败')
  }
}
function onAttachClosed() {
  if (attachDialog.row) attachDialog.row.attachmentCount = attachFiles.value.length
  attachFiles.value = []
  attachDialog.row = null
}
async function downloadFile(file) {
  downloadingId.value = file.id
  try {
    await startFileDownload(file.id, file.originalName || file.name || '简易合同附件')
  } catch {
    ElMessage.error('下载失败')
  } finally {
    downloadingId.value = null
  }
}

onMounted(load)
</script>

<style scoped>
.summary-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 10px; margin-bottom: 12px; }
.summary-item { padding: 11px 14px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; display: flex; flex-direction: column; gap: 4px; background: var(--el-fill-color-blank); }
.summary-item span { font-size: 12px; color: var(--el-text-color-secondary); }
.summary-item strong { font-size: 18px; font-variant-numeric: tabular-nums; }
.summary-item.ok strong { color: var(--el-color-success); }

.floor-bar { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; }
.floor-pill {
  display: inline-flex; align-items: center; gap: 6px; padding: 6px 12px;
  border-radius: 999px; border: 1px solid var(--el-border-color); background: var(--el-fill-color-blank);
  color: var(--el-text-color-regular); font-size: 13px; cursor: pointer; transition: all .15s;
}
.floor-pill:hover { border-color: var(--el-color-primary); color: var(--el-color-primary); }
.floor-pill.active { background: var(--el-color-primary); border-color: var(--el-color-primary); color: #fff; font-weight: 600; }
.pill-count { font-size: 11px; padding: 1px 6px; border-radius: 999px; background: rgba(0, 0, 0, .08); }
.floor-pill.active .pill-count { background: rgba(255, 255, 255, .24); }

.floor-board { display: flex; flex-direction: column; gap: 10px; margin-bottom: 14px; }
.floor-group { padding: 10px 14px; border: 1px solid var(--el-border-color-lighter); border-radius: 10px; background: var(--el-fill-color-blank); }
.floor-group__head { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-bottom: 8px; flex-wrap: wrap; }
.floor-group__title { display: flex; align-items: baseline; gap: 8px; }
.muted { font-size: 12px; color: var(--el-text-color-secondary); }
.room-wall { display: flex; flex-wrap: wrap; gap: 8px; }
.room-chip {
  display: inline-flex; align-items: center; gap: 6px; max-width: 240px;
  padding: 5px 10px; border-radius: 8px; border: 1px solid var(--el-border-color);
  background: var(--el-fill-color-lighter); cursor: pointer; font-size: 12px; transition: all .15s;
}
.room-chip:hover { transform: translateY(-1px); border-color: var(--el-color-primary); }
.room-chip.is-active { background: var(--el-color-success-light-9); border-color: var(--el-color-success-light-5); }
.room-chip.is-expired { background: var(--el-color-warning-light-9); border-color: var(--el-color-warning-light-5); }
.room-chip.is-closed { opacity: .68; }
.room-chip__code { font-weight: 700; color: var(--el-color-primary); flex-shrink: 0; }
.room-chip__name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--el-text-color-primary); }
.room-chip__att { font-size: 11px; color: var(--el-color-success); flex-shrink: 0; }

.toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; flex-wrap: wrap; }
.toolbar-tip { font-size: 12px; color: var(--el-text-color-secondary); }
.room-code { color: var(--el-color-primary); font-variant-numeric: tabular-nums; }
.room-input-row { display: flex; gap: 8px; width: 100%; }
.attach-uploader { margin-top: 14px; }
.attach-downloads { margin-top: 12px; border-top: 1px solid var(--el-border-color-lighter); }
.attach-download { display: flex; align-items: center; gap: 10px; padding: 8px 0; }
.attach-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
