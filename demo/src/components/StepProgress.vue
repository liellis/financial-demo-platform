<template>
  <div class="v3-progress" aria-label="演示主线">
    <button
      v-for="(step, index) in steps"
      :key="step.id"
      :class="{ active: route.name === step.id }"
      @click="go(step.id)"
    >
      <span>{{ index + 1 }}</span>{{ step.name }}<i v-if="index < steps.length - 1"></i>
    </button>
  </div>
</template>

<script setup>
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const steps = [
  { id: 'knowledge', name: '知识准备' },
  { id: 'skills', name: '导入 Skills' },
  { id: 'develop', name: '需求与开发' },
  { id: 'quality', name: '质量验证' },
  { id: 'trace', name: '追溯分析' }
]

function go(id) {
  router.push({ name: id })
}
</script>
