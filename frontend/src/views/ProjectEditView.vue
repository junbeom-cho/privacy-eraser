<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DbConnectionFields from '@/components/DbConnectionFields.vue'
import { emptyConnection, getProject, toInput, updateProject } from '@/api/projects'

const route = useRoute()
const router = useRouter()
const id = Number(route.params.id)

const name = ref('')
const rawConnection = reactive(emptyConnection())
const editConnection = reactive(emptyConnection())

const loading = ref(true)
const saving = ref(false)
const error = ref('')

onMounted(async () => {
  try {
    const project = await getProject(id)
    name.value = project.name
    Object.assign(rawConnection, toInput(project.rawConnection))
    Object.assign(editConnection, toInput(project.editConnection))
  } catch (e) {
    error.value = e instanceof Error ? e.message : '프로젝트를 불러오지 못했습니다.'
  } finally {
    loading.value = false
  }
})

async function submit() {
  saving.value = true
  error.value = ''
  try {
    // 스키마가 비어 있으면 이관 대상을 아직 정하지 않은 것으로 봅니다.
    const edit = editConnection.schema.trim() ? editConnection : null
    await updateProject(id, name.value, rawConnection, edit)
    router.push({ name: 'project-list', query: { message: `'${name.value}' 프로젝트를 저장했습니다.` } })
  } catch (e) {
    error.value = e instanceof Error ? e.message : '저장하지 못했습니다.'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <main class="container py-4" style="max-width: 46rem">
    <RouterLink to="/" class="link-secondary small text-decoration-none">← 목록</RouterLink>
    <h1 class="h4 mt-2 mb-4">프로젝트 수정</h1>

    <div v-if="error" class="alert alert-danger" role="alert">{{ error }}</div>

    <div v-if="loading" class="text-body-secondary small">
      <span class="spinner-border spinner-border-sm me-1" aria-hidden="true"></span>
      불러오는 중
    </div>

    <form v-else class="vstack gap-3" @submit.prevent="submit">
      <div>
        <label class="form-label small">프로젝트 이름</label>
        <input v-model="name" class="form-control" required />
      </div>

      <DbConnectionFields
        v-model="rawConnection"
        title="원본 (raw_schema)"
        hint="비식별화 대상 원본입니다. 읽기만 합니다."
        schema-placeholder="RAW_SCHEMA"
        password-hint="비워두면 기존 비밀번호를 그대로 씁니다."
      />

      <DbConnectionFields
        v-model="editConnection"
        title="이관 대상 (edit_schema)"
        hint="비식별화 결과가 저장됩니다. 스키마를 비워두면 아직 정하지 않은 것으로 둡니다."
        schema-placeholder="EDIT_SCHEMA"
        password-hint="비워두면 기존 비밀번호를 그대로 씁니다."
      />

      <div class="d-flex gap-2">
        <button type="submit" class="btn btn-primary" :disabled="saving">
          <span v-if="saving" class="spinner-border spinner-border-sm me-1" aria-hidden="true"></span>
          {{ saving ? '저장 중' : '저장' }}
        </button>
        <RouterLink to="/" class="btn btn-outline-secondary">취소</RouterLink>
      </div>
    </form>
  </main>
</template>
