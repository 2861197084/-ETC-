import { AppRouteRecord } from '@/types/router'

/**
 * ETC 大数据管理平台路由配置
 */
export const etcRoutes: AppRouteRecord = {
  name: 'ETC',
  path: '/etc',
  component: '/index/index',
  redirect: '/etc/monitor',
  meta: {
    title: 'ETC大数据平台',
    icon: 'ri:road-map-line',
    roles: ['R_SUPER', 'R_ADMIN']
  },
  children: [
    {
      path: 'monitor',
      name: 'EtcMonitor',
      component: '/etc/monitor',
      meta: {
        title: '实时监控指挥舱',
        icon: 'ri:dashboard-3-line',
        keepAlive: true,
        fixedTab: true
      }
    },
    {
      path: 'query',
      name: 'EtcQuery',
      component: '/etc/query',
      meta: {
        title: '数据查询中心',
        icon: 'ri:database-2-line',
        keepAlive: false
      }
    },
    {
      path: 'prediction',
      name: 'EtcPrediction',
      component: '/etc/prediction',
      meta: {
        title: '离线预测分析',
        icon: 'ri:line-chart-line',
        keepAlive: false
      }
    }
  ]
}
