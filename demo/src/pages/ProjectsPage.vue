<template>
  <PageIntro
    kicker="WORKSPACE / CODE PROJECTS"
    title="代码工程管理"
    desc="上传已有工程压缩包，维护代码资产，并选择当前演示所使用的工程。"
  >
    <button class="btn btn-primary" :disabled="!projects.ready" @click="openModal('project-form')">
      <AppIcon name="upload" :size="14" />上传代码工程
    </button>
  </PageIntro>

  <div class="project-storage-notice" :class="{ temporary: projects.mode === 'memory' }">
    <AppIcon name="database" :size="16" />
    <div>
      <b>{{ storageTitle }}</b>
      <p>{{ projects.error || '压缩包与工程信息保存在当前浏览器中，不上传服务器；当前未接入解压、知识分析或代码执行。' }}</p>
    </div>
    <span>LOCAL</span>
  </div>

  <div class="project-summary">
    <div><span>工程总数</span><b>{{ projects.items.length }}<small>个</small></b></div>
    <div><span>可用工程</span><b>{{ availableCount }}<small>个</small></b></div>
    <div><span>压缩包占用</span><b>{{ projectSize(totalSize) }}</b></div>
    <div><span>当前演示工程</span><b class="project-current-name">{{ activeProject?.name || '尚未选择' }}</b></div>
  </div>

  <section class="panel">
    <div class="project-list-toolbar">
      <div>
        <h2>工程列表</h2>
        <small>支持上传、查看、编辑、下载和删除</small>
      </div>
      <div class="project-list-filters">
        <label class="search-input">
          <AppIcon name="search" :size="15" />
          <input v-model="projects.query" aria-label="搜索工程" placeholder="搜索工程名称、标识或压缩包">
        </label>
        <select v-model="projects.filter" class="form-input" aria-label="筛选工程状态">
          <option value="all">全部状态</option>
          <option value="available">可用</option>
          <option value="archived">已归档</option>
        </select>
      </div>
    </div>

    <div class="table-wrap">
      <table class="project-table">
        <thead>
          <tr><th>工程名称 / 标识</th><th>代码压缩包</th><th>工程状态</th><th>更新时间</th><th>操作</th></tr>
        </thead>
        <tbody>
          <template v-if="projects.ready">
            <tr v-for="project in rows" :key="project.id">
              <td>
                <button class="project-name-link" @click="openModal('project-detail', { id: project.id })">
                  <AppIcon name="folder" :size="17" />
                  <span><b>{{ project.name }}</b><small>{{ project.code }}</small></span>
                </button>
              </td>
              <td>
                <span class="project-archive-name">{{ project.archiveName }}</span>
                <small>{{ projectSize(project.archiveSize) }} · {{ project.language }}</small>
              </td>
              <td>
                <span class="pill" :class="project.status === 'archived' ? 'neutral' : 'success'">
                  {{ project.status === 'archived' ? '已归档' : '可用' }}
                </span>
                <small v-if="project.id === projects.activeId" class="project-selected-label">当前演示工程</small>
              </td>
              <td>{{ projectDate(project.updatedAt) }}<small>本地记录</small></td>
              <td>
                <div class="project-row-actions">
                  <button @click="openModal('project-detail', { id: project.id })">查看</button>
                  <button @click="editProject(project.id)">编辑</button>
                  <button class="project-delete-link" @click="openModal('project-delete', { id: project.id })">删除</button>
                </div>
              </td>
            </tr>
            <tr v-if="!rows.length">
              <td colspan="5">
                <div class="project-list-empty">
                  <AppIcon name="folder" :size="35" />
                  <h3>{{ projects.items.length ? '没有匹配的代码工程' : '还没有代码工程' }}</h3>
                  <p>{{ projects.items.length ? '调整关键词或状态筛选后再试。' : '将已有工程打包为 ZIP、TAR 或 TAR.GZ 后上传，开始管理工程资产。' }}</p>
                  <button v-if="!projects.items.length" class="btn btn-primary" @click="openModal('project-form')">上传第一个代码工程</button>
                </div>
              </td>
            </tr>
          </template>
          <tr v-else><td colspan="5">正在读取工程…</td></tr>
        </tbody>
      </table>
    </div>
  </section>

  <div class="project-help-grid">
    <div>
      <AppIcon name="folder" :size="19" />
      <div>
        <b>一个压缩包，一份完整工程</b>
        <p>支持 .zip、.tar、.tar.gz、.tgz，单个压缩包上限 100 MB。保留原始文件，可随时下载。</p>
      </div>
    </div>
    <div>
      <AppIcon name="graph" :size="19" />
      <div>
        <b>选择工程，关联演示工作区</b>
        <p>绑定后会在各演示页面显示当前工程；知识索引、版本分析和真实任务将在平台接入后提供。</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import AppIcon from '../components/AppIcon.vue'
import PageIntro from '../components/PageIntro.vue'
import {
  activeProject as findActiveProject,
  filteredProjects,
  projectDate,
  projectSize,
  projects
} from '../services/projects'
import { openModal } from '../store/ui'

const rows = computed(() => {
  projects.query
  projects.filter
  projects.items
  return filteredProjects()
})

const availableCount = computed(() => projects.items.filter((item) => item.status !== 'archived').length)
const totalSize = computed(() => projects.items.reduce((total, item) => total + item.archiveSize, 0))
const activeProject = computed(() => findActiveProject())
const storageTitle = computed(() => {
  if (projects.mode === 'loading') return '正在读取本地工程库'
  if (projects.mode === 'persistent') return '本地工程库 · 刷新后保留'
  return '临时工程库 · 刷新后丢失'
})

function editProject(id) {
  openModal('project-form', { id })
}
</script>
