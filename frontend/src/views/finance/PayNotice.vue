<template>
  <div class="page-container">
    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
            <el-option label="待发送" :value="1" />
            <el-option label="已发送" :value="2" />
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
        <el-popconfirm title="将对逾期/待收付账单批量生成收款通知,确认?" width="240" @confirm="generate">
          <template #reference>
            <el-button type="primary"><el-icon><Bell /></el-icon>一键生成通知</el-button>
          </template>
        </el-popconfirm>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="noticeNo" label="通知单号" min-width="180" />
        <el-table-column prop="billId" label="账单ID" width="90" />
        <el-table-column label="欠款金额" width="140" align="right">
          <template #default="{ row }"><span class="owe">¥{{ money(row.amount) }}</span></template>
        </el-table-column>
        <el-table-column label="渠道" width="110">
          <template #default="{ row }"><el-tag type="info">{{ row.sendChannel || '站内信' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 2 ? 'success' : 'warning'">
              {{ row.status === 2 ? '已发送' : '待发送' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sendTime" label="发送时间" width="180" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 1" link type="primary" @click="openSend(row)">发送</el-button>
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

    <!-- 发送弹窗 -->
    <el-dialog v-model="sendDialog.visible" title="发送收款通知" width="420px">
      <el-form label-width="80px">
        <el-form-item label="通知单号">
          <span>{{ sendDialog.noticeNo }}</span>
        </el-form-item>
        <el-form-item label="发送渠道">
          <el-select v-model="sendDialog.channel" style="width: 100%">
            <el-option label="站内信" value="站内信" />
            <el-option label="短信" value="短信" />
            <el-option label="微信" value="微信" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sendDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="doSend">确认发送</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { payNoticeApi } from '@/api/finance'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, status: null })

function money(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function load() {
  loading.value = true
  try {
    const res = await payNoticeApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
function reset() {
  Object.assign(query, { pageNo: 1, status: null })
  load()
}

async function generate() {
  const n = await payNoticeApi.generate()
  ElMessage.success(`已生成 ${n ?? 0} 条通知`)
  load()
}

const sendDialog = reactive({ visible: false, id: null, noticeNo: '', channel: '站内信' })
function openSend(row) {
  Object.assign(sendDialog, { visible: true, id: row.id, noticeNo: row.noticeNo, channel: row.sendChannel || '站内信' })
}
async function doSend() {
  await payNoticeApi.send(sendDialog.id, { sendChannel: sendDialog.channel })
  ElMessage.success('发送成功')
  sendDialog.visible = false
  load()
}

async function remove(id) {
  await payNoticeApi.remove(id)
  ElMessage.success('删除成功')
  load()
}

onMounted(load)
</script>

<style scoped>
.owe { color: #e5484d; font-weight: 650; font-variant-numeric: tabular-nums; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
