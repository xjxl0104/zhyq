// Local experiment: the actual homepage, controls and model with an opt-in look.
import { createApp, h, ref, shallowRef } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createMemoryHistory, RouterView } from 'vue-router'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn.mjs'
import * as icons from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import '../src/styles/index.scss'
import Layout from '../src/layout/Layout.vue'
import TwinDashboard from '../src/views/twin/TwinDashboard.vue'
import request from '../src/utils/request'
import './twin-anime.css'

if (!import.meta.env.DEV || location.port !== '5323') throw new Error('This isolated experiment runs only on local development port 5323.')
const style = new URLSearchParams(location.search).get('style') === 'original' ? 'original' : 'anime'
const latest = shallowRef(null), measuring = ref(false), remaining = ref(0), status = ref(''), details = ref(false)
const savedKey = 'zhyq-local-anime-benchmark-v1'
let saved = {}
try { saved = JSON.parse(sessionStorage.getItem(savedKey) || '{}') } catch { /* An optional local record never blocks the demo. */ }
const results = ref(saved)
let samples = [], signature = ''
const signatureOf = metric => JSON.stringify([metric.mode, metric.floor, metric.weather, metric.rotating, metric.buffer?.width, metric.buffer?.height])
const mean = values => values.reduce((a, b) => a + b, 0) / (values.length || 1)
function receiveMetrics(metric) {
  latest.value = metric
  if (!metric) {
    if (measuring.value) status.value = '场景已暂停或切换，请重新测量。'
    measuring.value = false; remaining.value = 0; samples = []; signature = ''
    return
  }
  if (!measuring.value || document.hidden) return
  if (signatureOf(metric) !== signature) {
    measuring.value = false; status.value = '场景已变化，请在视角稳定后重新测量。'; return
  }
  samples.push(metric); remaining.value = 10 - samples.length
  if (samples.length < 10) return
  const frames = samples.reduce((sum, value) => sum + value.frames, 0)
  const gpu = samples.map(item => item.gpuMs).filter(value => value != null)
  results.value[style] = {
    style, mode: metric.mode, floor: metric.floor, weather: metric.weather, rotating: metric.rotating,
    buffer: metric.buffer, fps: mean(samples.map(item => item.fps)),
    submissionMs: frames ? samples.reduce((sum, item) => sum + item.submissionMs * item.frames, 0) / frames : 0,
    gpuMs: gpu.length ? mean(gpu) : null,
    calls: Math.round(mean(samples.map(item => item.calls))),
    triangles: Math.round(mean(samples.map(item => item.triangles))),
    date: new Date().toISOString(),
  }
  try { sessionStorage.setItem(savedKey, JSON.stringify(results.value)) } catch { /* Storage may be unavailable in private sessions. */ }
  measuring.value = false; status.value = '已记录当前视图的 10 秒样本。'; details.value = true
}
function measure() {
  if (!latest.value) return
  samples = []; signature = signatureOf(latest.value); remaining.value = 10; measuring.value = true
  status.value = '采样中，请保持当前视角和天气。'
}
const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: Layout, children: [
  { path: 'dashboard', meta: { title: '首页' }, component: TwinDashboard },
  { path: ':pathMatch(.*)*', component: { setup: () => () => h('div', { style: 'padding:48px' }, [
    h('h2', '已进入业务模块'), h('p', router.currentRoute.value.path),
    h('p', '本地实验保留原模块导航。业务数据未接入，请在正式系统登录后使用。'),
    h('button', { onClick: () => router.push('/dashboard') }, '返回三维首页'),
  ]) } },
] }] })
request.defaults.adapter = async config => ({ data: { code: 0, data: config.url === '/building/project/list'
  ? [{ id: 1001, name: '云仓参考项目 · 动漫三维实验' }, { id: 1002, name: '第二项目 · 布局演示' }] : null },
  status: 200, statusText: 'OK', headers: {}, config })
const fmt = (value, digits = 1) => Number.isFinite(value) ? value.toFixed(digits) : '—'
const label = value => value === 'anime' ? '动漫三维' : '原版对照'
const App = { setup: () => () => h('div', { class: 'anime-lab-shell' }, [
  h('header', { class: 'anime-lab-bar' }, [
    h('strong', '可旋转的动漫园区'), h('span', { class: 'anime-lab-note' }, '完整交互实验 · 示例数据'),
    h('nav', { 'aria-label': '渲染方案对照' }, ['anime', 'original'].map(value => h('a', {
      href: '/dev/twin-anime.html' + (value === 'original' ? '?style=original' : ''),
      'aria-current': style === value ? 'page' : null,
    }, label(value)))),
    h('output', { class: 'anime-lab-live', 'data-testid': 'render-metrics', 'data-metrics': JSON.stringify(latest.value) },
      latest.value ? `${fmt(latest.value.fps, 0)} 绘制帧/秒 · ${latest.value.calls} 次绘制` : '正在加载场景'),
    h('button', { disabled: !latest.value || measuring.value, onClick: measure }, measuring.value ? `测量中 ${remaining.value}s` : '测量 10 秒'),
    h('button', { onClick: () => { details.value = !details.value }, 'aria-expanded': details.value }, '测量说明'),
  ]),
  details.value ? h('section', { class: 'anime-lab-details', 'aria-label': '本机测量与方案说明' }, [
    h('p', '保留原模型、自由旋转、楼层展开、室内空间、天气、业务点位、导出与全屏。动漫模式使用分阶明暗与直接渲染；数据和模块页面为本地演示。'),
    h('p', '对比时请使用相同窗口、模式和天气，并开启自动环绕。静止时绘制帧数降至 0 属于按需渲染。CPU 提交耗时不等于 GPU 耗时；当前设备若不支持 GPU 计时，会显示“—”。'),
    h('div', { class: 'anime-lab-results' }, Object.values(results.value).map(result => h('p', { 'data-testid': `result-${result.style}` },
      `${label(result.style)}：${fmt(result.fps)} 帧/秒；CPU 提交 ${fmt(result.submissionMs, 2)}ms；GPU ${fmt(result.gpuMs, 2)}ms；${result.calls} 次绘制；${result.triangles.toLocaleString()} 三角形；${result.buffer?.width}×${result.buffer?.height}。`))),
    h('p', { role: 'status' }, status.value || '尚未记录当前视图。结果仅代表这台设备当前窗口。'),
    h('button', { onClick: () => { details.value = false } }, '收起说明'),
  ]) : null,
  h('div', { class: 'anime-lab-app' }, [h(RouterView)]),
]) }
const app = createApp(App)
app.provide('warehouse-scene-demo', { style, onMetrics: receiveMetrics })
for (const [key, component] of Object.entries(icons)) app.component(key, component)
app.use(createPinia()).use(router).use(ElementPlus, { locale: zhCn })
await router.push('/dashboard')
app.mount('#app')
