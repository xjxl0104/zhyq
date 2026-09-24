<template>
  <div class="page-container">
    <div class="table-card">
      <div class="toolbar">
        <span class="title">客户评级与总比例</span>
        <el-button type="primary" @click="save" :loading="saving">保存</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border>
        <el-table-column prop="code" label="评级" width="70" align="center" />
        <el-table-column label="名称" width="120">
          <template #default="{ row }"><el-input v-model="row.name" size="small" /></template>
        </el-table-column>
        <el-table-column label="租赁佣金月数" width="150">
          <template #default="{ row }"><el-input-number v-model="row.leaseCommissionMonths" :min="0.25" :max="2" :step="0.25" :precision="2" size="small" controls-position="right" /></template>
        </el-table-column>
        <el-table-column label="入仓总比例 %" width="140">
          <template #default="{ row }"><el-input-number v-model="row.erpTotalRate" :min="0" :max="100" :precision="2" size="small" controls-position="right" /></template>
        </el-table-column>
        <el-table-column label="入仓签约奖(元)" width="150">
          <template #default="{ row }"><el-input-number v-model="row.contractBonus" :min="0" :step="100" :precision="2" size="small" controls-position="right" /></template>
        </el-table-column>
        <el-table-column label="自动建议:月租金 ≥" width="170">
          <template #default="{ row }"><el-input-number v-model="row.autoMinRent" :min="0" :step="1000" size="small" controls-position="right" /></template>
        </el-table-column>
        <el-table-column label="自动建议:月单量 ≥" width="170">
          <template #default="{ row }"><el-input-number v-model="row.autoMinOrders" :min="0" :step="100" size="small" controls-position="right" /></template>
        </el-table-column>
        <el-table-column label="说明" min-width="160">
          <template #default="{ row }"><el-input v-model="row.descr" size="small" /></template>
        </el-table-column>
      </el-table>
      <div class="hint">
        <p>园区入驻(租赁):佣金池 = 月租金(单价 × 面积)× 佣金月数,合同审批通过一次性生成,首期租金到账解冻。</p>
        <p>客户入仓:每张出库单佣金池 = 园区服务费 × 入仓总比例;签约奖可为 0(默认)。</p>
        <p>算例:A 级租客 300 元/㎡/月 × 100 ㎡ = 月租 30,000 × 1 个月 = 佣金池 30,000;4 级链拆分 15,000 / 6,000 / 4,500 / 4,500。</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { mktGradeApi } from '@/api/marketing'

const loading = ref(false)
const saving = ref(false)
const list = ref([])

async function load() {
  loading.value = true
  try { list.value = await mktGradeApi.list() } finally { loading.value = false }
}
async function save() {
  saving.value = true
  try {
    await mktGradeApi.update(list.value)
    ElMessage.success('已保存')
    load()
  } finally { saving.value = false }
}
onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; }
.title { font-weight: 600; margin-right: auto; }
.hint { color: var(--el-text-color-secondary); font-size: 12px; margin-top: 12px; }
.hint p { margin: 2px 0; }
</style>
