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
  ],
})

export default router
