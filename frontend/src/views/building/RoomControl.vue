<template>
  <div class="room-control">
    <!-- 左侧 项目-楼宇树 -->
    <div class="tree-panel">
      <div class="tree-title">项目 / 楼宇</div>
      <el-tree :data="treeData" :props="{ label: 'label', children: 'children' }"
               node-key="key" highlight-current :expand-on-click-node="false"
               default-expand-all @node-click="onNodeClick" v-loading="treeLoading" />
    </div>

    <!-- 右侧 -->
    <div class="main-panel">
      <!-- 统计卡 -->
      <div class="stat-cards">
        <div class="stat-card"><div class="num" style="color:#2563eb">{{ stats.rentedCount ?? 0 }}</div><div class="label">在租房间</div></div>
        <div class="stat-card"><div class="num">{{ stats.totalRoom ?? 0 }}</div><div class="label">房源数量</div></div>
        <div class="stat-card"><div class="num" style="color:#10b981">{{ stats.rentRate ?? 0 }}%</div><div class="label">出租率</div></div>
        <div class="stat-card"><div class="num">{{ fmt(stats.manageArea) }}</div><div class="label">管理面积(㎡)</div></div>
        <div class="stat-card"><div class="num" style="color:#f59e0b">{{ fmt(stats.rentArea) }}</div><div class="label">在租面积(㎡)</div></div>
        <div class="stat-card"><div class="num" style="color:#8b5cf6">¥{{ fmt(stats.avgPrice) }}</div><div class="label">在租均价(元/㎡)</div></div>
      </div>

      <!-- 房源表格 -->
      <div class="table-card">
        <div class="toolbar">
          <span class="panel-title">{{ currentLabel }} 房源清单</span>
          <div class="legend">
            <span v-for="s in statusLegend" :key="s.v"><i :class="'dot dot-' + s.type"></i>{{ s.label }}</span>
          </div>
          <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增房间</el-button>
        </div>
        <el-table :data="rooms" v-loading="loading" border stripe>
          <el-table-column prop="roomNo" label="房号" width="120" />
          <el-table-column prop="code" label="房间编码" width="140" />
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="计租面积" width="110"><template #default="{ row }">{{ row.rentArea }} ㎡</template></el-table-column>
          <el-table-column label="建筑面积" width="110"><template #default="{ row }">{{ row.buildArea }} ㎡</template></el-table-column>
          <el-table-column prop="orientation" label="朝向" width="80" />
          <el-table-column prop="decoration" label="装修" width="90" />
          <el-table-column label="租金底价" width="120"><template #default="{ row }">¥{{ row.basePrice }}/㎡</template></el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
              <el-popconfirm title="确认删除?" @confirm="remove(row.id)">
                <template #reference><el-button link type="danger">删除</el-button></template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination class="pager" background layout="total, prev, pager, next"
                       :total="total" v-model:current-page="query.pageNo"
                       v-model:page-size="query.pageSize" @change="loadRooms" />
      </div>
    </div>

    <el-dialog v-model="dialog.visible" :title="dialog.title" width="560px">
      <el-form :model="form" label-width="90px" ref="formRef" :rules="rules">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="房间编码" prop="code"><el-input v-model="form.code" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="房号" prop="roomNo"><el-input v-model="form.roomNo" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="状态">
            <el-select v-model="form.status" style="width:100%">
              <el-option v-for="s in allStatus" :key="s.v" :label="s.label" :value="s.v" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="用途"><el-input v-model="form.usageType" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="计租面积"><el-input-number v-model="form.rentArea" :min="0" :precision="2" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="建筑面积"><el-input-number v-model="form.buildArea" :min="0" :precision="2" style="width:100%" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="朝向"><el-input v-model="form.orientation" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="装修"><el-input v-model="form.decoration" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="租金底价"><el-input-number v-model="form.basePrice" :min="0" :precision="2" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="物业费"><el-input-number v-model="form.propertyFee" :min="0" :precision="2" style="width:100%" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { projectApi, buildingApi, floorApi, roomApi } from '@/api/building'

