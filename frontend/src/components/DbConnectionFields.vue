<script setup lang="ts">
import { ref } from 'vue'
import {
  testConnection,
  type ConnectionTestResult,
  type DbConnectionInput,
} from '@/api/projects'

defineProps<{ title: string; hint: string; schemaPlaceholder: string }>()

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
  <fieldset class="connection">
    <legend>{{ title }}</legend>
    <p class="hint">{{ hint }}</p>

    <label>
      JDBC URL
      <input v-model="connection.url" placeholder="jdbc:oracle:thin:@localhost:1521/XE" />
    </label>
    <label>
      사용자명
      <input v-model="connection.username" autocomplete="off" />
    </label>
    <label>
      비밀번호
      <input v-model="connection.password" type="password" autocomplete="new-password" />
    </label>
    <label>
      스키마
      <input v-model="connection.schema" :placeholder="schemaPlaceholder" />
    </label>

    <button type="button" :disabled="testing" @click="runTest">
      {{ testing ? '접속 확인 중…' : '접속 테스트' }}
    </button>

    <p v-if="result" class="result" :class="{ ok: result.success }" role="status">
      {{ result.message }}
    </p>
  </fieldset>
</template>

<style lang="scss" scoped>
.connection {
  display: grid;
  gap: 0.75rem;
  padding: 1rem 1.25rem 1.25rem;
  border: 1px solid var(--line);
  border-radius: 8px;

  legend {
    padding: 0 0.4rem;
    font-weight: 600;
  }

  .hint {
    margin: 0;
    color: var(--muted);
    font-size: 0.85rem;
  }

  label {
    display: grid;
    gap: 0.3rem;
    font-size: 0.9rem;
  }

  button {
    justify-self: start;
  }

  .result {
    margin: 0;
    font-size: 0.9rem;
    color: var(--danger);

    &.ok {
      color: var(--success);
    }
  }
}
</style>
