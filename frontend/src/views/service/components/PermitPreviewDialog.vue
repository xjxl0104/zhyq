<template>
  <el-dialog
    :model-value="modelValue"
    :title="dialogTitle"
    width="1020px"
    top="2vh"
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div class="stamp-bar">
      <div class="bar-group">
        <span class="lbl">电子印章</span>
        <el-select v-model="stamp.sealId" placeholder="选择电子印章" clearable style="width: 240px" @change="onSealPick">
          <el-option v-for="s in seals" :key="s.id" :label="s.name" :value="s.id">
            <div class="seal-opt">
              <img v-if="s.imageData" :src="s.imageData" class="seal-opt-img" alt="" />
              <span>{{ s.name }}</span>
            </div>
          </el-option>
        </el-select>
        <el-switch v-model="stamp.enabled" active-text="盖章" />
      </div>
      <div class="bar-group">
        <span class="lbl">盖章页</span>
        <el-radio-group v-model="stamp.page" size="small" @change="onPageSwitch">
          <el-radio-button :value="1">第1页(正面)</el-radio-button>
          <el-radio-button :value="2">第2页(反面)</el-radio-button>
        </el-radio-group>
      </div>
      <div class="bar-group">
        <span class="lbl">缩放 {{ Math.round(stamp.scale * 100) }}%</span>
        <el-slider v-model="stamp.scale" :min="0.5" :max="1.8" :step="0.02" style="width: 100px" />
      </div>
      <div class="bar-group">
        <span class="lbl">旋转 {{ stamp.rotation }}°</span>
        <el-slider v-model="stamp.rotation" :min="-30" :max="30" :step="1" style="width: 84px" />
      </div>
      <el-button size="small" @click="resetPos">归位审批章处</el-button>
    </div>

    <div class="tip-row">
      <span>提示：按住电子印章可拖动到任意签字/盖章栏，拖动右下角蓝点可缩放大小；点击右下角「打印 / 导出 PDF」即可输出与标准 PDF 版式完全一致的双面两页表格（打印弹窗选择「另存为 PDF」即可导出 PDF）。</span>
    </div>

    <div class="pages-viewport">
      <component :is="'style'">{{ scopedCss }}</component>
      <div v-for="(pageHtml, idx) in pages" :key="idx" class="page-wrapper">
        <div class="page-label">第 {{ idx + 1 }} 页 / 共 2 页（{{ idx === 0 ? '正面' : '反面' }}）</div>
        <div :ref="(el) => setPageRef(el, idx + 1)" class="a4-sheet">
          <div class="permit-preview-root" v-html="pageHtml"></div>
          <div
            v-if="stamp.enabled && activeSeal && stamp.page === idx + 1"
            class="seal-drag-box"
            :style="sealStyle"
            @pointerdown.prevent="(e) => startDrag(e, idx + 1)"
          >
            <img :src="activeSeal.imageData" :alt="activeSeal.name" class="seal-drag-img" draggable="false" />
            <div class="seal-resize-dot" title="拖拽缩放印章大小" @pointerdown.stop.prevent="startResize"></div>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="foot-bar">
        <span class="foot-hint">{{ stamp.enabled && activeSeal ? `当前电子印章：${activeSeal.name}（第${stamp.page}页）` : '当前未启用电子印章' }}</span>
        <div>
          <el-button @click="$emit('update:modelValue', false)">关闭</el-button>
          <el-button type="success" plain :loading="saving" @click="saveStamp">保存盖章设置</el-button>
          <el-button type="primary" @click="doPrint">打印 / 导出 PDF</el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { decorationApi } from '@/api/service'
import { buildDecorationPages, buildHotWorkPages, PERMIT_CSS, printPermitForm } from '../permitPrint'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  record: { type: Object, default: null },
  seals: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const saving = ref(false)
const pageRefs = {}
function setPageRef(el, pageNo) {
  if (el) pageRefs[pageNo] = el
}

const isHotWork = computed(() => String(props.record?.applyType || '').toUpperCase() === 'HOT_WORK')
const dialogTitle = computed(() => `${isHotWork.value ? '动火作业审批表' : '装修报批申请表'} · 生成与盖章打印（${props.record?.applyNo || ''}）`)

