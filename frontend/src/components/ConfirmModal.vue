<script setup lang="ts">
/**
 * Bootstrap 모달을 Vue 로 제어합니다. Bootstrap JS 는 DOM 을 직접 건드려 Vue 와 겹칩니다.
 */
defineProps<{
  title: string
  confirmLabel: string
  /** 진행 중이면 버튼을 잠그고 스피너를 보여줍니다. */
  busy?: boolean
}>()

const emit = defineEmits<{ confirm: []; cancel: [] }>()
</script>

<template>
  <div class="modal d-block" tabindex="-1" role="dialog">
    <div class="modal-dialog modal-dialog-centered">
      <div class="modal-content">
        <div class="modal-header">
          <h2 class="modal-title h6 mb-0">{{ title }}</h2>
          <button type="button" class="btn-close" aria-label="닫기" @click="emit('cancel')"></button>
        </div>
        <div class="modal-body">
          <slot />
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-outline-secondary" @click="emit('cancel')">취소</button>
          <button type="button" class="btn btn-danger" :disabled="busy" @click="emit('confirm')">
            <span v-if="busy" class="spinner-border spinner-border-sm me-1" aria-hidden="true"></span>
            {{ confirmLabel }}
          </button>
        </div>
      </div>
    </div>
  </div>
  <div class="modal-backdrop fade show"></div>
</template>
