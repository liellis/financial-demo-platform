<template>
  <PageIntro
    kicker="02 / REUSABLE ENGINEERING SKILLS"
    title="让已有技能，成为标准研发能力"
    desc="按任务组织专业 Skills，为执行提供知识、规范和质量约束。"
  >
    <button class="btn btn-primary" @click="openModal('import-skills')">
      <AppIcon name="download" :size="14" />{{ demo.skillsImported ? '查看导入清单' : '从能力库导入示例' }}
    </button>
  </PageIntro>

  <div class="v3-banner">
    <AppIcon name="layers" :size="22" />
    <div>
      <b>本次场景能力包 <span>SDLC / 示例配置</span></b>
      <p>理解工程 → 分析需求 → 评估影响 → 增量开发 → 验证与交付</p>
    </div>
    <span class="pill" :class="demo.skillsImported ? 'success' : 'pending'">
      {{ demo.skillsImported ? '已加入本地演示' : '待导入示例' }}
    </span>
  </div>

  <section class="panel">
    <div class="panel-heading">
      <h2 class="panel-title">SDLC Skills 清单</h2>
      <span class="pill neutral">名称示例 · 版本待映射</span>
    </div>
    <div class="v3-skill-grid">
      <button v-for="skill in skillRows" :key="skill.skill" @click="openModal('skill-detail', { skill: skill.skill })">
        <span class="module-icon"><AppIcon :name="skill.icon" :size="20" /></span>
        <b>{{ skill.name }}</b>
        <code>{{ skill.skill }}</code>
        <p>{{ skill.desc }}</p>
        <span class="text-link">上下文与产出 <AppIcon name="arrow" :size="12" /></span>
      </button>
    </div>
  </section>

  <div class="callout blue">
    <AppIcon name="help" :size="16" />
    <span>导入操作只配置本地演示清单；实际 Skill 文件、版本和执行命令将在平台接入时映射。</span>
  </div>

  <div class="v3-next">
    <span>复用专业知识与规范，减少每次任务重新组织上下文</span>
    <button class="btn btn-primary" @click="next">进入需求与 AI 开发 <AppIcon name="arrow" :size="13" /></button>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import PageIntro from '../components/PageIntro.vue'
import { demo, skillRows } from '../store/demo'
import { openModal, showToast } from '../store/ui'

const router = useRouter()

function next() {
  if (!demo.skillsImported) {
    showToast('请先导入本地示例 Skills 清单')
    return
  }
  router.push({ name: 'develop' })
}
</script>
