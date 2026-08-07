import { createRouter, createWebHistory } from 'vue-router'
import ProjectCreateView from '@/views/ProjectCreateView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'project-create',
      component: ProjectCreateView,
    },
  ],
})

export default router
