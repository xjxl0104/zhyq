<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId } from 'vue'
import { gsap } from 'gsap'

// React Bits StrokeText 的忠实 Vue 移植(fillMode: wipe):
// 测量 SVG 字形(含 document.fonts.ready 后重测)→ 描边按字勾画(stagger)→ 左→右填充擦入。
// mount 播放一次,悬停重播;prefers-reduced-motion 直接呈现终态。
defineOptions({ name: 'StrokeBrand' })

const TEXT = '智慧云仓系统'
const STROKE_COLOR = '#A78BFA'
const FILL_COLOR = '#F8FAFC'
const STROKE_WIDTH = 1.3   // 配合 non-scaling-stroke,小尺寸下仍保持参考实现的线条锐度
const DRAW_DURATION = 2
const FILL_DELAY = 0.2
const STAGGER = 0.05
const EASE = 'power1.inOut'
const FONT_SIZE = 128
// 参考实现的 fontSize*7 按拉丁字母校准;汉字笔画路径长得多,
// dash 必须覆盖单字全部轮廓长度,否则虚线循环导致勾画断线
const DASH = FONT_SIZE * 45

const characters = Array.from(TEXT)
const root = ref(null)
const strokeTextEl = ref(null)
const wipeRect = ref(null)
const box = ref(null)
const wipeId = `stroke-wipe-${useId().replace(/[^a-zA-Z0-9_-]/g, '')}`
const viewBox = computed(() => box.value
  ? `${box.value.x} ${box.value.y} ${box.value.width} ${box.value.height}`
  : `0 ${-FONT_SIZE} ${characters.length * FONT_SIZE} ${FONT_SIZE * 1.3}`)

let timeline
let reduced = false
let disposed = false

function collectTargets() {
  const strokes = gsap.utils.toArray(root.value.querySelectorAll('[data-stroke-char]'))
  const fills = gsap.utils.toArray(root.value.querySelectorAll('[data-fill-char]'))
  return { strokes, fills, wipe: wipeRect.value }
}

function play() {
  if (disposed || !root.value || !box.value) return
  const { strokes, fills, wipe } = collectTargets()
  if (!strokes.length) return
  const all = [...strokes, ...fills, wipe].filter(Boolean)
  timeline?.kill()
  gsap.killTweensOf(all)

  if (reduced) {
    gsap.set(strokes, { strokeDasharray: 'none', strokeDashoffset: 0 })
    gsap.set(fills, { opacity: 1 })
    if (wipe) gsap.set(wipe, { attr: { width: box.value.width } })
    return
  }

  const fillDuration = Math.max(0.4, DRAW_DURATION * 0.5)
  gsap.set(strokes, { strokeDasharray: DASH, strokeDashoffset: DASH })
  gsap.set(fills, { opacity: 1 }) // wipe 模式下 fill 常显,由 clip 矩形控制可见范围
  if (wipe) gsap.set(wipe, { attr: { width: 0 } })

  timeline = gsap.timeline({ defaults: { overwrite: 'auto' } })
  timeline.to(strokes, { strokeDashoffset: 0, duration: DRAW_DURATION, ease: EASE, stagger: STAGGER }, 0)
  // 勾画完成后取消虚线:无论字形路径多长,终态描边都连续无缺口
  timeline.set(strokes, { strokeDasharray: 'none' }, DRAW_DURATION + STAGGER * (characters.length - 1))
  if (wipe) {
    timeline.to(
      wipe,
      { attr: { width: box.value.width }, duration: fillDuration, ease: 'power2.inOut' },
      DRAW_DURATION + FILL_DELAY,
    )
  }
}

// 字体加载前后字形宽度会变(中文尤甚),ready 后重测重播,避免裁切/错位
function measure() {
  if (disposed) return
  let bbox
  try { bbox = strokeTextEl.value?.getBBox() } catch { return }
  if (!bbox?.width) return
  const pad = Math.max(STROKE_WIDTH, FONT_SIZE * 0.1)
  const next = { x: bbox.x - pad, y: bbox.y - pad, width: bbox.width + pad * 2, height: bbox.height + pad * 2 }
  const prev = box.value
  if (prev
    && Math.abs(prev.x - next.x) < .5
    && Math.abs(prev.y - next.y) < .5
    && Math.abs(prev.width - next.width) < .5) return
  box.value = next
  nextTick(play)
}

function replay() {
  if (!reduced) play()
}

onMounted(async () => {
  reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false
  await nextTick()
  measure()
  if (typeof document !== 'undefined' && document.fonts?.ready) {
    document.fonts.ready.then(() => measure()).catch(() => {})
  }
})
onBeforeUnmount(() => {
  disposed = true
  timeline?.kill()
})
</script>

<template>
  <span ref="root" class="stroke-brand" role="img" :aria-label="TEXT" @pointerenter="replay">
    <svg class="stroke-brand__svg" :viewBox="viewBox" preserveAspectRatio="xMidYMid meet"
         aria-hidden="true" focusable="false">
      <defs>
        <clipPath :id="wipeId" clipPathUnits="userSpaceOnUse">
          <rect ref="wipeRect" :x="box?.x ?? 0" :y="box?.y ?? -FONT_SIZE"
                width="0" :height="box?.height ?? FONT_SIZE * 1.3" />
        </clipPath>
      </defs>
      <text ref="strokeTextEl" class="stroke-brand__stroke" x="0" y="0" fill="none"
            :stroke="STROKE_COLOR" :stroke-width="STROKE_WIDTH"
            stroke-linejoin="round" stroke-linecap="round" vector-effect="non-scaling-stroke">
        <tspan v-for="(c, i) in characters" :key="'s' + i" data-stroke-char>{{ c }}</tspan>
      </text>
      <text class="stroke-brand__fill" data-fill-text x="0" y="0"
            :fill="FILL_COLOR" stroke="none" :clip-path="`url(#${wipeId})`">
        <tspan v-for="(c, i) in characters" :key="'f' + i" data-fill-char>{{ c }}</tspan>
      </text>
    </svg>
  </span>
</template>

<style scoped>
/* 思源黑体(Noto Sans SC)800 字重,仅含品牌 6 字的子集(~2KB),自托管 */
@font-face {
  font-family: 'Brand Source Han';
  src: url('@/assets/fonts/brand-source-han-800.woff2') format('woff2');
  font-weight: 800;
  font-style: normal;
  font-display: swap;
}
.stroke-brand { display: block; width: 100%; line-height: 0; cursor: default; }
.stroke-brand__svg { display: block; width: 100%; height: 44px; }
.stroke-brand__stroke,
.stroke-brand__fill {
  font-family: 'Brand Source Han', 'Source Han Sans SC', 'Noto Sans SC', 'PingFang SC', sans-serif;
  font-size: 128px;
  font-weight: 800;
  letter-spacing: -4px;
  user-select: none;
}
</style>
