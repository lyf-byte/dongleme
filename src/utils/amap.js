import AMapLoader from '@amap/amap-jsapi-loader'

let loaderPromise = null

export class MissingAmapKeyError extends Error {
  constructor() {
    super('缺少高德地图 JS API Key')
    this.name = 'MissingAmapKeyError'
  }
}

export function hasAmapKey() {
  return Boolean(import.meta.env.VITE_AMAP_JS_KEY)
}

export function loadAmap() {
  const key = import.meta.env.VITE_AMAP_JS_KEY
  if (!key) return Promise.reject(new MissingAmapKeyError())
  if (loaderPromise) return loaderPromise

  const securityCode = import.meta.env.VITE_AMAP_SECURITY_CODE
  if (securityCode) {
    window._AMapSecurityConfig = { securityJsCode: securityCode }
  }

  loaderPromise = AMapLoader.load({
    key,
    version: '2.0'
  }).catch((error) => {
    loaderPromise = null
    throw error
  })
  return loaderPromise
}
