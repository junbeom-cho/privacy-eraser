<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import ConfirmModal from '@/components/ConfirmModal.vue'
import { messageOf } from '@/api/http'
import {
  latestMigration,
  migrationSetupScript,
  startMigration,
  STATUS_LABEL,
  type ColumnMaskingStatView,
  type MigrationRunView,
} from '@/api/migration'

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

/** 0건은 통계로 남기지 않으므로 여기서 0으로 나눌 일은 없지만, 방어해둡니다. */
const ratio = (stat: ColumnMaskingStatView) =>
  stat.totalRows === 0 ? 0 : Math.round((stat.fullyMaskedRows / stat.totalRows) * 100)

// 이관 대상 스키마는 도구가 만들지 않습니다. CREATE USER 권한을 앱에 두지 않기 위해서입니다.
const script = ref('')
const scriptLoading = ref(false)
const scriptError = ref('')
const copied = ref(false)

async function loadScript() {
  if (script.value || scriptLoading.value) return
  scriptLoading.value = true
  scriptError.value = ''
  try {
    script.value = (await migrationSetupScript(projectId)).script
  } catch (e) {
    scriptError.value = messageOf(e, '스크립트를 만들지 못했습니다.')
  } finally {
    scriptLoading.value = false
  }
}

async function copyScript() {
  await navigator.clipboard.writeText(script.value)
  copied.value = true
  window.setTimeout(() => (copied.value = false), 1500)
}

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

      <!--
        검수 화면의 경고는 표본 1행 기준이라, 표본이 긴 값이면 경고가 뜨지 않습니다.
        실제로 몇 건이 통째로 가려졌는지는 옮기고 나서 전수를 세야 알 수 있습니다.
      -->
      <div v-if="run?.status === 'SUCCEEDED'" class="card mb-3">
        <div class="card-header fw-semibold">이관 후 통계</div>

        <div v-if="run.stats.length === 0" class="card-body text-body-secondary small">
          통째로 가려진 행이 없습니다. 모든 마스킹 값에 원본 일부가 남아 있습니다.
        </div>

        <template v-else>
          <div class="card-body pb-0 small text-body-secondary">
            정책 길이가 값 길이보다 길어 <strong>값 전체가 <code>*</code> 로만 남은</strong> 행입니다.
            식별은 막았지만 그 컬럼으로는 아무것도 구분할 수 없게 됩니다.
          </div>
          <div class="table-responsive">
            <table class="table table-sm align-middle mb-0 small">
              <thead>
                <tr class="text-body-secondary">
                  <th scope="col">컬럼</th>
                  <th scope="col" class="text-end">전체 마스킹</th>
                  <th scope="col" class="text-end">전체</th>
                  <th scope="col" style="width: 8rem">비율</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="stat in run.stats" :key="`${stat.tableName}.${stat.columnName}`">
                  <td class="font-mono">{{ stat.tableName }}.{{ stat.columnName }}</td>
                  <td class="text-end font-mono text-warning">{{ stat.fullyMaskedRows.toLocaleString() }}</td>
                  <td class="text-end font-mono text-body-secondary">{{ stat.totalRows.toLocaleString() }}</td>
                  <td>
                    <div class="progress" style="height: 0.4rem" role="progressbar">
                      <div class="progress-bar bg-warning" :style="{ width: `${ratio(stat)}%` }"></div>
                    </div>
                    <span class="text-body-secondary font-mono">{{ ratio(stat) }}%</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>
      </div>

      <!-- 쿼터나 SELECT 권한이 빠지면 이관 도중에야 실패합니다. 그때는 이미 시간을 다 쓴 뒤입니다. -->
      <details class="card mb-3" @toggle="loadScript">
        <summary class="card-header small">
          이관 대상 스키마가 아직 없나요? — 만드는 SQL 보기
        </summary>
        <div class="card-body">
          <p class="text-body-secondary small">
            이 도구는 스키마를 <strong>만들지 않습니다.</strong> 그러려면 <code>CREATE USER</code> 권한이
            필요한데, 그 권한을 가진 계정을 앱에 저장하면 사고가 났을 때 피해가 DB 전체로 번집니다.
            아래 SQL을 DBA에게 전달하세요.
          </p>

          <div v-if="scriptLoading" class="text-body-secondary small">
            <span class="spinner-border spinner-border-sm me-1" aria-hidden="true"></span>
            원본 테이블 목록을 읽는 중
          </div>
          <div v-else-if="scriptError" class="alert alert-danger small mb-0">{{ scriptError }}</div>
          <div v-else-if="!script" class="text-body-secondary small">
            이관 대상 접속 정보를 먼저 등록하세요.
          </div>
          <template v-else>
            <button type="button" class="btn btn-sm btn-outline-secondary mb-2" @click="copyScript">
              {{ copied ? '복사했습니다' : '복사' }}
            </button>
            <pre class="bg-body-tertiary border rounded p-2 small mb-0" style="max-height: 20rem; overflow: auto"><code>{{ script }}</code></pre>
          </template>
        </div>
      </details>

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
