<script setup lang="ts">
withDefaults(
  defineProps<{
    total: number
    unit?: string
  }>(),
  {
    unit: '条'
  }
)
</script>

<template>
  <div class="page-toolbar">
    <slot />
    <div class="spacer"></div>
    <slot name="actions" />
    <span class="total-text">共 {{ total }} {{ unit }}</span>
  </div>
</template>
