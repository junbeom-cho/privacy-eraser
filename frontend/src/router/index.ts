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
      // 프로젝트 작업 공간. 단계 표시줄을 고정으로 두고 각 단계를 자식 라우트로 띄웁니다.
      path: '/projects/:id',
      component: () => import('@/views/ProjectWorkspaceView.vue'),
      children: [
        {
          path: '',
          name: 'project-settings',
          component: () => import('@/views/ProjectSettingsView.vue'),
        },
        {
          path: 'schema',
          name: 'project-schema',
          component: () => import('@/views/ProjectSchemaView.vue'),
        },
        {
          path: 'keywords',
          name: 'project-keywords',
          component: () => import('@/views/ProjectKeywordsView.vue'),
        },
        {
          path: 'migration',
          name: 'project-migration',
          component: () => import('@/views/ProjectMigrationView.vue'),
        },
        {
          path: 'review',
          name: 'project-review',
          component: () => import('@/views/ProjectReviewView.vue'),
        },
      ],
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
