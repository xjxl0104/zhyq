<template>
  <div class="page-container">
    <el-tabs v-model="activeTab" class="msg-tabs">
      <el-tab-pane label="消息模板" name="template">
        <!-- 查询区 -->
        <div class="search-bar">
          <el-form :inline="true" :model="tplQuery">
            <el-form-item label="编码">
              <el-input v-model="tplQuery.code" placeholder="请输入编码" clearable style="width: 160px" />
            </el-form-item>
            <el-form-item label="名称">
              <el-input v-model="tplQuery.name" placeholder="请输入名称" clearable style="width: 160px" />
            </el-form-item>
            <el-form-item label="渠道">
              <el-select v-model="tplQuery.channel" placeholder="全部" clearable style="width: 130px">
                <el-option v-for="c in channelOptions" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadTemplates"><el-icon><Search /></el-icon>查询</el-button>
              <el-button @click="resetTplQuery">重置</el-button>
            </el-form-item>
          </el-form>
        </div>

        <div class="table-card">
          <div class="toolbar">
            <el-button type="primary" @click="openTplDialog()"><el-icon><Plus /></el-icon>新增模板</el-button>
          </div>
          <el-table :data="tplList" v-loading="tplLoading" border stripe>
            <el-table-column type="index" label="序号" width="70" />
            <el-table-column prop="code" label="编码" min-width="140" />
            <el-table-column prop="name" label="名称" min-width="140" />
            <el-table-column label="渠道" width="100">
              <template #default="{ row }"><el-tag>{{ row.channel }}</el-tag></template>
            </el-table-column>
            <el-table-column label="内容" min-width="220">
              <template #default="{ row }">
                <el-tooltip :content="row.content" placement="top">
                  <span class="ellipsis">{{ row.content }}</span>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openTplDialog(row)">编辑</el-button>
                <el-button link type="success" @click="openSendDialog(row)">发送测试</el-button>
                <el-popconfirm title="确认删除?" @confirm="removeTemplate(row.id)">
                  <template #reference><el-button link type="danger">删除</el-button></template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                         :total="tplTotal" v-model:current-page="tplQuery.pageNo"
                         v-model:page-size="tplQuery.pageSize" :page-sizes="[10,20,50]" @change="loadTemplates" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="发送记录" name="record">
        <div class="search-bar">
          <el-form :inline="true" :model="recQuery">
            <el-form-item label="接收人">
              <el-input v-model="recQuery.receiver" placeholder="请输入接收人" clearable style="width: 160px" />
            </el-form-item>
            <el-form-item label="渠道">
              <el-select v-model="recQuery.channel" placeholder="全部" clearable style="width: 130px">
                <el-option v-for="c in channelOptions" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="recQuery.status" placeholder="全部" clearable style="width: 120px">
                <el-option label="成功" :value="1" />
                <el-option label="失败" :value="2" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadRecords"><el-icon><Search /></el-icon>查询</el-button>
              <el-button @click="resetRecQuery">重置</el-button>
            </el-form-item>
          </el-form>
        </div>

        <div class="table-card">
          <el-table :data="recList" v-loading="recLoading" border stripe>
            <el-table-column type="index" label="序号" width="70" />
            <el-table-column prop="templateCode" label="模板编码" min-width="140" />
            <el-table-column prop="receiver" label="接收人" min-width="120" />
            <el-table-column label="渠道" width="100">
              <template #default="{ row }"><el-tag>{{ row.channel }}</el-tag></template>
            </el-table-column>
            <el-table-column label="内容" min-width="220">
              <template #default="{ row }">
                <el-tooltip :content="row.content" placement="top">
                  <span class="ellipsis">{{ row.content }}</span>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tooltip v-if="row.status === 2 && row.failReason" :content="row.failReason" placement="top">
                  <el-tag type="danger">失败</el-tag>
                </el-tooltip>
                <el-tag v-else :type="row.status === 1 ? 'success' : 'danger'">
                  {{ row.status === 1 ? '成功' : '失败' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="sendTime" label="发送时间" width="170" />
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-popconfirm title="确认删除?" @confirm="removeRecord(row.id)">
                  <template #reference><el-button link type="danger">删除</el-button></template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                         :total="recTotal" v-model:current-page="recQuery.pageNo"
                         v-model:page-size="recQuery.pageSize" :page-sizes="[10,20,50]" @change="loadRecords" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 模板表单弹窗 -->
    <el-dialog v-model="tplDialog.visible" :title="tplDialog.title" width="560px">
      <el-form :model="tplForm" label-width="90px" ref="tplFormRef" :rules="tplRules">
        <el-form-item label="编码" prop="code">
          <el-input v-model="tplForm.code" :disabled="!!tplForm.id" placeholder="如 BILL_REMIND" />
        </el-form-item>
        <el-form-item label="名称" prop="name"><el-input v-model="tplForm.name" /></el-form-item>
        <el-form-item label="渠道" prop="channel">
          <el-select v-model="tplForm.channel" style="width: 100%">
            <el-option v-for="c in channelOptions" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input v-model="tplForm.content" type="textarea" :rows="3" placeholder="变量用 {var} 表示" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="tplForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="附件">
          <FileUpload v-model="attachFiles" biz-type="message" :biz-id="tplForm.id" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tplDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitTemplate">确定</el-button>
      </template>
    </el-dialog>

    <!-- 发送测试弹窗 -->
    <el-dialog v-model="sendDialog.visible" title="发送测试" width="480px">
      <el-form :model="sendForm" label-width="90px">
        <el-form-item label="模板编码"><el-input :model-value="sendDialog.template?.code" disabled /></el-form-item>
        <el-form-item label="接收人" required>
          <el-input v-model="sendForm.receiver" placeholder="请输入接收人" />
        </el-form-item>
        <el-form-item v-for="v in sendDialog.vars" :key="v" :label="v">
          <el-input v-model="sendForm.vars[v]" :placeholder="`请输入 ${v}`" />
        </el-form-item>
      </el-form>
      <div v-if="sendResult" class="send-result">
        <div class="send-result-title">发送成功,内容:</div>
        <div class="send-result-content">{{ sendResult }}</div>
      </div>
      <template #footer>
        <el-button @click="sendDialog.visible = false">关闭</el-button>
        <el-button type="primary" @click="submitSend">发送</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { msgApi } from '@/api/system'
import { fileApi } from '@/api/file'
import FileUpload from '@/components/FileUpload.vue'

const channelOptions = ['站内信', '短信', '邮件']
const activeTab = ref('template')

// ===== 消息模板 =====
const tplLoading = ref(false)
const tplList = ref([])
const tplTotal = ref(0)
const tplQuery = reactive({ pageNo: 1, pageSize: 10, code: '', name: '', channel: null })

async function loadTemplates() {
  tplLoading.value = true
  try {
    const res = await msgApi.templatePage(tplQuery)
    tplList.value = res.records
    tplTotal.value = res.total
  } finally {
    tplLoading.value = false
  }
}
function resetTplQuery() {
  Object.assign(tplQuery, { pageNo: 1, code: '', name: '', channel: null })
  loadTemplates()
}

const tplFormRef = ref()
const tplDialog = reactive({ visible: false, title: '' })
const emptyTplForm = () => ({ id: null, code: '', name: '', channel: '站内信', content: '', status: 1 })
const tplForm = reactive(emptyTplForm())
const attachFiles = ref([])
const tplRules = {
  code: [{ required: true, message: '请输入编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  channel: [{ required: true, message: '请选择渠道', trigger: 'change' }]
}
async function openTplDialog(row) {
  tplDialog.visible = true
  tplDialog.title = row ? '编辑模板' : '新增模板'
  attachFiles.value = []
  if (row) {
    Object.assign(tplForm, row)
    try { attachFiles.value = await fileApi.list('message', row.id) } catch (e) { /* 忽略 */ }
  } else {
    Object.assign(tplForm, emptyTplForm())
  }
}
async function submitTemplate() {
  await tplFormRef.value.validate()
  let templateId = tplForm.id
  if (tplForm.id) await msgApi.templateUpdate(tplForm)
  else templateId = await msgApi.templateAdd(tplForm)
  const pendingIds = (attachFiles.value || []).filter(f => f && f.id && !f.bizId).map(f => f.id)
  if (templateId && pendingIds.length) {
    try { await fileApi.attach('message', templateId, pendingIds) } catch (e) { /* 忽略,不阻断保存 */ }
  }
  ElMessage.success('保存成功')
  tplDialog.visible = false
  loadTemplates()
}
async function removeTemplate(id) {
  await msgApi.templateRemove(id)
  ElMessage.success('删除成功')
  loadTemplates()
}

// ===== 发送测试 =====
const sendDialog = reactive({ visible: false, template: null, vars: [] })
const sendForm = reactive({ receiver: '', vars: {} })
const sendResult = ref('')
function openSendDialog(row) {
  sendDialog.visible = true
  sendDialog.template = row
  const matches = [...(row.content || '').matchAll(/\{(\w+)\}/g)]
  const varNames = [...new Set(matches.map(m => m[1]))]
  sendDialog.vars = varNames
  sendForm.receiver = ''
  sendForm.vars = {}
  varNames.forEach(v => { sendForm.vars[v] = '' })
  sendResult.value = ''
}
async function submitSend() {
  if (!sendForm.receiver) {
    ElMessage.warning('请输入接收人')
    return
  }
  const content = await msgApi.send({
    templateCode: sendDialog.template.code,
    receiver: sendForm.receiver,
    vars: sendForm.vars
  })
  sendResult.value = content
  ElMessage.success('发送成功')
  if (activeTab.value === 'record') loadRecords()
}

// ===== 发送记录 =====
const recLoading = ref(false)
const recList = ref([])
const recTotal = ref(0)
const recQuery = reactive({ pageNo: 1, pageSize: 10, receiver: '', channel: null, status: null })

async function loadRecords() {
  recLoading.value = true
  try {
    const res = await msgApi.recordPage(recQuery)
    recList.value = res.records
    recTotal.value = res.total
  } finally {
    recLoading.value = false
  }
}
function resetRecQuery() {
  Object.assign(recQuery, { pageNo: 1, receiver: '', channel: null, status: null })
  loadRecords()
}
async function removeRecord(id) {
  await msgApi.recordRemove(id)
  ElMessage.success('删除成功')
  loadRecords()
}

onMounted(() => {
  loadTemplates()
  loadRecords()
})
</script>

<style scoped>
.msg-tabs { margin-bottom: 8px; }
.pager { margin-top: 16px; justify-content: flex-end; }
.ellipsis { display: inline-block; max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; vertical-align: bottom; }
.send-result { margin-top: 8px; padding: 10px 12px; background: var(--bg-hover, #f5f7fa); border-radius: 6px; }
.send-result-title { font-size: 12px; color: var(--text-secondary); margin-bottom: 4px; }
.send-result-content { font-size: 13px; color: var(--text-title); word-break: break-all; }
</style>
