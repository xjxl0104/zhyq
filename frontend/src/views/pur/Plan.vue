<template>
  <div class="page-container">
    <div class="search-bar">
      <el-form :inline="true">
        <el-form-item label="计划名称">
          <el-input v-model="query.title" placeholder="计划名称" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item :label="periodLabel">
          <el-input v-model="query.period" :placeholder="periodPlaceholder" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 130px">
            <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="Number(k)" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="table-card">
      <div class="toolbar">
        <span class="section-title">{{ planTypeLabel }}</span>
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增计划</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column prop="planNo" label="计划编号" min-width="140" />
        <el-table-column prop="title" label="计划名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="period" :label="periodLabel" width="110" />
        <el-table-column prop="department" label="申请部门" width="110" />
        <el-table-column prop="applicant" label="制定人" width="100" />
        <el-table-column label="预算金额" width="120" align="right">
          <template #default="{ row }">{{ row.budgetAmount != null ? `¥${row.budgetAmount}` : '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status]?.type || 'info'">
              {{ statusMap[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除该计划?" @confirm="doRemove(row)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                     :total="total" v-model:current-page="query.pageNo"
                     v-model:page-size="query.pageSize" :page-sizes="[10,20,50]" @change="load" />
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑计划' : '新增计划'" width="560px">
      <el-form :model="form" label-width="90px" ref="formRef" :rules="rules">
        <el-form-item label="计划名称" prop="title">
          <el-input v-model="form.title" placeholder="如:2026年度办公物资采购计划" />
        </el-form-item>
        <el-form-item :label="periodLabel" prop="period">
          <el-date-picker v-if="planType === 1" v-model="form.period" type="year" value-format="YYYY"
                          :placeholder="periodPlaceholder" style="width: 100%" />
          <el-date-picker v-else-if="planType === 2" v-model="form.period" type="month" value-format="YYYY-MM"
                          :placeholder="periodPlaceholder" style="width: 100%" />
          <el-date-picker v-else v-model="form.period" type="date" value-format="YYYY-MM-DD"
                          :placeholder="periodPlaceholder" style="width: 100%" />
        </el-form-item>
        <el-form-item label="申请部门"><el-input v-model="form.department" /></el-form-item>
        <el-form-item label="制定人"><el-input v-model="form.applicant" /></el-form-item>
        <el-form-item label="预算金额">
          <el-input-number v-model="form.budgetAmount" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="Number(k)" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { purPlanApi } from '@/api/pur'

const route = useRoute()
// 一组件多路由:年度/月度/临时三个菜单共用本页,靠 route.meta.planType 区分
const planType = computed(() => route.meta.planType || 1)
const planTypeLabel = computed(() => ({ 1: '年度采购计划', 2: '月度采购计划', 3: '临时采购计划' }[planType.value]))
const periodLabel = computed(() => ({ 1: '年度', 2: '月份', 3: '日期' }[planType.value]))
const periodPlaceholder = computed(() => ({ 1: '如 2026', 2: '如 2026-03', 3: '如 2026-03-15' }[planType.value]))

const statusMap = {
  1: { label: '草稿', type: 'info' },
  2: { label: '生效', type: 'primary' },
  3: { label: '已完成', type: 'success' },
  4: { label: '已关闭', type: 'info' }
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, title: '', period: '', status: null })

async function load() {
  loading.value = true
  try {
    const res = await purPlanApi.page({ ...query, planType: planType.value })
    list.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}
function search() { query.pageNo = 1; load() }
function reset() { query.title = ''; query.period = ''; query.status = null; search() }

// 切换年度/月度/临时菜单时,同一组件复用需重新加载
watch(planType, () => { reset() })

const formRef = ref()
const dialogVisible = ref(false)
const defaultForm = () => ({
  id: null, title: '', period: '', department: '', applicant: '', budgetAmount: 0, status: 1, remark: ''
})
const form = reactive(defaultForm())
const rules = {
  title: [{ required: true, message: '请输入计划名称', trigger: 'blur' }],
  period: [{ required: true, message: '请选择周期', trigger: 'change' }]
}

function openDialog(row) {
  Object.assign(form, defaultForm())
  if (row) Object.assign(form, row)
  dialogVisible.value = true
}

async function submit() {
  await formRef.value.validate()
  const payload = { ...form, planType: planType.value }
  if (form.id) {
    await purPlanApi.update(payload)
    ElMessage.success('已保存')
  } else {
    await purPlanApi.add(payload)
    ElMessage.success('新增成功')
  }
  dialogVisible.value = false
  await load()
}

async function doRemove(row) {
  await purPlanApi.remove(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>

<style scoped>
.section-title { font-size: 15px; font-weight: 600; color: #303133; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
