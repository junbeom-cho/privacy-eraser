<script setup lang="ts">
import { computed, inject, onMounted, ref, type Ref } from 'vue'
import { useRoute } from 'vue-router'
import { messageOf } from '@/api/http'
import { KEY_BADGE, type ProjectView } from '@/api/projects'
import {
  DIRECTION_LABEL,
  MASKING_TYPE_LABEL,
  type MaskingDirection,
  type MaskingType,
} from '@/api/keywords'
import ConfirmModal from '@/components/ConfirmModal.vue'
import {
  clearAllOverrides,
  clearOverride,
  listReview,
  overrideColumn,
  SOURCE_LABEL,
  type ColumnReviewView,
} from '@/api/review'

const route = useRoute()
const projectId = Number(route.params.id)
const project = inject<Ref<ProjectView | null>>('workspaceProject')

const rows = ref<ColumnReviewView[]>([])
const loading = ref(true)
const error = ref('')
const savingKey = ref<string | null>(null)
const onlyMasked = ref(false)
const confirmingRevertAll = ref(false)
const revertingAll = ref(false)

const maskedCount = computed(() => rows.value.filter((r) => r.masked).length)
const overriddenCount = computed(() => rows.value.filter((r) => r.source === 'USER').length)
const warningCount = computed(() => rows.value.filter((r) => r.policyExceedsLength).length)
/** PK·UNIQUE 를 마스킹하면 값이 겹쳐 이관 자체가 시작되지 않습니다. */
const conflicts = computed(() => rows.value.filter((r) => r.uniqueConflict))

/** 테이블 단위로 묶어서 보여줍니다. */
const tables = computed(() => {
  const visible = onlyMasked.value ? rows.value.filter((r) => r.masked) : rows.value
  const byTable = new Map<string, ColumnReviewView[]>()
  for (const row of visible) {
    if (!byTable.has(row.tableName)) byTable.set(row.tableName, [])
    byTable.get(row.tableName)!.push(row)
  }
  return [...byTable.entries()].map(([name, columns]) => ({ name, columns }))
})

const keyOf = (row: ColumnReviewView) => `${row.tableName}.${row.columnName}`

