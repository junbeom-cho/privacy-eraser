<script setup lang="ts">
import { inject, ref } from 'vue'
import { useRoute } from 'vue-router'
import { messageOf } from '@/api/http'
import { sheetUrl, uploadSheet, type ApplySheetResult } from '@/api/review'

/**
 * 컬럼 정의서로 비식별화 대상을 정합니다.
 * <p>
 * 키워드는 이름 규칙이라 의도한 것보다 항상 더 많이 걸립니다. 이건 <b>적은 줄만</b> 바꿉니다.
 */
const route = useRoute()
const projectId = Number(route.params.id)
const reloadWorkspace = inject<() => Promise<void>>('reloadWorkspace')

const uploading = ref(false)
const result = ref<ApplySheetResult | null>(null)
const error = ref('')

async function onPicked(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploading.value = true
  error.value = ''
  result.value = null
  try {
    result.value = await uploadSheet(projectId, file)
    await reloadWorkspace?.()
  } catch (e) {
    error.value = messageOf(e, '올리지 못했습니다.')
  } finally {
    uploading.value = false
    input.value = ''
  }
}
</script>

<template>
  <div>
    <div class="d-flex align-items-center justify-content-between mb-1">
      <h2 class="h6 mb-0">3. 대상 지정 · 엑셀</h2>
      <RouterLink :to="`/projects/${projectId}/target`" class="btn btn-sm btn-link p-0">
        ← 방법 다시 고르기
      </RouterLink>
    </div>
    <p class="text-body-secondary small mb-4">
      빈 양식을 받아 채운 뒤 올리면 <strong>적은 줄만</strong> 반영합니다.
      적지 않은 컬럼은 손대지 않습니다.
    </p>

    <div v-if="error" class="alert alert-danger" role="alert">{{ error }}</div>

    <div class="card mb-3">
      <div class="card-header fw-semibold">① 양식 받기</div>
      <div class="card-body">
        <p class="text-body-secondary small">
          머리글만 있는 빈 파일입니다. 방식마다 채우는 칸이 달라, 파일 안 <code>작성 방법</code> 을
          함께 보세요.
        </p>
        <!-- 양식 파일은 코드 밖에 있습니다. 여기서 표를 그대로 흉내 내면 파일을 고칠 때마다 어긋납니다. -->
        <ul class="text-body-secondary small mb-3 ps-3">
          <li><code>테이블명</code>, <code>컬럼명</code>, <code>마스킹</code> 은 반드시 있어야 합니다</li>
          <li>열 순서를 바꾸거나 칸을 더 넣어도 됩니다 — 머리글을 이름으로 찾습니다</li>
        </ul>
        <a :href="sheetUrl(projectId)" class="btn btn-outline-secondary">양식 내려받기</a>
      </div>
    </div>

    <div class="card mb-3">
      <div class="card-header fw-semibold">② 채워서 올리기</div>
      <div class="card-body">
        <label class="btn btn-primary mb-0">
          <span v-if="uploading" class="spinner-border spinner-border-sm me-1" aria-hidden="true"></span>
          {{ uploading ? '반영 중' : '정의서 올리기' }}
          <input type="file" accept=".xlsx" class="d-none" :disabled="uploading" @change="onPicked" />
        </label>

        <template v-if="result">
          <p class="small mt-3 mb-0" :class="result.applied > 0 ? 'text-success' : 'text-body-secondary'">
            {{ result.applied }}개 컬럼을 반영했습니다.
          </p>
          <!-- 틀린 줄을 조용히 넘기면 무엇이 빠졌는지 알 수 없습니다. -->
          <details v-if="result.errors.length" class="small mt-1">
            <summary class="text-warning">반영하지 못한 줄 {{ result.errors.length }}개</summary>
            <ul class="mb-0 mt-1 ps-3 text-body-secondary">
              <li v-for="(message, i) in result.errors.slice(0, 50)" :key="i">{{ message }}</li>
              <li v-if="result.errors.length > 50">… 외 {{ result.errors.length - 50 }}개</li>
            </ul>
          </details>
        </template>
      </div>
    </div>

    <div class="d-flex align-items-center">
      <p class="text-body-secondary small mb-0">
        여러 번 올려도 됩니다. 같은 컬럼을 다시 적으면 나중에 올린 값이 남습니다.
      </p>
      <RouterLink
        :to="`/projects/${projectId}/review`"
        class="btn btn-outline-primary ms-auto text-nowrap"
      >
        다음 단계 · 검수 →
      </RouterLink>
    </div>
  </div>
</template>
