import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

export default defineConfig({
  plugins: [uni()],
  server: {
    port: 5274,
    proxy: { '/api': { target: 'http://localhost:8090', changeOrigin: true } }
  }
})
