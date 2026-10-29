<script setup lang="ts">
import { ElMessage } from 'element-plus'

const props = withDefaults(
  defineProps<{
    text?: string | null
    label?: string
    mono?: boolean
  }>(),
  {
    text: '',
    label: '内容',
    mono: false
  }
)

async function copy() {
  if (!props.text) {
    return
  }
  try {
    await navigator.clipboard.writeText(props.text)
    ElMessage.success('已复制')
  } catch {
    // 非 https / 浏览器限制下 clipboard 不可用
    ElMessage.warning('复制失败，请手动选中后复制')
  }
}
</script>

<template>
  <span v-if="!text" class="copy-empty">-</span>
  <el-tooltip v-else :content="`点击复制${label}`" placement="top" :show-after="400">
    <span class="copy-text" :class="{ mono }" @click.stop="copy">{{ text }}</span>
  </el-tooltip>
</template>

<style scoped>
.copy-text {
  cursor: copy;
  border-bottom: 1px dashed transparent;
  word-break: break-all;
}

.copy-text:hover {
  color: var(--el-color-primary);
  border-bottom-color: var(--el-color-primary-light-5);
}

.copy-text.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  letter-spacing: 0.2px;
}

.copy-empty {
  color: var(--el-text-color-placeholder);
}
</style>
