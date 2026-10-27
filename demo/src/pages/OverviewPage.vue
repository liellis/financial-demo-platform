<template>
  <PageIntro
    kicker="DEMO PORTAL / OVERVIEW"
    title="看见完整流程，理解平台价值"
    desc="从业务需求与存量资产出发，理解平台如何支撑复用、研发和可信交付。"
  >
    <button class="btn btn-primary" @click="go('development')">
      进入存量系统开发 <AppIcon name="arrow" :size="14" />
    </button>
  </PageIntro>

  <ReferenceFlow scope="overview" />

  <div class="portal-overview-values">
    <div v-for="value in values" :key="value.title">
      <AppIcon :name="value.icon" :size="20" />
      <span><b>{{ value.title }}</b><p>{{ value.desc }}</p></span>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import PageIntro from '../components/PageIntro.vue'
import ReferenceFlow from '../components/ReferenceFlow.vue'

const router = useRouter()

const values = [
  { icon: 'chart', title: '研发生产力', desc: '专业 Skills 串联分析、实施与验证' },
  { icon: 'layers', title: '存量资产复用', desc: '让已有业务知识、代码与测试参与开发' },
  { icon: 'shield', title: '质量与安全', desc: '变更边界、质量门禁和证据贯穿全程' }
]

function go(name) {
  router.push({ name })
}
</script>
