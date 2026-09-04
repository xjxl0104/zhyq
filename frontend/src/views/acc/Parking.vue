<template>
  <div class="page-container">
    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="车牌号">
          <el-input v-model="query.plateNo" placeholder="请输入车牌号" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item label="车主类型">
          <el-select v-model="query.ownerType" placeholder="全部类型" clearable style="width: 130px">
            <el-option label="员工" value="staff" />
            <el-option label="访客" value="visitor" />
            <el-option label="临时" value="temp" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 130px">
            <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="Number(k)" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 费用提示 -->
    <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 12px">
      停车费为试算金额，仅供参考展示，未计入账单，不触发收款。
    </el-alert>

    <!-- 表格区 -->
    <div class="table-card">
      <div class="toolbar">
        <el-button type="primary" @click="openEnter"><el-icon><Plus /></el-icon>车辆入场</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column prop="plateNo" label="车牌号" width="130" />
        <el-table-column label="车主类型" width="100">
          <template #default="{ row }">{{ ownerTypeLabel(row.ownerType) }}</template>
        </el-table-column>
        <el-table-column prop="enterTime" label="入场时间" width="170" />
        <el-table-column prop="leaveTime" label="出场时间" width="170">
          <template #default="{ row }">{{ row.leaveTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status]?.type || 'info'">
              {{ statusMap[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="试算费用" width="110" align="right">
          <template #default="{ row }">{{ row.fee != null ? `¥${Number(row.fee).toFixed(2)}` : '-' }}</template>
        </el-table-column>
        <el-table-column prop="feeRule" label="计费说明" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 1" link type="primary" @click="doLeave(row)">出场</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                     :total="total" v-model:current-page="query.pageNo"
                     v-model:page-size="query.pageSize" :page-sizes="[10,20,50]" @change="load" />
    </div>

    <!-- 入场弹窗 -->
    <el-dialog v-model="enterVisible" title="车辆入场" width="420px">
      <el-form :model="enterForm" label-width="90px" ref="enterFormRef" :rules="enterRules">
        <el-form-item label="车牌号" prop="plateNo"><el-input v-model="enterForm.plateNo" placeholder="请输入车牌号" /></el-form-item>
        <el-form-item label="车主类型" prop="ownerType">
          <el-select v-model="enterForm.ownerType" placeholder="请选择" style="width: 100%">
            <el-option label="员工" value="staff" />
            <el-option label="访客" value="visitor" />
            <el-option label="临时" value="temp" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="enterVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEnter">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { parkingApi } from '@/api/acc'
import { useCrudPage } from '@/composables/useCrudPage'

const statusMap = {
  1: { label: '在场', type: 'warning' },
  2: { label: '已离场', type: 'info' }
}

const ownerTypeLabel = (t) => ({ staff: '员工', visitor: '访客', temp: '临时' }[t] || t)

// 停车记录靠 enter/leave 专属动作流转,不用 useCrudPage 的新增/编辑弹窗
const { loading, list, total, query, load, reset } = useCrudPage(
  { page: parkingApi.page },
  { defaultQuery: { plateNo: '', ownerType: null, status: null } }
)

const enterVisible = ref(false)
const enterFormRef = ref()
const enterForm = reactive({ plateNo: '', ownerType: 'staff' })
const enterRules = {
  plateNo: [{ required: true, message: '请输入车牌号', trigger: 'blur' }],
  ownerType: [{ required: true, message: '请选择车主类型', trigger: 'change' }]
}

function openEnter() {
  enterForm.plateNo = ''
  enterForm.ownerType = 'staff'
  enterVisible.value = true
}

async function submitEnter() {
  await enterFormRef.value.validate()
  await parkingApi.enter(enterForm.plateNo, enterForm.ownerType)
  ElMessage.success('入场成功')
  enterVisible.value = false
  await load()
}

async function doLeave(row) {
  await parkingApi.leave(row.id)
  ElMessage.success('出场成功，已试算费用')
  await load()
}

onMounted(() => load())
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
