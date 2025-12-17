/**
 * 权限码枚举
 *
 * 定义彭城交通系统的权限码
 *
 * @module enums/permissionEnum
 */

/**
 * 权限码
 */
export enum PermissionEnum {
  // ==================== 管理员权限 ====================
  /** 管理员地图大屏 */
  ADMIN_MAP = 'admin:map',
  /** 实时数据查看 */
  ADMIN_REALTIME = 'admin:realtime',
  /** 离线数据查询 */
  ADMIN_QUERY = 'admin:query',
  /** Text2SQL 功能 */
  ADMIN_TEXT2SQL = 'admin:text2sql',
  /** 违禁信息管理 */
  ADMIN_VIOLATION = 'admin:violation',
  /** 站口压力预警 */
  ADMIN_PRESSURE = 'admin:pressure',

  // ==================== 车主权限 ====================
  /** 车主地图服务 */
  OWNER_MAP = 'owner:map',
  /** 通行记录查看 */
  OWNER_RECORDS = 'owner:records',
  /** 通行账单查看 */
  OWNER_BILLS = 'owner:bills',
  /** 异常提醒查看 */
  OWNER_ALERTS = 'owner:alerts',
  /** 轨迹回放 */
  OWNER_TRAJECTORY = 'owner:trajectory',
  /** 车主查询 */
  OWNER_QUERY = 'owner:query'
}
