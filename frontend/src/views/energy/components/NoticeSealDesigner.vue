<template>
  <el-dialog
    :model-value="modelValue"
    :title="title || '通知单电子印章模板排版'"
    width="1140px"
    top="3vh"
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div class="designer-bar">
      <div class="bar-item">
        <span class="bar-label">选择电子印章</span>
        <el-select v-model="draft.sealId" placeholder="请选择电子印章" style="width: 260px" clearable @change="onSealChange">
          <el-option v-for="s in seals" :key="s.id" :label="s.name" :value="s.id">
            <div class="seal-opt">
              <img v-if="s.imageData" :src="s.imageData" class="seal-opt-img" alt="" />
              <span>{{ s.name }}</span>
            </div>
          </el-option>
        </el-select>
      </div>
      <div class="bar-item">
        <el-switch v-model="draft.enabled" :active-value="1" :inactive-value="0" active-text="输出通知单自动盖章" />
      </div>
      <div class="bar-item slider-item">
        <span class="bar-label">缩放 {{ Math.round(draft.scale * 100) }}%</span>
        <el-slider v-model="draft.scale" :min="0.5" :max="2.0" :step="0.02" style="width: 120px" />
      </div>
      <div class="bar-item slider-item">
        <span class="bar-label">旋转 {{ draft.rotation }}°</span>
        <el-slider v-model="draft.rotation" :min="-30" :max="30" :step="1" style="width: 96px" />
      </div>
      <div class="bar-item slider-item">
        <span class="bar-label">透明度 {{ Math.round(draft.opacity * 100) }}%</span>
        <el-slider v-model="draft.opacity" :min="0.4" :max="1" :step="0.02" style="width: 90px" />
      </div>
      <el-button size="small" @click="resetPosition">归位落款处</el-button>
    </div>

    <div class="designer-tip">
      <span>提示：直接用鼠标<b>按住红章拖动</b>可调整位置，拖动印章<b>右下角蓝色圆点</b>或上方滑块可缩放大小；保存后即成为模板的一部分，后续预览与打印缴费通知单将自动附带该电子印章。</span>
      <span class="coord-badge">位置 X: {{ draft.posX.toFixed(1) }}% · Y: {{ draft.posY.toFixed(1) }}%</span>
    </div>

    <div class="stage-scroll">
      <div ref="stageRef" class="paper-stage">
        <div class="notice-render-wrap" v-html="scopedHtml"></div>
        <div
          v-if="activeSeal && draft.enabled === 1"
          class="seal-draggable"
          :class="{ dragging: dragMode }"
          :style="sealBoxStyle"
          @pointerdown.prevent="startDrag"
        >
          <img :src="activeSeal.imageData" :alt="activeSeal.name" class="seal-overlay-img" draggable="false" />
          <div class="resize-handle" title="拖拽缩放印章大小" @pointerdown.stop.prevent="startResize"></div>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="dlg-foot">
        <span class="foot-note">
          <template v-if="!seals.length">暂未上传电子印章，请先前往「园区运营 → 办公管理 → 印章管理」上传。</template>
          <template v-else-if="draft.enabled === 1 && activeSeal">当前生效：{{ activeSeal.name }}</template>
          <template v-else>当前模板已关闭电子印章，保存后打印通知单将不显示印章</template>
        </span>
        <div>
          <el-button @click="$emit('update:modelValue', false)">取消</el-button>
          <el-button type="primary" :loading="saving" @click="save">保存到通知单模板</el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { sealApi } from '@/api/oa'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  templateKey: { type: String, required: true },
  title: { type: String, default: '' },
  noticeMarkup: { type: String, default: '' },
  seals: { type: Array, default: () => [] },
  placement: { type: Object, default: null },
  initialSealId: { type: [Number, String], default: null },
  pageWidthMm: { type: Number, default: 273 }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const stageRef = ref(null)
const saving = ref(false)
const dragMode = ref('')

const draft = reactive({
  sealId: null,
  enabled: 1,
  posX: 20,
  posY: 88,
  scale: 1,
  rotation: 0,
  opacity: 0.92
})

