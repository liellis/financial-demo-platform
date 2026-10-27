<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.65"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    v-html="icons[name] || icons.document"
  />
</template>

<script setup>
import { icons } from '../icons'

defineProps({
  name: { type: String, default: 'document' },
  size: { type: [Number, String], default: 18 }
})
</script>
