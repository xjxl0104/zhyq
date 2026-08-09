<template>
  <div class="page-container">
    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="门/闸机">
          <el-input v-model="query.gateCode" placeholder="请输入门禁编号" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item label="空间">
          <el-input-number v-model="query.spaceId" :min="0" :controls="false" placeholder="空间ID" style="width: 120px" />
        </el-form-item>
        <el-form-item label="人员类型">
          <el-select v-model="query.personType" placeholder="全部类型" clearable style="width: 130px">
            <el-option label="员工" value="staff" />
            <el-option label="访客" value="visitor" />
            <el-option label="未知" value="unknown" />
          </el-select>
        </el-form-item>
        <el-form-item label="方向">
          <el-select v-model="query.direction" placeholder="全部" clearable style="width: 100px">
            <el-option label="进" :value="1" />
            <el-option label="出" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="结果">
          <el-select v-model="query.result" placeholder="全部" clearable style="width: 100px">
            <el-option label="放行" :value="1" />
            <el-option label="拒绝" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间">
          <el-date-picker v-model="timeRange" type="datetimerange" value-format="YYYY-MM-DD HH:mm:ss"
                         start-placeholder="开始" end-placeholder="结束" style="width: 340px" @change="onTimeRangeChange" />
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
        <el-button type="primary" @click="openAdd()"><el-icon><Plus /></el-icon>登记通行</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="gateCode" label="门/闸机编号" min-width="130" />
        <el-table-column prop="spaceId" label="所在空间" width="100" />
        <el-table-column label="人员类型" width="100">
          <template #default="{ row }">{{ personTypeLabel(row.personType) }}</template>
        </el-table-column>
        <el-table-column prop="personRef" label="人员标识" min-width="130" show-overflow-tooltip />
        <el-table-column label="方向" width="80">
          <template #default="{ row }">
            <el-tag :type="row.direction === 1 ? 'success' : 'info'">{{ row.direction === 1 ? '进' : '出' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <el-tag :type="row.result === 1 ? 'success' : 'danger'">{{ row.result === 1 ? '放行' : '拒绝' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="accessTime" label="通行时刻" width="180" />
        <el-table-column prop="approver" label="审批人" width="100" />
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                     :total="total" v-model:current-page="query.pageNo"
                     v-model:page-size="query.pageSize" :page-sizes="[10,20,50]" @change="load" />
    </div>

    <!-- 登记通行弹窗 -->
    <el-dialog v-model="dialogVisible" title="登记通行" width="480px">
      <el-form :model="form" label-width="100px" ref="formRef" :rules="rules">
        <el-form-item label="门/闸机" prop="gateCode"><el-input v-model="form.gateCode" placeholder="门禁编号" /></el-form-item>
        <el-form-item label="所在空间">
          <el-input-number v-model="form.spaceId" :min="0" :controls="false" style="width: 200px" />
        </el-form-item>
        <el-form-item label="人员类型" prop="personType">
          <el-select v-model="form.personType" placeholder="请选择" style="width: 200px">
            <el-option label="员工" value="staff" />
            <el-option label="访客" value="visitor" />
            <el-option label="未知" value="unknown" />
          </el-select>
        </el-form-item>
        <el-form-item label="人员标识"><el-input v-model="form.personRef" placeholder="员工号/访客id" /></el-form-item>
        <el-form-item label="方向" prop="direction">
          <el-radio-group v-model="form.direction">
            <el-radio :value="1">进</el-radio>
            <el-radio :value="2">出</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="结果" prop="result">
          <el-radio-group v-model="form.result">
            <el-radio :value="1">放行</el-radio>
            <el-radio :value="2">拒绝</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="通行时刻">
          <el-date-picker v-model="form.accessTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 200px" />
        </el-form-item>
        <el-form-item label="审批人"><el-input v-model="form.approver" placeholder="审批人姓名" style="width: 200px" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { accessApi } from '@/api/acc'
import { useCrudPage } from '@/composables/useCrudPage'

const formRef = ref()
const timeRange = ref([])

const blank = () => ({
  gateCode: '', spaceId: null, personType: 'staff', personRef: '',
  direction: 1, result: 1, accessTime: '', approver: ''
})
const rules = {
  gateCode: [{ required: true, message: '请输入门/闸机编号', trigger: 'blur' }],
  personType: [{ required: true, message: '请选择人员类型', trigger: 'change' }],
  direction: [{ required: true, message: '请选择方向', trigger: 'change' }],
  result: [{ required: true, message: '请选择结果', trigger: 'change' }]
}

const personTypeLabel = (t) => ({ staff: '员工', visitor: '访客', unknown: '未知' }[t] || t)

// 通行流水只增不改:借用 useCrudPage 的 add 流程承载"登记通行"(record),不做编辑/删除
const {
  loading, list, total, query, load, reset,
  dialogVisible, form,
  openAdd, submit
} = useCrudPage({ page: accessApi.page, add: accessApi.record }, {
  defaultQuery: { gateCode: '', spaceId: null, personType: null, direction: null, result: null, from: null, to: null },
  emptyForm: blank,
  beforeSubmit: () => formRef.value.validate()
})

function onTimeRangeChange(val) {
  query.from = val?.[0] || null
  query.to = val?.[1] || null
}

onMounted(() => load())
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
