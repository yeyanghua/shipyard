import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';

/**
 * 8 个核心页面 — 对应 docs/superpowers/wireframes/ 8 个 anchor.
 *
 * <p>M3 阶段: 路由占位,只显示页面名 + "M{N} 接入" 提示.
 * M4 之后按 milestone 填实际 UI.
 */
export const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'dashboard',
    component: () => import('@/views/Dashboard.vue'),
    meta: { title: '总览', milestone: 'M3 stub' },
  },
  {
    path: '/projects',
    name: 'project-list',
    component: () => import('@/views/ProjectList.vue'),
    meta: { title: '项目列表', milestone: 'M4' },
  },
  {
    path: '/projects/new',
    name: 'create-project',
    component: () => import('@/views/CreateProject.vue'),
    meta: { title: '创建项目', milestone: 'M5' },
  },
  {
    path: '/projects/:id',
    name: 'project-detail',
    component: () => import('@/views/ProjectDetail.vue'),
    meta: { title: '项目详情', milestone: 'M5' },
  },
  {
    path: '/projects/:id/pipeline',
    name: 'pipeline-edit',
    component: () => import('@/views/PipelineEdit.vue'),
    meta: { title: '流水线编辑', milestone: 'M6 3' },
  },
  {
    path: '/projects/:id/deployments',
    name: 'deployments',
    component: () => import('@/views/Deployments.vue'),
    meta: { title: '部署记录', milestone: 'M9' },
  },
  {
    path: '/builds/:id',
    name: 'build-detail',
    component: () => import('@/views/BuildDetail.vue'),
    meta: { title: '构建详情', milestone: 'M5' },
  },
  {
    path: '/deploys/:id',
    name: 'deploy-detail',
    component: () => import('@/views/DeployDetail.vue'),
    meta: { title: '发布详情', milestone: 'M9' },
  },
  {
    path: '/envs',
    name: 'env-list',
    component: () => import('@/views/EnvList.vue'),
    meta: { title: '环境列表', milestone: 'M4' },
  },
  {
    path: '/envs/:id/variables',
    name: 'env-vars',
    component: () => import('@/views/EnvVars.vue'),
    meta: { title: '环境变量', milestone: 'M7' },
  },
  {
    path: '/ai/diagnosis',
    name: 'ai-diagnosis',
    component: () => import('@/views/AiDiagnosis.vue'),
    meta: { title: 'AI 诊断', milestone: 'M12' },
  },
  {
    path: '/monitoring',
    name: 'monitoring',
    component: () => import('@/views/Monitoring.vue'),
    meta: { title: '监控', milestone: 'M13' },
  },
  {
    path: '/activity',
    name: 'activity',
    component: () => import('@/views/Activity.vue'),
    meta: { title: '活动日志', milestone: 'M13' },
  },
  {
    path: '/notifications',
    name: 'notifications',
    component: () => import('@/views/Notifications.vue'),
    meta: { title: '通知', milestone: 'M13' },
  },
  {
    path: '/workers',
    name: 'workers',
    component: () => import('@/views/Workers.vue'),
    meta: { title: 'Worker 管理', milestone: 'M13' },
  },
  {
    // 404 兜底
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFound.vue'),
    meta: { title: '404' },
  },
];

export const router = createRouter({
  history: createWebHistory(),
  routes,
});

// 标题同步
router.afterEach((to) => {
  const title = (to.meta?.title as string | undefined) ?? 'shipyard';
  document.title = `${title} · shipyard`;
});
