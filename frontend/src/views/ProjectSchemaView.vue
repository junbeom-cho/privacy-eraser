<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getProject, listTables, type ProjectView, type TableView } from '@/api/projects'

const route = useRoute()
const id = Number(route.params.id)

const project = ref<ProjectView | null>(null)
const tables = ref<TableView[]>([])
const loading = ref(true)
const error = ref('')
const expanded = ref<Set<string>>(new Set())

const totalColumns = computed(() => tables.value.reduce((sum, t) => sum + t.columnCount, 0))

function toggle(name: string) {
  const next = new Set(expanded.value)
  next.has(name) ? next.delete(name) : next.add(name)
  expanded.value = next
}

function expandAll() {
  expanded.value = new Set(tables.value.map((t) => t.name))
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    project.value = await getProject(id)
    tables.value = await listTables(id)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '원본 스키마를 읽지 못했습니다.'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <main class="container py-4" style="max-width: 56rem">
    <RouterLink to="/" class="link-secondary small text-decoration-none">← 목록</RouterLink>

    <div class="d-flex align-items-center justify-content-between mt-2 mb-1">
      <h1 class="h4 mb-0">원본 스키마</h1>
      <button type="button" class="btn btn-sm btn-outline-secondary" :disabled="loading" @click="load">
        다시 읽기
      </button>
    </div>
    <p v-if="project" class="text-body-secondary small mb-4">
      {{ project.name }} · <span class="font-mono">{{ project.rawConnection.schema }}</span>
      <span v-if="tables.length"> · 테이블 {{ tables.length }}개, 컬럼 {{ totalColumns }}개</span>
    </p>

    <div v-if="error" class="alert alert-danger" role="alert">{{ error }}</div>

    <div v-if="loading" class="text-body-secondary small">
      <span class="spinner-border spinner-border-sm me-1" aria-hidden="true"></span>
      원본 DB에서 읽는 중
    </div>

    <div v-else-if="tables.length === 0 && !error" class="card">
      <div class="card-body text-center py-5 text-body-secondary">
        이 스키마에는 테이블이 없습니다.
      </div>
    </div>

    <template v-else>
      <div class="d-flex justify-content-end mb-2">
        <button type="button" class="btn btn-sm btn-link p-0" @click="expandAll">모두 펼치기</button>
        <span class="text-body-secondary mx-2">·</span>
        <button type="button" class="btn btn-sm btn-link p-0" @click="expanded = new Set()">모두 접기</button>
      </div>

      <div class="vstack gap-2">
        <div v-for="table in tables" :key="table.name" class="card">
          <button
            type="button"
            class="card-header d-flex align-items-center justify-content-between bg-transparent border-0 text-start w-100"
            @click="toggle(table.name)"
          >
            <span class="fw-semibold font-mono">{{ table.name }}</span>
            <span class="text-body-secondary small">
              컬럼 {{ table.columnCount }}개
              <span class="ms-2">{{ expanded.has(table.name) ? '▲' : '▼' }}</span>
            </span>
          </button>

          <div v-if="expanded.has(table.name)" class="table-responsive border-top">
            <table class="table table-sm align-middle mb-0">
              <thead>
                <tr class="small text-body-secondary">
                  <th scope="col">컬럼</th>
                  <th scope="col">타입</th>
                  <th scope="col">NULL</th>
                  <th scope="col">토큰</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="column in table.columns" :key="column.name">
                  <td class="font-mono">{{ column.name }}</td>
                  <td class="font-mono small text-body-secondary">{{ column.type }}</td>
                  <td class="small text-body-secondary">{{ column.nullable ? '허용' : '—' }}</td>
                  <td>
                    <span
                      v-for="token in column.tokens"
                      :key="token"
                      class="badge text-bg-secondary me-1 font-mono fw-normal"
                    >
                      {{ token }}
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <p class="text-body-secondary small mt-3 mb-0">
        토큰은 컬럼명을 <code>_</code> 로 나눈 것입니다. 키워드를 이 토큰과 대조해 마스킹 대상을 정합니다.
      </p>
    </template>
  </main>
</template>
