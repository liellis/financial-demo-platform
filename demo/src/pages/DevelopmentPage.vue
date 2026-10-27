<template>
  <PageIntro
    kicker="02 / EXISTING SYSTEM DEVELOPMENT"
    title="存量系统开发需求"
    desc="先查看研发流程，再进入下方或左侧的二级菜单开展演示。"
  >
    <button class="btn" @click="go('projects')"><AppIcon name="folder" :size="14" />查看工程来源</button>
  </PageIntro>

  <ReferenceFlow scope="development" />

  <div class="portal-secondary-heading">
    <h2>研发工作区</h2>
    <span>以下六项与左侧二级菜单一一对应</span>
  </div>
  <div class="portal-workspaces">
    <a v-for="(workspace, index) in workflowItems" :key="workspace.id" href="#" @click.prevent="go(workspace.id)">
      <span class="portal-workspace-num">02.{{ index + 1 }}</span>
      <span class="module-icon"><AppIcon :name="workspace.icon" :size="18" /></span>
      <div>
        <h3>{{ workspace.name }}</h3>
        <p>{{ workspace.description }}</p>
      </div>
      <AppIcon name="arrow" :size="14" />
    </a>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import PageIntro from '../components/PageIntro.vue'
import ReferenceFlow from '../components/ReferenceFlow.vue'
import { workflowItems } from '../store/demo'

const router = useRouter()

function go(name) {
  router.push({ name })
}
</script>
