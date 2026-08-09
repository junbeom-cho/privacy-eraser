<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

// 한글 경로는 인코딩되어 들어옵니다. 그대로 보여주면 무슨 주소인지 알 수 없습니다.
const path = computed(() => {
  try {
    return decodeURIComponent(route.fullPath)
  } catch {
    return route.fullPath
  }
})
</script>

<template>
  <main class="container py-5" style="max-width: 32rem">
    <div class="card">
      <div class="card-header fw-semibold">
        Privacy Eraser <span class="text-body-secondary fw-normal small ms-2">DB 개인정보 비식별화</span>
      </div>
      <div class="card-body">
        <h1 class="h5">페이지를 찾을 수 없습니다</h1>
        <p class="text-body-secondary small">주소가 바뀌었거나 삭제된 페이지일 수 있습니다.</p>

        <dl class="row font-mono small border rounded mx-0 mb-4 py-1">
          <dt class="col-4 text-body-secondary py-1">STATUS</dt>
          <dd class="col-8 py-1 mb-0">404 NOT_FOUND</dd>
          <dt class="col-4 text-body-secondary py-1 border-top">PATH</dt>
          <dd class="col-8 py-1 mb-0 border-top text-truncate">{{ path }}</dd>
        </dl>

        <RouterLink to="/" class="btn btn-primary">프로젝트 목록으로</RouterLink>
      </div>
    </div>
  </main>
</template>