const parsedFormData = computed(() => {
  if (!props.record?.formDataJson) return {}
  try { return JSON.parse(props.record.formDataJson) } catch { return {} }
})

const stamp = reactive({
  sealId: null,
  enabled: false,
  page: 1,
  posX: 66,
  posY: 89,
  scale: 1,
  rotation: 0,
  opacity: 0.92
})

const activeSeal = computed(() => props.seals.find((s) => String(s.id) === String(stamp.sealId)) || null)

function defaultSpot(hotWork, pageNo) {
  if (hotWork) return pageNo === 1 ? { posX: 66, posY: 89 } : { posX: 74, posY: 31 }
  return pageNo === 2 ? { posX: 52, posY: 42 } : { posX: 25, posY: 74 }
}

watch(
  () => [props.modelValue, props.record],
  ([visible]) => {
    if (!visible || !props.record) return
    let cfg = null
    if (props.record.sealConfigJson) {
      try { cfg = JSON.parse(props.record.sealConfigJson) } catch { cfg = null }
    }
    const defaultPage = isHotWork.value ? 1 : 2
    const spot = defaultSpot(isHotWork.value, cfg?.page || defaultPage)
    const defaultSeal = props.seals.find((s) => s.isDefault === 1) || props.seals[0]
    stamp.sealId = props.record.sealId ?? cfg?.sealId ?? defaultSeal?.id ?? null
    stamp.enabled = cfg ? Boolean(cfg.enabled) : Boolean(props.record.sealId)
    stamp.page = Number(cfg?.page || defaultPage)
    stamp.posX = Number(cfg?.posX ?? spot.posX)
    stamp.posY = Number(cfg?.posY ?? spot.posY)
    stamp.scale = Number(cfg?.scale ?? 1)
    stamp.rotation = Number(cfg?.rotation ?? 0)
    stamp.opacity = Number(cfg?.opacity ?? 0.92)
  },
  { immediate: true }
)

const pages = computed(() => {
  if (!props.record) return []
  return isHotWork.value
    ? buildHotWorkPages(props.record, parsedFormData.value, null)
    : buildDecorationPages(props.record, parsedFormData.value, null)
})

// 将打印 CSS 限定在 .permit-preview-root 下，避免污染主界面样式
const scopedCss = computed(() => {
  const cleaned = PERMIT_CSS.replace(/@media print[\s\S]*$/, '').replace(/\bbody\s*\{[^}]*\}/g, '')
  return cleaned.replace(/(^|\})\s*([.#a-zA-Z][^{]*)\{/g, (_, pre, sel) => {
    const scopedSel = sel.split(',').map((s) => `.permit-preview-root ${s.trim()}`).join(', ')
    return `${pre}\n${scopedSel} {`
  })
})

const sealStyle = computed(() => {
  const baseMm = Number(activeSeal.value?.defaultWidthMm || 44)
  const widthPct = ((baseMm / 190) * 100) * Number(stamp.scale || 1)
  return {
    left: `${stamp.posX}%`,
    top: `${stamp.posY}%`,
    width: `${widthPct.toFixed(2)}%`,
    opacity: stamp.opacity,
    transform: `translate(-50%, -50%) rotate(${stamp.rotation || 0}deg)`
  }
})

function onSealPick(val) {
  if (val) stamp.enabled = true
}

function onPageSwitch(pageNo) {
  const spot = defaultSpot(isHotWork.value, pageNo)
  stamp.posX = spot.posX
  stamp.posY = spot.posY
}

function resetPos() {
  const defaultPage = isHotWork.value ? 1 : 2
  stamp.page = defaultPage
  const spot = defaultSpot(isHotWork.value, defaultPage)
  stamp.posX = spot.posX
  stamp.posY = spot.posY
  stamp.scale = 1
  stamp.rotation = 0
  stamp.opacity = 0.92
}

