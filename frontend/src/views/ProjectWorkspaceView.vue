<script setup lang="ts">
import { computed, onMounted, provide, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { messageOf } from '@/api/http'
import { getProject, type ProjectView } from '@/api/projects'
import { listKeywords } from '@/api/keywords'

/**
 * 프로젝트 작업 공간입니다. 단계 표시줄을 고정으로 두고 각 단계 화면을 안에 띄웁니다.
 * 순서는 안내하되 가두지 않습니다 — 검수 결과를 보고 키워드로 되돌아오는 일이 잦기 때문입니다.
 */
const route = useRoute()
const projectId = Number(route.params.id)

const project = ref<ProjectView | null>(null)
const keywordCount = ref(0)
const loading = ref(true)
const error = ref('')

async function loadSummary() {
  error.value = ''
  try {
    project.value = await getProject(projectId)
    keywordCount.value = (await listKeywords(projectId)).length
  } catch (e) {
    error.value = messageOf(e, '프로젝트를 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
}

// 하위 단계에서 값이 바뀌면 표시줄을 갱신해야 합니다.
provide('reloadWorkspace', loadSummary)
// 하위 단계가 이관 가능 여부 등을 판단하려면 프로젝트가 필요합니다. ref 를 그대로 넘겨 반응성을 유지합니다.
provide('workspaceProject', project)

const steps = computed(() => [
  {
    no: 1,
    name: '설정',
    to: `/projects/${projectId}`,
    exact: true,
    badge: project.value?.rawConnection.schema ?? null,
    blockedReason: null as string | null,
  },
  {
    no: 2,
    name: '스키마',
    to: `/projects/${projectId}/schema`,
    exact: false,
    badge: null,
    blockedReason: null,
  },
  {
    no: 3,
    name: '키워드',
    to: `/projects/${projectId}/keywords`,
    exact: false,
    badge: keywordCount.value > 0 ? `${keywordCount.value}` : null,
    blockedReason: null,
  },
  {
    no: 4,
    name: '검수',
    // 키워드가 없으면 전부 비대상으로만 나와 볼 것이 없습니다.
    to: keywordCount.value > 0 ? `/projects/${projectId}/review` : '',
    exact: false,
    badge: null,
    blockedReason: '키워드를 먼저 등록하세요',
  },
  {
    no: 5,
    name: '이관',
    to: `/projects/${projectId}/migration`,
    exact: false,
    badge: null,
    blockedReason: null,
  },
])

onMounted(loadSummary)
watch(() => route.fullPath, loadSummary)
</script>

<template>
  <main class="container py-4" style="max-width: 56rem">
    <RouterLink to="/" class="link-secondary small text-decoration-none">← 프로젝트 목록</RouterLink>

    <h1 class="h4 mt-2 mb-1">{{ project?.name ?? '프로젝트' }}</h1>
    <p v-if="project" class="text-body-secondary small mb-3">
      원본 <span class="font-mono">{{ project.rawConnection.schema }}</span>
      <span class="mx-1">→</span>
      이관 대상
      <span class="font-mono">{{ project.editConnection.schema }}</span>
    </p>

    <!-- 단계 표시줄. 잠긴 단계는 이유를 함께 보여줍니다. -->
    <nav class="nav nav-pills flex-nowrap overflow-auto border-bottom pb-2 mb-4">
      <template v-for="step in steps" :key="step.no">
        <RouterLink
          v-if="step.to"
          :to="step.to"
          class="nav-link d-flex align-items-center gap-2 text-nowrap"
          :class="{ active: step.exact ? route.path === step.to : route.path.startsWith(step.to) }"
        >
          <span class="opacity-75">{{ step.no }}</span>
          {{ step.name }}
          <span v-if="step.badge" class="badge text-bg-secondary font-mono fw-normal">{{ step.badge }}</span>
        </RouterLink>

        <span
          v-else
          class="nav-link disabled d-flex align-items-center gap-2 text-nowrap"
          :title="step.blockedReason ?? ''"
        >
          <span class="opacity-75">{{ step.no }}</span>
          {{ step.name }}
          <span class="badge text-bg-light fw-normal">{{ step.blockedReason }}</span>
        </span>
      </template>
    </nav>

    <div v-if="error" class="alert alert-danger" role="alert">{{ error }}</div>

    <div v-if="loading" class="text-body-secondary small">
      <span class="spinner-border spinner-border-sm me-1" aria-hidden="true"></span>
      불러오는 중
    </div>

    <RouterView v-else />
  </main>
</template>
