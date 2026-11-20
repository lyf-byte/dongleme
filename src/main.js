import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './styles/global.css'

if ('serviceWorker' in navigator) {
  const hadController = Boolean(navigator.serviceWorker.controller)
  let refreshing = false
  navigator.serviceWorker.addEventListener('controllerchange', () => {
    if (!hadController || refreshing) return
    refreshing = true
    window.location.reload()
  })
}

createApp(App).use(router).mount('#app')
