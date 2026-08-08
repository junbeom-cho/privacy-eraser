<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import DbConnectionFields from '@/components/DbConnectionFields.vue'
import { createProject, emptyConnection } from '@/api/projects'

const router = useRouter()

const name = ref('')
const rawConnection = reactive(emptyConnection())

const saving = ref(false)
const error = ref('')

async function submit() {
  saving.value = true
  error.value = ''
  try {
    await createProject(name.value, rawConnection)
    // 저장에 성공하면 목록으로 돌아가고, 목록에서 결과를 알립니다.
    router.push({ name: 'project-list', query: { message: `'${name.value}' 프로젝트를 생성했습니다.` } })
  } catch (e) {
    error.value = e instanceof Error ? e.message : '프로젝트 생성에 실패했습니다.'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <main class="container py-4" style="max-width: 46rem">
    <RouterLink to="/" class="link-secondary small text-decoration-none">← 목록</RouterLink>
    <h1 class="h4 mt-2 mb-1">프로젝트 생성</h1>
    <p class="text-body-secondary small mb-4">
      비식별화할 원본 DB를 등록합니다. 이관 대상은 원본을 살펴본 뒤 비식별화를 실행할 때 정합니다.
    </p>

    <div v-if="error" class="alert alert-danger" role="alert">{{ error }}</div>

    <form class="vstack gap-3" @submit.prevent="submit">
      <div>
        <label class="form-label small">프로젝트 이름</label>
        <input v-model="name" class="form-control" required placeholder="예) 고객정보 비식별화" />
      </div>

      <DbConnectionFields
        v-model="rawConnection"
        title="원본 (raw_schema)"
        hint="비식별화 대상 원본입니다. 읽기만 합니다."
        schema-placeholder="RAW_SCHEMA"
      />

      <div class="d-flex gap-2">
        <button type="submit" class="btn btn-primary" :disabled="saving">
          <span v-if="saving" class="spinner-border spinner-border-sm me-1" aria-hidden="true"></span>
          {{ saving ? '생성 중' : '프로젝트 생성' }}
        </button>
        <RouterLink to="/" class="btn btn-outline-secondary">취소</RouterLink>
      </div>
    </form>
  </main>
</template>
