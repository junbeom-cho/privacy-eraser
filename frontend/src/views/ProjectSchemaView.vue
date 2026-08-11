<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { KEY_BADGE, listTables, type TableView } from '@/api/projects'

const route = useRoute()
const id = Number(route.params.id)

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
  <div>
    <div class="d-flex align-items-center justify-content-between mb-1">
      <h2 class="h6 mb-0">2. 스키마</h2>
      <button type="button" class="btn btn-sm btn-outline-secondary" :disabled="loading" @click="load">
        다시 읽기
      </button>
    </div>
    <p class="text-body-secondary small mb-4">
      원본에서 실시간으로 읽습니다. 저장하지 않습니다.
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
                  <th scope="col">키</th>
                  <th scope="col">타입</th>
                  <th scope="col">NULL</th>
                  <th scope="col">토큰</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="column in table.columns" :key="column.name">
                  <td class="font-mono">{{ column.name }}</td>
                  <td>
                    <span
                      v-for="key in column.keys"
                      :key="key"
                      class="badge me-1 fw-normal"
                      :class="KEY_BADGE[key].className"
                      :title="KEY_BADGE[key].title"
                    >{{ KEY_BADGE[key].label }}</span>
                  </td>
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

      <div class="d-flex align-items-center mt-3">
        <p class="text-body-secondary small mb-0">
          토큰은 컬럼명을 <code>_</code> 로 나눈 것입니다. 키워드를 이 토큰과 대조해 마스킹 대상을 정합니다.
          <strong>PK·UQ</strong> 컬럼은 값이 겹치면 안 되므로 마스킹할 수 없습니다.
        </p>
        <RouterLink :to="`/projects/${id}/target`" class="btn btn-outline-primary ms-auto text-nowrap">
          다음 단계 · 대상 지정 →
        </RouterLink>
      </div>
    </template>
  </div>
</template>
