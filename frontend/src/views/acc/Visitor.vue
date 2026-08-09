<template>
  <div class="page-container">
    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="姓名">
          <el-input v-model="query.name" placeholder="请输入访客姓名" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="query.phone" placeholder="请输入电话" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item label="到访地点">
          <el-input-number v-model="query.spaceId" :min="0" :controls="false" placeholder="空间ID" style="width: 120px" />
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

    <!-- 表格区 -->
    <div class="table-card">
      <div class="toolbar">
        <el-button type="primary" @click="openAdd()"><el-icon><Plus /></el-icon>登记预约</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="name" label="访客姓名" width="110" />
        <el-table-column prop="phone" label="电话" width="130" />
        <el-table-column prop="hostUser" label="被访人" width="110" />
        <el-table-column prop="spaceId" label="到访地点" width="100" />
        <el-table-column prop="visitTime" label="预约到访时间" width="170" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status]?.type || 'info'">
              {{ statusMap[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="plateNo" label="随车车牌" width="110" />
        <el-table-column prop="approver" label="审批人" width="100" />
        <el-table-column prop="remark" label="备注" min-width="130" show-overflow-tooltip />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 1" link type="success" @click="doCheckin(row)">签到</el-button>
            <el-button v-if="row.status === 2" link type="primary" @click="doLeave(row)">离场</el-button>
            <el-popconfirm v-if="row.status === 1" title="确认取消该预约?" @confirm="doCancel(row)">
              <template #reference><el-button link type="danger">取消</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                     :total="total" v-model:current-page="query.pageNo"
                     v-model:page-size="query.pageSize" :page-sizes="[10,20,50]" @change="load" />
    </div>

    <!-- 登记预约弹窗 -->
    <el-dialog v-model="dialogVisible" title="登记预约" width="480px">
      <el-form :model="form" label-width="100px" ref="formRef" :rules="rules">
        <el-form-item label="访客姓名" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="电话" prop="phone"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="被访人"><el-input v-model="form.hostUser" placeholder="被访人姓名" /></el-form-item>
        <el-form-item label="到访地点">
          <el-input-number v-model="form.spaceId" :min="0" :controls="false" style="width: 200px" />
        </el-form-item>
        <el-form-item label="预约时间" prop="visitTime">
          <el-date-picker v-model="form.visitTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 200px" />
        </el-form-item>
        <el-form-item label="随车车牌"><el-input v-model="form.plateNo" placeholder="选填" /></el-form-item>
        <el-form-item label="审批人"><el-input v-model="form.approver" placeholder="审批人姓名" /></el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
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
import { ElMessage } from 'element-plus'
import { visitorApi } from '@/api/acc'
import { useCrudPage } from '@/composables/useCrudPage'

const formRef = ref()
const blank = () => ({
  name: '', phone: '', hostUser: '', spaceId: null, visitTime: '', plateNo: '', remark: '', approver: ''
})
const rules = {
  name: [{ required: true, message: '请输入访客姓名', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入电话', trigger: 'blur' }],
  visitTime: [{ required: true, message: '请选择预约到访时间', trigger: 'change' }]
}

const statusMap = {
  1: { label: '已预约', type: 'warning' },
  2: { label: '已到访', type: 'success' },
  3: { label: '已离场', type: 'info' },
  4: { label: '已取消', type: 'danger' }
}

// 访客登记只增不改:借用 useCrudPage 的 add 流程承载"登记预约"(reserve),状态流转靠专属动作按钮
const {
  loading, list, total, query, load, reset,
  dialogVisible, form,
  openAdd, submit
} = useCrudPage({ page: visitorApi.page, add: visitorApi.reserve }, {
  defaultQuery: { name: '', phone: '', spaceId: null, status: null },
  emptyForm: blank,
  beforeSubmit: () => formRef.value.validate()
})

async function doCheckin(row) {
  await visitorApi.checkin(row.id)
  ElMessage.success('签到成功')
  await load()
}

async function doLeave(row) {
  await visitorApi.leave(row.id)
  ElMessage.success('已登记离场')
  await load()
}

async function doCancel(row) {
  await visitorApi.cancel(row.id)
  ElMessage.success('预约已取消')
  await load()
}

onMounted(() => load())
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
