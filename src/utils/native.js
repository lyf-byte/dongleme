/**
 * 原生壳能力封装。Web 端这些函数全部降级成 no-op / 浏览器原生实现，
 * 所以同一份代码可以同时跑在浏览器和 APK 里。
 */

const bridge = () => (typeof window !== 'undefined' ? window.DonglemeNative : null)

export function hasNativeBridge() {
  return Boolean(bridge())
}

export function hasNativeLocation() {
  return Boolean(bridge()?.startTracking)
}

export function nativeStartTracking() {
  try {
    return bridge()?.startTracking() ?? 'unsupported'
  } catch {
    return 'error'
  }
}

export function nativeStopTracking() {
  try {
    bridge()?.stopTracking()
  } catch {
    /* ignore */
  }
}

export function nativeIsTracking() {
  try {
    return bridge()?.isTracking() === true
  } catch {
    return false
  }
}

export function nativePendingPoints() {
  try {
    const raw = bridge()?.pendingPoints()
    const list = JSON.parse(raw || '[]')
    return Array.isArray(list) ? list : []
  } catch {
    return []
  }
}

export function nativeToast(message) {
  try {
    bridge()?.toast(String(message || ''))
  } catch {
    /* ignore */
  }
}

function blobToBase64(blob) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => {
      const result = String(reader.result || '')
      resolve(result.slice(result.indexOf(',') + 1))
    }
    reader.onerror = () => reject(reader.error)
    reader.readAsDataURL(blob)
  })
}

function browserDownload(blob, fileName) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

/**
 * 保存文件：原生壳走 MediaStore 写进「下载/动了么」，
 * 浏览器仍用 <a download>。
 * 返回 'ok' / 'error' 便于调用方决定要不要提示。
 */
export async function saveBlob(blob, fileName) {
  const native = bridge()
  if (!native?.saveFile) {
    browserDownload(blob, fileName)
    return 'ok'
  }
  try {
    const base64 = await blobToBase64(blob)
    const result = String(native.saveFile(fileName, blob.type || 'application/octet-stream', base64))
    return result.startsWith('ok') ? 'ok' : 'error'
  } catch {
    return 'error'
  }
}
