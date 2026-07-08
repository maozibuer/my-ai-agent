import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // SSE streaming endpoint — must not be buffered by the proxy
      '/api/chat/stream': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // Disable response buffering so SSE frames arrive immediately
        configure(proxy) {
          proxy.on('proxyReq', (proxyReq) => {
            proxyReq.setHeader('Connection', 'keep-alive')
          })
          proxy.on('proxyRes', (proxyRes) => {
            // Flush each chunk immediately without buffering
            proxyRes.headers['x-accel-buffering'] = 'no'
            proxyRes.headers['cache-control'] = 'no-cache'
          })
        }
      },
      // All other API calls
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
