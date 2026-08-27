<template>
  <div class="page-container">
    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="(label, val) in statusMap" :key="val" :label="label" :value="Number(val)" />
          </el-select>
        </el-form-item>
        <el-form-item label="级别">
          <el-select v-model="query.level" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="(label, val) in levelMap" :key="val" :label="label" :value="Number(val)" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 表格区 -->
    <div class="table-card">
      <HighlightNotice
        :visible="isHighlighting"
        :highlight-id="highlightId"
        label="告警"
        @clear="clearHighlight"
      />
      <div class="toolbar">
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增告警</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe :row-class-name="rowClass">
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="alarmType" label="告警类型" min-width="130" />
        <el-table-column label="级别" width="90">
          <template #default="{ row }">
            <el-tag :type="levelTagType(row.level)">{{ levelMap[row.level] || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="location" label="位置" min-width="140" />
        <el-table-column prop="content" label="内容" min-width="180" show-overflow-tooltip />
        <el-table-column prop="alarmTime" label="告警时间" width="170" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusMap[row.status] || '未知' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="row.status !== 1" @click="confirm(row.id)">确认</el-button>
            <el-button link type="warning" :disabled="row.status === 5" @click="close(row.id)">关闭</el-button>
            <el-badge v-if="countOf(row.id)" :value="countOf(row.id)" class="order-badge">
              <el-button link type="success" @click="openRelated(row)">关联工单</el-button>
            </el-badge>
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除?" @confirm="remove(row.id)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                     :total="total" v-model:current-page="query.pageNo"
                     v-model:page-size="query.pageSize" :page-sizes="[10,20,50]" @change="load" />
    </div>

    <!-- 表单弹窗 -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="520px">
      <el-form :model="form" label-width="90px" ref="formRef" :rules="rules">
        <el-form-item label="告警类型" prop="alarmType"><el-input v-model="form.alarmType" /></el-form-item>
        <el-form-item label="设备ID"><el-input-number v-model="form.deviceId" :min="0" /></el-form-item>
        <el-form-item label="级别">
          <el-select v-model="form.level" style="width: 100%">
            <el-option v-for="(label, val) in levelMap" :key="val" :label="label" :value="Number(val)" />
          </el-select>
        </el-form-item>
        <el-form-item label="位置"><el-input v-model="form.location" /></el-form-item>
        <el-form-item label="内容"><el-input v-model="form.content" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="告警时间">
          <el-date-picker v-model="form.alarmTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option v-for="(label, val) in statusMap" :key="val" :label="label" :value="Number(val)" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submit">确定</el-button>
      </template>
    </el-dialog>

    <RelatedOrderDrawer
      v-model:visible="drawer.visible"
      :loading="drawer.loading"
      :list="drawer.list"
      source-type="ALARM"
      @goto="gotoOrder"
    />
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { alarmApi } from '@/api/iot'
import HighlightNotice from '@/components/HighlightNotice.vue'
import RelatedOrderDrawer from '@/components/RelatedOrderDrawer.vue'
import { useHighlightFilter, useRelatedOrders } from '@/composables/useSourceLink'

// 1新建 2已确认 3处理中 4已恢复 5已关闭 6误报
const statusMap = { 1: '新建', 2: '已确认', 3: '处理中', 4: '已恢复', 5: '已关闭', 6: '误报' }
const levelMap = { 1: '低', 2: '中', 3: '高' }

function statusTagType(status) {
  const map = { 1: 'warning', 2: 'primary', 3: 'primary', 4: 'success', 5: 'info', 6: 'info' }
  return map[status] || 'info'
}
function levelTagType(level) {
  const map = { 1: 'info', 2: 'warning', 3: 'danger' }
  return map[level] || 'info'
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, status: null, level: null, id: null })

async function load() {
  loading.value = true
  try {
    const res = await alarmApi.page(query)
    list.value = res.records
    total.value = res.total
    loadCounts(res.records)
  } finally {
    loading.value = false
  }
}
// 工单来源联动:告警是唯一自动转单的来源(RuleActionExecutor),
// 反查用得最多。immediate:false — 本页 onMounted 已有 load(),避免请求两次
const { highlightId, isHighlighting, clearHighlight, rowClass, applyHighlight } =
  useHighlightFilter(query, load, { immediate: false })
const { countOf, loadCounts, drawer, openRelated, gotoOrder } = useRelatedOrders('ALARM')

function reset() {
  // id 一并清掉,否则从工单跳来后点重置会仍被定位条件锁住
  Object.assign(query, { pageNo: 1, status: null, level: null, id: null })
  highlightId.value = null
  load()
}

async function confirm(id) {
  await alarmApi.confirm(id)
  ElMessage.success('已确认')
  load()
}
async function close(id) {
  await alarmApi.close(id)
  ElMessage.success('已关闭')
  load()
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const form = reactive({ id: null, alarmType: '', deviceId: null, level: 2, location: '', content: '', alarmTime: null, status: 1 })
const rules = {
  alarmType: [{ required: true, message: '请输入告警类型', trigger: 'blur' }]
}

function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑告警' : '新增告警'
  if (row) Object.assign(form, row)
  else Object.assign(form, { id: null, alarmType: '', deviceId: null, level: 2, location: '', content: '', alarmTime: null, status: 1 })
}
async function submit() {
  await formRef.value.validate()
  if (form.id) await alarmApi.update(form)
  else await alarmApi.add(form)
  ElMessage.success('保存成功')
  dialog.visible = false
  load()
}
async function remove(id) {
  await alarmApi.remove(id)
  ElMessage.success('删除成功')
  load()
}

onMounted(() => {
  // 先落定位条件再取数,顺序反了会先查全量再被覆盖
  applyHighlight()
  load()
})
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
.toolbar { margin-bottom: 12px; }
/* 徽标默认会把数字盖在按钮文字上,右移一点 */
.order-badge :deep(.el-badge__content) { transform: translate(6px, -6px); }
/* 定位到的行加底色。scoped 样式进不到 el-table 内部,要 :deep */
:deep(.source-highlight-row) > td { background: var(--el-color-warning-light-9) !important; }
</style>
