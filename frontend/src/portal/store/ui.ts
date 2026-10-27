import { createStore, useStoreVersion } from "./store";

export interface ModalState {
  type: string;
  payload: Record<string, any>;
}

interface UiState {
  modal: ModalState | null;
  toast: string;
}

const uiStore = createStore<UiState>({ modal: null, toast: "" });

export const ui = uiStore.state;

export function useUi(): UiState {
  useStoreVersion(uiStore);
  return ui;
}

let toastTimer: ReturnType<typeof setTimeout> | null = null;

export function openModal(type: string, payload: Record<string, any> = {}) {
  ui.modal = { type, payload };
  document.body.style.overflow = "hidden";
}

export function closeModal() {
  ui.modal = null;
  document.body.style.overflow = "";
}

export function showToast(message: string) {
  ui.toast = message;
  if (toastTimer) clearTimeout(toastTimer);
  toastTimer = setTimeout(() => {
    ui.toast = "";
  }, 3600);
}
