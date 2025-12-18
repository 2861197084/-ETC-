import { AppRouteRecord } from '@/types/router'

/**
 * 交通管理员路由配置
 */
export const etcRoutes: AppRouteRecord = {
  name: 'ETC',
  path: '/etc',
  component: '/index/index',
  redirect: '/etc/monitor',
  meta: {
    title: '交通管理',
    icon: 'ri:shield-user-line',
    roles: ['admin'] // 管理员可见
  },
  children: [
    {
      path: 'monitor',
      name: 'EtcMonitor',
      component: '/etc/monitor',
      meta: {
        title: '地图大屏',
        icon: 'ri:map-2-line',
        keepAlive: true,
        fixedTab: true
      }
    },
    {
      path: 'realtime',
      name: 'EtcRealtime',
      component: '/etc/realtime',
      meta: {
        title: '实时监控',
        icon: 'ri:pulse-line',
        keepAlive: false
      }
    },
    {
      path: 'query',
      name: 'EtcQuery',
      component: '/etc/query',
      meta: {
        title: '数据查询',
        icon: 'ri:search-line',
        keepAlive: false
      }
    }
  ]
}

/**
 * 车主端路由配置
 */
export const ownerRoutes: AppRouteRecord = {
  name: 'Owner',
  path: '/owner',
  component: '/index/index',
  redirect: '/owner/map',
  meta: {
    title: '我的车辆',
    icon: 'ri:car-line',
    roles: ['user'] // 普通用户可见
  },
  children: [
    {
      path: 'map',
      name: 'OwnerMap',
      component: '/owner/map',
      meta: {
        title: '地图服务',
        icon: 'ri:map-pin-line',
        keepAlive: false
      }
    },
    {
      path: 'records',
      name: 'OwnerRecords',
      component: '/owner/records',
      meta: {
        title: '我的记录',
        icon: 'ri:file-list-3-line',
        keepAlive: false
      }
    }
  ]
}
