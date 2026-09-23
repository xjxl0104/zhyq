import { defineConfig, loadEnv } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

export default defineConfig(({ mode }) => {
  const env = { ...loadEnv(mode, process.cwd(), 'VITE_'), ...process.env }
  const origin = process.env.UNI_PLATFORM === 'mp-weixin' ? 'https://zhyq.aoleplat.com' : ''
  const injectedMpApiBase = env.VITE_API_BASE || `${origin}/api/mp/v1`
  const injectedWhApiBase = env.VITE_WH_API_BASE || `${origin}/api/wh/v1`
  if (origin && (!/^https:\/\//.test(injectedMpApiBase) || !/^https:\/\//.test(injectedWhApiBase))) {
    throw new Error('微信小程序必须配置 HTTPS 绝对接口地址：VITE_API_BASE / VITE_WH_API_BASE')
  }
  return {
    plugins: [uni()],
    define: {
      __ZHYQ_MP_API_BASE__: JSON.stringify(injectedMpApiBase),
      __ZHYQ_WH_API_BASE__: JSON.stringify(injectedWhApiBase)
    },
    server: {
      port: 5274,
      proxy: { '/api': { target: 'http://localhost:8090', changeOrigin: true } }
    }
  }
})
