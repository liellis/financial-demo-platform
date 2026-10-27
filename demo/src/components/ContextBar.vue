<template>
  <section class="v3-context">
    <div class="scenario-icon"><AppIcon name="credit" :size="22" /></div>
    <div class="scenario-title">
      <span>当前演示场景 <i>{{ demo.scenario === 'card' ? 'CR-001' : 'DEMO-001' }} · 示例</i></span>
      <b>{{ scenarioName() }}</b>
    </div>
    <button class="text-link" @click="openModal('scenario')">切换场景 <AppIcon name="down" :size="12" /></button>
    <div class="context-divider"></div>
    <div class="run-context">
      <span>DemoRun ID</span>
      <button @click="openModal('events')">DEMO-RUN-001 <AppIcon name="external" :size="11" /></button>
    </div>
    <span class="v3-ready">
      <i class="dot"></i>{{ demo.running ? '演练中' : demo.reviewed ? '模拟评审完成' : 'Ready · 本地原型' }}
    </span>
  </section>
  <div v-if="activeProject" class="project-active-context">
    <AppIcon name="folder" :size="14" />
    <span>当前代码工程 <b>{{ activeProject.name }}</b></span>
    <small>{{ activeProject.code }} · 已绑定本地压缩包，尚未解压分析</small>
    <button @click="goProjects">管理工程 <AppIcon name="arrow" :size="11" /></button>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from './AppIcon.vue'
import { demo, scenarioName } from '../store/demo'
import { openModal } from '../store/ui'
import { projects } from '../services/projects'

const router = useRouter()
const activeProject = computed(() => projects.items.find((item) => item.id === projects.activeId))

function goProjects() {
  router.push({ name: 'projects' })
}
</script>
