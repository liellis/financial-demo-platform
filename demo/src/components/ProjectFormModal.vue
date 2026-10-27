<template>
  <div class="overlay-head">
    <div>
      <div class="eyebrow">LOCAL PROJECT WORKSPACE</div>
      <h2 id="dialog-title">{{ project ? '编辑代码工程' : '上传代码工程' }}</h2>
      <p>{{ project ? '维护工程资料，或上传新压缩包替换当前文件。' : '将已有代码工程压缩后导入本地工程库。' }}</p>
    </div>
    <button class="icon-button" aria-label="关闭对话框" @click="$emit('close')"><AppIcon name="close" :size="20" /></button>
  </div>

  <form id="project-form" @submit.prevent="submit">
    <label class="input-label" for="project-name">工程名称 <span>*</span></label>
    <input id="project-name" v-model="form.name" class="form-input" required maxlength="80" placeholder="例如：银行信用卡业务系统">

    <div class="project-form-columns">
      <div>
        <label class="input-label" for="project-code">工程标识 <span>*</span></label>
        <input id="project-code" v-model="form.code" class="form-input" required maxlength="40" title="使用字母、数字、短横线或下划线">
      </div>
      <div>
        <label class="input-label" for="project-language">主要技术栈</label>
        <select id="project-language" v-model="form.language" class="form-input">
          <option v-for="item in languages" :key="item">{{ item }}</option>
        </select>
      </div>
    </div>

    <label class="input-label" for="project-description">工程说明</label>
    <textarea id="project-description" v-model="form.description" class="form-input" rows="3" maxlength="1000" placeholder="描述工程背景、业务范围或用途"></textarea>

    <label class="input-label" for="project-status">工程状态</label>
    <select id="project-status" v-model="form.status" class="form-input">
      <option value="available">可用</option>
      <option value="archived">已归档</option>
    </select>

    <div class="input-label">代码压缩包 {{ project ? '（可选替换）' : '*' }}</div>
    <div
      class="project-dropzone"
      :class="{ dragging }"
      @dragover.prevent="dragging = true"
      @dragleave="dragging = false"
      @drop.prevent="onDrop"
    >
      <span class="project-drop-icon"><AppIcon name="folder" :size="28" /></span>
      <b>将工程压缩包拖到这里，或选择文件</b>
      <small>ZIP / TAR / TAR.GZ · 最大 100 MB</small>
      <input id="project-archive" ref="fileInputEl" type="file" accept=".zip,.tar,.tar.gz,.tgz" aria-label="选择代码工程压缩包" @change="onInputChange">
      <button type="button" class="btn" @click="fileInputEl?.click()">选择压缩包</button>
    </div>

    <div class="project-selected-file">{{ fileStatus }}</div>
    <div class="project-form-error" role="alert">{{ error }}</div>
    <div class="project-form-note">压缩包仅在当前浏览器中保存，不上传服务器，也不会被自动解压或执行。</div>

    <div class="modal-actions">
      <button type="button" class="btn" @click="$emit('close')">取消</button>
      <button type="submit" class="btn btn-primary" :disabled="projects.busy">
        {{ projects.busy ? '正在保存…' : project ? '保存修改' : '上传并创建工程' }}
      </button>
    </div>
  </form>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import AppIcon from './AppIcon.vue'
import { getProject, projectSize, projects, saveProject, validateArchiveFile } from '../services/projects'
import { showToast } from '../store/ui'

const props = defineProps({
  id: { type: String, default: '' }
})
const emit = defineEmits(['close'])

const project = computed(() => getProject(props.id))
const languages = ['待识别', 'Java / Spring', 'JavaScript / TypeScript', 'Python', 'COBOL / 主机', '其他']
const form = reactive({
  name: project.value?.name || '',
  code: project.value?.code || `PRJ-${Date.now().toString(36).toUpperCase()}`,
  language: project.value?.language || '待识别',
  description: project.value?.description || '',
  status: project.value?.status || 'available'
})

const selectedFile = ref(null)
const fileStatus = ref(project.value ? `当前文件：${project.value.archiveName} · ${projectSize(project.value.archiveSize)}` : '尚未选择压缩包')
const error = ref('')
const dragging = ref(false)
const fileInputEl = ref(null)

async function acceptFile(file) {
  if (!file) return
  selectedFile.value = null
  fileStatus.value = '正在检查压缩包格式…'
  error.value = ''
  try {
    await validateArchiveFile(file)
    selectedFile.value = file
    fileStatus.value = `已选择：${file.name} · ${projectSize(file.size)}（格式头已检查，尚未解压）`
    if (!form.name.trim()) form.name = file.name.replace(/\.(zip|tar|tar\.gz|tgz)$/i, '')
  } catch (err) {
    selectedFile.value = null
    error.value = err.message
    fileStatus.value = '文件未接受，请重新选择'
  }
}

function onInputChange(event) {
  acceptFile(event.target.files?.[0])
}

function onDrop(event) {
  dragging.value = false
  if (event.dataTransfer.files.length !== 1) {
    error.value = '一次请选择一个工程压缩包。'
    return
  }
  acceptFile(event.dataTransfer.files[0])
}

async function submit() {
  if (projects.busy) return
  error.value = ''
  const name = form.name.trim()
  const code = form.code.trim()
  if (!name) {
    error.value = '请填写工程名称。'
    return
  }
  if (!/^[A-Za-z0-9_-]+$/.test(code)) {
    error.value = '工程标识只能使用字母、数字、短横线或下划线。'
    return
  }
  if (!project.value && !selectedFile.value) {
    error.value = '请选择代码工程压缩包。'
    return
  }

  projects.busy = true
  try {
    await saveProject({
      id: project.value?.id,
      name,
      code,
      language: form.language,
      description: form.description.trim(),
      status: form.status,
      archiveFile: selectedFile.value
    })
    emit('close')
    showToast(project.value ? '已保存工程修改' : `代码工程已保存至${projects.mode === 'persistent' ? '本地工程库' : '临时内存'}`)
  } catch (err) {
    error.value = err.name === 'QuotaExceededError'
      ? '浏览器存储空间不足，请删除不需要的工程或缩小压缩包。'
      : err.message || '保存失败，请重试；本地存储可能不可用。'
  } finally {
    projects.busy = false
  }
}
</script>
