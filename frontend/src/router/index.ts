import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'Portal',
    component: () => import('@/views/portal/index.vue'),
    meta: { title: 'ETC 大数据平台' }
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/views/dashboard/index.vue'),
    meta: { title: '数据大屏' }
  },
  {
    path: '/query',
    name: 'Query',
    component: () => import('@/views/query/index.vue'),
    meta: { title: '交互式查询' }
  },
  {
    path: '/predict',
    name: 'Predict',
    component: () => import('@/views/predict/index.vue'),
    meta: { title: '离线预测' }
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 路由守卫 - 设置页面标题
router.beforeEach((to, _from, next) => {
  document.title = (to.meta.title as string) || 'ETC 大数据平台'
  next()
})

export default router

