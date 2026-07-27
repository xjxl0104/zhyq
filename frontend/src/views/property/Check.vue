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
          <el-button type="primary" @click="load"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 表格区 -->
    <div class="table-card">
      <div class="toolbar">
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增{{ ctype }}检查</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="55" />
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
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm v-if="row.status === 2" title="确认整改完成?" @confirm="doRectify(row)">
              <template #reference><el-button link type="warning">整改完成</el-button></template>
            </el-popconfirm>
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
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { checkApi } from '@/api/property'
import { fileApi } from '@/api/file'
import FileUpload from '@/components/FileUpload.vue'

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
const query = reactive({ pageNo: 1, pageSize: 10, location: '', status: null })

async function load() {
  loading.value = true
  try {
    const res = await checkApi.page({ ...query, ctype: ctype.value })
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
function reset() {
  Object.assign(query, { pageNo: 1, location: '', status: null })
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
  load()
})

onMounted(load)
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
.score-good { color: #16a34a; }
.score-mid { color: #ea9a13; }
.score-bad { color: #e5484d; }
</style>
