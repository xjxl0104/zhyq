<template>
  <div class="page-container">
    <!-- 合同档案库:所有合同都在册,不只是走完生命周期的。
         执行中的合同也要能查到,否则新签约的一批在这里永远是空白 -->
    <el-tabs v-model="activeTab" @tab-change="onTabChange" class="archive-tabs">
      <el-tab-pane label="全部" name="all" />
      <el-tab-pane label="在租中" name="running" />
      <el-tab-pane label="已到期" name="expired" />
      <el-tab-pane label="已终止" name="terminated" />
      <el-tab-pane label="已归档" name="archived" />
    </el-tabs>

    <section v-if="expiryAlerts.length" class="expiry-warning" aria-label="合同到期预警">
      <div class="expiry-warning__heading">
        <span>合同到期预警</span>
        <span>未来两个月内有 {{ expiryAlerts.length }} 份在租合同到期，请提前续签或办理退租。</span>
      </div>
      <div class="expiry-warning__items">
        <div v-for="item in expiryAlerts" :key="item.id" class="expiry-warning__item">
          <strong>{{ item.code }}</strong>
          <span>{{ item.tenantName || '未关联租客' }}</span>
          <span>{{ item.endDate }} 到期 · 剩余 {{ item.daysRemaining }} 天</span>
          <el-button link type="warning" @click="viewExpiryContract(item)">查看</el-button>
        </div>
      </div>
    </section>

    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="合同编号">
          <el-input v-model="query.code" placeholder="请输入合同编号" clearable style="width: 200px" />
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
        <el-button @click="exportContracts"><el-icon><Download /></el-icon>导出合同台账</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column prop="code" label="合同编号" min-width="160" />
        <!-- 与合同列表同口径:后端已填好 tenantName,页面上不再露裸 ID -->
        <el-table-column prop="tenantName" label="租客" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.tenantName || `租客 #${row.tenantRefId ?? '-'}` }}</template>
        </el-table-column>
        <el-table-column label="起止日期" width="220">
          <template #default="{ row }">{{ row.startDate || '-' }} ~ {{ row.endDate || '-' }}</template>
        </el-table-column>
        <el-table-column prop="terminateDate" label="退租时间" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openAttachments(row)">合同附件</el-button>
            <!-- 与后端守卫同口径:仅已到期(8)/已终止(9)可归档,执行中的不给按钮 -->
            <el-popconfirm v-if="[8, 9].includes(row.status)" title="确认归档该合同?" @confirm="archive(row.id)">
              <template #reference><el-button link type="primary">归档</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                     :total="total" v-model:current-page="query.pageNo"
                     v-model:page-size="query.pageSize" :page-sizes="[10,20,50]" @change="load" />
    </div>

    <el-dialog v-model="attachmentDialog.visible" :title="attachmentDialog.title" width="560px" @closed="clearAttachments">
      <el-alert type="info" :closable="false" show-icon>
        可上传合同扫描件、补充协议等附件；上传后点击文件名即可下载，便于查阅合同原件。
      </el-alert>
      <div class="attachment-uploader">
        <FileUpload v-model="attachmentFiles" biz-type="contract" :biz-id="attachmentDialog.contract?.id"
                    accept=".pdf,.doc,.docx,.xls,.xlsx,.jpg,.jpeg,.png,.zip,.rar" />
      </div>
      <div v-if="attachmentFiles.length" class="attachment-downloads">
        <div v-for="file in attachmentFiles" :key="file.id" class="attachment-download">
          <span class="attachment-name">{{ file.originalName || file.name || '合同附件' }}</span>
          <el-button link type="primary" :loading="downloadingFileId === file.id" @click="downloadAttachment(file)">下载查看</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { contractApi } from '@/api/contract'
import { fileApi } from '@/api/file'
import { startFileDownload } from '@/utils/fileDownload'
import FileUpload from '@/components/FileUpload.vue'

// null = 不按状态过滤(全部)。档案库要能查到所有合同,不只走完生命周期的那些
const tabStatus = { all: null, running: 5, expired: 8, terminated: 9, archived: 10 }
const activeTab = ref('all')

