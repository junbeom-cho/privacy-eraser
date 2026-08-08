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
const saved = ref(false)
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
  saved.value = false
  error.value = ''
  try {
    // 스키마가 비어 있으면 이관 대상을 아직 정하지 않은 것으로 봅니다.
    const edit = editConnection.schema.trim() ? editConnection : null
    await updateProject(id, name.value, rawConnection, edit)
    saved.value = true
  } catch (e) {
    error.value = e instanceof Error ? e.message : '저장하지 못했습니다.'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <main class="project-edit">
    <RouterLink to="/" class="back">← 목록</RouterLink>
    <h1>프로젝트 수정</h1>

    <p v-if="loading">불러오는 중…</p>

    <form v-else @submit.prevent="submit">
      <label class="name">
        프로젝트 이름
        <input v-model="name" required />
      </label>

      <DbConnectionFields
        v-model="rawConnection"
        title="원본 (raw_schema)"
        hint="비식별화 대상 원본입니다. 읽기만 합니다."
        schema-placeholder="RAW_SCHEMA"
      />

      <DbConnectionFields
        v-model="editConnection"
        title="이관 대상 (edit_schema)"
        hint="비식별화 결과가 저장됩니다. 스키마를 비워두면 아직 정하지 않은 것으로 둡니다."
        schema-placeholder="EDIT_SCHEMA"
      />

      <button type="submit" class="primary" :disabled="saving">
        {{ saving ? '저장 중…' : '저장' }}
      </button>
    </form>

    <p v-if="saved" class="ok" role="status">저장했습니다.</p>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
  </main>
</template>

<style lang="scss" scoped>
.project-edit {
  max-width: 46rem;
  margin: 0 auto;
  padding: 2rem 1rem 4rem;

  .back {
    display: inline-block;
    margin-bottom: 1rem;
    color: var(--muted);
    font-size: 0.9rem;
  }

  h1 {
    margin: 0 0 1.5rem;
    font-size: 1.5rem;
  }

  form {
    display: grid;
    gap: 1.25rem;
  }

  .name {
    display: grid;
    gap: 0.3rem;
    font-size: 0.9rem;
  }

  .primary {
    justify-self: start;
    padding: 0.6rem 1.4rem;
  }

  .ok {
    color: var(--success);
  }

  .error {
    color: var(--danger);
  }
}
</style>
