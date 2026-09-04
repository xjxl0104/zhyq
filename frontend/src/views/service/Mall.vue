<template>
  <div class="page-container">
    <!-- 统计卡(占位数据) -->
    <div class="stat-row">
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">在售商品</div>
        <div class="stat-value">{{ total }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">累计销量</div>
        <div class="stat-value">{{ totalSales }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">销售额(元)</div>
        <div class="stat-value">{{ totalAmount.toFixed(2) }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">今日订单</div>
        <div class="stat-value">0</div>
      </el-card>
    </div>

    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="商品名称">
          <el-input v-model="query.name" placeholder="请输入名称" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.productType" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="t in productTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="上架" :value="1" />
            <el-option label="下架" :value="0" />
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
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增商品</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column prop="name" label="商品名称" min-width="180" />
        <el-table-column prop="productType" label="类型" min-width="100" />
        <el-table-column label="价格" width="120">
          <template #default="{ row }">{{ Number(row.price || 0).toFixed(2) }} 元</template>
        </el-table-column>
        <el-table-column prop="stock" label="库存" width="90" />
        <el-table-column prop="sales" label="销量" width="90" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '上架' : '下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
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
        <el-form-item label="商品名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.productType" placeholder="请选择" clearable style="width: 100%">
            <el-option v-for="t in productTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="价格"><el-input-number v-model="form.price" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="库存"><el-input-number v-model="form.stock" :min="0" /></el-form-item>
        <el-form-item label="销量"><el-input-number v-model="form.sales" :min="0" /></el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">上架</el-radio>
            <el-radio :value="0">下架</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { productApi } from '@/api/service'

const productTypes = ['实物', '虚拟', '服务', '预约']

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, name: '', productType: null, status: null })

const totalSales = computed(() => list.value.reduce((s, p) => s + (p.sales || 0), 0))
const totalAmount = computed(() => list.value.reduce((s, p) => s + (p.sales || 0) * Number(p.price || 0), 0))

async function load() {
  loading.value = true
  try {
    const res = await productApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
function reset() {
  Object.assign(query, { pageNo: 1, name: '', productType: null, status: null })
  load()
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const form = reactive({ id: null, name: '', productType: null, price: 0, stock: 0, sales: 0, status: 1 })
const rules = {
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }]
}

function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑商品' : '新增商品'
  if (row) Object.assign(form, row)
  else Object.assign(form, { id: null, name: '', productType: null, price: 0, stock: 0, sales: 0, status: 1 })
}
async function submit() {
  await formRef.value.validate()
  if (form.id) await productApi.update(form)
  else await productApi.add(form)
  ElMessage.success('保存成功')
  dialog.visible = false
  load()
}
async function remove(id) {
  await productApi.remove(id)
  ElMessage.success('删除成功')
  load()
}

onMounted(load)
</script>

<style scoped>
.stat-row { display: flex; gap: 16px; margin-bottom: 16px; }
.stat-card { flex: 1; }
.stat-label { color: var(--text-secondary); font-size: 13px; }
.stat-value { font-size: 26px; font-weight: 600; margin-top: 8px; }
.pager { margin-top: 16px; justify-content: flex-end; }
.toolbar { margin-bottom: 12px; }
</style>
