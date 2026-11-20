import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { VitePWA } from 'vite-plugin-pwa'

// mode === 'native' 产出的是打进 Android APK 的资源：由 WebView 从本地加载，
// Service Worker 没有意义、反而会缓存打架，所以关闭 PWA。
export default defineConfig(({ mode }) => {
  const isNative = mode === 'native'

  return {
    plugins: [
      vue(),
      VitePWA({
        disable: isNative,
        registerType: 'autoUpdate',
        injectRegister: 'script',
        includeAssets: ['pwa-192.png', 'pwa-512.png'],
        manifest: {
          name: '动了么',
          short_name: '动了么',
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
          skipWaiting: true,
          clientsClaim: true,
          navigateFallback: '/index.html',
          globPatterns: ['**/*.{js,css,html,svg,png,jpg,jpeg,ico}']
        },
        devOptions: {
          enabled: false
        }
      })
    ],
    server: {
      port: 5173,
      proxy: {
        '/api': 'http://127.0.0.1:8080'
      }
    },
    build: {
      // 原生包单独输出，避免覆盖 Web 的 dist；
      // 不自动清空，增量写入即可（文件名带 hash，旧文件不影响运行）
      outDir: isNative ? 'dist-native' : 'dist',
      emptyOutDir: !isNative
    }
  }
})
