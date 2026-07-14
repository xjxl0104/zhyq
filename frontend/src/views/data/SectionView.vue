<template>
  <div class="page-container">
    <!-- 顶部工具条:项目 + 楼宇 + 图例 -->
    <div class="toolbar">
      <div class="filters">
        <el-select v-model="projectId" placeholder="选择项目" style="width: 200px" @change="onProjectChange">
          <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
        <el-select v-model="buildingId" placeholder="选择楼宇" style="width: 200px" @change="loadSection">
          <el-option v-for="b in buildings" :key="b.id" :label="b.name" :value="b.id" />
        </el-select>
      </div>
      <div class="legend">
        <span v-for="lg in legends" :key="lg.label" class="legend-item">
          <i class="legend-dot" :style="{ background: lg.color }"></i>{{ lg.label }}
        </span>
      </div>
    </div>

    <!-- 楼宇剖面网格 -->
    <div v-loading="loading" class="section-card">
      <div v-if="floorRows.length" class="floors">
        <div v-for="fr in floorRows" :key="fr.floor.id" class="floor-row">
          <div class="floor-badge">
            <div class="fb-name">{{ fr.floor.name || (fr.floor.floorNo + 'F') }}</div>
            <div class="fb-cnt">{{ fr.rooms.length }} 间</div>
          </div>
          <div class="room-grid">
            <div v-for="rm in fr.rooms" :key="rm.id" class="room-cell"
                 :style="cellStyle(rm.status)" @click="openRoom(rm)">
              <div class="rc-no">{{ rm.roomNo }}</div>
              <div class="rc-area">{{ fmtArea(rm.rentArea) }}</div>
            </div>
            <el-empty v-if="!fr.rooms.length" description="本层暂无房间" :image-size="50" />
          </div>
        </div>
      </div>
      <el-empty v-else :description="buildingId ? '该楼宇暂无楼层数据' : '请选择项目和楼宇'" :image-size="90" />
    </div>

    <!-- 底部统计条 -->
    <div v-if="floorRows.length" class="summary-bar">
      <div class="sm-item"><span class="sm-num">{{ summary.total }}</span><span class="sm-lab">总房间</span></div>
      <div class="sm-item"><span class="sm-num" style="color:#4f46e5">{{ summary.rented }}</span><span class="sm-lab">在租</span></div>
      <div class="sm-item"><span class="sm-num" style="color:#16a34a">{{ summary.vacant }}</span><span class="sm-lab">可租</span></div>
      <div class="sm-item"><span class="sm-num" style="color:#f59e0b">{{ summary.rate }}%</span><span class="sm-lab">出租率</span></div>
    </div>

    <!-- 房间详情 -->
    <el-dialog v-model="dialogVisible" title="房源详情" width="480px">
      <el-descriptions v-if="current" :column="1" border>
        <el-descriptions-item label="房号">{{ current.roomNo }}</el-descriptions-item>
        <el-descriptions-item label="编码">{{ current.code || '—' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :color="statusColor(current.status)" style="color:#fff;border:none">{{ statusLabel(current.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="所属楼宇">{{ currentBuildingName }}</el-descriptions-item>
        <el-descriptions-item label="计租面积">{{ fmtArea(current.rentArea) }}</el-descriptions-item>
        <el-descriptions-item label="使用面积">{{ fmtArea(current.useArea) }}</el-descriptions-item>
        <el-descriptions-item label="朝向">{{ current.orientation || '—' }}</el-descriptions-item>
        <el-descriptions-item label="装修">{{ current.decoration || '—' }}</el-descriptions-item>
        <el-descriptions-item label="租金底价">{{ current.basePrice != null ? '¥' + current.basePrice : '—' }}</el-descriptions-item>
        <el-descriptions-item label="物业费">{{ current.propertyFee != null ? '¥' + current.propertyFee : '—' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { projectApi, buildingApi, floorApi, roomApi } from '@/api/building'
import { ROOM_STATUS_LEGENDS as legends, roomStatusLabel, roomStatusColor } from '@/constants/roomStatus'

const projects = ref([])
const buildings = ref([])
const floorRows = ref([])
const projectId = ref(null)
const buildingId = ref(null)
const loading = ref(false)

const dialogVisible = ref(false)
const current = ref(null)

const statusLabel = roomStatusLabel
const statusColor = roomStatusColor
const fmtArea = (v) => (v == null ? '—' : Number(v) + ' ㎡')

// 状态色浅底 + 左侧 4px 状态色条
function cellStyle(status) {
  const c = statusColor(status)
  return { background: hexToSoft(c), borderLeft: `4px solid ${c}` }
}
function hexToSoft(hex) {
  const h = hex.replace('#', '')
  const r = parseInt(h.slice(0, 2), 16)
  const g = parseInt(h.slice(2, 4), 16)
  const b = parseInt(h.slice(4, 6), 16)
  return `rgba(${r},${g},${b},0.10)`
}

const currentBuildingName = computed(() => {
  const b = buildings.value.find(x => x.id === buildingId.value)
  return b ? b.name : '—'
})

const summary = computed(() => {
  let total = 0, rented = 0, vacant = 0
  floorRows.value.forEach(fr => fr.rooms.forEach(rm => {
    total++
    if (rm.status === 5) rented++
    else if (rm.status === 1) vacant++
  }))
  const rentable = rented + vacant
  const rate = rentable ? Math.round(rented * 1000 / rentable) / 10 : 0
  return { total, rented, vacant, rate }
})

async function onProjectChange() {
  buildingId.value = null
  floorRows.value = []
  buildings.value = projectId.value ? (await buildingApi.list(projectId.value)) || [] : []
}

async function loadSection() {
  if (!buildingId.value) { floorRows.value = []; return }
  loading.value = true
  try {
    const floors = (await floorApi.list(buildingId.value)) || []
    // 楼层从高到低
    floors.sort((a, b) => (b.floorNo || 0) - (a.floorNo || 0))
    const rows = []
    for (const f of floors) {
      const rooms = (await roomApi.list(f.id)) || []
      rows.push({ floor: f, rooms })
    }
    floorRows.value = rows
  } finally {
    loading.value = false
  }
}

function openRoom(rm) {
  current.value = rm
  dialogVisible.value = true
}

onMounted(async () => {
  projects.value = (await projectApi.list()) || []
  if (projects.value.length) {
    projectId.value = projects.value[0].id
    await onProjectChange()
    if (buildings.value.length) {
      buildingId.value = buildings.value[0].id
      await loadSection()
    }
  }
})
</script>

<style scoped>
.toolbar {
  display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px;
  background: var(--bg-card); border: 1px solid var(--border); border-radius: var(--radius);
  padding: 14px 20px; margin-bottom: 16px;
}
.filters { display: flex; gap: 12px; }
.legend { display: flex; flex-wrap: wrap; gap: 16px; }
.legend-item { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--text-secondary); }
.legend-dot { width: 12px; height: 12px; border-radius: 3px; display: inline-block; }

.section-card {
  background: var(--bg-card); border: 1px solid var(--border); border-radius: var(--radius);
  padding: 8px 20px 20px; min-height: 200px;
}
.floors { display: flex; flex-direction: column; }
.floor-row { display: flex; gap: 16px; padding: 16px 0; border-bottom: 1px dashed var(--border); }
.floor-row:last-child { border-bottom: none; }
.floor-badge {
  flex-shrink: 0; width: 72px; display: flex; flex-direction: column; align-items: center; justify-content: center;
  background: var(--el-color-primary-light-9); border: 1px solid var(--border); border-radius: var(--radius-sm); padding: 10px 0;
}
.fb-name { font-size: 15px; font-weight: 700; color: var(--brand); }
.fb-cnt { font-size: 12px; color: var(--text-muted); margin-top: 4px; }
.room-grid {
  flex: 1; display: grid; grid-template-columns: repeat(auto-fill, minmax(110px, 1fr)); gap: 10px;
}
.room-cell {
  border-radius: var(--radius-sm); padding: 12px 12px; cursor: pointer; transition: all 0.2s;
  box-shadow: var(--shadow-card);
}
.room-cell:hover { transform: translateY(-3px); box-shadow: var(--shadow-pop); }
.rc-no { font-size: 15px; font-weight: 600; color: var(--text-title); }
.rc-area { font-size: 12px; color: var(--text-secondary); margin-top: 4px; }

.summary-bar {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px;
  background: var(--bg-card); border: 1px solid var(--border); border-radius: var(--radius);
  padding: 16px 20px; margin-top: 16px;
}
.sm-item { display: flex; flex-direction: column; align-items: center; }
.sm-num { font-size: 26px; font-weight: 700; color: var(--text-title); }
.sm-lab { font-size: 13px; color: var(--text-secondary); margin-top: 4px; }
</style>
