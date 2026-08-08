<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { deleteProject, listProjects, type ProjectView } from '@/api/projects'

const projects = ref<ProjectView[]>([])
const loading = ref(true)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    projects.value = await listProjects()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '목록을 불러오지 못했습니다.'
  } finally {
    loading.value = false
  }
}

async function remove(project: ProjectView) {
  if (!confirm(`'${project.name}' 프로젝트를 삭제합니다. 되돌릴 수 없습니다.`)) return
  try {
    await deleteProject(project.id)
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '삭제하지 못했습니다.'
  }
}

onMounted(load)
</script>

<template>
  <main class="project-list">
    <header>
      <h1>프로젝트</h1>
      <RouterLink to="/projects/new" class="button primary">새 프로젝트</RouterLink>
    </header>

    <p v-if="loading">불러오는 중…</p>
    <p v-else-if="error" class="error" role="alert">{{ error }}</p>

    <p v-else-if="projects.length === 0" class="empty">
      아직 프로젝트가 없습니다. 비식별화할 원본 DB를 등록해 시작하세요.
    </p>

    <table v-else>
      <thead>
        <tr>
          <th>이름</th>
          <th>원본</th>
          <th>이관 대상</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="project in projects" :key="project.id">
          <td>
            <RouterLink :to="`/projects/${project.id}`">{{ project.name }}</RouterLink>
          </td>
          <td class="mono">{{ project.rawConnection.schema }}</td>
          <td class="mono">
            <span v-if="project.editConnection">{{ project.editConnection.schema }}</span>
            <span v-else class="muted">미지정</span>
          </td>
          <td class="actions">
            <RouterLink :to="`/projects/${project.id}`" class="button">수정</RouterLink>
            <button type="button" class="danger" @click="remove(project)">삭제</button>
          </td>
        </tr>
      </tbody>
    </table>
  </main>
</template>

<style lang="scss" scoped>
.project-list {
  max-width: 52rem;
  margin: 0 auto;
  padding: 2rem 1rem 4rem;

  header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 1.5rem;

    h1 {
      margin: 0;
      font-size: 1.5rem;
    }
  }

  .empty {
    padding: 2.5rem 0;
    color: var(--muted);
    text-align: center;
  }

  table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.9rem;
  }

  th,
  td {
    padding: 0.6rem 0.5rem;
    border-bottom: 1px solid var(--line);
    text-align: left;
  }

  th {
    color: var(--muted);
    font-weight: 600;
  }

  .mono {
    font-family: ui-monospace, monospace;
  }

  .muted {
    color: var(--muted);
  }

  .actions {
    display: flex;
    gap: 0.4rem;
    justify-content: flex-end;
  }

  .error {
    color: var(--danger);
  }
}
</style>
