<template>
  <div class="page-container">
    <div class="table-card">
      <div class="toolbar"><h3 style="margin:0">我的建议</h3></div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
        <el-table-column label="类型" width="80">
          <template #default="{ row }">{{ typeMap[row.type] }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusStyle[row.status]">{{ statusMap[row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="提交时间" width="170" />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewDetail(row.id)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next"
                     :total="total" v-model:current-page="page" :page-size="20" @change="load" />
    </div>

    <el-dialog v-model="detailVisible" title="建议详情" width="600px">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="标题" :span="2">{{ detail.suggestion.title }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ typeMap[detail.suggestion.type] }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusMap[detail.suggestion.status] }}</el-descriptions-item>
          <el-descriptions-item label="详细说明" :span="2">{{ detail.suggestion.content || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="detail.images.length" style="margin-top:12px">
          <p style="font-weight:500;margin-bottom:8px">截图</p>
          <el-image v-for="img in detail.images" :key="img.id"
            :src="`/api/file/download/${img.fileId}`"
            :preview-src-list="detail.images.map(i => `/api/file/download/${i.fileId}`)"
            style="width:80px;height:80px;margin-right:8px" fit="cover" />
        </div>
        <div v-if="detail.logs.length" style="margin-top:16px">
          <p style="font-weight:500;margin-bottom:8px">处理记录</p>
          <el-timeline>
            <el-timeline-item v-for="log in detail.logs" :key="log.id" :timestamp="log.createdAt" placement="top">
              <span>{{ log.operatorName }} - {{ log.action }}</span>
              <span v-if="log.remark"> | {{ log.remark }}</span>
            </el-timeline-item>
          </el-timeline>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { suggestionApi } from '@/api/suggestion'

const typeMap = { 1: 'Bug', 2: '建议', 3: '其他' }
const statusMap = { 1: '待处理', 2: '已确认', 3: '处理中', 4: '已解决', 5: '已采纳', 6: '已关闭' }
const statusStyle = { 1: 'info', 2: 'warning', 3: '', 4: 'success', 5: 'success', 6: 'info' }

const list = ref([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const detailVisible = ref(false)
const detail = ref(null)

async function load() {
  loading.value = true
  try {
    const res = await suggestionApi.mine({ page: page.value, size: 20 })
    list.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

async function viewDetail(id) {
  const res = await suggestionApi.mineDetail(id)
  detail.value = res.data
  detailVisible.value = true
}

onMounted(load)
</script>
