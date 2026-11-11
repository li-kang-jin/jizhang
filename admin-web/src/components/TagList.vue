<script setup lang="ts">
/**
 * 账单标签组。
 *
 * 后台多处列表（交易流水、账户流水）都要展示标签，统一成一个组件，省得两处各写一套样式；
 * 标签多了自动换行，不会把列宽撑爆或者被省略号吃掉。
 */
withDefaults(
  defineProps<{
    tags?: string[]
    /** 无标签时的占位文案 */
    empty?: string
  }>(),
  { empty: '-' }
)
</script>

<template>
  <div v-if="tags && tags.length" class="tag-list">
    <el-tag v-for="tag in tags" :key="tag" size="small" type="info" effect="plain">
      {{ tag }}
    </el-tag>
  </div>
  <span v-else class="muted">{{ empty }}</span>
</template>

<style scoped>
.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.muted {
  color: var(--el-text-color-placeholder);
}
</style>
