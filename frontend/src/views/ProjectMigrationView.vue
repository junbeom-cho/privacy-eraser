<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import ConfirmModal from '@/components/ConfirmModal.vue'
import { messageOf } from '@/api/http'
import { latestMigration, startMigration, STATUS_LABEL, type MigrationRunView } from '@/api/migration'

const route = useRoute()
const projectId = Number(route.params.id)

const run = ref<MigrationRunView | null>(null)
const loading = ref(true)
const starting = ref(false)
const confirming = ref(false)
const error = ref('')

let timer: number | undefined

const percent = computed(() => {
  if (!run.value || run.value.totalTables === 0) return 0
  return Math.round((run.value.completedTables / run.value.totalTables) * 100)
})

async function load() {
  try {
    run.value = await latestMigration(projectId)
  } catch (e) {
    error.value = messageOf(e, '상태를 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
  // 진행 중일 때만 계속 확인합니다. 끝났으면 폴링을 멈춥니다.
  if (run.value?.status === 'RUNNING') {
    timer = window.setTimeout(load, 1000)
  }
}

async function start() {
  starting.value = true
  error.value = ''
  confirming.value = false
  try {
    await startMigration(projectId)
    await load()
  } catch (e) {
    error.value = messageOf(e, '이관을 시작하지 못했습니다.')
  } finally {
    starting.value = false
  }
}

onMounted(load)
onUnmounted(() => window.clearTimeout(timer))
</script>

<template>
  <div>
    <h2 class="h6 mb-3">5. 이관</h2>

    <div class="alert alert-secondary small">
      검수에서 확정한 대로 원본을 이관 대상으로 옮깁니다. 원본은 읽기만 합니다.
      <strong>다시 실행하면 같은 이름의 테이블을 지우고 새로 만듭니다.</strong>
    </div>

    <div v-if="error" class="alert alert-danger" role="alert">{{ error }}</div>

    <div v-if="loading" class="text-body-secondary small">불러오는 중</div>

    <template v-else>
      <div v-if="run" class="card mb-3">
        <div class="card-body">
          <div class="d-flex align-items-center justify-content-between mb-2">
            <span class="badge" :class="{
              'text-bg-primary': run.status === 'RUNNING',
              'text-bg-success': run.status === 'SUCCEEDED',
              'text-bg-danger': run.status === 'FAILED',
            }">{{ STATUS_LABEL[run.status] }}</span>
            <span class="small text-body-secondary font-mono">
              {{ run.completedTables }} / {{ run.totalTables }} 테이블
            </span>
          </div>

          <div class="progress" style="height: 0.5rem" role="progressbar">
            <div
              class="progress-bar"
              :class="{
                'progress-bar-striped progress-bar-animated': run.status === 'RUNNING',
                'bg-success': run.status === 'SUCCEEDED',
                'bg-danger': run.status === 'FAILED',
              }"
              :style="{ width: `${percent}%` }"
            ></div>
          </div>

          <p v-if="run.currentTable" class="small text-body-secondary mt-2 mb-0 font-mono">
            {{ run.currentTable }} 처리 중…
          </p>
          <p v-if="run.message" class="small text-danger mt-2 mb-0">{{ run.message }}</p>
        </div>
      </div>

      <div v-else class="card mb-3">
        <div class="card-body text-body-secondary small">아직 이관한 적이 없습니다.</div>
      </div>

      <button
        type="button"
        class="btn btn-primary"
        :disabled="starting || run?.status === 'RUNNING'"
        @click="confirming = true"
      >
        <span v-if="starting" class="spinner-border spinner-border-sm me-1" aria-hidden="true"></span>
        {{ run ? '다시 이관' : '이관 시작' }}
      </button>
    </template>

    <ConfirmModal
      v-if="confirming"
      title="이관 실행"
      confirm-label="실행"
      :busy="starting"
      @confirm="start"
      @cancel="confirming = false"
    >
      <p class="mb-0">
        이관 대상 스키마에서 <strong>원본과 같은 이름의 테이블을 지우고</strong> 새로 만듭니다.
        되돌릴 수 없습니다.
      </p>
    </ConfirmModal>
  </div>
</template>
