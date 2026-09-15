// Local visual QA harness, not a Vite production entry and never imported by the application.
import { createApp, h } from 'vue'
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

if (!import.meta.env.DEV || location.port !== '5323') throw new Error('This isolated visual QA harness runs only on local development port 5323.')

// An isolated origin protects the primary preview's storage. No requests reach a backend.
request.defaults.adapter = async config => ({ data: { code: 0, data: config.url === '/building/project/list'
  ? [{ id: 1001, name: '云仓参考项目 · 布局演示' }, { id: 1002, name: '第二项目 · 布局演示' }] : null },
  status: 200, statusText: 'OK', headers: {}, config })
const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: Layout, children: [
  { path: 'dashboard', meta: { title: '首页' }, component: TwinDashboard },
  { path: ':pathMatch(.*)*', component: { setup: () => () => h('div', { style: 'padding:48px' }, [
    h('h2', '已进入模块入口'), h('p', router.currentRoute.value.path),
    h('p', '这是原布局的本地验证页，项目与运营数字为示例。业务功能请在原系统登录后使用。'),
    h('button', { onClick: () => router.push('/dashboard') }, '返回三维首页'),
  ]) } },
] }] })
const app = createApp({ render: () => h(RouterView) })
for (const [key, component] of Object.entries(icons)) app.component(key, component)
app.use(createPinia()).use(router).use(ElementPlus, { locale: zhCn })
await router.push('/dashboard')
app.mount('#app')
