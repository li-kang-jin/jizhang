<script setup lang="ts">
import { ref, watch } from 'vue'
import type { EntityOption } from '@/types'

const props = withDefaults(
  defineProps<{
    modelValue?: string
    loader: (keyword: string) => Promise<EntityOption[]>
    placeholder?: string
    disabled?: boolean
    clearable?: boolean
    /** 从别的页面带过来的已选值：选项还没加载时用它兜底显示名称 */
    selectedLabel?: string
    width?: string
  }>(),
  {
    modelValue: '',
    placeholder: '请选择',
    disabled: false,
    clearable: true,
    selectedLabel: '',
    width: '200px'
  }
)

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const options = ref<EntityOption[]>([])
const loading = ref(false)
let requestId = 0

function seedSelected() {
  if (!props.modelValue || !props.selectedLabel) {
    return
  }
  if (!options.value.some((item) => item.value === props.modelValue)) {
    options.value = [{ value: props.modelValue, label: props.selectedLabel }, ...options.value]
  }
}

async function search(keyword: string) {
  const current = ++requestId
  loading.value = true
  try {
    const result = await props.loader(keyword.trim())
    if (current !== requestId) {
      return
    }
    options.value = result
    seedSelected()
  } catch {
    if (current === requestId) {
      options.value = []
    }
  } finally {
    if (current === requestId) {
      loading.value = false
    }
  }
}

function handleVisible(visible: boolean) {
  if (visible) {
    void search('')
  }
}

function handleChange(value: unknown) {
  emit('update:modelValue', typeof value === 'string' ? value : '')
}

watch(
  () => [props.modelValue, props.selectedLabel] as const,
  () => seedSelected()
)
</script>

<template>
  <el-select
    :model-value="modelValue"
    filterable
    remote
    :remote-method="search"
    :loading="loading"
    :placeholder="placeholder"
    :disabled="disabled"
    :clearable="clearable"
    :style="{ width }"
    @visible-change="handleVisible"
    @change="handleChange"
  >
    <el-option v-for="item in options" :key="item.value" :label="item.label" :value="item.value" />
  </el-select>
</template>