const statusMap = {
  1: { label: '草稿', type: 'info' },
  2: { label: '待审核', type: 'warning' },
  3: { label: '待签署', type: 'warning' },
  4: { label: '待执行', type: 'warning' },
  5: { label: '在租中', type: 'success' },
  6: { label: '变更中', type: 'warning' },
  7: { label: '退租中', type: 'warning' },
  8: { label: '已到期', type: 'warning' },
  9: { label: '已终止', type: 'danger' },
  10: { label: '已归档', type: 'info' }
}
function statusText(v) { return statusMap[v]?.label ?? v }
function statusType(v) { return statusMap[v]?.type ?? 'info' }

const loading = ref(false)
const list = ref([])
const total = ref(0)
const expiryAlerts = ref([])
const query = reactive({ pageNo: 1, pageSize: 10, code: '', status: tabStatus[activeTab.value] })

async function load() {
  loading.value = true
  try {
    const res = await contractApi.page(query)
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
  Object.assign(query, { pageNo: 1, code: '', status: tabStatus[activeTab.value] })
  load()
}

async function loadExpiryAlerts() {
  try {
    expiryAlerts.value = await contractApi.expiryAlerts()
  } catch (e) {
    // 预警加载失败不影响合同档案正常使用，避免空白页。
    expiryAlerts.value = []
  }
}
function viewExpiryContract(item) {
  activeTab.value = 'running'
  Object.assign(query, { pageNo: 1, code: item.code, status: tabStatus.running })
  load()
}

const attachmentFiles = ref([])
const downloadingFileId = ref(null)
const attachmentDialog = reactive({ visible: false, title: '合同附件', contract: null })
async function openAttachments(row) {
  attachmentDialog.contract = row
  attachmentDialog.title = `合同附件 · ${row.code || '-'}`
  attachmentDialog.visible = true
  attachmentFiles.value = []
  try {
    attachmentFiles.value = await fileApi.list('contract', row.id)
  } catch (e) {
    ElMessage.error('附件加载失败，请稍后重试')
  }
}
function clearAttachments() {
  attachmentFiles.value = []
  attachmentDialog.contract = null
}

async function downloadAttachment(file) {
  if (!file?.id) {
    ElMessage.warning('附件尚未上传完成，请稍后重试')
    return
  }
  downloadingFileId.value = file.id
  try {
    await startFileDownload(file.id, file.originalName || file.name || '合同附件')
    ElMessage.success('已交给浏览器下载，请在下载列表查看进度')
  } catch (e) {
    ElMessage.error('附件下载失败，请稍后重试')
  } finally {
    downloadingFileId.value = null
  }
}

async function exportContracts() {
  const response = await contractApi.export({ code: query.code, status: query.status })
  const url = URL.createObjectURL(response.data)
  const link = document.createElement('a')
  link.href = url
  link.download = '合同档案.xlsx'
  link.click()
  URL.revokeObjectURL(url)
}

async function archive(id) {
  await contractApi.archive(id)
  ElMessage.success('归档成功')
  load()
}

onMounted(() => {
  load()
  loadExpiryAlerts()
})
</script>

<style scoped>
.archive-tabs { margin-bottom: 8px; }
.pager { margin-top: 16px; justify-content: flex-end; }
.attachment-uploader { margin-top: 16px; }
.attachment-downloads { margin-top: 12px; border-top: 1px solid var(--el-border-color-lighter); }
.attachment-download { display: flex; align-items: center; gap: 10px; padding: 8px 0; }
.attachment-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.expiry-warning {
  margin-bottom: 16px;
  padding: 14px 18px;
  border: 1px solid var(--el-color-warning-light-7);
  border-radius: 8px;
  background: var(--el-color-warning-light-9);
}
.expiry-warning__heading { display: flex; gap: 12px; align-items: center; color: var(--el-color-warning-dark-2); }
.expiry-warning__heading span:first-child { font-weight: 700; }
.expiry-warning__items { display: flex; flex-wrap: wrap; gap: 8px 16px; margin-top: 10px; }
.expiry-warning__item { display: flex; align-items: center; gap: 8px; color: var(--el-text-color-regular); }
.expiry-warning__item strong { color: var(--el-text-color-primary); }
</style>
