<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
</script>

<template>
  <nav class="navbar navbar-expand bg-body-tertiary border-bottom">
    <div class="container">
      <RouterLink to="/" class="navbar-brand fw-semibold">Privacy Eraser</RouterLink>
      <span class="navbar-text small">DB 개인정보 비식별화</span>
    </div>
  </nav>

  <RouterView />
</template>
