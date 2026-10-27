<template>
  <SidebarNav />
  <div class="v3-shell">
    <TopBar />
    <div class="v3-workspace">
      <ContextBar v-if="route.name !== 'projects'" />
      <StepProgress v-if="isDetail" />
      <div class="v3-layout" :class="{ wide: !isDetail }">
        <main id="main" class="v3-main">
          <RouterView />
          <div v-if="demo.view === 'technical'" class="tech-panel">
            <b>执行来源 · MOCK / 本地原型</b><br>
            run: DEMO-RUN-001 · code_revision: pending · adapter: not_connected<br>
            真实任务、Skill 版本、事件、产物与日志将在后台接入后绑定。
          </div>
        </main>
        <RunRail v-if="isDetail" />
      </div>
      <footer class="v3-footer">
        <span><i class="dot"></i> Demo Environment · 交互原型，尚未连接后台</span>
        <span>统一场景 · 统一运行 · 统一证据 <b>V3.3</b></span>
      </footer>
    </div>
  </div>
  <ModalHost />
  <div id="toast" role="status" aria-live="polite" :class="{ visible: ui.toast }">{{ ui.toast }}</div>
</template>

<script setup>
import { computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import SidebarNav from './components/SidebarNav.vue'
import TopBar from './components/TopBar.vue'
import ContextBar from './components/ContextBar.vue'
import StepProgress from './components/StepProgress.vue'
import RunRail from './components/RunRail.vue'
import ModalHost from './components/ModalHost.vue'
import { demo } from './store/demo'
import { ui } from './store/ui'
import { initializeProjects } from './services/projects'

const route = useRoute()

const isDetail = computed(() => !['overview', 'development', 'projects', 'platform', 'evolution', 'resources'].includes(route.name))

onMounted(() => {
  initializeProjects()
})

watch(
  () => [demo.view, demo.showcase, demo.mobileNav],
  () => {
    document.body.classList.toggle('technical', demo.view === 'technical')
    document.body.classList.toggle('showcase', demo.showcase)
    document.body.classList.toggle('mobile-nav', demo.mobileNav)
  },
  { immediate: true }
)

watch(
  () => route.fullPath,
  () => {
    demo.mobileNav = false
  }
)
</script>
