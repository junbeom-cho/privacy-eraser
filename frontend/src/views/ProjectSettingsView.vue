<script setup lang="ts">
import { inject, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DbConnectionFields from '@/components/DbConnectionFields.vue'
import { messageOf } from '@/api/http'
import { emptyConnection, getProject, toInput, updateProject } from '@/api/projects'
import EditSchemaScriptCard from '@/components/EditSchemaScriptCard.vue'

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

      <EditSchemaScriptCard />

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
