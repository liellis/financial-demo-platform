<template>
  <aside class="sidebar v3-sidebar">
    <a class="v3-brand" href="#/overview" @click.prevent="go('overview')">
      <span class="brand-logo"><AppIcon name="code" :size="22" /></span>
      <div>
        <b>AI-Coding <em>Demo Portal</em></b>
        <small>BANKING AI SOFTWARE ENGINEERING</small>
      </div>
    </a>

    <div class="portal-nav-scroll">
      <button class="portal-overview-nav" :class="{ active: section === 'overview' }" @click="go('overview')">
        <AppIcon name="grid" :size="17" /><b>演示总览</b><span>总流程</span>
      </button>

      <div class="space-label portal-nav-label">DEMO MODULES <span>功能导航</span></div>

      <nav aria-label="一级与二级菜单">
        <div v-for="item in navItems" :key="item.id" class="portal-nav-group">
          <button
            class="theme-nav"
            :class="{ selected: section === item.id }"
            :aria-current="route.name === item.id ? 'page' : undefined"
            @click="item.id === 'platform' ? goPlatformHome() : go(item.id)"
          >
            <span class="theme-number">{{ item.number }}</span>
            <div>
              <b>{{ item.name }}</b>
              <small>{{ item.desc }}</small>
            </div>
            <AppIcon v-if="item.id === 'development'" :name="section === item.id ? 'down' : 'chevron'" :size="12" />
            <span v-else-if="section === item.id" class="selected-dot"></span>
          </button>

          <nav
            v-if="item.id === 'development' && section === 'development'"
            class="portal-subnav"
            aria-label="存量系统开发二级菜单"
          >
            <button
              v-for="(workflow, index) in workflowItems"
              :key="workflow.id"
              class="portal-subitem"
              :class="{ active: route.name === workflow.id || (route.name === 'business' && workflow.id === 'efficiency') }"
              @click="go(workflow.id)"
            >
              <span>02.{{ index + 1 }}</span>
              <b>{{ workflow.name }}</b>
              <AppIcon v-if="isCompleted(workflow.id)" name="check" :size="11" />
            </button>
          </nav>
        </div>
      </nav>
    </div>

    <div class="v3-sidebar-bottom">
      <button class="project-management-nav" :class="{ active: route.name === 'projects' }" @click="go('projects')">
        <AppIcon name="folder" :size="16" /> 代码工程管理
        <span class="project-nav-count">{{ projects.items.length }}</span>
      </button>
      <button @click="openModal('integrations')">
        <AppIcon name="plug" :size="15" /> 演示环境 <span><i class="dot"></i> 待接入</span>
      </button>
      <div class="v3-profile">
        <span class="avatar">DE</span>
        <div>
          <b>演示工作空间</b>
          <small>原型评审 · 合成示例</small>
        </div>
        <button aria-label="演示帮助" @click="openModal('guide')"><AppIcon name="help" :size="16" /></button>
      </div>
    </div>
  </aside>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from './AppIcon.vue'
import { demo, isCompleted, workflowItems } from '../store/demo'
import { openModal } from '../store/ui'
import { projects } from '../services/projects'

const route = useRoute()
const router = useRouter()

const navItems = [
  { id: 'platform', number: '01', name: 'iCode 整体功能', desc: '能力全景与模块入口' },
  { id: 'development', number: '02', name: '存量系统开发需求', desc: '增量研发流程与工作区' },
  { id: 'evolution', number: '03', name: 'RSI 自演进', desc: '反馈、评测与版本发布' },
  { id: 'resources', number: '04', name: 'Token 资源监控', desc: '用量、时延与成本' }
]

const section = computed(() => {
  const name = route.name
  if (name === 'overview') return 'overview'
  if (name === 'projects') return 'projects'
  if (name === 'platform') return 'platform'
  if (name === 'evolution') return 'evolution'
  if (name === 'resources') return 'resources'
  return 'development'
})

function go(id) {
  demo.mobileNav = false
  router.push({ name: id })
}

function goPlatformHome() {
  demo.platformTab = 'overview'
  go('platform')
}
</script>
