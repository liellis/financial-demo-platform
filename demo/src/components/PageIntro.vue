<template>
  <div class="v3-intro">
    <div>
      <span class="v3-kicker">{{ kicker }}</span>
      <h1 v-html="title"></h1>
      <p v-html="desc"></p>
    </div>
    <div class="heading-actions">
      <slot />
    </div>
  </div>
</template>

<script setup>
defineProps({
  kicker: { type: String, default: '' },
  title: { type: String, default: '' },
  desc: { type: String, default: '' }
})
</script>