function startDrag(e, pageNo) {
  const el = pageRefs[pageNo]
  if (!el) return
  const rect = el.getBoundingClientRect()
  const sx = e.clientX
  const sy = e.clientY
  const ox = stamp.posX
  const oy = stamp.posY
  const onMove = (ev) => {
    stamp.posX = Math.max(5, Math.min(95, Math.round((ox + ((ev.clientX - sx) / rect.width) * 100) * 10) / 10))
    stamp.posY = Math.max(4, Math.min(96, Math.round((oy + ((ev.clientY - sy) / rect.height) * 100) * 10) / 10))
  }
  const onUp = () => {
    window.removeEventListener('pointermove', onMove)
    window.removeEventListener('pointerup', onUp)
  }
  window.addEventListener('pointermove', onMove)
  window.addEventListener('pointerup', onUp)
}

function startResize(e) {
  const sx = e.clientX
  const orig = stamp.scale
  const onMove = (ev) => {
    stamp.scale = Math.max(0.5, Math.min(1.8, Math.round((orig + (ev.clientX - sx) / 110) * 100) / 100))
  }
  const onUp = () => {
    window.removeEventListener('pointermove', onMove)
    window.removeEventListener('pointerup', onUp)
  }
  window.addEventListener('pointermove', onMove)
  window.addEventListener('pointerup', onUp)
}

function currentSealPayload() {
  if (!stamp.enabled || !activeSeal.value) return null
  return {
    enabled: true,
    sealId: activeSeal.value.id,
    imageData: activeSeal.value.imageData,
    defaultWidthMm: activeSeal.value.defaultWidthMm || 44,
    page: stamp.page,
    posX: stamp.posX,
    posY: stamp.posY,
    scale: stamp.scale,
    rotation: stamp.rotation,
    opacity: stamp.opacity
  }
}

async function saveStamp() {
  if (!props.record?.id) return
  saving.value = true
  try {
    const cfg = {
      enabled: stamp.enabled && !!stamp.sealId,
      sealId: stamp.sealId,
      page: stamp.page,
      posX: stamp.posX,
      posY: stamp.posY,
      scale: stamp.scale,
      rotation: stamp.rotation,
      opacity: stamp.opacity
    }
    const updated = {
      ...props.record,
      sealId: cfg.enabled ? stamp.sealId : null,
      sealConfigJson: JSON.stringify(cfg)
    }
    await decorationApi.update(updated)
    ElMessage.success('电子印章设置已保存到该申请表')
    emit('saved', updated)
  } finally {
    saving.value = false
  }
}

async function doPrint() {
  // 自动同步保存当前印章配置，确保打印记录一致
  if (props.record?.id && stamp.enabled && stamp.sealId) {
    try { await saveStamp() } catch { /* 不阻断打印 */ }
  }
  const ok = printPermitForm(props.record, parsedFormData.value, currentSealPayload())
  if (!ok) ElMessage.warning('浏览器拦截了打印窗口，请允许弹出窗口后重试')
}
</script>

<style scoped>
.stamp-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 14px;
  padding: 10px 14px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  margin-bottom: 8px;
}
.bar-group {
  display: flex;
  align-items: center;
  gap: 8px;
}
.lbl {
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
.tip-row {
  font-size: 12px;
  color: #64748b;
  margin-bottom: 10px;
}
.pages-viewport {
  max-height: 68vh;
  overflow: auto;
  background: #cbd5e1;
  border-radius: 10px;
  padding: 18px 16px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
}
.page-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}
.page-label {
  font-size: 12px;
  font-weight: 600;
  color: #334155;
  background: rgba(255, 255, 255, 0.85);
  padding: 2px 12px;
  border-radius: 999px;
}
.a4-sheet {
  position: relative;
  width: 210mm;
  background: #fff;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.22);
  user-select: none;
}
.seal-drag-box {
  position: absolute;
  z-index: 30;
  cursor: move;
  border: 1.5px dashed rgba(79, 70, 229, 0.8);
  border-radius: 6px;
  padding: 2px;
}
.seal-drag-box:hover {
  box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.16);
}
.seal-drag-img {
  display: block;
  width: 100%;
  height: auto;
  mix-blend-mode: multiply;
  pointer-events: none;
}
.seal-resize-dot {
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
.foot-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}
.foot-hint {
  font-size: 12px;
  color: #64748b;
}
</style>
