<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import DbConnectionFields from '@/components/DbConnectionFields.vue'
import { createProject, emptyConnection } from '@/api/projects'

const router = useRouter()

const name = ref('')
const rawConnection = reactive(emptyConnection())
const editConnection = reactive(emptyConnection())

const saving = ref(false)
const error = ref('')

async function submit() {
  saving.value = true
  error.value = ''
  try {
    const created = await createProject(name.value, rawConnection, editConnection)
    // 만들자마자 다음 단계로 이어집니다. 목록으로 돌려보내면 흐름이 끊깁니다.
    router.push(`/projects/${created.id}/schema`)
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
      비식별화할 원본 DB와, 결과를 옮겨 담을 이관 대상을 등록합니다.
      이관 대상 계정이 아직 없어도 됩니다 — 스키마 이름만 정하면 만드는 SQL을 설정 화면에서 알려줍니다.
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

      <DbConnectionFields
        v-model="editConnection"
        title="이관 대상 (edit_schema)"
        hint="비식별화 결과가 저장됩니다. 원본과 같은 스키마는 쓸 수 없습니다."
        schema-placeholder="EDIT_SCHEMA"
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
