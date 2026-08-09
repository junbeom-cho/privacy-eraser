<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ConfirmModal from '@/components/ConfirmModal.vue'
import { deleteProject, listProjects, type ProjectView } from '@/api/projects'

const route = useRoute()
const router = useRouter()

const projects = ref<ProjectView[]>([])
const loading = ref(true)
const error = ref('')

// 생성·수정 화면이 돌아오면서 결과를 쿼리로 넘깁니다.
const notice = ref(typeof route.query.message === 'string' ? route.query.message : '')
function dismissNotice() {
  notice.value = ''
  router.replace({ name: 'project-list' })
}

const target = ref<ProjectView | null>(null)
const deleting = ref(false)

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

async function confirmDelete() {
  if (!target.value) return
  deleting.value = true
  try {
    await deleteProject(target.value.id)
    notice.value = `'${target.value.name}' 프로젝트를 삭제했습니다.`
    target.value = null
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '삭제하지 못했습니다.'
    target.value = null
  } finally {
    deleting.value = false
  }
}

onMounted(load)
</script>

<template>
  <main class="container py-4" style="max-width: 56rem">
    <div class="d-flex align-items-center justify-content-between mb-4">
      <h1 class="h4 mb-0">프로젝트</h1>
      <RouterLink to="/projects/new" class="btn btn-primary">새 프로젝트</RouterLink>
    </div>

    <div v-if="notice" class="alert alert-success alert-dismissible" role="status">
      {{ notice }}
      <button type="button" class="btn-close" aria-label="닫기" @click="dismissNotice"></button>
    </div>
    <div v-if="error" class="alert alert-danger" role="alert">{{ error }}</div>

    <div v-if="loading" class="text-body-secondary small">
      <span class="spinner-border spinner-border-sm me-1" aria-hidden="true"></span>
      불러오는 중
    </div>

    <div v-else-if="projects.length === 0" class="card">
      <div class="card-body text-center py-5">
        <p class="text-body-secondary mb-3">아직 프로젝트가 없습니다.</p>
        <RouterLink to="/projects/new" class="btn btn-outline-primary">
          첫 프로젝트 만들기
        </RouterLink>
      </div>
    </div>

    <div v-else class="card">
      <div class="table-responsive">
        <table class="table table-hover align-middle mb-0">
          <thead>
            <tr class="small text-body-secondary">
              <th scope="col">이름</th>
              <th scope="col">원본</th>
              <th scope="col">이관 대상</th>
              <th scope="col" class="text-end">작업</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="project in projects" :key="project.id">
              <td>
                <RouterLink :to="`/projects/${project.id}`" class="fw-medium text-decoration-none">
                  {{ project.name }}
                </RouterLink>
              </td>
              <td><span class="badge text-bg-secondary font-mono">{{ project.rawConnection.schema }}</span></td>
              <td>
                <span v-if="project.editConnection" class="badge text-bg-secondary font-mono">
                  {{ project.editConnection.schema }}
                </span>
                <span v-else class="text-body-secondary small">미지정</span>
              </td>
              <td class="text-end">
                <button type="button" class="btn btn-sm btn-outline-danger" @click="target = project">
                  삭제
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <ConfirmModal
      v-if="target"
      title="프로젝트 삭제"
      confirm-label="삭제"
      :busy="deleting"
      @confirm="confirmDelete"
      @cancel="target = null"
    >
      <p class="mb-0">
        <strong>{{ target.name }}</strong> 프로젝트를 삭제합니다. 되돌릴 수 없습니다.
      </p>
    </ConfirmModal>
  </main>
</template>
