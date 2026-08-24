<template>
  <div class="page-container">
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width:120px">
            <el-option v-for="(v, k) in statusMap" :key="k" :label="v" :value="Number(k)" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.type" placeholder="全部" clearable style="width:100px">
            <el-option label="Bug" :value="1" /><el-option label="建议" :value="2" /><el-option label="其他" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>
    <div class="table-card">
      <SuggestionWall v-loading="loading" :items="list" :type-map="typeMap" :status-map="statusMap"
                      @open="row => openDetail(row.id)">
        <template #actions="{ item }">
          <span class="note-actions" @click.stop>
            <el-button v-if="item.status < 4" link type="warning" size="small" @click="openStatusDialog(item)">流转</el-button>
            <el-button v-if="item.status <= 3" link size="small" @click="openAssignDialog(item)">指派</el-button>
          </span>
        </template>
      </SuggestionWall>
      <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                     :total="total" v-model:current-page="query.page"
                     v-model:page-size="query.size" :page-sizes="[10,20,50]" @change="load" />
    </div>

    <!-- 详情 -->
    <el-dialog v-model="detailVisible" title="建议详情" width="650px">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="标题" :span="2">{{ detail.suggestion.title }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ typeMap[detail.suggestion.type] }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusMap[detail.suggestion.status] }}</el-descriptions-item>
          <el-descriptions-item label="提交人">ID: {{ detail.suggestion.userId }}</el-descriptions-item>
          <el-descriptions-item label="来源页面">{{ detail.suggestion.sourceUrl || '-' }}</el-descriptions-item>
          <el-descriptions-item label="详细说明" :span="2">{{ detail.suggestion.content || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="detail.images.length" style="margin-top:12px">
          <el-image v-for="img in detail.images" :key="img.id"
            :src="srcFor(img.fileId)"
            :preview-src-list="detail.images.map(i => srcFor(i.fileId)).filter(Boolean)"
            style="width:100px;height:100px;margin-right:8px" fit="cover" />
        </div>
        <el-timeline style="margin-top:16px">
          <el-timeline-item v-for="log in detail.logs" :key="log.id" :timestamp="log.createdAt" placement="top">
            {{ log.operatorName }} — {{ log.action }}
            <template v-if="log.remark"> | {{ log.remark }}</template>
          </el-timeline-item>
        </el-timeline>
      </template>
    </el-dialog>

    <!-- 状态流转 -->
    <el-dialog v-model="statusDialogVisible" title="状态流转" width="400px">
      <el-form label-width="80px">
        <el-form-item label="目标状态">
          <el-select v-model="statusForm.status">
            <el-option v-for="s in allowedTransitions" :key="s" :label="statusMap[s]" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注" :required="statusForm.status === 6">
          <el-input v-model="statusForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="statusDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="doChangeStatus">确认</el-button>
      </template>
    </el-dialog>

    <!-- 指派 -->
    <el-dialog v-model="assignVisible" title="指派处理人" width="360px">
      <el-form label-width="80px">
        <el-form-item label="处理人ID">
          <el-input-number v-model="assigneeId" :min="1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" @click="doAssign">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { suggestionApi } from '@/api/suggestion'
import { useAuthImage } from '@/utils/authImage'
import SuggestionWall from './SuggestionWall.vue'

// 同 MySuggestions:裸 <img src> 无 token 会 401,必须换 blob
const { srcFor, resolveAll, revokeAll } = useAuthImage()

const typeMap = { 1: 'Bug', 2: '建议', 3: '其他' }
const statusMap = { 1: '待处理', 2: '已确认', 3: '处理中', 4: '已解决', 5: '已采纳', 6: '已关闭' }
const statusStyle = { 1: 'info', 2: 'warning', 3: '', 4: 'success', 5: 'success', 6: 'info' }
const transitions = { 1: [2, 6], 2: [3, 6], 3: [4, 5, 6] }

const query = reactive({ status: null, type: null, page: 1, size: 20 })
const list = ref([])
const loading = ref(false)
const total = ref(0)

const detailVisible = ref(false)
const detail = ref(null)
const statusDialogVisible = ref(false)
const statusForm = reactive({ id: null, status: null, remark: '' })
const currentRow = ref(null)
const allowedTransitions = computed(() => transitions[currentRow.value?.status] || [])
const assignVisible = ref(false)
const assigneeId = ref(null)
const assignTargetId = ref(null)

async function load() {
  loading.value = true
  try {
    const res = await suggestionApi.manageList(query)
    list.value = res.records || []
    total.value = res.total || 0
  } finally { loading.value = false }
}

function resetQuery() { query.status = null; query.type = null; query.page = 1; load() }

async function openDetail(id) {
  const res = await suggestionApi.manageDetail(id)
  detail.value = res
  detailVisible.value = true
  await resolveAll((res.images || []).map(i => i.fileId))
}

function openStatusDialog(row) {
  currentRow.value = row
  statusForm.id = row.id
  statusForm.status = null
  statusForm.remark = ''
  statusDialogVisible.value = true
}

async function doChangeStatus() {
  if (!statusForm.status) { ElMessage.warning('请选择目标状态'); return }
  if (statusForm.status === 6 && !statusForm.remark) { ElMessage.warning('关闭必须填写原因'); return }
  await suggestionApi.changeStatus(statusForm.id, { status: statusForm.status, remark: statusForm.remark })
  ElMessage.success('状态已更新')
  statusDialogVisible.value = false
  load()
}

function openAssignDialog(row) { assignTargetId.value = row.id; assigneeId.value = null; assignVisible.value = true }

async function doAssign() {
  if (!assigneeId.value) { ElMessage.warning('请输入处理人ID'); return }
  await suggestionApi.assign(assignTargetId.value, { assigneeId: assigneeId.value })
  ElMessage.success('指派成功')
  assignVisible.value = false
  load()
}

onMounted(load)
onUnmounted(revokeAll)
</script>
