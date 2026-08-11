<script setup lang="ts">
import { inject, onMounted, ref, type Ref } from 'vue'
import { useRoute } from 'vue-router'
import { messageOf } from '@/api/http'
import { listKeywords } from '@/api/keywords'

/**
 * 비식별화 대상을 어떻게 정할지 고르는 단계입니다.
 * <p>
 * 방법을 저장하지는 않습니다. 둘 다 써도 되고, 언제든 바꿔도 됩니다.
 * 엑셀로 지정한 컬럼은 키워드 판정을 이깁니다.
 */
const route = useRoute()
const projectId = Number(route.params.id)
const reloadWorkspace = inject<() => Promise<void>>('reloadWorkspace')

const keywordCount = ref(0)
const loading = ref(true)
const error = ref('')

onMounted(async () => {
  try {
    keywordCount.value = (await listKeywords(projectId)).length
  } catch (e) {
    error.value = messageOf(e, '현재 상태를 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
  await reloadWorkspace?.()
})
</script>

<template>
  <div>
    <h2 class="h6 mb-1">3. 대상 지정</h2>
    <p class="text-body-secondary small mb-4">
      어떤 컬럼을 비식별화할지 정하는 방법을 고릅니다. 어느 쪽으로 하든 다음 단계인
      <strong>검수</strong>에서 결과를 확인하고 고칠 수 있습니다.
    </p>

    <div v-if="error" class="alert alert-danger" role="alert">{{ error }}</div>

    <div class="row g-3">
      <div class="col-md-6">
        <RouterLink
          :to="`/projects/${projectId}/target/excel`"
          class="card h-100 text-decoration-none text-body"
        >
          <div class="card-body">
            <h3 class="h6 mb-2">1. 엑셀로 정하기</h3>
            <p class="text-body-secondary small mb-3">
              이미 정해둔 <strong>컬럼 정의서</strong>가 있을 때 씁니다.
              빈 양식을 받아 테이블·컬럼을 적어 올리면 <strong>적은 줄만</strong> 그대로 반영합니다.
            </p>
            <ul class="text-body-secondary small mb-3 ps-3">
              <li>적지 않은 컬럼은 손대지 않습니다</li>
              <li>이름이 틀리면 몇 행이 왜 안 됐는지 알려줍니다</li>
            </ul>
            <span class="btn btn-sm btn-outline-primary">양식 받으러 가기 →</span>
          </div>
        </RouterLink>
      </div>

      <div class="col-md-6">
        <RouterLink
          :to="`/projects/${projectId}/keywords`"
          class="card h-100 text-decoration-none text-body"
        >
          <div class="card-body">
            <h3 class="h6 mb-2">
              2. 키워드로 찾기
              <span v-if="!loading && keywordCount > 0" class="badge text-bg-secondary ms-1">
                {{ keywordCount }}
              </span>
            </h3>
            <p class="text-body-secondary small mb-3">
              정해둔 목록이 없을 때 씁니다. 컬럼명을 <code>_</code> 로 쪼갠 토큰과 대조해
              <strong>이름이 겹치는 컬럼을 한꺼번에</strong> 찾습니다.
            </p>
            <ul class="text-body-secondary small mb-3 ps-3">
              <li>컬럼이 늘어도 규칙이 그대로 적용됩니다</li>
              <li>의도한 것보다 많이 걸릴 수 있어 검수가 필요합니다</li>
            </ul>
            <span class="btn btn-sm btn-outline-primary">키워드 설정하러 가기 →</span>
          </div>
        </RouterLink>
      </div>
    </div>

    <p class="text-body-secondary small mt-3 mb-0">
      둘 다 써도 됩니다. 같은 컬럼에 둘 다 걸리면 <strong>엑셀로 지정한 쪽이 이깁니다.</strong>
    </p>
  </div>
</template>
