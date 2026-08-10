<script setup lang="ts">
import { inject, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DbConnectionFields from '@/components/DbConnectionFields.vue'
import { messageOf } from '@/api/http'
import { emptyConnection, getProject, toInput, updateProject } from '@/api/projects'
import { migrationSetupScript } from '@/api/migration'

const route = useRoute()
const router = useRouter()
const projectId = Number(route.params.id)
const reloadWorkspace = inject<() => Promise<void>>('reloadWorkspace')

const name = ref('')
const rawConnection = reactive(emptyConnection())
const editConnection = reactive(emptyConnection())

const loading = ref(true)
const saving = ref(false)
const saved = ref(false)
const error = ref('')

onMounted(async () => {
  try {
    const project = await getProject(projectId)
    name.value = project.name
    Object.assign(rawConnection, toInput(project.rawConnection))
    Object.assign(editConnection, toInput(project.editConnection))
  } catch (e) {
    error.value = messageOf(e, '프로젝트를 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
})

async function submit() {
  saving.value = true
  saved.value = false
  error.value = ''
  try {
    await updateProject(projectId, name.value, rawConnection, editConnection)
    saved.value = true
    await reloadWorkspace?.()
    // 스크립트는 저장된 이관 대상 기준이라, 저장할 때마다 다시 만듭니다.
    if (script.value) {
      script.value = ''
      await loadScript()
    }
    return true
  } catch (e) {
    error.value = messageOf(e, '저장하지 못했습니다.')
    return false
  } finally {
    saving.value = false
  }
}

/** 저장하지 않고 넘어가면 입력한 접속 정보가 사라집니다. 눌렀으면 저장까지가 의도입니다. */
async function saveAndNext() {
  if (await submit()) {
    router.push(`/projects/${projectId}/schema`)
  }
}

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
</script>

<template>
  <div>
    <h2 class="h6 mb-3">1. 설정</h2>

    <div v-if="saved" class="alert alert-success alert-dismissible" role="status">
      저장했습니다.
      <button type="button" class="btn-close" aria-label="닫기" @click="saved = false"></button>
    </div>
    <div v-if="error" class="alert alert-danger" role="alert">{{ error }}</div>

    <div v-if="loading" class="text-body-secondary small">불러오는 중</div>

    <form v-else class="vstack gap-3" @submit.prevent="saveAndNext">
      <div>
        <label class="form-label small">프로젝트 이름</label>
        <input v-model="name" class="form-control" required />
      </div>

      <DbConnectionFields
        v-model="rawConnection"
        title="원본 (raw_schema)"
        hint="비식별화 대상 원본입니다. 읽기만 합니다."
        schema-placeholder="RAW_SCHEMA"
        password-hint="비워두면 기존 비밀번호를 그대로 씁니다."
      />

      <DbConnectionFields
        v-model="editConnection"
        title="이관 대상 (edit_schema)"
        hint="비식별화 결과가 저장됩니다. 계정이 아직 없어도 됩니다 — 아래에서 만드는 SQL을 알려줍니다."
        schema-placeholder="EDIT_SCHEMA"
        password-hint="비워두면 기존 비밀번호를 그대로 씁니다."
      />

      <!--
        이관 대상 스키마는 도구가 만들지 않습니다. 그러려면 CREATE USER 권한이 필요한데,
        그 권한을 가진 계정을 앱에 저장하면 사고가 났을 때 피해가 DB 전체로 번집니다.
      -->
      <details class="card" @toggle="loadScript">
        <summary class="card-header small">
          이관 대상 스키마가 아직 없나요? — 만드는 SQL 보기
        </summary>
        <div class="card-body">
          <p class="text-body-secondary small">
            이 도구는 스키마를 <strong>만들지 않습니다.</strong> 아래 SQL을 DBA에게 전달하세요.
            쿼터나 SELECT 권한이 빠지면 이관 도중에야 실패합니다.
            <strong>저장된 이관 대상</strong> 기준으로 만들어집니다.
          </p>

          <div v-if="scriptLoading" class="text-body-secondary small">
            <span class="spinner-border spinner-border-sm me-1" aria-hidden="true"></span>
            원본 테이블 목록을 읽는 중
          </div>
          <div v-else-if="scriptError" class="alert alert-danger small mb-0">{{ scriptError }}</div>
          <div v-else-if="!script" class="text-body-secondary small">
            이관 대상 스키마를 입력하고 저장하세요.
          </div>
          <template v-else>
            <button type="button" class="btn btn-sm btn-outline-secondary mb-2" @click="copyScript">
              {{ copied ? '복사했습니다' : '복사' }}
            </button>
            <pre class="bg-body-tertiary border rounded p-2 small mb-0" style="max-height: 20rem; overflow: auto"><code>{{ script }}</code></pre>
          </template>
        </div>
      </details>

      <!-- 저장만 하는 버튼은 두지 않습니다. 저장 없이 넘어갈 이유가 없어 한 동작으로 합칩니다. -->
      <div class="d-flex justify-content-end">
        <button type="submit" class="btn btn-primary" :disabled="saving">
          <span v-if="saving" class="spinner-border spinner-border-sm me-1" aria-hidden="true"></span>
          {{ saving ? '저장 중' : '저장하고 다음 단계 · 스키마 →' }}
        </button>
      </div>
    </form>
  </div>
</template>
