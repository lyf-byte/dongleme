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
let activeDialog = null
const dialogQueue = []

export function toast(message, duration = 1800) {
  if (toastTimer) clearTimeout(toastTimer)
  uiState.toast.message = message
  uiState.toast.visible = true
  toastTimer = setTimeout(() => {
    uiState.toast.visible = false
    toastTimer = null
  }, duration)
}

function showDialog(entry) {
  activeDialog = entry
  const options = entry.options
  uiState.dialog = {
    visible: true,
    title: options.title || '确认操作',
    message: options.message || '',
    confirmText: options.confirmText || '确定',
    cancelText: options.cancelText || '取消',
    danger: Boolean(options.danger)
  }
}

export function confirmDialog(options = {}) {
  return new Promise((resolve, reject) => {
    const entry = { options, resolve, reject }
    if (activeDialog) dialogQueue.push(entry)
    else showDialog(entry)
  })
}

export function closeDialog(confirmed) {
  const entry = activeDialog
  if (!entry) return
  activeDialog = null
  uiState.dialog.visible = false

  if (confirmed) entry.resolve()
  else entry.reject()

  const next = dialogQueue.shift()
  if (next) showDialog(next)
}
