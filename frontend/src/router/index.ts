import { createRouter, createWebHistory } from 'vue-router'
import ProjectListView from '@/views/ProjectListView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'project-list',
      component: ProjectListView,
    },
    {
      path: '/projects/new',
      name: 'project-create',
      component: () => import('@/views/ProjectCreateView.vue'),
    },
    {
      path: '/projects/:id',
      name: 'project-edit',
      component: () => import('@/views/ProjectEditView.vue'),
    },
    {
      path: '/projects/:id/schema',
      name: 'project-schema',
      component: () => import('@/views/ProjectSchemaView.vue'),
    },
    {
      // 서버가 모르는 경로를 index.html 로 돌려보내므로, 앱 안에서 404 를 처리해야 합니다.
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
    },
  ],
})

export default router
