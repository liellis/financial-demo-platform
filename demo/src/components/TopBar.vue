<template>
  <header class="v3-top">
    <div class="v3-crumb">
      <button class="icon-button mobile-menu" aria-label="展开导航" @click="demo.mobileNav = !demo.mobileNav">
        <AppIcon name="menu" :size="19" />
      </button>
      <span>Demo Portal</span>
      <AppIcon name="chevron" :size="12" />
      <b>{{ section === 'development' ? '存量系统增量开发' : pageLabel }}</b>
    </div>
    <div class="v3-tools">
      <button class="icon-button lang-toggle" :aria-label="i18n.lang === 'en' ? 'Switch to Chinese' : '切换语言'" @click="toggleLang">
        <b>{{ i18n.lang === 'en' ? '中' : 'EN' }}</b>
      </button>
      <button class="icon-button" aria-label="演示指南" @click="openModal('guide')"><AppIcon name="help" :size="18" /></button>
      <button class="icon-button" aria-label="切换主秀布局" @click="demo.showcase = !demo.showcase">
        <AppIcon name="expand" :size="18" />
      </button>
      <span class="v3-user">DE</span>
    </div>
  </header>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppIcon from './AppIcon.vue'
import { demo, pageLabels } from '../store/demo'
import { openModal } from '../store/ui'
import { i18n, toggleLang } from '../i18n'

const route = useRoute()

const section = computed(() => {
  const name = route.name
  if (['overview', 'projects', 'platform', 'evolution', 'resources'].includes(name)) return name
  return 'development'
})

const pageLabel = computed(() => pageLabels[route.name] || '演示总览')
</script>
