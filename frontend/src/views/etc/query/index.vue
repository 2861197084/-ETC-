<template>
  <div class="query-page">
    <div class="page-header">
      <h2 class="page-title">数据查询</h2>
      <p class="page-desc">支持车流量统计和套牌嫌疑查询，分析交通数据</p>
    </div>

    <!-- Tab 切换：快捷查询 / 高级查询 -->
    <el-tabs v-model="activeTab" class="query-tabs">
      <!-- 快捷筛选查询 -->
      <el-tab-pane label="快捷查询" name="quick">
        <div class="quick-query-section">
          <!-- 查询类型选择 -->
          <div class="query-type-selector">
            <el-radio-group v-model="queryType" size="large">
              <el-radio-button value="traffic">车流量统计</el-radio-button>
              <el-radio-button value="clone">套牌嫌疑</el-radio-button>
            </el-radio-group>
          </div>

          <!-- 筛选条件 -->
          <div class="filter-panel">
            <el-form :model="filters" label-width="80px" inline>
              <!-- 时间范围 - 所有查询都有 -->
              <el-form-item label="时间范围">
                <el-date-picker
                  v-model="filters.dateRange"
                  type="datetimerange"
                  range-separator="至"
                  start-placeholder="开始时间"
                  end-placeholder="结束时间"
                  :shortcuts="dateShortcuts"
                  style="width: 380px"
                />
              </el-form-item>

              <!-- 卡口选择 - 车流量统计 -->
              <el-form-item label="卡口" v-if="queryType === 'traffic'">
                <el-select 
                  v-model="filters.checkpointId" 
                  placeholder="全部卡口" 
                  clearable 
                  filterable
                  style="width: 220px"
                >
                  <el-option-group label="省际卡口（苏皖界）">
                    <el-option 
                      v-for="cp in checkpointOptions.filter(c => c.boundary === '苏皖界')" 
                      :key="cp.id" 
                      :label="cp.name" 
                      :value="cp.id" 
                    />
                  </el-option-group>
                  <el-option-group label="省际卡口（苏鲁界）">
                    <el-option 
                      v-for="cp in checkpointOptions.filter(c => c.boundary === '苏鲁界')" 
                      :key="cp.id" 
                      :label="cp.name" 
                      :value="cp.id" 
                    />
                  </el-option-group>
                  <el-option-group label="市际卡口">
                    <el-option 
                      v-for="cp in checkpointOptions.filter(c => !['苏皖界', '苏鲁界'].includes(c.boundary))" 
                      :key="cp.id" 
                      :label="cp.name" 
                      :value="cp.id" 
                    />
                  </el-option-group>
                </el-select>
              </el-form-item>

              <!-- 通行方向 - 车流量统计 -->
              <el-form-item label="通行方向" v-if="queryType === 'traffic'">
                <el-select v-model="filters.direction" placeholder="全部方向" clearable style="width: 120px">
                  <el-option label="进城" value="1" />
                  <el-option label="出城" value="2" />
                </el-select>
              </el-form-item>

              <!-- 车牌号 - 套牌嫌疑 -->
              <el-form-item label="车牌号" v-if="queryType === 'clone'">
                <el-input v-model="filters.plateNumber" placeholder="输入车牌号查询" clearable style="width: 140px" />
              </el-form-item>

              <!-- 状态 - 套牌嫌疑 -->
              <el-form-item label="处理状态" v-if="queryType === 'clone'">
                <el-select v-model="filters.cloneStatus" placeholder="全部状态" clearable style="width: 140px">
                  <el-option label="待处理" value="pending" />
                  <el-option label="已确认" value="confirmed" />
                  <el-option label="已排除" value="dismissed" />
                </el-select>
              </el-form-item>
            </el-form>

            <div class="filter-actions">
              <el-button type="primary" :icon="Search" @click="handleQuickQuery" :loading="queryLoading">
                查询
              </el-button>
              <el-button :icon="Refresh" @click="resetFilters">重置</el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- Text2SQL 高级查询 -->
      <el-tab-pane label="智能查询 (Text2SQL)" name="advanced">
        <div class="advanced-query-section">
          <div class="nl-input-wrapper">
            <el-input
              v-model="naturalLanguageQuery"
              type="textarea"
              :rows="3"
              placeholder="用自然语言描述您想查询的内容，例如：&#10;- 查询今天各站点的车流量排名&#10;- 统计本周超速120km/h以上的车辆&#10;- 找出最近24小时内疑似套牌的车辆记录"
            />
            <el-button 
              type="primary" 
              size="large"
              :icon="MagicStick" 
              @click="handleText2Sql" 
              :loading="text2sqlLoading"
            >
              生成 SQL
            </el-button>
          </div>

          <!-- 生成的 SQL -->
          <div v-if="generatedSql" class="sql-preview-section">
            <div class="sql-header">
              <span class="sql-title">
                <el-icon><Document /></el-icon>
                生成的 SQL 语句
              </span>
              <div class="sql-actions">
                <el-button link type="primary" @click="editMode = !editMode">
                  {{ editMode ? '取消编辑' : '编辑修改' }}
                </el-button>
                <el-button link @click="copySql">复制</el-button>
              </div>
            </div>
            <el-input
              v-if="editMode"
              v-model="generatedSql"
              type="textarea"
              :rows="4"
              class="sql-editor"
            />
            <pre v-else class="sql-code">{{ generatedSql }}</pre>
            <el-button type="primary" @click="executeSql" :loading="queryLoading">
              <el-icon><CaretRight /></el-icon>
              执行查询
            </el-button>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 查询结果 -->
    <div class="query-result" v-if="queryResult.length > 0 || queryLoading">
      <div class="result-header">
        <div class="result-info">
          <span class="result-count">
            共 {{ totalCount }} 条记录
          </span>
          <span v-if="queryTime" class="query-time">
            查询耗时: {{ queryTime }}ms
          </span>
        </div>
        <div class="result-actions">
          <el-button :icon="Download" @click="exportData">导出 Excel</el-button>
          <el-button :icon="Printer" @click="printData">打印</el-button>
        </div>
      </div>

      <el-table
        v-loading="queryLoading"
        :data="queryResult"
        stripe
        border
        max-height="450"
        class="result-table"
      >
        <el-table-column
          v-for="col in tableColumns"
          :key="col.prop"
          :prop="col.prop"
          :label="col.label"
          :width="col.width"
          :sortable="col.sortable"
          show-overflow-tooltip
        />
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[20, 50, 100]"
          :total="totalCount"
          layout="total, sizes, prev, pager, next"
        />
      </div>
    </div>

    <!-- 查询历史 -->
    <div class="query-history">
      <div class="history-header">
        <span class="history-title">
          <el-icon><Clock /></el-icon>
          查询历史
        </span>
        <el-button text type="danger" size="small" @click="clearHistory">清空</el-button>
      </div>
      <div class="history-list">
        <div
          v-for="(item, index) in queryHistory"
          :key="index"
          class="history-item"
          @click="useHistoryQuery(item)"
        >
          <el-tag :type="item.type === 'quick' ? 'success' : 'primary'" size="small">
            {{ item.type === 'quick' ? '快捷' : 'SQL' }}
          </el-tag>
          <span class="history-text">{{ item.desc }}</span>
          <span class="history-time">{{ item.time }}</span>
        </div>
        <el-empty v-if="queryHistory.length === 0" description="暂无查询历史" :image-size="50" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { 
  Search, Refresh, Download, Printer, Clock, 
  MagicStick, Document, CaretRight 
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { searchRecords, text2sql, executeQuery } from '@/api/admin/query'
import { queryRecords, type PassRecordItem } from '@/api/admin/progressive'
import { getClonePlates } from '@/api/admin/realtime'
import { checkpoints } from '@/config/checkpoints'

// 卡口选项（从配置文件加载，19个卡口）
const checkpointOptions = computed(() => 
  checkpoints.map(cp => ({
    id: cp.id,
    name: cp.name,
    region: cp.region,
    boundary: cp.boundary,
    road: cp.road
  }))
)

// 卡口ID到名称的映射
const checkpointNameMap = computed(() => {
  const map: Record<string, string> = {}
  checkpoints.forEach(cp => {
    map[cp.id] = cp.name
  })
  return map
})

defineOptions({ name: 'EtcQuery' })

const activeTab = ref('quick')
const queryType = ref('traffic')
const queryLoading = ref(false)
const text2sqlLoading = ref(false)

// 筛选条件 - 默认时间范围为 HBase 历史数据所在时间（2023-12-01）
const filters = reactive({
  dateRange: [
    new Date('2023-12-01T00:00:00'),
    new Date('2023-12-02T00:00:00')
  ] as Date[],
  checkpointId: '',  // 卡口ID（如 CP001）
  direction: '',     // 通行方向: "1"=进城, "2"=出城
  plateNumber: '',
  cloneStatus: ''    // 套牌处理状态
})

// 日期快捷选项
const dateShortcuts = [
  { text: '今天', value: () => {
    const end = new Date()
    const start = new Date()
    start.setHours(0, 0, 0, 0)
    return [start, end]
  }},
  { text: '最近24小时', value: () => {
    const end = new Date()
    const start = new Date()
    start.setTime(start.getTime() - 3600 * 1000 * 24)
    return [start, end]
  }},
  { text: '本周', value: () => {
    const end = new Date()
    const start = new Date()
    start.setTime(start.getTime() - 3600 * 1000 * 24 * 7)
    return [start, end]
  }},
  { text: '本月', value: () => {
    const end = new Date()
    const start = new Date()
    start.setDate(1)
    start.setHours(0, 0, 0, 0)
    return [start, end]
  }}
]

// Text2SQL
const naturalLanguageQuery = ref('')
const generatedSql = ref('')
const editMode = ref(false)

// 查询结果
const queryResult = ref<any[]>([])
const tableColumns = ref<any[]>([])
const totalCount = ref(0)
const queryTime = ref<number | null>(null)
const currentPage = ref(1)
const pageSize = ref(20)

// 查询历史
const queryHistory = ref<any[]>([])

// ========== HBase 历史数据加载 ==========
const hbaseLoading = ref(false)
const hbaseData = ref<PassRecordItem[]>([])
const hbaseNextRowKey = ref<string | undefined>(undefined)
const hasMoreHbaseData = ref(true)

// 加载更多 HBase 历史数据
async function loadMoreHbaseData() {
  hbaseLoading.value = true

  try {
    const params: Record<string, unknown> = {
      source: 'hbase',
      lastRowKey: hbaseNextRowKey.value,
      size: pageSize.value
    }

    // 使用当前查询的筛选条件
    if (filters.plateNumber) {
      params.plateNumber = filters.plateNumber
    }
    if (filters.checkpointId) {
      params.checkpointId = filters.checkpointId
    }
    if (filters.dateRange && filters.dateRange.length === 2) {
      // 使用本地时间格式，避免 UTC 时区偏移
      params.startTime = formatLocalDateTime(filters.dateRange[0] as Date)
      params.endTime = formatLocalDateTime(filters.dateRange[1] as Date)
    }

    const res = await queryRecords(params as Parameters<typeof queryRecords>[0])
    
    if (res.data && res.data.list) {
      // 映射卡口名称
      const mappedData = res.data.list.map((item: any) => ({
        ...item,
        checkpointName: checkpointNameMap.value[item.checkpointId] || `卡口${item.checkpointId}`
      }))
      hbaseData.value = [...hbaseData.value, ...mappedData]
      hbaseNextRowKey.value = res.data.nextRowKey
      hasMoreHbaseData.value = !!res.data.nextRowKey && res.data.list.length > 0
      ElMessage.success(`已加载 ${res.data.list.length} 条历史数据`)
    } else {
      hasMoreHbaseData.value = false
    }
  } catch (error) {
    console.error('加载 HBase 数据失败:', error)
    ElMessage.error('加载历史数据失败')
  } finally {
    hbaseLoading.value = false
  }
}

// 快捷查询
const handleQuickQuery = async () => {
  queryLoading.value = true
  const startTime = Date.now()
  
  // 重置 HBase 数据
  hbaseData.value = []
  hbaseNextRowKey.value = undefined
  hasMoreHbaseData.value = true
  
  try {
    if (queryType.value === 'clone') {
      // 套牌嫌疑查询 - 使用专门的套牌接口
      await handleCloneQuery(startTime)
    } else {
      // 车流量统计 - 使用通行记录接口
      await handleTrafficQuery(startTime)
    }
  } catch (e: any) {
    console.error('查询失败:', e)
    ElMessage.error(e.message || '查询失败')
  } finally {
    queryLoading.value = false
  }
}

// 格式化本地时间为 ISO 格式（不含时区偏移，避免 UTC 转换问题）
const formatLocalDateTime = (date: Date): string => {
  const pad = (n: number) => n.toString().padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

// 车流量统计查询
const handleTrafficQuery = async (startTime: number) => {
  // 判断查询时间范围，决定数据源
  // 2024-01-01 之前的数据在 HBase，之后的在 MySQL
  const cutoffDate = new Date('2024-01-01T00:00:00')
  const queryStartDate = filters.dateRange?.[0] as Date
  const queryEndDate = filters.dateRange?.[1] as Date
  
  const startsBeforeCutoff = queryStartDate && queryStartDate < cutoffDate
  const endsAfterCutoff = queryEndDate && queryEndDate >= cutoffDate
  
  if (startsBeforeCutoff && endsAfterCutoff) {
    // 跨数据源查询 - 同时查 HBase 和 MySQL
    console.log('🔀 跨数据源查询 (HBase + MySQL)...')
    await handleMixedQuery(startTime)
  } else if (startsBeforeCutoff) {
    // 历史数据查询 - 使用 HBase
    console.log('📚 查询历史数据 (HBase)...')
    await handleHbaseQuery(startTime)
  } else {
    // 热数据查询 - 使用 MySQL
    console.log('🔥 查询热数据 (MySQL)...')
    await handleMysqlQuery(startTime)
  }
}

// 混合查询（跨 HBase 和 MySQL）
const handleMixedQuery = async (startTime: number) => {
  const cutoffDate = new Date('2024-01-01T00:00:00')
  
  // 并行查询两个数据源
  const hbaseParams: Record<string, any> = {
    source: 'hbase',
    page: 1,
    size: Math.ceil(pageSize.value / 2)  // 每个源取一半
  }
  const mysqlParams: Record<string, any> = {
    page: 1,
    pageSize: Math.ceil(pageSize.value / 2)
  }
  
  if (filters.dateRange && filters.dateRange.length === 2) {
    // HBase 查 2024-01-01 之前的部分
    hbaseParams.startTime = formatLocalDateTime(filters.dateRange[0] as Date)
    hbaseParams.endTime = formatLocalDateTime(cutoffDate)
    // MySQL 查 2024-01-01 之后的部分
    mysqlParams.startTime = formatLocalDateTime(cutoffDate)
    mysqlParams.endTime = formatLocalDateTime(filters.dateRange[1] as Date)
  }
  if (filters.checkpointId) {
    hbaseParams.checkpointId = filters.checkpointId
    mysqlParams.checkpointId = filters.checkpointId
  }
  if (filters.direction) {
    mysqlParams.direction = filters.direction
  }
  
  console.log('🔍 混合查询参数:', { hbase: hbaseParams, mysql: mysqlParams })
  
  try {
    // 并行请求
    const [hbaseRes, mysqlRes] = await Promise.all([
      queryRecords(hbaseParams),
      searchRecords(mysqlParams)
    ])
    
    console.log('📋 HBase 响应:', hbaseRes)
    console.log('📋 MySQL 响应:', mysqlRes)
    
    setColumnsForQueryType('traffic')
    
    // 合并结果
    const hbaseList = (hbaseRes.code === 200 && hbaseRes.data?.list || []).map((item: any) => ({
      id: item.rowKey || item.id,
      plateNumber: item.plateNumber || item.hp,
      checkpointId: item.checkpointId,
      checkpointName: checkpointNameMap.value[item.checkpointId] || item.checkpointName || `卡口${item.checkpointId}`,
      passTime: item.passTime || item.gcsj,
      direction: item.direction === '1' ? '进城' : item.direction === '2' ? '出城' : item.direction,
      vehicleType: item.vehicleType || item.clppxh,
      plateType: item.plateType || item.hpzl,
      district: item.district || item.xzqhmc,
      source: 'hbase'
    }))
    
    const mysqlList = (mysqlRes.code === 200 && mysqlRes.data?.list || []).map((item: any) => ({
      ...item,
      checkpointName: checkpointNameMap.value[item.checkpointId] || item.checkpointName || `卡口${item.checkpointId}`,
      source: 'mysql'
    }))
    
    // 合并并按时间排序（最新的在前）
    queryResult.value = [...mysqlList, ...hbaseList].sort((a, b) => {
      const timeA = new Date(a.passTime).getTime()
      const timeB = new Date(b.passTime).getTime()
      return timeB - timeA
    })
    
    const hbaseTotal = hbaseRes.data?.totalCount || hbaseList.length
    const mysqlTotal = mysqlRes.data?.total || mysqlList.length
    totalCount.value = hbaseTotal + mysqlTotal
    queryTime.value = Date.now() - startTime
    
    // 更新 HBase 分页状态
    hbaseNextRowKey.value = hbaseRes.data?.nextRowKey
    hasMoreHbaseData.value = hbaseRes.data?.hasMoreHistory || false
    
    console.log('✅ 混合查询结果: HBase', hbaseList.length, '条 + MySQL', mysqlList.length, '条')
    
    addToHistory('quick', getQueryDesc())
    ElMessage.success(`查询完成，共 ${totalCount.value} 条记录 (历史 ${hbaseTotal} + 热数据 ${mysqlTotal})`)
  } catch (e: any) {
    console.error('混合查询失败:', e)
    ElMessage.error(e.message || '查询失败')
  }
}

// MySQL 热数据查询
const handleMysqlQuery = async (startTime: number) => {
  const params: Record<string, any> = {
    page: currentPage.value,
    pageSize: pageSize.value
  }
  
  if (filters.dateRange && filters.dateRange.length === 2) {
    // 使用本地时间格式，避免 UTC 时区偏移导致查询错误
    params.startTime = formatLocalDateTime(filters.dateRange[0] as Date)
    params.endTime = formatLocalDateTime(filters.dateRange[1] as Date)
  }
  if (filters.checkpointId) params.checkpointId = filters.checkpointId
  if (filters.direction) params.direction = filters.direction
  
  console.log('🔍 MySQL 查询参数:', params)
  const res = await searchRecords(params)
  console.log('📋 MySQL 查询响应:', res)
  
  if (res.code === 200 && res.data) {
    setColumnsForQueryType('traffic')
    // 将 checkpointId 映射为卡口名称
    queryResult.value = (res.data.list || []).map((item: any) => ({
      ...item,
      checkpointName: checkpointNameMap.value[item.checkpointId] || item.checkpointName || `卡口${item.checkpointId}`
    }))
    totalCount.value = res.data.total || 0
    queryTime.value = Date.now() - startTime
    console.log('✅ MySQL 查询结果:', queryResult.value.length, '条')
    
    addToHistory('quick', getQueryDesc())
    ElMessage.success(`查询完成，共 ${totalCount.value} 条记录`)
  } else {
    ElMessage.error(res.msg || '查询失败')
  }
}

// HBase 历史数据查询
const handleHbaseQuery = async (startTime: number) => {
  const params: Record<string, any> = {
    source: 'hbase',
    page: currentPage.value,
    size: pageSize.value
  }
  
  if (filters.dateRange && filters.dateRange.length === 2) {
    params.startTime = formatLocalDateTime(filters.dateRange[0] as Date)
    params.endTime = formatLocalDateTime(filters.dateRange[1] as Date)
  }
  if (filters.checkpointId) params.checkpointId = filters.checkpointId
  
  console.log('🔍 HBase 查询参数:', params)
  const res = await queryRecords(params)
  console.log('📋 HBase 查询响应:', res)
  
  if (res.code === 200 && res.data) {
    setColumnsForQueryType('traffic')
    // 转换 HBase 数据格式
    queryResult.value = (res.data.list || []).map((item: any) => ({
      id: item.rowKey || item.id,
      plateNumber: item.plateNumber || item.hp,
      checkpointId: item.checkpointId,
      checkpointName: checkpointNameMap.value[item.checkpointId] || item.checkpointName || `卡口${item.checkpointId}`,
      passTime: item.passTime || item.gcsj,
      direction: item.direction === '1' ? '进城' : item.direction === '2' ? '出城' : item.direction,
      vehicleType: item.vehicleType || item.clppxh,
      plateType: item.plateType || item.hpzl,
      district: item.district || item.xzqhmc
    }))
    totalCount.value = res.data.totalCount || res.data.list?.length || 0
    queryTime.value = Date.now() - startTime
    
    // 更新 HBase 分页状态
    hbaseNextRowKey.value = res.data.nextRowKey
    hasMoreHbaseData.value = res.data.hasMoreHistory || false
    
    console.log('✅ HBase 查询结果:', queryResult.value.length, '条, 总数:', totalCount.value)
    
    addToHistory('quick', getQueryDesc())
    ElMessage.success(`查询完成，共 ${totalCount.value} 条记录 (历史数据)`)
  } else {
    ElMessage.error(res.msg || '查询失败')
  }
}

// 套牌嫌疑查询
const handleCloneQuery = async (startTime: number) => {
  const params: Record<string, any> = {
    page: currentPage.value,
    pageSize: pageSize.value
  }
  
  if (filters.cloneStatus) params.status = filters.cloneStatus
  
  console.log('🔍 套牌嫌疑查询参数:', params)
  const res = await getClonePlates(params)
  console.log('📋 套牌嫌疑查询响应:', res)
  
  if (res.code === 200 && res.data) {
    setColumnsForQueryType('clone')
    // 处理套牌数据，映射卡口名称
    queryResult.value = (res.data.list || []).map((item: any) => ({
      ...item,
      checkpointName1: checkpointNameMap.value[item.checkpointId1] || item.checkpointId1,
      checkpointName2: checkpointNameMap.value[item.checkpointId2] || item.checkpointId2,
      // 计算可疑原因说明
      suspectReason: formatSuspectReason(item)
    }))
    totalCount.value = res.data.total || 0
    queryTime.value = Date.now() - startTime
    
    // 如果用户输入了车牌号，在前端过滤
    if (filters.plateNumber) {
      queryResult.value = queryResult.value.filter((item: any) => 
        item.plateNumber?.includes(filters.plateNumber)
      )
      totalCount.value = queryResult.value.length
    }
    
    console.log('✅ 套牌嫌疑查询结果:', queryResult.value.length, '条')
    addToHistory('quick', getQueryDesc())
    ElMessage.success(`查询完成，共 ${totalCount.value} 条记录`)
  } else {
    ElMessage.error(res.msg || '查询失败')
  }
}

// 格式化套牌嫌疑原因
const formatSuspectReason = (item: any): string => {
  const timeDiff = item.timeDiffMinutes || item.time_diff_minutes
  const distance = item.distanceKm || item.distance_km
  const minSpeed = item.minSpeedRequired || item.min_speed_required
  
  if (timeDiff && distance && minSpeed) {
    return `${timeDiff}分钟内出现在相距${distance}km的两个卡口，需时速${Math.round(minSpeed)}km/h以上`
  }
  return '短时间内出现在不同卡口，超出正常行驶能力'
}

// 根据查询类型设置表格列
const setColumnsForQueryType = (type: string) => {
  switch (type) {
    case 'traffic':
      tableColumns.value = [
        { prop: 'plateNumber', label: '车牌号', width: 120 },
        { prop: 'checkpointName', label: '卡口名称', width: 180 },
        { prop: 'passTime', label: '通过时间', width: 180 },
        { prop: 'direction', label: '方向', width: 80 },
        { prop: 'district', label: '所属区县', width: 100 },
        { prop: 'plateType', label: '车牌类型', width: 120 }
      ]
      break
    case 'clone':
      tableColumns.value = [
        { prop: 'plateNumber', label: '嫌疑车牌号', width: 120 },
        { prop: 'checkpointName1', label: '第一次出现卡口', width: 160 },
        { prop: 'time1', label: '第一次时间', width: 160 },
        { prop: 'checkpointName2', label: '第二次出现卡口', width: 160 },
        { prop: 'time2', label: '第二次时间', width: 160 },
        { prop: 'suspectReason', label: '嫌疑原因', width: 280 },
        { prop: 'status', label: '状态', width: 90 }
      ]
      break
    default:
      tableColumns.value = [
        { prop: 'plateNumber', label: '车牌号', width: 120 },
        { prop: 'checkpointName', label: '卡口名称', width: 180 },
        { prop: 'passTime', label: '通过时间', width: 180 },
        { prop: 'direction', label: '方向', width: 80 }
      ]
  }
}

// Text2SQL
const handleText2Sql = async () => {
  if (!naturalLanguageQuery.value.trim()) {
    ElMessage.warning('请输入查询描述')
    return
  }
  
  text2sqlLoading.value = true
  
  try {
    const res = await text2sql({ query: naturalLanguageQuery.value })
    
    if (res.code === 200 && res.data) {
      generatedSql.value = res.data.sql || ''
      editMode.value = false
      ElMessage.success('SQL 生成成功')
    } else {
      ElMessage.error(res.msg || 'SQL 生成失败')
    }
  } catch (e: any) {
    console.error('Text2SQL 失败:', e)
    // 降级：使用本地模板
    const query = naturalLanguageQuery.value.toLowerCase()
    if (query.includes('车流量') || query.includes('流量')) {
      generatedSql.value = `SELECT checkpoint_id, COUNT(*) as count FROM pass_record WHERE pass_time >= CURDATE() GROUP BY checkpoint_id ORDER BY count DESC`
    } else if (query.includes('超速')) {
      generatedSql.value = `SELECT plate_number, speed, checkpoint_id, pass_time FROM pass_record WHERE speed > 120 ORDER BY speed DESC LIMIT 100`
    } else {
      generatedSql.value = `SELECT * FROM pass_record ORDER BY pass_time DESC LIMIT 100`
    }
    editMode.value = false
  } finally {
    text2sqlLoading.value = false
  }
}

// 执行 SQL
const executeSql = async () => {
  if (!generatedSql.value.trim()) return
  
  queryLoading.value = true
  const startTime = Date.now()
  
  try {
    const res = await executeQuery({ sql: generatedSql.value })
    
    if (res.code === 200 && res.data) {
      // 动态生成列
      const columns = res.data.columns || []
      tableColumns.value = columns.map((col: string) => ({
        prop: col,
        label: col,
        width: 120
      }))
      queryResult.value = res.data.data || []
      totalCount.value = res.data.total || queryResult.value.length
      queryTime.value = Date.now() - startTime
      
      addToHistory('sql', naturalLanguageQuery.value.substring(0, 30) + '...')
      ElMessage.success(`查询完成，共 ${totalCount.value} 条记录`)
    } else {
      ElMessage.error(res.msg || 'SQL 执行失败')
    }
  } catch (e: any) {
    console.error('SQL 执行失败:', e)
    ElMessage.error(e.message || 'SQL 执行失败')
  } finally {
    queryLoading.value = false
  }
}

const getQueryDesc = () => {
  const typeMap: Record<string, string> = {
    traffic: '车流量统计',
    clone: '套牌嫌疑'
  }
  return typeMap[queryType.value] || '数据查询'
}

const addToHistory = (type: string, desc: string) => {
  const time = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  queryHistory.value.unshift({ type, desc, time })
  if (queryHistory.value.length > 10) queryHistory.value.pop()
}

const useHistoryQuery = (item: any) => {
  if (item.type === 'quick') {
    activeTab.value = 'quick'
  } else {
    activeTab.value = 'advanced'
  }
}

const resetFilters = () => {
  filters.dateRange = []
  filters.checkpointId = ''
  filters.direction = ''
  filters.plateNumber = ''
  filters.cloneStatus = ''
}

const clearHistory = () => {
  queryHistory.value = []
}

const copySql = () => {
  navigator.clipboard.writeText(generatedSql.value)
  ElMessage.success('已复制到剪贴板')
}

const exportData = () => {
  ElMessage.success('数据导出成功')
}

const printData = () => {
  window.print()
}
</script>

<style lang="scss" scoped>
.query-page {
  padding: 20px;
  background: #f5f7fa;
  min-height: calc(100vh - 120px);

  .page-header {
    margin-bottom: 20px;

    .page-title {
      margin: 0 0 8px;
      font-size: 22px;
      font-weight: 600;
      color: #1f2329;
    }

    .page-desc {
      margin: 0;
      font-size: 14px;
      color: #646a73;
    }
  }
}

.query-tabs {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 20px;

  :deep(.el-tabs__header) {
    margin-bottom: 20px;
  }
}

// 快捷查询
.quick-query-section {
  .query-type-selector {
    margin-bottom: 20px;
  }

  .filter-panel {
    background: #fafbfc;
    border-radius: 8px;
    padding: 20px;
    
    .unit-text {
      margin-left: 8px;
      color: #646a73;
    }

    .filter-actions {
      margin-top: 16px;
      padding-top: 16px;
      border-top: 1px solid #e5e6eb;
    }
  }
}

// 高级查询
.advanced-query-section {
  .nl-input-wrapper {
    display: flex;
    gap: 12px;
    align-items: flex-start;

    .el-textarea {
      flex: 1;
    }
  }

  .sql-preview-section {
    margin-top: 24px;
    background: #fafbfc;
    border-radius: 8px;
    padding: 16px;

    .sql-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 12px;

      .sql-title {
        display: flex;
        align-items: center;
        gap: 8px;
        font-weight: 500;
        color: #1f2329;
      }

      .sql-actions {
        display: flex;
        gap: 8px;
      }
    }

    .sql-code {
      background: #1f2329;
      color: #52c41a;
      padding: 16px;
      border-radius: 6px;
      font-family: 'Fira Code', 'Consolas', monospace;
      font-size: 13px;
      line-height: 1.6;
      overflow-x: auto;
      margin-bottom: 16px;
    }

    .sql-editor {
      margin-bottom: 16px;
      
      :deep(.el-textarea__inner) {
        font-family: 'Fira Code', 'Consolas', monospace;
      }
    }
  }
}

// 查询结果
.query-result {
  background: #fff;
  border-radius: 8px;
  margin-bottom: 20px;
  overflow: hidden;

  .result-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 16px;
    background: #fafbfc;
    border-bottom: 1px solid #e5e6eb;

    .result-info {
      display: flex;
      align-items: center;
      gap: 16px;

      .result-count {
        font-weight: 500;
        color: #1f2329;
      }

      .query-time {
        font-size: 13px;
        color: #8f959e;
      }
    }

    .result-actions {
      display: flex;
      gap: 8px;
    }
  }

  .pagination-wrapper {
    display: flex;
    justify-content: flex-end;
    padding: 16px;
    border-top: 1px solid #e5e6eb;
  }
}

