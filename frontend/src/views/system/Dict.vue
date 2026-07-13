<template>
  <div class="page-container">
    <el-row :gutter="12">
      <el-col :span="10">
        <div class="table-card">
          <div class="toolbar">
            <span class="panel-title">字典类型</span>
            <el-button type="primary" size="small" @click="openTypeDialog()"><el-icon><Plus /></el-icon>新增</el-button>
          </div>
          <el-table :data="types" v-loading="loadingType" border highlight-current-row
                    @current-change="selectType">
            <el-table-column prop="dictName" label="字典名称" min-width="120" />
            <el-table-column prop="dictType" label="类型编码" min-width="140" />
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button link type="primary" @click.stop="openTypeDialog(row)">编辑</el-button>
                <el-button link type="danger" @click.stop="removeType(row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination class="pager" background layout="total, prev, pager, next"
                         :total="typeTotal" v-model:current-page="typeQuery.pageNo" @change="loadTypes" small />
        </div>
      </el-col>

      <el-col :span="14">
        <div class="table-card">
          <div class="toolbar">
            <span class="panel-title">字典项 {{ current ? '· ' + current.dictName : '' }}</span>
            <el-button type="primary" size="small" :disabled="!current" @click="openDataDialog()">
              <el-icon><Plus /></el-icon>新增
            </el-button>
          </div>
          <el-table :data="dataList" v-loading="loadingData" border>
            <el-table-column prop="label" label="标签" min-width="120">
              <template #default="{ row }">
                <el-tag v-if="row.color" :type="row.color">{{ row.label }}</el-tag>
                <span v-else>{{ row.label }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="value" label="值" min-width="100" />
            <el-table-column prop="sort" label="排序" width="70" />
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button link type="primary" @click="openDataDialog(row)">编辑</el-button>
                <el-button link type="danger" @click="removeData(row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
    </el-row>

    <el-dialog v-model="typeDialog.visible" :title="typeDialog.title" width="460px">
      <el-form :model="typeForm" label-width="90px" ref="typeRef" :rules="typeRules">
        <el-form-item label="字典名称" prop="dictName"><el-input v-model="typeForm.dictName" /></el-form-item>
        <el-form-item label="类型编码" prop="dictType"><el-input v-model="typeForm.dictType" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="typeForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitType">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="dataDialog.visible" :title="dataDialog.title" width="460px">
      <el-form :model="dataForm" label-width="90px" ref="dataRef" :rules="dataRules">
        <el-form-item label="标签" prop="label"><el-input v-model="dataForm.label" /></el-form-item>
        <el-form-item label="值" prop="value"><el-input v-model="dataForm.value" /></el-form-item>
        <el-form-item label="标签色">
          <el-select v-model="dataForm.color" clearable placeholder="默认">
            <el-option label="蓝(primary)" value="primary" />
            <el-option label="绿(success)" value="success" />
            <el-option label="橙(warning)" value="warning" />
            <el-option label="红(danger)" value="danger" />
            <el-option label="灰(info)" value="info" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序"><el-input-number v-model="dataForm.sort" :min="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dataDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitData">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { dictApi } from '@/api/system'

const loadingType = ref(false)
const types = ref([])
const typeTotal = ref(0)
const typeQuery = reactive({ pageNo: 1, pageSize: 10 })
const current = ref(null)

async function loadTypes() {
  loadingType.value = true
  try { const res = await dictApi.typePage(typeQuery); types.value = res.records; typeTotal.value = res.total }
  finally { loadingType.value = false }
}
function selectType(row) { if (row) { current.value = row; loadData() } }

const loadingData = ref(false)
const dataList = ref([])
async function loadData() {
  if (!current.value) return
  loadingData.value = true
  try { dataList.value = await dictApi.dataByType(current.value.dictType) }
  finally { loadingData.value = false }
}

const typeRef = ref()
const typeDialog = reactive({ visible: false, title: '' })
const typeForm = reactive({ id: null, dictName: '', dictType: '', remark: '', status: 1 })
const typeRules = {
  dictName: [{ required: true, message: '请输入', trigger: 'blur' }],
  dictType: [{ required: true, message: '请输入', trigger: 'blur' }]
}
function openTypeDialog(row) {
  typeDialog.visible = true; typeDialog.title = row ? '编辑字典类型' : '新增字典类型'
  if (row) Object.assign(typeForm, row)
  else Object.assign(typeForm, { id: null, dictName: '', dictType: '', remark: '', status: 1 })
}
async function submitType() {
  await typeRef.value.validate()
  typeForm.id ? await dictApi.updateType(typeForm) : await dictApi.addType(typeForm)
  ElMessage.success('保存成功'); typeDialog.visible = false; loadTypes()
}
async function removeType(id) { await dictApi.removeType(id); ElMessage.success('删除成功'); loadTypes() }

const dataRef = ref()
const dataDialog = reactive({ visible: false, title: '' })
const dataForm = reactive({ id: null, dictType: '', label: '', value: '', color: '', sort: 0, status: 1 })
const dataRules = {
  label: [{ required: true, message: '请输入', trigger: 'blur' }],
  value: [{ required: true, message: '请输入', trigger: 'blur' }]
}
function openDataDialog(row) {
  dataDialog.visible = true; dataDialog.title = row ? '编辑字典项' : '新增字典项'
  if (row) Object.assign(dataForm, row)
  else Object.assign(dataForm, { id: null, dictType: current.value.dictType, label: '', value: '', color: '', sort: 0, status: 1 })
}
async function submitData() {
  await dataRef.value.validate()
  dataForm.dictType = current.value.dictType
  dataForm.id ? await dictApi.updateData(dataForm) : await dictApi.addData(dataForm)
  ElMessage.success('保存成功'); dataDialog.visible = false; loadData()
}
async function removeData(id) { await dictApi.removeData(id); ElMessage.success('删除成功'); loadData() }

onMounted(loadTypes)
</script>
<style scoped>
.panel-title { font-weight: 600; margin-right: auto; }
.toolbar { align-items: center; }
.pager { margin-top: 12px; justify-content: flex-end; }
</style>
