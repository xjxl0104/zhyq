<template>
  <div class="page-container">
    <div class="table-card">
      <div class="toolbar">
        <span class="title">加盟申请(未上线的云仓)</span>
        <el-radio-group v-model="filter" size="small" @change="load">
          <el-radio-button :value="null">全部在途</el-radio-button>
          <el-radio-button :value="2">待资质审核</el-radio-button>
          <el-radio-button :value="3">ERP 对接中</el-radio-button>
          <el-radio-button :value="4">待签协议</el-radio-button>
        </el-radio-group>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="code" label="编码" width="120" />
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="contact" label="联系人" width="100" />
        <el-table-column prop="phone" label="电话" width="130" />
        <el-table-column label="当前步骤" width="130">
          <template #default="{ row }"><el-tag>{{ JOIN[row.joinStatus] }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="createTime" label="申请时间" width="160" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <template v-if="row.joinStatus === 2">
              <el-button link type="success" @click="act(row, 'passQualification')">审核通过</el-button>
              <el-button link type="danger" @click="withReason(row, 'rejectQualification', '驳回资质')">驳回</el-button>
            </template>
            <el-button v-else-if="row.joinStatus === 3" link type="success" @click="act(row, 'markErp')">标记 ERP 已联通</el-button>
            <el-button v-else-if="row.joinStatus === 4" link type="success" @click="signAgreement(row)">上传协议并上线</el-button>
            <router-link to="/crm/marketing/warehouse" class="link">去云仓管理</router-link>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next"
                     :total="total" v-model:current-page="query.pageNo" v-model:page-size="query.pageSize" @change="load" />
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { mktWarehouseApi } from '@/api/marketing'

const JOIN = { 1: '申请', 2: '资质审核', 3: 'ERP 对接中', 4: '待签协议' }
const filter = ref(null)
const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 20 })

async function load() {
  loading.value = true
  try {
    const res = await mktWarehouseApi.page({ ...query, joinStatus: filter.value, inProgress: filter.value == null ? 1 : undefined })
    list.value = res.records; total.value = res.total
  } finally { loading.value = false }
}
async function act(row, fn) { await mktWarehouseApi[fn](row.id); ElMessage.success('已处理'); load() }
async function withReason(row, fn, title) {
  const { value } = await ElMessageBox.prompt('请填写原因', title, { inputPattern: /\S+/, inputErrorMessage: '原因必填' })
  await mktWarehouseApi[fn](row.id, { reason: value }); ElMessage.success('已处理'); load()
}
async function signAgreement(row) {
  const { value } = await ElMessageBox.prompt('协议签署件路径或附件 ID', '上传加盟协议', { inputPattern: /\S+/, inputErrorMessage: '必填' })
  await mktWarehouseApi.signAgreement(row.id, { contractFile: value }); ElMessage.success('已上线'); load()
}
onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 16px; }
.title { font-weight: 600; margin-right: auto; }
.link { margin-left: 8px; font-size: 12px; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
