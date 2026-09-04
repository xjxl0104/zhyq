<template>
  <div class="page-container">
    <!-- 上半:会议室列表 -->
    <div class="table-card">
      <div class="toolbar">
        <span class="section-title">会议室</span>
        <el-button type="primary" @click="openRoomDialog()"><el-icon><Plus /></el-icon>新增会议室</el-button>
      </div>
      <el-table :data="rooms" v-loading="roomLoading" border stripe>
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column label="容量" width="100">
          <template #default="{ row }">{{ row.capacity || 0 }} 人</template>
        </el-table-column>
        <el-table-column prop="equipment" label="设备" min-width="160" show-overflow-tooltip />
        <el-table-column prop="openTime" label="开放时间" min-width="140" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '可用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openRoomDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除?" @confirm="removeRoom(row.id)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next"
                     :total="roomTotal" v-model:current-page="roomQuery.pageNo"
                     v-model:page-size="roomQuery.pageSize" @change="loadRooms" />
    </div>

    <!-- 下半:预约记录 -->
    <div class="table-card" style="margin-top: 16px">
      <div class="toolbar">
        <span class="section-title">预约记录</span>
        <div>
          <el-select v-model="bookingQuery.roomId" placeholder="按会议室筛选" clearable
                     style="width: 200px; margin-right: 12px" @change="loadBookings">
            <el-option v-for="r in roomOptions" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
          <el-button type="primary" @click="openBookingDialog()"><el-icon><Plus /></el-icon>新增预约</el-button>
        </div>
      </div>
      <el-table :data="bookings" v-loading="bookingLoading" border stripe>
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column label="会议室" min-width="140">
          <template #default="{ row }">{{ roomName(row.roomId) }}</template>
        </el-table-column>
        <el-table-column prop="booker" label="预约人" min-width="120" />
        <el-table-column prop="startTime" label="开始时间" min-width="170" />
        <el-table-column prop="endTime" label="结束时间" min-width="170" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="bookingStatusMap[row.status]?.type || 'info'">
              {{ bookingStatusMap[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-popconfirm title="确认删除?" @confirm="removeBooking(row.id)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next"
                     :total="bookingTotal" v-model:current-page="bookingQuery.pageNo"
                     v-model:page-size="bookingQuery.pageSize" @change="loadBookings" />
    </div>

    <!-- 会议室弹窗 -->
    <el-dialog v-model="roomDialog.visible" :title="roomDialog.title" width="480px">
      <el-form :model="roomForm" label-width="90px" ref="roomFormRef" :rules="roomRules">
        <el-form-item label="名称" prop="name"><el-input v-model="roomForm.name" /></el-form-item>
        <el-form-item label="容量">
          <el-input-number v-model="roomForm.capacity" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="设备"><el-input v-model="roomForm.equipment" placeholder="投影/白板/视频会议" /></el-form-item>
        <el-form-item label="开放时间"><el-input v-model="roomForm.openTime" placeholder="09:00-18:00" /></el-form-item>
        <el-form-item label="计费规则"><el-input v-model="roomForm.feeRule" /></el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="roomForm.status">
            <el-radio :value="1">可用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roomDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitRoom">确定</el-button>
      </template>
    </el-dialog>

    <!-- 预约弹窗 -->
    <el-dialog v-model="bookingDialog.visible" title="新增预约" width="480px">
      <el-form :model="bookingForm" label-width="90px" ref="bookingFormRef" :rules="bookingRules">
        <el-form-item label="会议室" prop="roomId">
          <el-select v-model="bookingForm.roomId" placeholder="请选择会议室" style="width: 100%">
            <el-option v-for="r in roomOptions" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="预约人" prop="booker"><el-input v-model="bookingForm.booker" /></el-form-item>
        <el-form-item label="开始时间" prop="startTime">
          <el-date-picker v-model="bookingForm.startTime" type="datetime" placeholder="选择开始时间"
                          value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束时间" prop="endTime">
          <el-date-picker v-model="bookingForm.endTime" type="datetime" placeholder="选择结束时间"
                          value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="附件"><FileUpload v-model="attachFiles" biz-type="meeting" :biz-id="bookingForm.id" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bookingDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitBooking">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { meetingRoomApi, bookingApi } from '@/api/property'
import { fileApi } from '@/api/file'
import FileUpload from '@/components/FileUpload.vue'

const bookingStatusMap = {
  1: { label: '待核销', type: 'warning' },
  2: { label: '已核销', type: 'success' },
  3: { label: '已取消', type: 'info' },
  4: { label: '已退款', type: 'info' }
}

// 会议室下拉(全部)
const roomOptions = ref([])
async function loadRoomOptions() {
  roomOptions.value = await meetingRoomApi.list()
}
function roomName(id) {
  return roomOptions.value.find((r) => r.id === id)?.name || id
}

// 会议室列表
const roomLoading = ref(false)
const rooms = ref([])
const roomTotal = ref(0)
const roomQuery = reactive({ pageNo: 1, pageSize: 10 })
async function loadRooms() {
  roomLoading.value = true
  try {
    const res = await meetingRoomApi.page(roomQuery)
    rooms.value = res.records
    roomTotal.value = res.total
  } finally {
    roomLoading.value = false
  }
}

const roomFormRef = ref()
const roomDialog = reactive({ visible: false, title: '' })
const defaultRoom = () => ({ id: null, name: '', capacity: 0, equipment: '', openTime: '', feeRule: '', status: 1 })
const roomForm = reactive(defaultRoom())
const roomRules = { name: [{ required: true, message: '请输入名称', trigger: 'blur' }] }
function openRoomDialog(row) {
  roomDialog.visible = true
  roomDialog.title = row ? '编辑会议室' : '新增会议室'
  if (row) Object.assign(roomForm, row)
  else Object.assign(roomForm, defaultRoom())
}
async function submitRoom() {
  await roomFormRef.value.validate()
  if (roomForm.id) await meetingRoomApi.update(roomForm)
  else await meetingRoomApi.add(roomForm)
  ElMessage.success('保存成功')
  roomDialog.visible = false
  await Promise.all([loadRooms(), loadRoomOptions()])
}
async function removeRoom(id) {
  await meetingRoomApi.remove(id)
  ElMessage.success('删除成功')
  await Promise.all([loadRooms(), loadRoomOptions()])
}

// 预约记录
const bookingLoading = ref(false)
const bookings = ref([])
const bookingTotal = ref(0)
const bookingQuery = reactive({ pageNo: 1, pageSize: 10, roomId: null })
async function loadBookings() {
  bookingLoading.value = true
  try {
    const res = await bookingApi.page(bookingQuery)
    bookings.value = res.records
    bookingTotal.value = res.total
  } finally {
    bookingLoading.value = false
  }
}

const bookingFormRef = ref()
const bookingDialog = reactive({ visible: false })
const defaultBooking = () => ({ roomId: null, booker: '', startTime: '', endTime: '', status: 1 })
const bookingForm = reactive(defaultBooking())
const attachFiles = ref([])
const bookingRules = {
  roomId: [{ required: true, message: '请选择会议室', trigger: 'change' }],
  booker: [{ required: true, message: '请输入预约人', trigger: 'blur' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }]
}
function openBookingDialog() {
  bookingDialog.visible = true
  Object.assign(bookingForm, defaultBooking())
  attachFiles.value = []
}
async function submitBooking() {
  await bookingFormRef.value.validate()
  // 冲突校验由后端完成:重叠时段后端抛 BizException,request 拦截器自动 ElMessage 提示
  const newId = await bookingApi.add(bookingForm)
  const pendingIds = (attachFiles.value || []).filter(f => f && f.id && !f.bizId).map(f => f.id)
  if (newId && pendingIds.length) {
    try { await fileApi.attach('meeting', newId, pendingIds) } catch (e) { /* 忽略,不阻断保存 */ }
  }
  ElMessage.success('预约成功')
  bookingDialog.visible = false
  loadBookings()
}
async function removeBooking(id) {
  await bookingApi.remove(id)
  ElMessage.success('删除成功')
  loadBookings()
}

onMounted(async () => {
  await loadRoomOptions()
  await Promise.all([loadRooms(), loadBookings()])
})
</script>

<style scoped>
.section-title { font-size: 15px; font-weight: 600; color: #303133; }
.toolbar { display: flex; justify-content: space-between; align-items: center; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
