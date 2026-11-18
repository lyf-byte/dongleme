import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
  plugins: [
    vue(),
    VitePWA({
      registerType: 'autoUpdate',
      injectRegister: 'script',
      includeAssets: ['pwa-192.png', 'pwa-512.png'],
      manifest: {
        name: '动了没',
        short_name: '动了没',
        description: '校园运动打卡，每天动一点，连续看得见',
        lang: 'zh-CN',
        theme_color: '#F2F4F8',
        background_color: '#F2F4F8',
        display: 'standalone',
        start_url: '/',
        icons: [
          { src: '/pwa-192.png', sizes: '192x192', type: 'image/png' },
          { src: '/pwa-512.png', sizes: '512x512', type: 'image/png' }
        ]
      },
      workbox: {
        navigateFallback: '/index.html',
        globPatterns: ['**/*.{js,css,html,svg,png,ico}']
      },
      devOptions: {
        enabled: false
      }
    })
  ],
  server: {
    port: 5173
  }
})
