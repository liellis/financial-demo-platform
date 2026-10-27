import { reactive } from 'vue'

export const PROJECT_MAX_BYTES = 100 * 1024 * 1024

export const projects = reactive({
  items: [],
  activeId: null,
  query: '',
  filter: 'all',
  ready: false,
  mode: 'loading',
  db: null,
  error: '',
  busy: false
})

function projectStore(name, mode, work) {
  return new Promise((resolve, reject) => {
    const tx = projects.db.transaction(name, mode)
    let result
    tx.oncomplete = () => resolve(result?.result)
    tx.onerror = () => reject(tx.error || new Error('存储失败'))
    tx.onabort = () => reject(tx.error || new Error('存储已中止'))
    try {
      result = work(tx.objectStore(name))
    } catch (error) {
      tx.abort()
      reject(error)
    }
  })
}

export async function initializeProjects() {
  try {
    if (!window.indexedDB) throw new Error('此浏览器不支持本地工程存储')
    projects.db = await new Promise((resolve, reject) => {
      const request = indexedDB.open('bank-ai-coding-projects-vue3', 1)
      request.onupgradeneeded = () => {
        request.result.createObjectStore('projects', { keyPath: 'id' })
        request.result.createObjectStore('settings', { keyPath: 'key' })
      }
      request.onsuccess = () => resolve(request.result)
      request.onerror = () => reject(request.error)
      request.onblocked = () => reject(new Error('本地工程库被其他页面占用'))
    })
    const [items, active] = await Promise.all([
      projectStore('projects', 'readonly', (store) => store.getAll()),
      projectStore('settings', 'readonly', (store) => store.get('active'))
    ])
    projects.items = items || []
    projects.activeId = active?.value || null
    projects.mode = 'persistent'
    projects.db.onversionchange = () => {
      projects.db.close()
      projects.mode = 'memory'
      projects.db = null
    }
  } catch (error) {
    projects.mode = 'memory'
    projects.error = '此浏览器无法使用本地工程库，当前仅保存在页面内存中，刷新后丢失。'
  }
  projects.ready = true
}

export function projectSize(bytes) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

export function projectDate(value) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).format(new Date(value))
}

export function projectFullDate(value) {
  return new Date(value).toLocaleString('zh-CN')
}

export function getProject(id) {
  return projects.items.find((item) => item.id === id)
}

export function activeProject() {
  return projects.items.find((item) => item.id === projects.activeId)
}

export function filteredProjects() {
  const query = projects.query.trim().toLocaleLowerCase()
  return projects.items
    .filter((item) => {
      const haystack = `${item.name} ${item.code} ${item.description} ${item.archiveName}`.toLocaleLowerCase()
      return haystack.includes(query) && (projects.filter === 'all' || item.status === projects.filter)
    })
    .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
}

export async function validateArchiveFile(file) {
  if (!file) throw new Error('请选择代码工程压缩包。')
  if (!/\.(zip|tar|tar\.gz|tgz)$/i.test(file.name)) throw new Error('请选择 ZIP、TAR 或 TAR.GZ 代码压缩包。')
  if (!file.size) throw new Error('压缩包为空，请重新选择。')
  if (file.size > PROJECT_MAX_BYTES) throw new Error('压缩包超过 100 MB，请缩小后重新上传。')

  const bytes = new Uint8Array(await file.slice(0, 512).arrayBuffer())
  const zip = bytes[0] === 0x50 && bytes[1] === 0x4b &&
    ((bytes[2] === 3 && bytes[3] === 4) || (bytes[2] === 5 && bytes[3] === 6) || (bytes[2] === 7 && bytes[3] === 8))
  const gzip = bytes[0] === 0x1f && bytes[1] === 0x8b
  let tar = false
  if (bytes.length === 512) {
    let sum = 0
    for (let i = 0; i < 512; i += 1) sum += i >= 148 && i < 156 ? 32 : bytes[i]
    const stored = parseInt(new TextDecoder().decode(bytes.slice(148, 156)).replace(/\0/g, '').trim(), 8)
    tar = sum === stored
  }
  const valid = /\.zip$/i.test(file.name) ? zip : /\.(tar\.gz|tgz)$/i.test(file.name) ? gzip : tar
  if (!valid) throw new Error('文件内容与压缩包格式不匹配，请重新选择有效压缩包。')
  return true
}

async function writeItem(item, unbind) {
  if (projects.db) {
    await new Promise((resolve, reject) => {
      const tx = projects.db.transaction(['projects', 'settings'], 'readwrite')
      tx.objectStore('projects').put(item)
      if (unbind) tx.objectStore('settings').put({ key: 'active', value: null })
      tx.oncomplete = resolve
      tx.onerror = () => reject(tx.error)
      tx.onabort = () => reject(tx.error)
    })
  }
  projects.items = projects.items.filter((entry) => entry.id !== item.id).concat(item)
  if (unbind) projects.activeId = null
}

export async function saveProject({ id, name, code, language, description, status, archiveFile }) {
  const existing = projects.items.find((item) => item.id === id)
  if (projects.items.some((item) => item.id !== existing?.id && item.code.toLowerCase() === code.toLowerCase())) {
    throw new Error('工程标识已存在，请使用其他标识。')
  }
  if (!existing && !archiveFile) throw new Error('请选择代码工程压缩包。')

  const archive = archiveFile || existing.archive
  const now = new Date().toISOString()
  let hash = existing?.sha256 || ''
  if (archiveFile && window.crypto?.subtle) {
    const digest = await crypto.subtle.digest('SHA-256', await archiveFile.arrayBuffer())
    hash = Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, '0')).join('')
  }

  const item = {
    id: existing?.id || (crypto.randomUUID ? crypto.randomUUID() : `p-${Date.now()}-${Math.random().toString(36).slice(2)}`),
    name,
    code,
    language,
    description,
    status,
    archive,
    archiveName: archiveFile?.name || existing.archiveName,
    archiveSize: archive.size,
    sha256: hash,
    createdAt: existing?.createdAt || now,
    updatedAt: now
  }

  const unbind = item.status === 'archived' && projects.activeId === item.id
  await writeItem(item, unbind)
  return item
}

export async function bindProject(id) {
  if (projects.db) {
    await projectStore('settings', 'readwrite', (store) => store.put({ key: 'active', value: id }))
  }
  projects.activeId = id
}

export async function removeProject(id) {
  if (projects.db) {
    await new Promise((resolve, reject) => {
      const tx = projects.db.transaction(['projects', 'settings'], 'readwrite')
      tx.objectStore('projects').delete(id)
      if (projects.activeId === id) tx.objectStore('settings').put({ key: 'active', value: null })
      tx.oncomplete = resolve
      tx.onerror = () => reject(tx.error)
      tx.onabort = () => reject(tx.error)
    })
  }
  projects.items = projects.items.filter((item) => item.id !== id)
  if (projects.activeId === id) projects.activeId = null
}

export function downloadProject(id) {
  const project = getProject(id)
  if (!project) return
  const url = URL.createObjectURL(project.archive)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = project.archiveName
  anchor.click()
  setTimeout(() => URL.revokeObjectURL(url), 2000)
}
