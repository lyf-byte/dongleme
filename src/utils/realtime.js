import { getToken } from './auth'

const listeners = new Map()
let source = null
let retryTimer = 0

function emit(name, payload) {
  const handlers = listeners.get(name)
  if (!handlers) return
  handlers.forEach((handler) => {
    try {
      handler(payload)
    } catch {
      /* 单个订阅出错不影响其它订阅 */
    }
  })
}

export function onRealtime(name, handler) {
  if (!listeners.has(name)) listeners.set(name, new Set())
  listeners.get(name).add(handler)
  return () => {
    listeners.get(name)?.delete(handler)
  }
}

export function connectRealtime() {
  if (typeof window === 'undefined' || source) return
  const token = getToken()
  if (!token) return

  const base = import.meta.env.VITE_API_BASE || '/api'
  const url = `${base}/messages/stream?token=${encodeURIComponent(token)}`
  source = new EventSource(url)

  ;['message', 'notification', 'recall'].forEach((name) => {
    source.addEventListener(name, (event) => {
      try {
        emit(name, JSON.parse(event.data))
      } catch {
        /* 忽略无法解析的事件 */
      }
    })
  })

  source.onerror = () => {
    if (!source || source.readyState !== EventSource.CLOSED) return
    source = null
    window.clearTimeout(retryTimer)
    if (!getToken()) return
    retryTimer = window.setTimeout(connectRealtime, 5000)
  }
}

export function disconnectRealtime() {
  window.clearTimeout(retryTimer)
  if (source) {
    source.close()
    source = null
  }
}
