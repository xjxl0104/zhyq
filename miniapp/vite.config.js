import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

const injectedMpApiBase = process.env.VITE_API_BASE || ''
const injectedWhApiBase = process.env.VITE_WH_API_BASE || ''

export default defineConfig({
  plugins: [uni()],
  define: {
    __ZHYQ_MP_API_BASE__: JSON.stringify(injectedMpApiBase),
    __ZHYQ_WH_API_BASE__: JSON.stringify(injectedWhApiBase)
  },
  server: {
    port: 5274,
    proxy: { '/api': { target: 'http://localhost:8090', changeOrigin: true } }
  }
})