const activeSeal = computed(() => props.seals.find((s) => String(s.id) === String(draft.sealId)) || null)

// 隔离预览样式，确保和真实打印版式一致
const scopedHtml = computed(() => props.noticeMarkup || '')

const sealBoxStyle = computed(() => {
  const baseMm = activeSeal.value?.defaultWidthMm || 44
  const widthPct = (baseMm / (props.pageWidthMm || 273)) * 100 * Number(draft.scale || 1)
  return {
    left: `${draft.posX}%`,
    top: `${draft.posY}%`,
    width: `${widthPct.toFixed(2)}%`,
    transform: `translate(-50%, -50%) rotate(${draft.rotation || 0}deg)`,
    opacity: draft.opacity ?? 0.92
  }
})

watch(
  () => [props.modelValue, props.placement, props.initialSealId],
  ([visible]) => {
    if (!visible) return
    const p = props.placement || {}
    const defaultSeal = props.seals.find((s) => s.isDefault === 1) || props.seals[0]
    const targetSealId = props.initialSealId != null
      ? Number(props.initialSealId)
      : (p.sealId ?? defaultSeal?.id ?? null)
    draft.sealId = targetSealId
    draft.enabled = props.initialSealId != null ? 1 : (p.enabled ?? (targetSealId ? 1 : 0))
    draft.posX = Number(p.posX ?? 20)
    draft.posY = Number(p.posY ?? 88)
    draft.scale = Number(p.scale ?? 1)
    draft.rotation = Number(p.rotation ?? 0)
    draft.opacity = Number(p.opacity ?? 0.92)
  },
  { immediate: true }
)

function onSealChange(val) {
  if (val) draft.enabled = 1
}

function resetPosition() {
  draft.posX = 20
  draft.posY = 88
  draft.scale = 1
  draft.rotation = 0
  draft.opacity = 0.92
}

function startDrag(e) {
  const rect = stageRef.value?.getBoundingClientRect()
  if (!rect) return
  dragMode.value = 'move'
  const startX = e.clientX
  const startY = e.clientY
  const origX = draft.posX
  const origY = draft.posY

  const onMove = (ev) => {
    const dxPct = ((ev.clientX - startX) / rect.width) * 100
    const dyPct = ((ev.clientY - startY) / rect.height) * 100
    draft.posX = Math.max(4, Math.min(96, Math.round((origX + dxPct) * 10) / 10))
    draft.posY = Math.max(4, Math.min(96, Math.round((origY + dyPct) * 10) / 10))
  }
  const onUp = () => {
    dragMode.value = ''
    window.removeEventListener('pointermove', onMove)
    window.removeEventListener('pointerup', onUp)
  }
  window.addEventListener('pointermove', onMove)
  window.addEventListener('pointerup', onUp)
}

function startResize(e) {
  const rect = stageRef.value?.getBoundingClientRect()
  if (!rect) return
  dragMode.value = 'resize'
  const startX = e.clientX
  const origScale = draft.scale

  const onMove = (ev) => {
    const delta = (ev.clientX - startX) / 110
    draft.scale = Math.max(0.5, Math.min(2.0, Math.round((origScale + delta) * 100) / 100))
  }
  const onUp = () => {
    dragMode.value = ''
    window.removeEventListener('pointermove', onMove)
    window.removeEventListener('pointerup', onUp)
  }
  window.addEventListener('pointermove', onMove)
  window.addEventListener('pointerup', onUp)
}

