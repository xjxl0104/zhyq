<template>
  <div class="page-container">
    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="计划名称">
          <el-input v-model="query.name" placeholder="请输入计划名称" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="周期">
          <el-select v-model="query.cycle" placeholder="全部" clearable style="width: 110px">
            <el-option v-for="c in cycles" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 110px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
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
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增计划</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="name" label="计划名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="周期" width="90">
          <template #default="{ row }">
            <el-tag :type="cycleMap[row.cycle] || 'info'">{{ row.cycle || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="route" label="路线" min-width="200" show-overflow-tooltip />
        <el-table-column prop="points" label="点位" min-width="150" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-popconfirm title="确认按该计划生成巡检工单?" @confirm="generate(row)">
              <template #reference><el-button link type="success">生成工单</el-button></template>
            </el-popconfirm>
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
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="560px">
      <el-form :model="form" label-width="90px" ref="formRef" :rules="rules">
        <el-form-item label="计划名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="周期">
          <el-select v-model="form.cycle" style="width: 100%">
            <el-option v-for="c in cycles" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="路线">
          <el-input v-model="form.route" placeholder="如:A栋1F大堂→电梯厅→楼顶" />
        </el-form-item>
        <el-form-item label="点位">
          <el-input v-model="form.points" type="textarea" placeholder="如:大堂,电梯,消防栓×12" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="附件"><FileUpload v-model="attachFiles" biz-type="inspection" :biz-id="form.id" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { inspectionApi } from '@/api/property'
import { fileApi } from '@/api/file'
import FileUpload from '@/components/FileUpload.vue'

const cycles = ['每日', '每周', '每月']
const cycleMap = { '每日': 'primary', '每周': 'success', '每月': 'warning' }

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, name: '', cycle: null, status: null })

async function load() {
  loading.value = true
  try {
    const res = await inspectionApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
function reset() {
  Object.assign(query, { pageNo: 1, name: '', cycle: null, status: null })
  load()
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const defaultForm = () => ({ id: null, name: '', cycle: '每日', route: '', points: '', status: 1 })
const form = reactive(defaultForm())
const attachFiles = ref([])
const rules = {
  name: [{ required: true, message: '请输入计划名称', trigger: 'blur' }]
}

async function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑计划' : '新增计划'
  attachFiles.value = []
  if (row) {
    Object.assign(form, defaultForm(), row)
    try { attachFiles.value = await fileApi.list('inspection', row.id) } catch (e) { /* 忽略 */ }
  } else {
    Object.assign(form, defaultForm())
  }
}
async function submit() {
  await formRef.value.validate()
  let newId = form.id
  if (form.id) await inspectionApi.update(form)
  else newId = await inspectionApi.add(form)
  const pendingIds = (attachFiles.value || []).filter(f => f && f.id && !f.bizId).map(f => f.id)
  if (newId && pendingIds.length) {
    try { await fileApi.attach('inspection', newId, pendingIds) } catch (e) { /* 忽略,不阻断保存 */ }
  }
  ElMessage.success('保存成功')
  dialog.visible = false
  load()
}
async function generate(row) {
  const res = await inspectionApi.generate(row.id)
  ElMessage.success(`巡检工单 ${res.code} 已生成,可在【物业报修】中查看`)
}
async function remove(id) {
  await inspectionApi.remove(id)
  ElMessage.success('删除成功')
  load()
}

onMounted(load)
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
