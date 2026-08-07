<script setup lang="ts">
import { reactive, ref } from 'vue'
import DbConnectionFields from '@/components/DbConnectionFields.vue'
import { createProject, emptyConnection } from '@/api/projects'

const name = ref('')
const rawConnection = reactive(emptyConnection())
const editConnection = reactive(emptyConnection())

const saving = ref(false)
const createdId = ref<number | null>(null)
const error = ref('')

async function submit() {
  saving.value = true
  createdId.value = null
  error.value = ''
  try {
    const created = await createProject(name.value, rawConnection, editConnection)
    createdId.value = created.id
  } catch (e) {
    error.value = e instanceof Error ? e.message : '프로젝트 생성에 실패했습니다.'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <main class="project-create">
    <h1>프로젝트 생성</h1>

    <form @submit.prevent="submit">
      <label class="name">
        프로젝트 이름
        <input v-model="name" required placeholder="예) 고객정보 비식별화" />
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
        hint="비식별화 결과가 저장됩니다. 원본과 같은 스키마는 사용할 수 없습니다."
        schema-placeholder="EDIT_SCHEMA"
      />

      <button type="submit" class="primary" :disabled="saving">
        {{ saving ? '생성 중…' : '프로젝트 생성' }}
      </button>
    </form>

    <p v-if="createdId" class="ok" role="status">프로젝트를 생성했습니다. (id: {{ createdId }})</p>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
  </main>
</template>

<style lang="scss" scoped>
.project-create {
  max-width: 46rem;
  margin: 0 auto;
  padding: 2rem 1rem 4rem;

  h1 {
    margin-bottom: 1.5rem;
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
