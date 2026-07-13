<template>
  <div class="page-container">
    <!-- 统计卡片 -->
    <div class="stat-row">
      <div class="stat-card">
        <div class="stat-label">总资产</div>
        <div class="stat-value">{{ stats.total || 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">在用</div>
        <div class="stat-value success">{{ stats.inUse || 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">维修中</div>
        <div class="stat-value warning">{{ stats.repairing || 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">已报废</div>
        <div class="stat-value danger">{{ stats.scrapped || 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">资产总值</div>
        <div class="stat-value primary">¥{{ Number(stats.totalValue || 0).toLocaleString() }}</div>
      </div>
    </div>

    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="编码">
          <el-input v-model="query.code" placeholder="请输入资产编码" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="query.name" placeholder="请输入资产名称" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="query.category" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 110px">
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
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增资产</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="code" label="编码" min-width="130" />
        <el-table-column prop="name" label="名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="category" label="分类" width="100" />
        <el-table-column prop="location" label="位置" min-width="130" show-overflow-tooltip />
        <el-table-column label="采购价" width="120" align="right">
          <template #default="{ row }">
            ¥{{ Number(row.price || 0).toLocaleString() }}
          </template>
        </el-table-column>
        <el-table-column prop="purchaseDate" label="采购日期" width="110" />
        <el-table-column prop="owner" label="责任人" width="100" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status]?.type || 'info'">
              {{ statusMap[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm v-if="row.status !== 4" title="确认报废该资产?" @confirm="scrap(row.id)">
              <template #reference><el-button link type="warning">报废</el-button></template>
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
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="560px">
      <el-form :model="form" label-width="90px" ref="formRef" :rules="rules">
        <el-form-item label="资产编码">
          <el-input v-model="form.code" placeholder="留空自动生成" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="资产名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" style="width: 100%">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="存放位置"><el-input v-model="form.location" /></el-form-item>
        <el-form-item label="采购价">
          <el-input-number v-model="form.price" :min="0" :precision="2" :step="100" style="width: 200px" />
        </el-form-item>
        <el-form-item label="采购日期">
          <el-date-picker v-model="form.purchaseDate" type="date" value-format="YYYY-MM-DD" style="width: 200px" />
        </el-form-item>
        <el-form-item label="保修到期">
          <el-date-picker v-model="form.warrantyEnd" type="date" value-format="YYYY-MM-DD" style="width: 200px" />
        </el-form-item>
        <el-form-item label="责任人"><el-input v-model="form.owner" /></el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">在用</el-radio>
            <el-radio :value="2">闲置</el-radio>
            <el-radio :value="3">维修</el-radio>
            <el-radio :value="4">报废</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
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
import { assetApi } from '@/api/property'

const categories = ['办公设备', '机电', '安防', '家具', 'IT']
const statusMap = {
  1: { label: '在用', type: 'success' },
  2: { label: '闲置', type: 'info' },
  3: { label: '维修', type: 'warning' },
  4: { label: '报废', type: 'danger' }
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const stats = reactive({ total: 0, inUse: 0, idle: 0, repairing: 0, scrapped: 0, totalValue: 0 })
const query = reactive({ pageNo: 1, pageSize: 10, code: '', name: '', category: null, status: null })

async function load() {
  loading.value = true
  try {
    const res = await assetApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
async function loadStats() {
  Object.assign(stats, await assetApi.stats())
}
async function refresh() {
  await Promise.all([load(), loadStats()])
}
function reset() {
  Object.assign(query, { pageNo: 1, code: '', name: '', category: null, status: null })
  load()
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const defaultForm = () => ({ id: null, code: '', name: '', category: '办公设备', location: '', price: 0, purchaseDate: null, warrantyEnd: null, owner: '', status: 1, remark: '' })
const form = reactive(defaultForm())
const rules = {
  name: [{ required: true, message: '请输入资产名称', trigger: 'blur' }]
}

function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑资产' : '新增资产'
  if (row) Object.assign(form, defaultForm(), row)
  else Object.assign(form, defaultForm())
}
async function submit() {
  await formRef.value.validate()
  if (form.id) await assetApi.update(form)
  else await assetApi.add(form)
  ElMessage.success('保存成功')
  dialog.visible = false
  refresh()
}
async function scrap(id) {
  await assetApi.scrap(id)
  ElMessage.success('资产已报废')
  refresh()
}
async function remove(id) {
  await assetApi.remove(id)
  ElMessage.success('删除成功')
  refresh()
}

onMounted(refresh)
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
.stat-value.primary { color: #4f46e5; }
.stat-value.success { color: #16a34a; }
.stat-value.warning { color: #ea9a13; }
.stat-value.danger { color: #e5484d; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
