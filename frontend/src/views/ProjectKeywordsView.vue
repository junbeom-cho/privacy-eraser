<script setup lang="ts">
import { computed, inject, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import ConfirmModal from '@/components/ConfirmModal.vue'
import { messageOf } from '@/api/http'
import {
  createKeyword,
  deleteAllKeywords,
  deleteKeyword,
  describePolicy,
  emptyKeyword,
  listKeywords,
  updateKeyword,
  type KeywordInput,
  type KeywordView,
} from '@/api/keywords'

const route = useRoute()
const projectId = Number(route.params.id)
const reloadWorkspace = inject<() => Promise<void>>('reloadWorkspace')

const keywords = ref<KeywordView[]>([])
const loading = ref(true)
const error = ref('')
const notice = ref('')

// 등록과 수정이 같은 폼을 씁니다. editingId 가 있으면 수정입니다.
const form = reactive<KeywordInput>(emptyKeyword())
const editingId = ref<number | null>(null)
const saving = ref(false)

const target = ref<KeywordView | null>(null)
const deleting = ref(false)

const doKeywords = computed(() => keywords.value.filter((k) => k.type === 'DO'))
const undoKeywords = computed(() => keywords.value.filter((k) => k.type === 'UNDO'))

async function load() {
  loading.value = true
  error.value = ''
  try {
    keywords.value = await listKeywords(projectId)
  } catch (e) {
    error.value = messageOf(e, '키워드를 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
}

function resetForm() {
  Object.assign(form, emptyKeyword())
  editingId.value = null
}

function startEdit(keyword: KeywordView) {
  editingId.value = keyword.id
  Object.assign(form, {
    word: keyword.word,
    type: keyword.type,
    direction: keyword.direction ?? 'FROM_END',
    length: keyword.length ?? 4,
  })
}

async function submit() {
  saving.value = true
  error.value = ''
  notice.value = ''
  try {
    if (editingId.value === null) {
      await createKeyword(projectId, { ...form })
      notice.value = `'${form.word}' 키워드를 추가했습니다.`
    } else {
      await updateKeyword(projectId, editingId.value, { ...form })
      notice.value = `'${form.word}' 키워드를 저장했습니다.`
    }
    resetForm()
    keywords.value = await listKeywords(projectId)
    await reloadWorkspace?.()
  } catch (e) {
    error.value = messageOf(e, '저장하지 못했습니다.')
  } finally {
    saving.value = false
  }
}

// 전체 삭제는 되돌릴 수 없어 목록과 별개로 확인을 받습니다.
const confirmingDeleteAll = ref(false)
const deletingAll = ref(false)

async function confirmDeleteAll() {
  deletingAll.value = true
  error.value = ''
  try {
    const count = keywords.value.length
    await deleteAllKeywords(projectId)
    notice.value = `키워드 ${count}개를 모두 삭제했습니다.`
    resetForm()
    keywords.value = await listKeywords(projectId)
    await reloadWorkspace?.()
  } catch (e) {
    error.value = messageOf(e, '삭제하지 못했습니다.')
  } finally {
    deletingAll.value = false
    confirmingDeleteAll.value = false
  }
}

async function confirmDelete() {
  if (!target.value) return
  deleting.value = true
  try {
    await deleteKeyword(projectId, target.value.id)
    notice.value = `'${target.value.word}' 키워드를 삭제했습니다.`
    if (editingId.value === target.value.id) resetForm()
    target.value = null
    keywords.value = await listKeywords(projectId)
    await reloadWorkspace?.()
  } catch (e) {
    error.value = messageOf(e, '삭제하지 못했습니다.')
    target.value = null
  } finally {
    deleting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <h2 class="h6 mb-3">3. 키워드 · 정책</h2>

    <div class="alert alert-secondary small">
      컬럼명을 <code>_</code> 로 나눈 토큰이 키워드와 맞으면 마스킹 대상이 됩니다. 대소문자는 구분하지 않습니다.
      <strong>Undo 가 우선입니다</strong> — Do 와 Undo 에 함께 걸리면 마스킹에서 제외합니다.
    </div>

    <div v-if="notice" class="alert alert-success alert-dismissible" role="status">
      {{ notice }}
      <button type="button" class="btn-close" aria-label="닫기" @click="notice = ''"></button>
    </div>
    <div v-if="error" class="alert alert-danger" role="alert">{{ error }}</div>

    <!-- 등록 · 수정 -->
    <div class="card mb-4">
      <div class="card-header fw-semibold">
        {{ editingId === null ? '키워드 추가' : '키워드 수정' }}
      </div>
      <div class="card-body">
        <!-- align-items-end 로 입력칸 아랫선을 맞춥니다. 한 열에만 도움말을 달면 그 열만 키가 커져 줄이 어긋납니다. -->
        <form class="row g-3 align-items-end" @submit.prevent="submit">
          <div class="col-md-3">
            <label class="form-label small">키워드</label>
            <input v-model="form.word" class="form-control font-mono" required placeholder="phone" />
          </div>

          <div class="col-md-3">
            <label class="form-label small">종류</label>
            <select v-model="form.type" class="form-select">
              <option value="DO">Do · 마스킹 대상</option>
              <option value="UNDO">Undo · 제외</option>
            </select>
          </div>

          <!-- Undo 는 제외가 전부라 정책이 없습니다. -->
          <template v-if="form.type === 'DO'">
            <div class="col-md-2">
              <label class="form-label small">마스킹 방향</label>
              <select v-model="form.direction" class="form-select">
                <option value="FROM_END">뒤에서부터</option>
                <option value="FROM_START">앞에서부터</option>
              </select>
            </div>
            <div class="col-md-2">
              <label class="form-label small">개수</label>
              <input v-model.number="form.length" type="number" min="1" class="form-control" required />
            </div>
          </template>

          <!-- ms-auto 로 항상 오른쪽 끝입니다. Do/Undo 에 따라 앞 칸 수가 달라져도 자리가 흔들리지 않습니다. -->
          <div class="col-md-auto ms-auto d-flex gap-2">
            <button v-if="editingId !== null" type="button" class="btn btn-outline-secondary" @click="resetForm">
              취소
            </button>
            <button type="submit" class="btn btn-primary" :disabled="saving">
              <span v-if="saving" class="spinner-border spinner-border-sm me-1" aria-hidden="true"></span>
              {{ editingId === null ? '추가' : '저장' }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <div v-if="loading" class="text-body-secondary small">
      <span class="spinner-border spinner-border-sm me-1" aria-hidden="true"></span>
      불러오는 중
    </div>

    <div v-else-if="keywords.length === 0" class="card">
      <div class="card-body text-center py-5 text-body-secondary">
        아직 키워드가 없습니다. 위에서 추가하세요.
      </div>
    </div>

    <template v-else>
      <div v-for="group in [
        { title: 'Do · 마스킹 대상', items: doKeywords, badge: 'text-bg-primary' },
        { title: 'Undo · 제외 (우선)', items: undoKeywords, badge: 'text-bg-secondary' },
      ]" :key="group.title" class="card mb-3">
        <div class="card-header d-flex justify-content-between align-items-center">
          <span class="fw-semibold">{{ group.title }}</span>
          <span class="badge" :class="group.badge">{{ group.items.length }}</span>
        </div>

        <div v-if="group.items.length === 0" class="card-body text-body-secondary small py-3">
          없습니다.
        </div>

        <div v-else class="table-responsive">
          <table class="table table-hover align-middle mb-0">
            <thead>
              <tr class="small text-body-secondary">
                <th scope="col">키워드</th>
                <th scope="col">정책</th>
                <th scope="col" class="text-end">작업</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="keyword in group.items" :key="keyword.id">
                <td class="font-mono">{{ keyword.word }}</td>
                <td class="small">{{ describePolicy(keyword) }}</td>
                <td class="text-end">
                  <button type="button" class="btn btn-sm btn-outline-secondary me-1" @click="startEdit(keyword)">
                    수정
                  </button>
                  <button type="button" class="btn btn-sm btn-outline-danger" @click="target = keyword">
                    삭제
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>

    <div v-if="keywords.length > 0" class="d-flex align-items-center mt-3">
      <!-- 되돌릴 수 없는 동작이라 다음 단계 버튼과 떨어뜨려 둡니다. -->
      <button type="button" class="btn btn-outline-danger btn-sm" @click="confirmingDeleteAll = true">
        전체 삭제
      </button>
      <RouterLink :to="`/projects/${projectId}/review`" class="btn btn-outline-primary ms-auto">
        다음 단계 · 검수 →
      </RouterLink>
    </div>

    <ConfirmModal
      v-if="confirmingDeleteAll"
      title="키워드 전체 삭제"
      confirm-label="전체 삭제"
      :busy="deletingAll"
      @confirm="confirmDeleteAll"
      @cancel="confirmingDeleteAll = false"
    >
      <p class="mb-2">
        키워드 <strong>{{ keywords.length }}개</strong>를 모두 삭제합니다. 되돌릴 수 없습니다.
      </p>
      <p class="text-body-secondary small mb-0">
        검수 판정은 전부 미매칭이 됩니다. 다만 검수 화면에서 <strong>직접 지정한 컬럼은
        그대로 남습니다.</strong>
      </p>
    </ConfirmModal>

    <ConfirmModal
      v-if="target"
      title="키워드 삭제"
      confirm-label="삭제"
      :busy="deleting"
      @confirm="confirmDelete"
      @cancel="target = null"
    >
      <p class="mb-0">
        <strong class="font-mono">{{ target.word }}</strong> 키워드를 삭제합니다.
      </p>
    </ConfirmModal>
  </div>
</template>
