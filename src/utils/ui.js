import { reactive } from 'vue'

export const uiState = reactive({
  toast: {
    visible: false,
    message: ''
  },
  dialog: {
    visible: false,
    title: '',
    message: '',
    confirmText: '确定',
    cancelText: '取消',
    danger: false
  }
})

let toastTimer = null
let dialogResolve = null
let dialogReject = null

export function toast(message, duration = 1800) {
  if (toastTimer) clearTimeout(toastTimer)
  uiState.toast.message = message
  uiState.toast.visible = true
  toastTimer = setTimeout(() => {
    uiState.toast.visible = false
    toastTimer = null
  }, duration)
}

export function confirmDialog(options = {}) {
  return new Promise((resolve, reject) => {
    dialogResolve = resolve
    dialogReject = reject
    uiState.dialog = {
      visible: true,
      title: options.title || '确认操作',
      message: options.message || '',
      confirmText: options.confirmText || '确定',
      cancelText: options.cancelText || '取消',
      danger: Boolean(options.danger)
    }
  })
}

export function closeDialog(confirmed) {
  const resolve = dialogResolve
  const reject = dialogReject
  dialogResolve = null
  dialogReject = null
  uiState.dialog.visible = false

  if (confirmed) resolve?.()
  else reject?.()
}
