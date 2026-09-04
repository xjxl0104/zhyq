<template>
  <div class="page-container">
    <!-- 时段查询区:选资源 + 选日期,看当天已订时段 -->
    <div class="search-bar">
      <el-form :inline="true">
        <el-form-item label="资源">
          <el-select v-model="filter.resourceId" placeholder="请选择资源" style="width: 200px" @change="loadSlots">
            <el-option v-for="r in resources" :key="r.id" :label="`${r.name}(${typeLabel(r.type)})`" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="filter.date" type="date" value-format="YYYY-MM-DD"
                          :clearable="false" style="width: 160px" @change="loadSlots" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadSlots"><el-icon><Search /></el-icon>查看时段</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="table-card">
      <div class="toolbar">
        <span class="section-title">当日已订时段</span>
        <el-button type="primary" @click="openBookDialog()"><el-icon><Plus /></el-icon>新增预订</el-button>
      </div>
      <el-table :data="slots" v-loading="slotsLoading" border stripe>
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column prop="startTime" label="开始时间" min-width="170" />
        <el-table-column prop="endTime" label="结束时间" min-width="170" />
        <el-table-column prop="booker" label="预订人" min-width="120" />
        <el-table-column prop="purpose" label="用途" min-width="150" show-overflow-tooltip />
        <el-table-column label="费用" width="100" align="right">
          <template #default="{ row }">{{ row.fee != null ? `¥${row.fee}` : '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status]?.type || 'info'">
              {{ statusMap[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!slotsLoading && slots.length === 0" class="empty-tip">当天暂无预订</div>
    </div>

    <!-- 预订记录(全部,可按资源/状态筛选,支持取消/完成) -->
    <div class="table-card" style="margin-top: 16px">
      <div class="toolbar">
        <span class="section-title">预订记录</span>
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 140px" @change="loadBookings">
          <el-option label="已预订" :value="1" />
          <el-option label="已取消" :value="2" />
          <el-option label="已完成" :value="3" />
        </el-select>
      </div>
      <el-table :data="bookingList" v-loading="bookingLoading" border stripe>
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column label="资源" min-width="150">
          <template #default="{ row }">{{ resourceName(row.resourceId) }}</template>
        </el-table-column>
        <el-table-column prop="startTime" label="开始时间" min-width="170" />
        <el-table-column prop="endTime" label="结束时间" min-width="170" />
        <el-table-column prop="booker" label="预订人" min-width="120" />
        <el-table-column prop="purpose" label="用途" min-width="150" show-overflow-tooltip />
        <el-table-column label="费用" width="100" align="right">
          <template #default="{ row }">{{ row.fee != null ? `¥${row.fee}` : '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status]?.type || 'info'">
              {{ statusMap[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-popconfirm v-if="row.status === 1" title="确认完成该预订?" @confirm="finishBooking(row)">
              <template #reference><el-button link type="primary">完成</el-button></template>
            </el-popconfirm>
            <el-popconfirm v-if="row.status === 1" title="确认取消该预订?" @confirm="cancelBooking(row)">
              <template #reference><el-button link type="danger">取消</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                     :total="bookingTotal" v-model:current-page="query.pageNo"
                     v-model:page-size="query.pageSize" :page-sizes="[10,20,50]" @change="loadBookings" />
    </div>

    <!-- 新增预订弹窗 -->
    <el-dialog v-model="dialogVisible" title="新增预订" width="480px">
      <el-form :model="form" label-width="90px" ref="formRef" :rules="rules">
        <el-form-item label="资源" prop="resourceId">
          <el-select v-model="form.resourceId" placeholder="请选择资源" style="width: 100%">
            <el-option v-for="r in resources" :key="r.id" :label="`${r.name}(${typeLabel(r.type)})`" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="预订人" prop="booker"><el-input v-model="form.booker" /></el-form-item>
        <el-form-item label="开始时间" prop="startTime">
          <el-date-picker v-model="form.startTime" type="datetime" placeholder="选择开始时间"
                          value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束时间" prop="endTime">
          <el-date-picker v-model="form.endTime" type="datetime" placeholder="选择结束时间"
                          value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="用途"><el-input v-model="form.purpose" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitBook">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { resourceApi, bookingApi } from '@/api/rsv'

const statusMap = {
  1: { label: '已预订', type: 'warning' },
  2: { label: '已取消', type: 'info' },
  3: { label: '已完成', type: 'success' }
}
const typeLabel = (type) => ({ MEETING: '会议室', SITE: '场地', DESK: '工位' }[type] || type)

// 资源下拉(全部)
const resources = ref([])
async function loadResources() {
  const res = await resourceApi.page({ pageNo: 1, pageSize: 100 })
  resources.value = res.records || []
}
function resourceName(id) {
  return resources.value.find((r) => r.id === id)?.name || id
}

// 当日时段查询
function today() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
const filter = reactive({ resourceId: null, date: today() })
const slots = ref([])
const slotsLoading = ref(false)
async function loadSlots() {
  if (!filter.resourceId || !filter.date) {
    slots.value = []
    return
  }
  slotsLoading.value = true
  try {
    slots.value = await bookingApi.slots(filter.resourceId, `${filter.date} 00:00:00`, `${filter.date} 23:59:59`)
  } finally {
    slotsLoading.value = false
  }
}

// 预订记录列表
const bookingLoading = ref(false)
const bookingList = ref([])
const bookingTotal = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, status: null })
async function loadBookings() {
  bookingLoading.value = true
  try {
    const res = await bookingApi.page(query)
    bookingList.value = res.records || []
    bookingTotal.value = res.total || 0
  } finally {
    bookingLoading.value = false
  }
}

// 新增预订
const formRef = ref()
const dialogVisible = ref(false)
const defaultForm = () => ({ resourceId: null, booker: '', startTime: '', endTime: '', purpose: '' })
const form = reactive(defaultForm())
const rules = {
  resourceId: [{ required: true, message: '请选择资源', trigger: 'change' }],
  booker: [{ required: true, message: '请输入预订人', trigger: 'blur' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }]
}
function openBookDialog() {
  Object.assign(form, defaultForm())
  if (filter.resourceId) form.resourceId = filter.resourceId
  dialogVisible.value = true
}
async function submitBook() {
  await formRef.value.validate()
  // 时段冲突由后端 BizException 拦截,request 拦截器自动 ElMessage 提示错误信息
  await bookingApi.book(form)
  ElMessage.success('预订成功')
  dialogVisible.value = false
  await Promise.all([loadSlots(), loadBookings()])
}

async function cancelBooking(row) {
  await bookingApi.cancel(row.id)
  ElMessage.success('已取消')
  await Promise.all([loadSlots(), loadBookings()])
}
async function finishBooking(row) {
  await bookingApi.finish(row.id)
  ElMessage.success('已完成')
  await Promise.all([loadSlots(), loadBookings()])
}

onMounted(async () => {
  await loadResources()
  await loadBookings()
})
</script>

<style scoped>
.section-title { font-size: 15px; font-weight: 600; color: #303133; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.pager { margin-top: 16px; justify-content: flex-end; }
.empty-tip { text-align: center; color: #909399; padding: 16px 0; }
</style>
