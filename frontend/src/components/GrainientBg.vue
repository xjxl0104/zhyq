<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { Mesh, Program, Renderer, Triangle } from 'ogl'
import { hexToRgb, lerpPalette } from '@/utils/palette'

// React Bits Grainient 的 Vue 落地:ogl WebGL2 颗粒渐变着色器。
// 扩展:多组配色轮询(每 cycleSeconds 平滑淡变到下一组)。
// WebGL 不可用时静默退化(露出容器底下的 CSS 渐变);reduced-motion 只渲染静态首帧。
defineOptions({ name: 'GrainientBg' })

const props = defineProps({
  palettes: {
    type: Array,
    default: () => [
      ['#5b4be0', '#1c1650', '#3b2f9e'],  // 靛紫
      ['#7c3aed', '#231a5c', '#4c1d95'],  // 紫罗兰
      ['#8b5cf6', '#1e1b4b', '#5b21b6'],  // 电光紫
      ['#6366f1', '#191645', '#4338ca'],  // 蓝紫过渡
    ],
  },
  cycleSeconds: { type: Number, default: 14 },
  fadeSeconds: { type: Number, default: 3 },
  timeSpeed: { type: Number, default: 0.2 },
  grainAmount: { type: Number, default: 0.06 },
  contrast: { type: Number, default: 1.15 },
  zoom: { type: Number, default: 0.9 },
})


const VERT = `#version 300 es
in vec2 position;
void main() { gl_Position = vec4(position, 0.0, 1.0); }
`

const FRAG = `#version 300 es
precision highp float;
uniform vec2 iResolution;
uniform float iTime;
uniform float uTimeSpeed;
uniform float uContrast;
uniform float uGrainAmount;
uniform float uZoom;
uniform vec3 uColor1;
uniform vec3 uColor2;
uniform vec3 uColor3;
out vec4 fragColor;
#define S(a,b,t) smoothstep(a,b,t)
mat2 Rot(float a){float s=sin(a),c=cos(a);return mat2(c,-s,s,c);}
vec2 hash(vec2 p){p=vec2(dot(p,vec2(2127.1,81.17)),dot(p,vec2(1269.5,283.37)));return fract(sin(p)*43758.5453);}
float noise(vec2 p){vec2 i=floor(p),f=fract(p),u=f*f*(3.0-2.0*f);float n=mix(mix(dot(-1.0+2.0*hash(i+vec2(0.0,0.0)),f-vec2(0.0,0.0)),dot(-1.0+2.0*hash(i+vec2(1.0,0.0)),f-vec2(1.0,0.0)),u.x),mix(dot(-1.0+2.0*hash(i+vec2(0.0,1.0)),f-vec2(0.0,1.0)),dot(-1.0+2.0*hash(i+vec2(1.0,1.0)),f-vec2(1.0,1.0)),u.x),u.y);return 0.5+0.5*n;}
void main(){
  float t=iTime*uTimeSpeed;
  vec2 uv=gl_FragCoord.xy/iResolution.xy;
  float ratio=iResolution.x/iResolution.y;
  vec2 tuv=uv-0.5;
  tuv/=max(uZoom,0.001);
  float degree=noise(vec2(t*0.1,tuv.x*tuv.y)*2.0);
  tuv.y*=1.0/ratio;
  tuv*=Rot(radians((degree-0.5)*500.0+180.0));
  tuv.y*=ratio;
  float frequency=5.0;
  float amplitude=50.0;
  float warpTime=t*2.0;
  tuv.x+=sin(tuv.y*frequency+warpTime)/amplitude;
  tuv.y+=sin(tuv.x*(frequency*1.5)+warpTime)/(amplitude*0.5);
  float s=0.05;
  float blendX=tuv.x;
  vec3 layer1=mix(uColor3,uColor2,S(-0.3-s,0.2+s,blendX));
  vec3 layer2=mix(uColor2,uColor1,S(-0.3-s,0.2+s,blendX));
  vec3 col=mix(layer1,layer2,S(0.5+s,-0.3-s,tuv.y));
  vec2 grainUv=uv*2.0;
  float grain=fract(sin(dot(grainUv,vec2(12.9898,78.233)))*43758.5453);
  col+=(grain-0.5)*uGrainAmount;
  col=(col-0.5)*uContrast+0.5;
  col=clamp(col,0.0,1.0);
  fragColor=vec4(col,1.0);
}
`

const ctn = ref(null)
let renderer, program, mesh, rafId = 0
let disposed = false

function resize() {
  if (!ctn.value || !renderer) return
  const rect = ctn.value.getBoundingClientRect()
  renderer.setSize(Math.max(1, rect.width | 0), Math.max(1, rect.height | 0))
  if (program) {
    program.uniforms.iResolution.value = [renderer.gl.drawingBufferWidth, renderer.gl.drawingBufferHeight]
  }
}

onMounted(() => {
  const el = ctn.value
  if (!el) return
  const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false
  try {
    renderer = new Renderer({ webgl: 2, alpha: true, antialias: false, dpr: Math.min(window.devicePixelRatio || 1, 2) })
  } catch (e) {
    return
  }
  const gl = renderer.gl
  if (!gl) return
  gl.canvas.style.width = '100%'
  gl.canvas.style.height = '100%'
  gl.canvas.style.display = 'block'
  el.appendChild(gl.canvas)

  const first = props.palettes[0].map(hexToRgb)
  program = new Program(gl, {
    vertex: VERT,
    fragment: FRAG,
    uniforms: {
      iTime: { value: 0 },
      iResolution: { value: [1, 1] },
      uTimeSpeed: { value: props.timeSpeed },
      uContrast: { value: props.contrast },
      uGrainAmount: { value: props.grainAmount },
      uZoom: { value: props.zoom },
      uColor1: { value: first[0] },
      uColor2: { value: first[1] },
      uColor3: { value: first[2] },
    },
  })
  mesh = new Mesh(gl, { geometry: new Triangle(gl), program })
  window.addEventListener('resize', resize)
  resize()

  const t0 = performance.now()
  const update = now => {
    if (disposed) return
    const t = (now - t0) * 0.001
    program.uniforms.iTime.value = t
    // 配色轮询:cycle 周期内 fade 秒做 smoothstep 淡变
    const n = props.palettes.length
    if (n > 1) {
      const cyc = props.cycleSeconds
      const idx = Math.floor(t / cyc) % n
      const next = (idx + 1) % n
      const phase = t % cyc
      const fadeStart = cyc - props.fadeSeconds
      let mixT = 0
      if (phase > fadeStart) {
        const p = (phase - fadeStart) / props.fadeSeconds
        mixT = p * p * (3 - 2 * p)
      }
      const cols = lerpPalette(props.palettes[idx], props.palettes[next], mixT)
      program.uniforms.uColor1.value = cols[0]
      program.uniforms.uColor2.value = cols[1]
      program.uniforms.uColor3.value = cols[2]
    }
    renderer.render({ scene: mesh })
    if (!reduced) rafId = requestAnimationFrame(update)
  }
  rafId = requestAnimationFrame(update)
})

onBeforeUnmount(() => {
  disposed = true
  cancelAnimationFrame(rafId)
  window.removeEventListener('resize', resize)
  const gl = renderer?.gl
  if (gl && ctn.value && gl.canvas.parentNode === ctn.value) ctn.value.removeChild(gl.canvas)
  gl?.getExtension('WEBGL_lose_context')?.loseContext()
})
</script>

<template>
  <div ref="ctn" class="grainient-bg" aria-hidden="true"></div>
</template>

<style scoped>
.grainient-bg { width: 100%; height: 100%; overflow: hidden; }
</style>
