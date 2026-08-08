<script setup lang="ts">
import { ref } from 'vue'
import { testConnection, type ConnectionTestResult, type DbConnectionInput } from '@/api/projects'

defineProps<{
  title: string
  hint: string
  schemaPlaceholder: string
  /** 수정 화면에서는 비밀번호를 비워두면 기존 값을 유지한다고 안내합니다. */
  passwordHint?: string
}>()

const connection = defineModel<DbConnectionInput>({ required: true })

const testing = ref(false)
const result = ref<ConnectionTestResult | null>(null)

async function runTest() {
  testing.value = true
  result.value = null
  try {
    result.value = await testConnection(connection.value)
  } catch (error) {
    result.value = {
      success: false,
      message: error instanceof Error ? error.message : '접속 테스트에 실패했습니다.',
    }
  } finally {
    testing.value = false
  }
}
</script>

<template>
  <div class="card">
    <div class="card-header d-flex align-items-center justify-content-between">
      <span class="fw-semibold">{{ title }}</span>
      <button
        type="button"
        class="btn btn-sm btn-outline-secondary"
        :disabled="testing"
        @click="runTest"
      >
        <span v-if="testing" class="spinner-border spinner-border-sm me-1" aria-hidden="true"></span>
        {{ testing ? '확인 중' : '접속 테스트' }}
      </button>
    </div>

    <div class="card-body">
      <p class="text-body-secondary small mb-3">{{ hint }}</p>

      <div class="row g-3">
        <div class="col-12">
          <label class="form-label small">JDBC URL</label>
          <input v-model="connection.url" class="form-control font-mono" spellcheck="false" />
        </div>
        <div class="col-md-6">
          <label class="form-label small">사용자명</label>
          <input v-model="connection.username" class="form-control" autocomplete="off" />
        </div>
        <div class="col-md-6">
          <label class="form-label small">비밀번호</label>
          <input
            v-model="connection.password"
            type="password"
            class="form-control"
            autocomplete="new-password"
          />
          <div v-if="passwordHint" class="form-text small">{{ passwordHint }}</div>
        </div>
        <div class="col-md-6">
          <label class="form-label small">스키마</label>
          <input
            v-model="connection.schema"
            class="form-control font-mono"
            :placeholder="schemaPlaceholder"
          />
        </div>
      </div>

      <div
        v-if="result"
        class="alert mt-3 mb-0 py-2 small"
        :class="result.success ? 'alert-success' : 'alert-danger'"
        role="status"
      >
        {{ result.message }}
      </div>
    </div>
  </div>
</template>
