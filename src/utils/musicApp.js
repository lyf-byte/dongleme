import { toast } from './ui'

/**
 * 音乐 App 唤起：不做应用内播放，只负责
 * 「选择音源 → 通过 URL Scheme 跳到对应音乐 App」。
 * 已选音源持久化在 localStorage，跟随用户下次使用。
 */

const STORE_KEY = 'dlm.music.source'

const SCHEMES = {
  netease: 'orpheus://',
  qq: 'qqmusic://'
}

export const MUSIC_OPTIONS = [
  { value: 'netease', name: '网易云音乐', desc: '跳转到网易云音乐播放' },
  { value: 'qq', name: 'QQ 音乐', desc: '跳转到 QQ 音乐播放' },
  { value: '', name: '不使用音乐', desc: '不跳转，安静记录' }
]

export function loadMusicSource() {
  try {
    return localStorage.getItem(STORE_KEY) || ''
  } catch {
    return ''
  }
}

export function saveMusicSource(value) {
  try {
    if (value) {
      localStorage.setItem(STORE_KEY, value)
    } else {
      localStorage.removeItem(STORE_KEY)
    }
  } catch {
    /* 隐私模式写不进去就忽略 */
  }
}

/**
 * 尝试唤起音乐 App。
 * 跳转成功页面会进入后台（document.hidden），据此判断成功与否；
 * 约 1.8 秒后仍在前台则提示未安装。
 */
export function openMusicApp(source) {
  const scheme = SCHEMES[source]
  if (!scheme) return false

  let timer = null
  const onHide = () => {
    if (document.hidden && timer) {
      clearTimeout(timer)
      timer = null
      document.removeEventListener('visibilitychange', onHide)
    }
  }
  document.addEventListener('visibilitychange', onHide)

  timer = setTimeout(() => {
    document.removeEventListener('visibilitychange', onHide)
    if (!document.hidden) {
      toast('未检测到对应的音乐 App，请确认已安装')
    }
  }, 1800)

  window.location.href = scheme
  return true
}
