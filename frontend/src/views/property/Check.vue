<template>
  <div class="page-container">
    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="位置">
          <el-input v-model="query.location" placeholder="请输入检查位置" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="Number(k)" />
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
        label="检查记录"
        @clear="clearHighlight"
      />
      <div class="toolbar">
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增{{ ctype }}检查</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe :row-class-name="rowClass">
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column prop="location" label="位置" min-width="150" show-overflow-tooltip />
        <el-table-column prop="checker" label="检查人" width="100" />
        <el-table-column prop="checkTime" label="检查时间" width="170" />
        <el-table-column label="评分" width="90">
          <template #default="{ row }">
            <span :class="scoreClass(row.score)" style="font-weight: 600">{{ row.score ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="issues" label="问题" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status]?.type || 'info'">
              {{ statusMap[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="270" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm v-if="row.status === 2" title="确认整改完成?" @confirm="doRectify(row)">
              <template #reference><el-button link type="warning">整改完成</el-button></template>
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
        <el-form-item label="位置" prop="location"><el-input v-model="form.location" /></el-form-item>
        <el-form-item label="检查人"><el-input v-model="form.checker" /></el-form-item>
        <el-form-item label="检查时间">
          <el-date-picker v-model="form.checkTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                          style="width: 100%" placeholder="选择时间" />
        </el-form-item>
        <el-form-item label="评分">
          <el-input-number v-model="form.score" :min="1" :max="10" />
        </el-form-item>
        <el-form-item label="问题"><el-input v-model="form.issues" type="textarea" /></el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">合格</el-radio>
            <el-radio :value="2">待整改</el-radio>
            <el-radio :value="3">已整改</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="附件"><FileUpload v-model="attachFiles" biz-type="pm_check" :biz-id="form.id" /></el-form-item>
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
      source-type="CHECK"
      @goto="gotoOrder"
    />
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { checkApi } from '@/api/property'
import { fileApi } from '@/api/file'
import FileUpload from '@/components/FileUpload.vue'
import HighlightNotice from '@/components/HighlightNotice.vue'
import RelatedOrderDrawer from '@/components/RelatedOrderDrawer.vue'
import { useHighlightFilter, useRelatedOrders } from '@/composables/useSourceLink'

const route = useRoute()
const ctype = computed(() => route.meta.ctype || '保洁')

const statusMap = {
  1: { label: '合格', type: 'success' },
  2: { label: '待整改', type: 'warning' },
  3: { label: '已整改', type: 'primary' }
}

function scoreClass(score) {
  if (score == null) return ''
  if (score >= 8) return 'score-good'
  if (score >= 5) return 'score-mid'
  return 'score-bad'
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, location: '', status: null, id: null })

async function load() {
  loading.value = true
  try {
    // 按 id 定位时不带 ctype:三检三条路由共用本页,工单只存了 sourceId 不知类型,
    // 硬带 ctype 会把跨类型跳来的那条记录筛没(又是一条死链)。id 本身唯一,足够。
    const params = query.id ? { ...query } : { ...query, ctype: ctype.value }
    const res = await checkApi.page(params)
    list.value = res.records
    total.value = res.total
    loadCounts(res.records)
  } finally {
    loading.value = false
  }
}

// 工单来源联动。immediate:false — 本页 onMounted 已有 load(),避免请求两次
const { highlightId, isHighlighting, clearHighlight, rowClass, applyHighlight } =
  useHighlightFilter(query, load, { immediate: false })
const { countOf, loadCounts, drawer, openRelated, gotoOrder } = useRelatedOrders('CHECK')

function reset() {
  // id 一并清掉,否则从工单跳来后点重置会仍被定位条件锁住
  Object.assign(query, { pageNo: 1, location: '', status: null, id: null })
  highlightId.value = null
  load()
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const form = reactive({ id: null, ctype: ctype.value, location: '', checker: '', checkTime: '', score: 8, issues: '', status: 1 })
const attachFiles = ref([])

const rules = {
  location: [{ required: true, message: '请输入检查位置', trigger: 'blur' }]
}

async function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? `编辑${ctype.value}检查` : `新增${ctype.value}检查`
  attachFiles.value = []
  if (row) {
    Object.assign(form, row)
    try { attachFiles.value = await fileApi.list('pm_check', row.id) } catch (e) { /* 忽略 */ }
  } else {
    Object.assign(form, { id: null, ctype: ctype.value, location: '', checker: '', checkTime: '', score: 8, issues: '', status: 1 })
  }
}
async function submit() {
  await formRef.value.validate()
  form.ctype = ctype.value
  let newId = form.id
  if (form.id) await checkApi.update(form)
  else newId = await checkApi.add(form)
  const pendingIds = (attachFiles.value || []).filter(f => f && f.id && !f.bizId).map(f => f.id)
  if (newId && pendingIds.length) {
    try { await fileApi.attach('pm_check', newId, pendingIds) } catch (e) { /* 忽略,不阻断保存 */ }
  }
  ElMessage.success('保存成功')
  dialog.visible = false
  load()
}
async function remove(id) {
  await checkApi.remove(id)
  ElMessage.success('删除成功')
  load()
}
async function doRectify(row) {
  await checkApi.rectify(row.id)
  ElMessage.success('整改完成')
  load()
}

watch(() => route.meta.ctype, () => {
  query.pageNo = 1
  // 切换三检类型是明确的换上下文动作,得解除定位,
  // 否则列表还锁在上一个类型带来的那一条上
  query.id = null
  highlightId.value = null
  load()
})

onMounted(() => {
  // 先落定位条件再取数,顺序反了会先查全量再被覆盖
  applyHighlight()
  load()
})
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
.score-good { color: #16a34a; }
.score-mid { color: #ea9a13; }
.score-bad { color: #e5484d; }
/* 徽标默认会把数字盖在按钮文字上,右移一点 */
.order-badge :deep(.el-badge__content) { transform: translate(6px, -6px); }
/* 定位到的行加底色。scoped 样式进不到 el-table 内部,要 :deep */
:deep(.source-highlight-row) > td { background: var(--el-color-warning-light-9) !important; }
</style>
