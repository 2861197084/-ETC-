/**
 * 用户角色枚举
 *
 * 定义彭城交通系统的用户角色类型
 *
 * @module enums/roleEnum
 */

/**
 * 用户角色
 */
export enum RoleEnum {
  /** 交通管理员 */
  TRAFFIC_ADMIN = 'TRAFFIC_ADMIN',
  /** 普通车主 */
  CAR_OWNER = 'CAR_OWNER'
}

/**
 * 角色名称映射
 */
export const RoleNameMap: Record<RoleEnum, string> = {
  [RoleEnum.TRAFFIC_ADMIN]: '交通管理员',
  [RoleEnum.CAR_OWNER]: '普通车主'
}
