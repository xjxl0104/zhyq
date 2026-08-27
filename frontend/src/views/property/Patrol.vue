<template>
  <div class="page-container">
    <!-- 统计卡片 -->
    <div class="stat-row">
      <div class="stat-card">
        <div class="stat-label">巡更总数</div>
        <div class="stat-value">{{ stats.total || 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">正常</div>
        <div class="stat-value success">{{ stats.normal || 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">异常</div>
        <div class="stat-value danger">{{ stats.abnormal || 0 }}</div>
      </div>
    </div>

    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="路线">
          <el-input v-model="query.routeName" placeholder="请输入巡更路线" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="结果">
          <el-select v-model="query.result" placeholder="全部" clearable style="width: 120px">
            <el-option label="正常" value="正常" />
            <el-option label="异常" value="异常" />
          </el-select>
        </el-form-item>
        <el-form-item label="巡更人">
          <el-input v-model="query.patroller" placeholder="请输入巡更人" clearable style="width: 140px" />
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
        label="巡更记录"
        @clear="clearHighlight"
      />
      <div class="toolbar">
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增巡更</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe :row-class-name="rowClass">
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="routeName" label="路线" min-width="140" show-overflow-tooltip />
        <el-table-column prop="point" label="点位" min-width="120" show-overflow-tooltip />
        <el-table-column prop="patroller" label="巡更人" width="100" />
        <el-table-column prop="patrolTime" label="巡更时间" width="170" />
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <el-tag :type="row.result === '异常' ? 'danger' : 'success'">{{ row.result }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm v-if="row.result === '异常'" title="确认转为工单?" @confirm="doToWorkOrder(row)">
              <template #reference><el-button link type="warning">转工单</el-button></template>
            </el-popconfirm>
            <el-badge v-if="countOf(row.id)" :value="countOf(row.id)" class="order-badge">
              <el-button link type="success" @click="openRelated(row)">关联工单</el-button>
            </el-badge>
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
        <el-form-item label="路线" prop="routeName"><el-input v-model="form.routeName" /></el-form-item>
        <el-form-item label="点位"><el-input v-model="form.point" /></el-form-item>
        <el-form-item label="巡更人"><el-input v-model="form.patroller" /></el-form-item>
        <el-form-item label="巡更时间">
          <el-date-picker v-model="form.patrolTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                          style="width: 100%" placeholder="选择时间" />
        </el-form-item>
        <el-form-item label="结果">
          <el-radio-group v-model="form.result">
            <el-radio value="正常">正常</el-radio>
            <el-radio value="异常">异常</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
        <el-form-item label="附件"><FileUpload v-model="attachFiles" biz-type="patrol" :biz-id="form.id" /></el-form-item>
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
      source-type="PATROL"
      @goto="gotoOrder"
    />
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { patrolApi } from '@/api/property'
import { fileApi } from '@/api/file'
import FileUpload from '@/components/FileUpload.vue'
import HighlightNotice from '@/components/HighlightNotice.vue'
import RelatedOrderDrawer from '@/components/RelatedOrderDrawer.vue'
import { useHighlightFilter, useRelatedOrders } from '@/composables/useSourceLink'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const stats = reactive({ total: 0, normal: 0, abnormal: 0 })
const query = reactive({ pageNo: 1, pageSize: 10, routeName: '', result: null, patroller: '', id: null })

async function load() {
  loading.value = true
  try {
    const res = await patrolApi.page(query)
    list.value = res.records
    total.value = res.total
    loadCounts(res.records)
  } finally {
    loading.value = false
  }
}

// 工单来源联动:正向接收工单跳来的定位,反向查本行派生的工单
// immediate:false — 本页 onMounted 已有 refresh(),定位时机交给它,避免请求两次
const { highlightId, isHighlighting, clearHighlight, rowClass, applyHighlight } =
  useHighlightFilter(query, load, { immediate: false })
const { countOf, loadCounts, drawer, openRelated, gotoOrder } = useRelatedOrders('PATROL')
async function loadStats() {
  Object.assign(stats, await patrolApi.stats())
}
async function refresh() {
  await Promise.all([load(), loadStats()])
}
function reset() {
  // id 一并清掉,否则从工单跳来后点重置会仍被定位条件锁住
  Object.assign(query, { pageNo: 1, routeName: '', result: null, patroller: '', id: null })
  highlightId.value = null
  load()
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const form = reactive({ id: null, routeName: '', point: '', patroller: '', patrolTime: '', result: '正常', remark: '' })
const attachFiles = ref([])
const rules = {
  routeName: [{ required: true, message: '请输入巡更路线', trigger: 'blur' }]
}

async function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑巡更记录' : '新增巡更记录'
  attachFiles.value = []
  if (row) {
    Object.assign(form, row)
    try { attachFiles.value = await fileApi.list('patrol', row.id) } catch (e) { /* 忽略 */ }
  } else {
    Object.assign(form, { id: null, routeName: '', point: '', patroller: '', patrolTime: '', result: '正常', remark: '' })
  }
}
async function submit() {
  await formRef.value.validate()
  let newId = form.id
  if (form.id) await patrolApi.update(form)
  else newId = await patrolApi.add(form)
  const pendingIds = (attachFiles.value || []).filter(f => f && f.id && !f.bizId).map(f => f.id)
  if (newId && pendingIds.length) {
    try { await fileApi.attach('patrol', newId, pendingIds) } catch (e) { /* 忽略,不阻断保存 */ }
  }
  ElMessage.success('保存成功')
  dialog.visible = false
  refresh()
}
async function remove(id) {
  await patrolApi.remove(id)
  ElMessage.success('删除成功')
  refresh()
}
async function doToWorkOrder(row) {
  const code = await patrolApi.toWorkOrder(row.id)
  ElMessage.success(`已生成工单:${code}`)
  refresh()
}

onMounted(() => {
  // 先落定位条件再取数,顺序反了会先查全量再被覆盖
  applyHighlight()
  refresh()
})
</script>

<style scoped>
.stat-row { display: flex; gap: 16px; margin-bottom: 16px; }
.stat-card {
  flex: 1; background: var(--bg-card); border-radius: var(--radius);
  border: 1px solid var(--border); padding: 20px 22px;
  transition: border-color .18s, transform .18s;
}
.stat-label { color: var(--text-secondary); font-size: 13px; margin-bottom: 8px; }
.stat-value { font-size: 24px; font-weight: 600; color: var(--text-title); }
.stat-value.success { color: #16a34a; }
.stat-value.danger { color: #e5484d; }
.pager { margin-top: 16px; justify-content: flex-end; }
/* 徽标默认会把数字盖在按钮文字上,右移一点 */
.order-badge :deep(.el-badge__content) { transform: translate(6px, -6px); }
/* 定位到的行加底色。scoped 样式进不到 el-table 内部,要 :deep */
:deep(.source-highlight-row) > td { background: var(--el-color-warning-light-9) !important; }
</style>
