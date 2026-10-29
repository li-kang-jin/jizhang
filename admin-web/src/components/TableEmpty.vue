<script setup lang="ts">
withDefaults(
  defineProps<{
    error?: boolean
    filtered?: boolean
  }>(),
  {
    error: false,
    filtered: false
  }
)

defineEmits<{ retry: []; clear: [] }>()
</script>

<template>
  <el-empty v-if="error" description="加载失败，请稍后重试" :image-size="72">
    <el-button type="primary" plain @click="$emit('retry')">重新加载</el-button>
  </el-empty>
  <el-empty v-else-if="filtered" description="没有符合筛选条件的数据" :image-size="72">
    <el-button @click="$emit('clear')">清空筛选</el-button>
  </el-empty>
  <el-empty v-else description="暂无数据" :image-size="72" />
</template>