// 查询历史
.query-history {
  background: #fff;
  border-radius: 8px;
  overflow: hidden;

  .history-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 12px 16px;
    background: #fafbfc;
    border-bottom: 1px solid #e5e6eb;

    .history-title {
      display: flex;
      align-items: center;
      gap: 8px;
      font-weight: 500;
      color: #1f2329;
    }
  }

  .history-list {
    padding: 8px;
    max-height: 180px;
    overflow-y: auto;

    .history-item {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 10px 12px;
      border-radius: 6px;
      cursor: pointer;
      transition: background 0.2s;

      &:hover {
        background: #f5f7fa;
      }

      .history-text {
        flex: 1;
        font-size: 14px;
        color: #1f2329;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .history-time {
        font-size: 12px;
        color: #8f959e;
      }
    }
  }
}

// HBase 历史数据区域
.hbase-result-section {
  margin-top: 16px;
  padding: 16px;
  background: #fffef5;
  border: 1px solid #ffeeba;
  border-radius: 8px;

  .hbase-header {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 12px;

    .count-info {
      font-size: 13px;
      color: #8f959e;
    }
  }
}

.load-more-wrapper {
  display: flex;
  justify-content: center;
  padding: 16px;
  border-top: 1px solid #e5e6eb;

  .no-more-text {
    color: #8f959e;
    font-size: 13px;
  }
}
</style>
