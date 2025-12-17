import { AppRouteRecord } from '@/types/router'
import { etcRoutes, ownerRoutes } from './etc'

/**
 * 导出所有模块化路由
 * 彭城交通管理系统 - 只保留交通管理和车主模块
 */
export const routeModules: AppRouteRecord[] = [
  etcRoutes,    // 交通管理（管理员端）
  ownerRoutes   // 我的车辆（车主端）
]
