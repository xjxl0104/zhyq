import { createApp } from 'vue'
import ElementPlus from 'element-plus'
// 必须导入官方语言包对象: 只传 { name: 'zh-cn' } 没有词典,
// 组件会把 el.pagination.total 这类 i18n key 原样显示出来
import zhCn from 'element-plus/es/locale/lang/zh-cn.mjs'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './styles/index.scss'
import { useThemeStore } from './stores/theme'

const app = createApp(App)
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}
app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

// 应用持久化的主题(暗/亮)——store 初始化时即 toggle <html>.dark
useThemeStore()

app.mount('#app')
