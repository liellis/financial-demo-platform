import { reactive } from 'vue'

export const ui = reactive({
  modal: null,
  toast: ''
})

let toastTimer = null

export function openModal(type, payload = {}) {
  ui.modal = { type, payload }
  document.body.style.overflow = 'hidden'
}

export function closeModal() {
  ui.modal = null
  document.body.style.overflow = ''
}

export function showToast(message) {
  ui.toast = message
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => {
    ui.toast = ''
  }, 3600)
}