// 状态映射(附录B)
const allStatus = [
  { v: 0, label: '未配置', type: 'info' }, { v: 1, label: '可租', type: 'success' },
  { v: 2, label: '锁定', type: 'warning' }, { v: 3, label: '意向占用', type: 'warning' },
  { v: 4, label: '签约中', type: 'warning' }, { v: 5, label: '在租', type: 'primary' },
  { v: 6, label: '退租处理中', type: 'danger' }, { v: 7, label: '维修', type: 'danger' },
  { v: 8, label: '停用', type: 'danger' }
]
const statusLegend = [allStatus[1], allStatus[5], allStatus[3], allStatus[7]]
const statusMap = Object.fromEntries(allStatus.map(s => [s.v, s]))
const statusText = (v) => statusMap[v]?.label || '-'
const statusType = (v) => statusMap[v]?.type || 'info'
const fmt = (v) => Number(v || 0).toLocaleString('zh-CN', { maximumFractionDigits: 1 })

const treeLoading = ref(false)
const treeData = ref([])
const currentProject = ref(null)
const currentBuilding = ref(null)
const currentLabel = ref('全部')

async function loadTree() {
  treeLoading.value = true
  try {
    const projects = await projectApi.list()
    const nodes = []
    for (const p of projects) {
      const buildings = await buildingApi.list(p.id)
      nodes.push({
        key: 'p-' + p.id, label: p.name, projectId: p.id,
        children: buildings.map(b => ({ key: 'b-' + b.id, label: b.name, projectId: p.id, buildingId: b.id }))
      })
    }
    treeData.value = nodes
  } finally { treeLoading.value = false }
}

function onNodeClick(node) {
  currentProject.value = node.projectId ?? null
  currentBuilding.value = node.buildingId ?? null
  currentLabel.value = node.label
  query.pageNo = 1
  loadRooms(); loadStats()
}

const stats = reactive({})
async function loadStats() {
  Object.assign(stats, await roomApi.stats(currentProject.value))
}

const loading = ref(false)
const rooms = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10 })
async function loadRooms() {
  loading.value = true
  try {
    const params = { ...query }
    if (currentProject.value) params.projectId = currentProject.value
    if (currentBuilding.value) params.buildingId = currentBuilding.value
    const res = await roomApi.page(params)
    rooms.value = res.records; total.value = res.total
  } finally { loading.value = false }
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const blank = { id: null, code: '', roomNo: '', status: 1, usageType: '办公', rentArea: 0, buildArea: 0, orientation: '', decoration: '', basePrice: 0, propertyFee: 0 }
const form = reactive({ ...blank })
const rules = {
  code: [{ required: true, message: '请输入房间编码', trigger: 'blur' }],
  roomNo: [{ required: true, message: '请输入房号', trigger: 'blur' }]
}
function openDialog(row) {
  dialog.visible = true; dialog.title = row ? '编辑房间' : '新增房间'
  Object.assign(form, row ? row : blank)
}
async function submit() {
  await formRef.value.validate()
  form.id ? await roomApi.update(form) : await roomApi.add(form)
  ElMessage.success('保存成功'); dialog.visible = false; loadRooms(); loadStats()
}
async function remove(id) { await roomApi.remove(id); ElMessage.success('删除成功'); loadRooms(); loadStats() }

onMounted(() => { loadTree(); loadRooms(); loadStats() })
</script>

<style scoped>
.room-control { display: flex; gap: 16px; }
.tree-panel { width: 240px; background: #fff; border-radius: 8px; padding: 16px; flex-shrink: 0; align-self: flex-start; }
.tree-title { font-weight: 600; margin-bottom: 12px; }
.main-panel { flex: 1; min-width: 0; }
.panel-title { font-weight: 600; margin-right: auto; }
.toolbar { align-items: center; gap: 16px; }
.legend { display: flex; gap: 12px; font-size: 12px; color: #6b7280; }
.dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 4px; }
.dot-success { background: #10b981; } .dot-primary { background: #2563eb; }
.dot-warning { background: #f59e0b; } .dot-danger { background: #ef4444; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