async function save() {
  if (draft.enabled === 1 && !draft.sealId) {
    return ElMessage.warning('请选择一枚电子印章，或关闭「输出通知单自动盖章」')
  }
  saving.value = true
  try {
    const saved = await sealApi.savePlacement(props.templateKey, {
      sealId: draft.sealId,
      enabled: draft.enabled,
      posX: draft.posX,
      posY: draft.posY,
      scale: draft.scale,
      rotation: draft.rotation,
      opacity: draft.opacity
    })
    ElMessage.success(saved?.enabled === 1 ? '电子印章排版已保存到通知单模板' : '已关闭该模板的电子印章')
    emit('saved', saved)
    emit('update:modelValue', false)
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.designer-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 14px;
  padding: 10px 14px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  margin-bottom: 10px;
}
.bar-item {
  display: flex;
  align-items: center;
  gap: 8px;
}
.bar-label {
  font-size: 12px;
  color: #475569;
  white-space: nowrap;
}
.seal-opt {
  display: flex;
  align-items: center;
  gap: 8px;
}
.seal-opt-img {
  width: 24px;
  height: 20px;
  object-fit: contain;
}
.designer-tip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
  color: #475569;
  margin-bottom: 10px;
  padding: 0 2px;
}
.coord-badge {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  background: #eef2ff;
  color: #4338ca;
  padding: 2px 8px;
  border-radius: 999px;
  white-space: nowrap;
}
.stage-scroll {
  max-height: 62vh;
  overflow: auto;
  background: #e2e8f0;
  padding: 18px;
  border-radius: 10px;
  display: flex;
  justify-content: center;
}
.paper-stage {
  position: relative;
  width: 1020px;
  min-width: 960px;
  background: #fff;
  padding: 18px 20px 26px;
  box-shadow: 0 8px 28px rgba(15, 23, 42, 0.16);
  user-select: none;
}
.notice-render-wrap :deep(.notice) {
  position: relative;
  margin: 0 !important;
  max-width: none !important;
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  font-size: 12px;
  color: #111;
}
.notice-render-wrap :deep(h1) {
  text-align: center;
  font-size: 20px;
  margin: 0 0 4px;
  letter-spacing: 2px;
}
.notice-render-wrap :deep(.sub) {
  text-align: center;
  margin: 0 0 10px;
  color: #444;
  font-size: 12px;
}
.notice-render-wrap :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: 8px;
}
.notice-render-wrap :deep(th),
.notice-render-wrap :deep(td) {
  border: 1px solid #333;
  padding: 5px 6px;
  vertical-align: middle;
}
.notice-render-wrap :deep(th) {
  background: #f3f3f3;
  font-weight: 600;
  text-align: center;
  white-space: nowrap;
}
.notice-render-wrap :deep(.head th) { width: 11%; }
.notice-render-wrap :deep(.head td) { width: 22%; }
.notice-render-wrap :deep(.items td.c),
.notice-render-wrap :deep(.items th) { text-align: center; }
.notice-render-wrap :deep(.items td.r) { text-align: right; font-variant-numeric: tabular-nums; white-space: nowrap; }
.notice-render-wrap :deep(.items td.remark) { font-size: 11px; color: #333; }
.notice-render-wrap :deep(.foot td.note) { width: 62%; font-size: 11px; line-height: 1.6; vertical-align: top; }
.notice-render-wrap :deep(.foot th) { width: 14%; }
.notice-render-wrap :deep(.foot td.r) { text-align: right; width: 12%; }
.notice-render-wrap :deep(.foot td.total) { font-weight: 700; font-size: 14px; }
.notice-render-wrap :deep(.sign) { display: flex; justify-content: space-between; margin-top: 28px; padding: 0 8px; }
.seal-draggable {
  position: absolute;
  z-index: 30;
  cursor: move;
  border: 1.5px dashed rgba(79, 70, 229, 0.75);
  border-radius: 6px;
  background: rgba(79, 70, 229, 0.03);
  padding: 2px;
  transition: box-shadow 0.12s ease;
}
.seal-draggable:hover,
.seal-draggable.dragging {
  border-color: #4f46e5;
  box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.16);
}
.seal-overlay-img {
  display: block;
  width: 100%;
  height: auto;
  mix-blend-mode: multiply;
  pointer-events: none;
}
.resize-handle {
  position: absolute;
  right: -7px;
  bottom: -7px;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #4f46e5;
  border: 2px solid #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.3);
  cursor: nwse-resize;
}
.dlg-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}
.foot-note {
  font-size: 12px;
  color: #64748b;
}
</style>
