<template>
  <div class="page-container">
    <div class="table-card">
      <div class="toolbar">
        <span class="title">客户评级与业务参数</span>
        <el-button type="primary" @click="save" :loading="saving">保存</el-button>
      </div>
      <p class="rule-note">评级用于客户分类和业务建议。云仓按单佣金由所属 P4 单独定价，A–D 评级不绑定固定金额或比例。</p>
      <el-table :data="list" v-loading="loading" border>
        <el-table-column prop="code" label="评级" width="70" align="center" />
        <el-table-column label="名称" width="120">
          <template #default="{ row }"><el-input v-model="row.name" size="small" /></template>
        </el-table-column>
        <el-table-column label="租赁佣金月数" width="150">
          <template #default="{ row }"><el-input-number v-model="row.leaseCommissionMonths" :min="0.25" :max="2" :step="0.25" :precision="2" size="small" controls-position="right" /></template>
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
        <p>客户入仓：总佣金、P4 本人及其他受益人的每单金额在小程序中配置，可在「客户管理 › 按单佣金」查看。</p>
        <p>租赁佣金月数与入仓签约奖保留原业务规则，不作为按单佣金的定价依据。</p>
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
    await mktGradeApi.update(list.value.map(({ erpTotalRate, ...grade }) => grade))
    ElMessage.success('已保存')
    load()
  } finally { saving.value = false }
}
onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; }
.title { font-weight: 600; margin-right: auto; }
.rule-note { margin: 0 0 16px; color: var(--el-text-color-regular); font-size: 13px; line-height: 1.7; }
.hint { color: var(--el-text-color-secondary); font-size: 12px; margin-top: 12px; }
.hint p { margin: 2px 0; }
</style>