async function load() {
  loading.value = true
  error.value = ''
  try {
    rows.value = await listReview(projectId)
  } catch (e) {
    error.value = messageOf(e, '검수 목록을 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
}

/**
 * 서버가 돌려준 줄로 해당 행만 바꿉니다. 전체를 다시 부르면 원본 스키마와 표본을
 * 테이블 수만큼 다시 읽게 되고, 화면도 깜빡이며 스크롤이 튑니다.
 */
function replaceRow(updated: ColumnReviewView) {
  const index = rows.value.findIndex((r) => keyOf(r) === keyOf(updated))
  if (index >= 0) rows.value[index] = updated
}

async function apply(row: ColumnReviewView, change: Partial<ColumnReviewView>) {
  const next = { ...row, ...change }
  savingKey.value = keyOf(row)
  error.value = ''
  try {
    replaceRow(
      await overrideColumn(projectId, row.tableName, row.columnName, {
        masked: next.masked,
        maskingType: next.masked ? (next.maskingType ?? 'PARTIAL') : null,
        // 방식마다 쓰는 칸이 다릅니다. 안 쓰는 칸을 보내면 서버가 거부합니다.
        fixedValue: next.masked && next.maskingType === 'FIXED' ? (next.fixedValue ?? '') : null,
        direction:
          next.masked && (next.maskingType ?? 'PARTIAL') === 'PARTIAL'
            ? (next.direction ?? 'FROM_END')
            : null,
        length:
          next.masked && (next.maskingType ?? 'PARTIAL') === 'PARTIAL' ? (next.length ?? 4) : null,
      }),
    )
  } catch (e) {
    error.value = messageOf(e, '저장하지 못했습니다.')
    // 실패하면 화면 값이 서버와 어긋나므로 그때만 다시 읽습니다.
    await load()
  } finally {
    savingKey.value = null
  }
}

async function revert(row: ColumnReviewView) {
  savingKey.value = keyOf(row)
  error.value = ''
  try {
    replaceRow(await clearOverride(projectId, row.tableName, row.columnName))
  } catch (e) {
    error.value = messageOf(e, '되돌리지 못했습니다.')
    await load()
  } finally {
    savingKey.value = null
  }
}

async function revertAll() {
  revertingAll.value = true
  error.value = ''
  try {
    rows.value = await clearAllOverrides(projectId)
    confirmingRevertAll.value = false
  } catch (e) {
    error.value = messageOf(e, '되돌리지 못했습니다.')
    confirmingRevertAll.value = false
  } finally {
    revertingAll.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="d-flex align-items-center justify-content-between mb-1">
      <h2 class="h6 mb-0">4. 검수</h2>
      <div class="form-check form-switch small">
        <input id="onlyMasked" v-model="onlyMasked" class="form-check-input" type="checkbox" />
        <label class="form-check-label" for="onlyMasked">마스킹 대상만</label>
      </div>
    </div>
    <p class="text-body-secondary small mb-3">
      키워드 판정은 <strong>제안</strong>입니다. 컬럼마다 직접 바꿀 수 있고, 바꾼 값이 항상 우선합니다.
      표본은 원본에서 읽은 <strong>실제 값</strong>이니 화면 공유에 주의하세요.
    </p>

    <div v-if="error" class="alert alert-danger" role="alert">{{ error }}</div>

    <!-- 이관 시작할 때도 막지만, 여기서 알아야 고칠 수 있습니다. -->
    <div v-if="conflicts.length" class="alert alert-danger" role="alert">
      <p class="mb-1">
        <strong>값이 겹치면 안 되는 컬럼을 부분 마스킹했습니다.</strong>
        값이 중복되어 PK·고유키를 걸 수 없으므로 이관을 시작할 수 없습니다.
        <strong>방식을 해시로 바꾸면</strong> 값이 겹치지 않아 그대로 이관할 수 있습니다.
      </p>
      <p class="mb-0 small font-mono">
        <span v-for="row in conflicts" :key="keyOf(row)" class="me-2">{{ keyOf(row) }}</span>
      </p>
    </div>

    <div v-if="loading" class="text-body-secondary small">
      <span class="spinner-border spinner-border-sm me-1" aria-hidden="true"></span>
      원본을 읽고 판정하는 중
    </div>

    <template v-else>
      <div class="d-flex gap-3 small mb-3">
        <span>전체 <strong>{{ rows.length }}</strong></span>
        <span class="text-primary">마스킹 <strong>{{ maskedCount }}</strong></span>
        <span class="text-body-secondary">직접 지정 <strong>{{ overriddenCount }}</strong></span>
        <span v-if="warningCount" class="text-warning">길이 초과 <strong>{{ warningCount }}</strong></span>

        <button
          v-if="overriddenCount > 0"
          type="button"
          class="btn btn-sm btn-outline-secondary ms-auto py-0"
          @click="confirmingRevertAll = true"
        >
          전체 되돌리기
        </button>
      </div>

      <div v-for="table in tables" :key="table.name" class="card mb-3">
        <div class="card-header fw-semibold font-mono">{{ table.name }}</div>
        <div class="table-responsive">
          <table class="table table-hover align-middle mb-0 small">
            <thead>
              <tr class="text-body-secondary">
                <th scope="col">컬럼</th>
                <th scope="col">타입</th>
                <th scope="col" style="width: 6rem">마스킹</th>
                <th scope="col" style="width: 19rem">정책</th>
                <th scope="col">표본 → 마스킹 결과</th>
                <th scope="col">판정 근거</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in table.columns" :key="keyOf(row)" :class="{ 'opacity-50': savingKey === keyOf(row) }">
                <td class="font-mono">
                  {{ row.columnName }}
                  <span
                    v-for="key in row.keys"
                    :key="key"
                    class="badge ms-1 fw-normal"
                    :class="KEY_BADGE[key].className"
                    :title="KEY_BADGE[key].title"
                  >{{ KEY_BADGE[key].label }}</span>
                  <span
                    v-if="row.policyExceedsLength"
                    class="badge text-bg-warning ms-1 fw-normal"
                    :title="row.maskingType === 'FIXED'
                      ? '고정값이 컬럼 길이보다 깁니다. 이관이 실패합니다.'
                      : '정책이 컬럼 길이보다 깁니다. 값 전체가 가려질 수 있습니다.'"
                  >길이 초과</span>
                </td>
                <td class="font-mono text-body-secondary">{{ row.type }}</td>

                <td>
                  <div class="form-check form-switch mb-0">
                    <input
                      class="form-check-input"
                      type="checkbox"
                      :checked="row.masked"
                      :disabled="savingKey === keyOf(row)"
                      @change="apply(row, { masked: !row.masked })"
                    />
                  </div>
                </td>

                <td>
                  <div v-if="row.masked" class="d-flex gap-1">
                    <select
                      class="form-select form-select-sm"
                      style="width: 8rem"
                      :value="row.maskingType ?? 'PARTIAL'"
                      :disabled="savingKey === keyOf(row)"
                      @change="apply(row, { maskingType: ($event.target as HTMLSelectElement).value as MaskingType })"
                    >
                      <option value="PARTIAL">{{ MASKING_TYPE_LABEL.PARTIAL }}</option>
                      <option value="HASH">{{ MASKING_TYPE_LABEL.HASH }}</option>
                      <option value="FIXED">{{ MASKING_TYPE_LABEL.FIXED }}</option>
                    </select>
                    <!-- 형식이 섞인 컬럼은 위치로 못 맞춰 값 하나로 통일합니다. -->
                    <input
                      v-if="row.maskingType === 'FIXED'"
                      class="form-control form-control-sm font-mono"
                      placeholder="01011111111"
                      :value="row.fixedValue ?? ''"
                      :disabled="savingKey === keyOf(row)"
                      @change="apply(row, { fixedValue: ($event.target as HTMLInputElement).value })"
                    />
                    <!-- 해시는 방향도 자릿수도 쓰지 않습니다. -->
                    <template v-else-if="row.maskingType !== 'HASH'">
                      <select
                        class="form-select form-select-sm"
                        :value="row.direction ?? 'FROM_END'"
                        :disabled="savingKey === keyOf(row)"
                        @change="apply(row, { direction: ($event.target as HTMLSelectElement).value as MaskingDirection })"
                      >
                        <option value="FROM_END">{{ DIRECTION_LABEL.FROM_END }}</option>
                        <option value="FROM_START">{{ DIRECTION_LABEL.FROM_START }}</option>
                      </select>
                      <input
                        type="number"
                        min="1"
                        class="form-control form-control-sm"
                        style="width: 4.5rem"
                        :value="row.length ?? 4"
                        :disabled="savingKey === keyOf(row)"
                        @change="apply(row, { length: Number(($event.target as HTMLInputElement).value) })"
                      />
                    </template>
                  </div>
                  <span v-else class="text-body-secondary">—</span>
                </td>

                <!-- 정책이 값을 어떻게 바꾸는지 눈으로 확인하는 자리입니다. -->
                <td class="font-mono">
                  <template v-if="row.sample !== null">
                    <span class="text-body-secondary">{{ row.sample }}</span>
                    <template v-if="row.masked && row.maskingType === 'HASH'">
                      <span class="mx-1 text-body-secondary">→</span>
                      <!-- 솔트가 이관 시점에 정해져 미리 계산할 수 없습니다. -->
                      <span class="text-body-secondary">해시로 대체됩니다</span>
                    </template>
                    <template v-else-if="row.masked">
                      <span class="mx-1 text-body-secondary">→</span>
                      <span :class="row.sampleFullyMasked ? 'text-warning' : 'text-primary'">
                        {{ row.maskedSample }}
                      </span>
                      <span
                        v-if="row.sampleFullyMasked"
                        class="badge text-bg-warning ms-1 fw-normal"
                        title="정책이 값보다 길어 표본이 통째로 가려집니다."
                      >전체</span>
                    </template>
                  </template>
                  <span v-else class="text-body-secondary opacity-50">NULL</span>
                </td>

                <td>
                  <span
                    class="badge fw-normal"
                    :class="{
                      'text-bg-info': row.source === 'USER',
                      'text-bg-secondary': row.source === 'UNDO_KEYWORD' || row.source === 'NO_MATCH',
                      'text-bg-primary': row.source === 'DO_KEYWORD',
                    }"
                  >{{ SOURCE_LABEL[row.source] }}</span>
                  <span v-if="row.matchedKeyword" class="font-mono text-body-secondary ms-1">
                    {{ row.matchedKeyword }}
                  </span>
                  <button
                    v-if="row.source === 'USER'"
                    type="button"
                    class="btn btn-sm btn-link p-0 ms-2"
                    :disabled="savingKey === keyOf(row)"
                    @click="revert(row)"
                  >
                    되돌리기
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="d-flex align-items-center mt-3">
        <p class="text-body-secondary small mb-0">
          여기서 확정한 대로 이관합니다. 마스킹 대상 {{ maskedCount }}개 컬럼.
        </p>
        <span v-if="conflicts.length" class="btn btn-outline-danger ms-auto text-nowrap disabled" aria-disabled="true">
          충돌 {{ conflicts.length }}건을 먼저 해결하세요
        </span>
        <RouterLink
          v-else
          :to="`/projects/${projectId}/migration`"
          class="btn btn-outline-primary ms-auto text-nowrap"
        >
          다음 단계 · 이관 →
        </RouterLink>
      </div>
    </template>

    <ConfirmModal
      v-if="confirmingRevertAll"
      title="전체 되돌리기"
      confirm-label="되돌리기"
      :busy="revertingAll"
      @confirm="revertAll"
      @cancel="confirmingRevertAll = false"
    >
      <p class="mb-0">
        직접 지정한 <strong>{{ overriddenCount }}개</strong> 컬럼을 모두 키워드 판정으로 되돌립니다.
        되돌린 내용은 복구할 수 없습니다.
      </p>
    </ConfirmModal>
  </div>
</template>
