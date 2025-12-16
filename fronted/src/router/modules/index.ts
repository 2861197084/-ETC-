import { AppRouteRecord } from '@/types/router'
import { dashboardRoutes } from './dashboard'
import { templateRoutes } from './template'
import { widgetsRoutes } from './widgets'
import { examplesRoutes } from './examples'
import { systemRoutes } from './system'
import { articleRoutes } from './article'
import { resultRoutes } from './result'
import { exceptionRoutes } from './exception'
import { safeguardRoutes } from './safeguard'
import { helpRoutes } from './help'
import { etcRoutes } from './etc'

/**
 * 导出所有模块化路由
 */
export const routeModules: AppRouteRecord[] = [
  etcRoutes, // ETC 大数据平台路由（优先显示）
  dashboardRoutes,
  templateRoutes,
  widgetsRoutes,
  examplesRoutes,
  systemRoutes,
  articleRoutes,
  resultRoutes,
  exceptionRoutes,
  safeguardRoutes,
  ...helpRoutes
]
