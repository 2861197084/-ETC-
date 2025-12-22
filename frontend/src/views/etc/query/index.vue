<template>
  <div class="query-page">
    <div class="page-header">
      <h2 class="page-title">数据查询</h2>
      <p class="page-desc">支持车流明细查询和套牌嫌疑分析</p>
    </div>

    <!-- Tab 切换：快捷查询 / 高级查询 -->
    <el-tabs v-model="activeTab" class="query-tabs">
      <!-- 快捷筛选查询 -->
      <el-tab-pane label="快捷查询" name="quick">
        <div class="quick-query-section">
          <!-- 查询类型选择 -->
          <div class="query-type-selector">
            <el-radio-group v-model="queryType" size="large">
              <el-radio-button value="detail">车流量明细</el-radio-button>
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
                <el-button 
                  size="small" 
                  type="info" 
                  plain 
                  @click="setEndTimeToNow" 
                  style="margin-left: 8px"
                  title="将结束时间设为当前时间"
                >
                  至今
                </el-button>
              </el-form-item>

              <!-- 卡口选择 - 明细查询 -->
              <el-form-item label="卡口" v-if="queryType === 'detail'">
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

              <!-- 通行方向 - 车流量明细 -->
              <el-form-item label="通行方向" v-if="queryType === 'detail'">
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
              <el-button type="primary" :icon="Search" @click="startNewQuery" :loading="queryLoading">
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

    <!-- 查询结果 (明细/套牌) -->
    <div class="query-result" v-if="displayData.length > 0 || queryLoading">
      <div class="result-header">
        <div class="result-info">
          <span class="result-count">
            <!-- 显示总数 -->
            共 {{ totalCount.toLocaleString() }} 条记录
            <el-tag v-if="dataSource" size="small" :type="dataSource === 'mysql' ? 'success' : dataSource === 'hbase' ? 'warning' : 'info'" style="margin-left: 8px">
              {{ dataSource === 'mysql' ? '热数据' : dataSource === 'hbase' ? '历史数据' : '混合数据' }}
            </el-tag>
            <el-tag v-if="dataSource === 'mixed' && hbaseLoading" size="small" type="info" style="margin-left: 4px">
              <el-icon class="is-loading"><Loading /></el-icon>
              加载历史数据中...
            </el-tag>
          </span>
          <span v-if="queryTime" class="query-time">
            查询耗时: {{ queryTime }}ms
          </span>
        </div>
        <div class="result-actions">
          <el-button :icon="Download" :loading="exportLoading" @click="exportData">
            {{ exportLoading ? '导出中...' : '导出 Excel' }}
          </el-button>
          <el-button :icon="Printer" @click="printData">打印</el-button>
        </div>
      </div>

      <el-table
        v-loading="queryLoading"
        :data="displayData"
        stripe
        border
        style="width: 100%"
        :header-cell-style="{ background: '#f5f7fa', color: '#606266', fontWeight: '600' }"
        :row-style="{ height: '48px' }"
        class="result-table"
      >
        <el-table-column
          v-for="col in tableColumns"
          :key="col.prop"
          :prop="col.prop"
          :label="col.label"
          :min-width="col.width || 120"
          :sortable="col.sortable"
          show-overflow-tooltip
          align="center"
        />
      </el-table>
      
      <!-- 加载更多历史数据按钮 -->
      <div v-if="dataSource === 'mixed' && hasMoreHbaseData" class="load-more-section">
        <el-button 
          type="primary" 
          plain 
          :loading="hbaseLoading"
          :icon="hbaseLoading ? undefined : MoreFilled"
          @click="loadMoreHbaseData"
        >
          {{ hbaseLoading ? '正在加载历史数据...' : '加载更多历史记录' }}
        </el-button>
      </div>

      <!-- 分页器 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[20, 50, 100]"
          :total="totalCount"
          :hide-on-single-page="false"
          :pager-count="7"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
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
  MagicStick, Document, CaretRight,
  TrendCharts, Location, DataAnalysis, Coin,
  MoreFilled, Loading
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { searchRecords, text2sql, executeQuery, executeQueryVanna } from '@/api/admin/query'
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
const queryType = ref('detail')
const queryLoading = ref(false)
const text2sqlLoading = ref(false)

// 统计查询相关状态
const statsLoading = ref(false)
const statsQueryTime = ref<number | null>(null)
const statsData = ref<{
  totalCount: number
  checkpointCount: number
  avgPerCheckpoint: number
  dataSource: string
  checkpointStats: Array<{
    checkpointId: string
    checkpointName: string
    count: number
    percentage: number
  }>
} | null>(null)

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

// 将结束时间设为当前实时时间
const setEndTimeToNow = () => {
  if (filters.dateRange && filters.dateRange.length === 2) {
    filters.dateRange = [filters.dateRange[0], new Date()]
  } else {
    // 如果没有选择开始时间，默认从今天0点开始
    const start = new Date()
    start.setHours(0, 0, 0, 0)
    filters.dateRange = [start, new Date()]
  }
}

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
const queryResult = ref<any[]>([])  // 当前页数据（后端分页）
const tableColumns = ref<any[]>([])
const totalCount = ref(0)
const queryTime = ref<number | null>(null)
const currentPage = ref(1)
const pageSize = ref(20)
const dataSource = ref<'mysql' | 'hbase' | 'mixed' | ''>('')  // 数据来源标识

// ========== 混合查询状态 ==========
const mysqlTotal = ref(0)           // MySQL 热数据总数
const mysqlData = ref<any[]>([])    // MySQL 已加载的热数据
const hbaseTotal = ref(0)           // HBase 历史数据总数
const hbaseDataCache = ref<any[]>([]) // HBase 已加载的历史数据
const hbaseReady = ref(false)       // HBase 首批数据是否已加载完成
const mixedQueryCutoff = ref<Date | null>(null)  // 混合查询的时间分界点
const mysqlFullLoaded = ref(false)  // MySQL 是否已加载全部数据

// ========== HBase 历史数据加载 ==========
const hbaseLoading = ref(false)
const hbaseData = ref<PassRecordItem[]>([])
const hbaseNextRowKey = ref<string | undefined>(undefined)
const hasMoreHbaseData = ref(false)

// 计算当前页显示的数据
const displayData = computed(() => {
  // 情况 1、2、3：后端分页，直接返回 queryResult
  if (dataSource.value === 'mysql' || dataSource.value === 'hbase') {
    return queryResult.value
  }
  
  // 情况 4：混合查询，前端从缓存中切片
  if (dataSource.value === 'mixed') {
    const allData = [...mysqlData.value, ...hbaseDataCache.value]
    const start = (currentPage.value - 1) * pageSize.value
    const end = start + pageSize.value
    const pageData = allData.slice(start, end)
    
    console.log(`📊 混合分页: 第${currentPage.value}页, 范围${start}-${end}, MySQL:${mysqlData.value.length}, HBase:${hbaseDataCache.value.length}`)
    
    // 如果当前页数据不足，检查是否还有数据可加载
    if (pageData.length < pageSize.value) {
      const mysqlRemaining = mysqlTotal.value - mysqlData.value.length
      const hbaseRemaining = hbaseTotal.value - hbaseDataCache.value.length
      
      if (mysqlRemaining > 0 || hbaseRemaining > 0) {
        return [...pageData, { _isLoadMoreRow: true }]
      }
    }
    
    return pageData
  }
  
  // 默认返回 queryResult
  return queryResult.value
})

// 查询历史
const queryHistory = ref<any[]>([])

// HBase 分页游标缓存：存储每页对应的 lastRowKey
const hbasePageKeys = ref<Map<number, string | undefined>>(new Map())
// 标记是否为筛选查询（HBase 筛选查询无法获取精确总数）
const hbaseFilteredQuery = ref(false)
// HBase 查询 Promise（用于并行查询时追踪状态）
let hbaseQueryPromise: Promise<void> | null = null

// 加载更多 HBase 历史数据（追加到现有数据后面）
async function loadMoreHbaseData() {
  if (hbaseLoading.value) return  // 防止重复点击
  
  hbaseLoading.value = true

  try {
    const params: Record<string, unknown> = {
      source: 'hbase',
      lastRowKey: hbaseNextRowKey.value,
      size: 500  // 每次加载 500 条，减少用户点击
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
    
    if (res.data && res.data.list && res.data.list.length > 0) {
      // 转换并追加数据
      const newData = res.data.list.map((item: any) => ({
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
      
      // 追加到现有结果
      queryResult.value = [...queryResult.value, ...newData]
      dataSource.value = 'mixed'
      
      // 更新 HBase 分页状态
      hbaseNextRowKey.value = res.data.nextRowKey
      hasMoreHbaseData.value = res.data.hasMoreHistory || false
      
      console.log('✅ 追加 HBase 数据:', newData.length, '条, 总计:', queryResult.value.length)
      ElMessage.success(`已加载 ${newData.length} 条历史数据`)
    } else {
      hasMoreHbaseData.value = false
      ElMessage.info('没有更多历史数据了')
    }
  } catch (error) {
    console.error('加载 HBase 数据失败:', error)
    ElMessage.error('加载历史数据失败')
  } finally {
    hbaseLoading.value = false
  }
}

// 加载更多 HBase 数据（混合查询专用，追加到 hbaseDataCache）
async function loadMoreHbaseDataForMixed() {
  if (hbaseLoading.value) return
  
  hbaseLoading.value = true

  try {
    const params: Record<string, any> = {
      source: 'hbase',
      lastRowKey: hbaseNextRowKey.value,
      size: 100  // 每次加载 100 条
    }

    if (filters.checkpointId) params.checkpointId = filters.checkpointId
    if (mixedQueryCutoff.value && filters.dateRange?.length === 2) {
      params.startTime = formatLocalDateTime(filters.dateRange[0] as Date)
      params.endTime = formatLocalDateTime(mixedQueryCutoff.value)
    }

    console.log('📥 加载更多 HBase 数据, lastRowKey:', hbaseNextRowKey.value)
    
    const res = await queryRecords(params)
    
    if (res.code === 200 && res.data?.list?.length > 0) {
      const newData = res.data.list.map((item: any) => ({
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
      
      hbaseDataCache.value = [...hbaseDataCache.value, ...newData]
      hbaseNextRowKey.value = res.data.nextRowKey
      hasMoreHbaseData.value = res.data.hasMoreHistory || (hbaseDataCache.value.length < hbaseTotal.value)
      
      console.log('✅ HBase 追加:', newData.length, '条, 总计:', hbaseDataCache.value.length)
    } else {
      hasMoreHbaseData.value = false
    }
  } catch (error) {
    console.error('加载 HBase 数据失败:', error)
  } finally {
    hbaseLoading.value = false
  }
}

// 开始新查询（点击查询按钮时调用，重置页码）
const startNewQuery = () => {
  currentPage.value = 1
  handleQuickQuery()
}

// 快捷查询（点击查询按钮触发）
const handleQuickQuery = async () => {
  // 统计查询单独处理
  if (queryType.value === 'stats') {
    await handleStatsQuery()
    return
  }
  
  queryLoading.value = true
  const startTime = Date.now()
  
  try {
    if (queryType.value === 'clone') {
      // 套牌嫌疑查询 - 使用专门的套牌接口
      await handleCloneQuery(startTime)
    } else {
      // 车流量明细 - 使用通行记录接口
      await handleTrafficQuery(startTime)
    }
  } catch (e: any) {
    console.error('查询失败:', e)
    ElMessage.error(e.message || '查询失败')
  } finally {
    queryLoading.value = false
  }
}

// 统计查询
const handleStatsQuery = async () => {
  statsLoading.value = true
  statsData.value = null
  const startTime = Date.now()
  
  try {
    const queryStartDate = filters.dateRange?.[0] as Date
    const queryEndDate = filters.dateRange?.[1] as Date
    
    if (!queryStartDate || !queryEndDate) {
      ElMessage.warning('请选择查询时间范围')
      return
    }
    
    // 调用统计接口
    const params: Record<string, string> = {
      startDate: queryStartDate.toISOString().split('T')[0],
      endDate: queryEndDate.toISOString().split('T')[0]
    }
    if (filters.checkpointId) {
      params.checkpointId = filters.checkpointId
    }
    
    console.log('🔍 统计查询参数:', params)
    
    const res = await fetch(`/api/stats/total?${new URLSearchParams(params)}`)
    const data = await res.json()
    
    console.log('📊 统计查询响应:', data)
    
    if (data.code === 200 && data.data) {
      const result = data.data
      const totalCount = (result.hbaseCount || 0) + (result.mysqlCount || 0)
      
      // 处理收费站统计数据
      const checkpointMap = result.checkpointCounts || {}
      const checkpointStats = Object.entries(checkpointMap)
        .map(([id, count]) => ({
          checkpointId: id,
          checkpointName: checkpointNameMap.value[id] || `卡口${id}`,
          count: count as number,
          percentage: totalCount > 0 ? ((count as number) / totalCount * 100) : 0
        }))
        .sort((a, b) => b.count - a.count)  // 按通行量降序排列
      
      statsData.value = {
        totalCount,
        checkpointCount: checkpointStats.length,
        avgPerCheckpoint: checkpointStats.length > 0 
          ? Math.round(totalCount / checkpointStats.length) 
          : 0,
        dataSource: result.source || (result.hbaseCount > 0 ? 'HBase' : 'MySQL'),
        checkpointStats
      }
      
      statsQueryTime.value = Date.now() - startTime
      ElMessage.success(`统计完成，共 ${formatNumber(totalCount)} 条记录`)
    } else {
      ElMessage.error(data.msg || '统计查询失败')
    }
  } catch (e: any) {
    console.error('统计查询失败:', e)
    ElMessage.error(e.message || '统计查询失败')
  } finally {
    statsLoading.value = false
  }
}

// 数字格式化
const formatNumber = (num: number): string => {
  return num.toLocaleString('zh-CN')
}

// 分页切换处理
const handlePageChange = async (page: number) => {
  console.log('📄 切换到第', page, '页, 数据源:', dataSource.value)
  currentPage.value = page
  
  // 情况 1、2、3：后端分页，重新请求
  if (dataSource.value === 'mysql' || dataSource.value === 'hbase') {
    handleQuickQuery()
    return
  }
  
  // 情况 4：混合查询，前端分页，按需加载
  if (dataSource.value === 'mixed') {
    const start = (page - 1) * pageSize.value
    const end = start + pageSize.value
    let allLoadedCount = mysqlData.value.length + hbaseDataCache.value.length
    
    console.log(`📄 混合翻页: 需要${start}-${end}, 已加载${allLoadedCount}`)
    
    // 循环加载直到数据足够
    while (end > allLoadedCount) {
      const mysqlLoaded = mysqlData.value.length
      const hbaseLoaded = hbaseDataCache.value.length
      
      if (mysqlLoaded < mysqlTotal.value) {
        console.log('📥 加载更多 MySQL...')
        await loadMoreMysqlData()
      } else if (hbaseLoaded < hbaseTotal.value) {
        console.log('📥 加载更多 HBase...')
        await loadMoreHbaseDataForMixed()
      } else {
        break
      }
      
      const newLoaded = mysqlData.value.length + hbaseDataCache.value.length
      if (newLoaded === allLoadedCount) break
      allLoadedCount = newLoaded
    }
    return
  }
  
  // 默认重新请求
  handleQuickQuery()
}

// 加载更多 MySQL 数据（混合查询时使用）
const loadMoreMysqlData = async () => {
  if (!mixedQueryCutoff.value) return
  
  const nextPage = Math.floor(mysqlData.value.length / 100) + 1
  const params: Record<string, any> = {
    page: nextPage,
    pageSize: 100
  }
  
  if (filters.dateRange && filters.dateRange.length === 2) {
    params.startTime = formatLocalDateTime(mixedQueryCutoff.value)
    params.endTime = formatLocalDateTime(filters.dateRange[1] as Date)
  }
  if (filters.checkpointId) params.checkpointId = filters.checkpointId
  if (filters.direction) params.direction = filters.direction
  
  console.log('📥 加载更多 MySQL 数据, 页码:', nextPage)
  
  try {
    const res = await searchRecords(params)
    if (res.code === 200 && res.data?.list) {
      const newData = res.data.list.map((item: any) => ({
        ...item,
        checkpointName: checkpointNameMap.value[item.checkpointId] || item.checkpointName || `卡口${item.checkpointId}`,
        source: 'mysql'
      }))
      mysqlData.value = [...mysqlData.value, ...newData]
      console.log('✅ MySQL 追加:', newData.length, '条, 总计:', mysqlData.value.length)
    }
  } catch (e) {
    console.error('加载 MySQL 数据失败:', e)
  }
}

// 每页条数切换处理
const handleSizeChange = (size: number) => {
  console.log('📄 每页显示', size, '条')
  pageSize.value = size
  currentPage.value = 1  // 重置到第一页
  handleQuickQuery()  // 重新查询
}

// 格式化本地时间为 ISO 格式（不含时区偏移，避免 UTC 转换问题）
const formatLocalDateTime = (date: Date): string => {
  const pad = (n: number) => n.toString().padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

// 车流量统计查询 - 根据筛选条件路由到不同数据源
const handleTrafficQuery = async (startTime: number) => {
  // 判断是否有额外筛选条件（卡口、方向）
  const hasFilters = !!(filters.checkpointId || filters.direction)
  
  // 判断查询时间范围
  const now = new Date()
  const sevenDaysAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000)
  sevenDaysAgo.setHours(0, 0, 0, 0)
  
  const queryStartDate = filters.dateRange?.[0] as Date
  const queryEndDate = filters.dateRange?.[1] as Date
  
  const allInLast7Days = queryStartDate && queryStartDate >= sevenDaysAgo
  const allBefore7Days = queryEndDate && queryEndDate < sevenDaysAgo
  const crossesBoundary = queryStartDate && queryEndDate && queryStartDate < sevenDaysAgo && queryEndDate >= sevenDaysAgo
  
  console.log(`📅 数据分界点: ${sevenDaysAgo.toISOString()} (7天前)`)
  console.log(`📅 查询范围: ${queryStartDate?.toISOString()} ~ ${queryEndDate?.toISOString()}`)
  console.log(`🔍 筛选条件: 卡口=${filters.checkpointId || '无'}, 方向=${filters.direction || '无'}`)
  
  // ===== 情况1: 只筛选日期，无卡口/方向 → HBase 后端分页 =====
  if (!hasFilters) {
    console.log('📚 【情况1】纯时间查询 → HBase 后端分页')
    await handleHbaseQuery(startTime)
    return
  }
  
  // ===== 情况2: 日期全在7天内 + 卡口或方向 → MySQL 后端分页 =====
  if (allInLast7Days) {
    console.log('🔥 【情况2】7天内 + 筛选 → MySQL 后端分页')
    await handleMysqlQuery(startTime)
    return
  }
  
  // ===== 情况3: 日期全在7天外 + 卡口或方向 → HBase 后端分页 =====
  if (allBefore7Days) {
    console.log('📚 【情况3】7天外 + 筛选 → HBase 后端分页')
    await handleHbaseQuery(startTime)
    return
  }
  
  // ===== 情况4: 日期跨越7天边界 + 卡口或方向 → 混合查询前端分页 =====
  if (crossesBoundary) {
    console.log('🔀 【情况4】跨7天边界 + 筛选 → MySQL+HBase 混合查询')
    await handleMixedQuery(startTime, sevenDaysAgo)
    return
  }
  
  // 默认走 HBase
  console.log('📚 默认 → HBase 后端分页')
  await handleHbaseQuery(startTime)
}

// 混合查询（MySQL 和 HBase 同时查，MySQL 先显示，HBase 无缝追加）
const handleMixedQuery = async (startTime: number, cutoffDate: Date) => {
  // 重置状态
  dataSource.value = 'mixed'
  hasMoreHbaseData.value = true
  hbaseNextRowKey.value = undefined
  currentPage.value = 1
  mysqlData.value = []
  mysqlTotal.value = 0
  hbaseDataCache.value = []
  hbaseTotal.value = 0
  hbaseReady.value = false
  
  // 保存 cutoffDate 供翻页时使用
  mixedQueryCutoff.value = cutoffDate
  
  // 首次只获取前 100 条数据（5页），同时获取总数
  const initialSize = 100
  
  // 构建 MySQL 查询参数
  const mysqlParams: Record<string, any> = {
    page: 1,
    pageSize: initialSize
  }
  
  if (filters.dateRange && filters.dateRange.length === 2) {
    mysqlParams.startTime = formatLocalDateTime(cutoffDate)
    mysqlParams.endTime = formatLocalDateTime(filters.dateRange[1] as Date)
  }
  if (filters.checkpointId) mysqlParams.checkpointId = filters.checkpointId
  if (filters.direction) mysqlParams.direction = filters.direction
  
  // 构建 HBase 查询参数
  const hbaseParams: Record<string, any> = {
    source: 'hbase',
    size: initialSize
  }
  if (filters.dateRange && filters.dateRange.length === 2) {
    hbaseParams.startTime = formatLocalDateTime(filters.dateRange[0] as Date)
    hbaseParams.endTime = formatLocalDateTime(cutoffDate)
  }
  if (filters.checkpointId) hbaseParams.checkpointId = filters.checkpointId
  
  console.log('🔍 混合查询 - MySQL 参数:', mysqlParams)
  console.log('🔍 混合查询 - HBase 参数:', hbaseParams)
  
  setColumnsForQueryType('detail')
  
  try {
    // MySQL 和 HBase 同时发起查询
    const mysqlPromise = searchRecords(mysqlParams)
    const hbasePromise = queryRecords(hbaseParams)
    
    // 1. 等待 MySQL 响应
    const mysqlRes = await mysqlPromise
    console.log('📋 MySQL 响应:', mysqlRes)
    
    if (mysqlRes.code === 200 && mysqlRes.data) {
      mysqlData.value = (mysqlRes.data.list || []).map((item: any) => ({
        ...item,
        checkpointName: checkpointNameMap.value[item.checkpointId] || item.checkpointName || `卡口${item.checkpointId}`,
        source: 'mysql'
      }))
      mysqlTotal.value = mysqlRes.data.total || mysqlData.value.length
      
      // 先用 MySQL 的总数显示
      totalCount.value = mysqlTotal.value
      queryTime.value = Date.now() - startTime
      
      console.log('✅ MySQL 返回:', mysqlData.value.length, '条, 总数:', mysqlTotal.value)
    }
    
    // 2. 后台等待 HBase 响应
    hbaseLoading.value = true
    hbasePromise.then(hbaseRes => {
      console.log('📋 HBase 响应:', hbaseRes)
      
      if (hbaseRes.code === 200 && hbaseRes.data) {
        const hbaseList = (hbaseRes.data.list || []).map((item: any) => ({
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
        
        hbaseDataCache.value = hbaseList
        hbaseTotal.value = hbaseRes.data.total || hbaseList.length
        hbaseNextRowKey.value = hbaseRes.data.nextRowKey
        hasMoreHbaseData.value = hbaseRes.data.hasMoreHistory || (hbaseList.length < hbaseTotal.value)
        
        // HBase 存的是全局数据，直接用 HBase 总数替换
        totalCount.value = hbaseTotal.value
        
        console.log('✅ HBase 返回:', hbaseList.length, '条, 总数:', hbaseTotal.value)
      } else {
        hasMoreHbaseData.value = false
        hbaseTotal.value = 0
      }
      
      hbaseReady.value = true
      hbaseLoading.value = false
    }).catch(e => {
      console.error('HBase 查询失败:', e)
      hbaseReady.value = true
      hbaseLoading.value = false
      hbaseTotal.value = 0
    })
    
    addToHistory('quick', getQueryDesc())
  } catch (e: any) {
    console.error('MySQL 查询失败:', e)
    ElMessage.error(e.message || '查询失败')
  }
}

// MySQL 热数据查询（近7天数据）- 标准后端分页
const handleMysqlQuery = async (startTime: number) => {
  // 重置状态
  dataSource.value = 'mysql'
  hasMoreHbaseData.value = false  // 纯 MySQL 查询，没有历史数据
  
  const params: Record<string, any> = {
    page: currentPage.value,
    pageSize: pageSize.value
  }
  
  if (filters.dateRange && filters.dateRange.length === 2) {
    params.startTime = formatLocalDateTime(filters.dateRange[0] as Date)
    params.endTime = formatLocalDateTime(filters.dateRange[1] as Date)
  }
  if (filters.checkpointId) params.checkpointId = filters.checkpointId
  if (filters.direction) params.direction = filters.direction
  
  console.log('🔍 MySQL 查询参数:', params)
  const res = await searchRecords(params)
  console.log('📋 MySQL 查询响应:', res)
  
  if (res.code === 200 && res.data) {
    setColumnsForQueryType('detail')
    queryResult.value = (res.data.list || []).map((item: any) => ({
      ...item,
      checkpointName: checkpointNameMap.value[item.checkpointId] || item.checkpointName || `卡口${item.checkpointId}`,
      source: 'mysql'
    }))
    totalCount.value = res.data.total || 0
    queryTime.value = Date.now() - startTime
    console.log('✅ MySQL 查询结果:', queryResult.value.length, '条, 总数:', totalCount.value)
    
    addToHistory('quick', getQueryDesc())
    ElMessage.success(`查询完成，共 ${totalCount.value} 条记录`)
  } else {
    ElMessage.error(res.msg || '查询失败')
  }
}

// HBase 历史数据查询 - 后端分页（游标模式）
const handleHbaseQuery = async (startTime: number) => {
  // 设置数据源
  dataSource.value = 'hbase'
  hasMoreHbaseData.value = false
  
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
  if (filters.direction) params.direction = filters.direction
  
  // 游标分页：page > 1 时使用 lastRowKey
  if (currentPage.value > 1 && hbaseNextRowKey.value) {
    params.lastRowKey = hbaseNextRowKey.value
  }
  
  console.log('🔍 HBase 查询参数:', params)
  const res = await queryRecords(params)
  console.log('📋 HBase 查询响应:', res)
  
  if (res.code === 200 && res.data) {
    setColumnsForQueryType('detail')
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
      district: item.district || item.xzqhmc,
      source: 'hbase'
    }))
    
    queryTime.value = Date.now() - startTime
    
    // 更新 nextRowKey（用于下一页）
    hbaseNextRowKey.value = res.data.nextRowKey
    hasMoreHbaseData.value = res.data.hasMoreHistory || false
    
    // total 只在首次查询时更新（page=1 返回精确值，后续返回 -1）
    if (res.data.total > 0) {
      totalCount.value = res.data.total
    }
    
    console.log('✅ HBase 查询结果:', queryResult.value.length, '条, 总数:', totalCount.value, ', nextRowKey:', hbaseNextRowKey.value)
    
    addToHistory('quick', getQueryDesc())
    if (currentPage.value === 1) {
      ElMessage.success(`查询完成，共 ${totalCount.value} 条记录`)
    }
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
  if (filters.plateNumber) params.plateNumber = filters.plateNumber
  if (filters.dateRange && filters.dateRange.length === 2) {
    params.startTime = formatLocalDateTime(filters.dateRange[0] as Date)
    params.endTime = formatLocalDateTime(filters.dateRange[1] as Date)
  }
  
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
    case 'detail':
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
  const startTime = Date.now()
  
  try {
    // 调用 Vanna 服务，同时生成 SQL 并执行
    const res = await text2sql({ query: naturalLanguageQuery.value, execute: true })
    
    if (res.code === 200 && res.data) {
      generatedSql.value = res.data.sql || ''
      editMode.value = false
      
      // 如果有查询结果，直接展示
      if (res.data.columns && res.data.result) {
        tableColumns.value = res.data.columns.map((col: string) => ({
          prop: col,
          label: col,
          width: 150
        }))
        // 转换数据格式 (数组 → 对象)
        queryResult.value = res.data.result.map((row: any[]) => {
          const obj: Record<string, unknown> = {}
          res.data.columns!.forEach((col: string, i: number) => {
            obj[col] = row[i]
          })
          return obj
        })
        totalCount.value = queryResult.value.length
        queryTime.value = Date.now() - startTime
        dataSource.value = 'mysql'
        
        addToHistory('sql', naturalLanguageQuery.value.substring(0, 30) + '...')
        ElMessage.success(`查询完成，共 ${totalCount.value} 条记录`)
      } else {
        ElMessage.success('SQL 生成成功，点击"执行查询"查看结果')
      }
    } else {
      ElMessage.error(res.msg || 'SQL 生成失败')
    }
  } catch (e: any) {
    console.error('Text2SQL 失败:', e)
    // 降级：使用本地模板
    const query = naturalLanguageQuery.value.toLowerCase()
    if (query.includes('车流量') || query.includes('流量')) {
      generatedSql.value = `SELECT checkpoint_id, COUNT(*) as count FROM pass_record WHERE DATE(gcsj) = CURDATE() GROUP BY checkpoint_id ORDER BY count DESC`
    } else if (query.includes('超速')) {
      generatedSql.value = `SELECT hp as plate_number, kkmc as checkpoint, gcsj as pass_time FROM pass_record WHERE clppxh LIKE '%跑车%' ORDER BY gcsj DESC LIMIT 100`
    } else if (query.includes('套牌')) {
      generatedSql.value = `SELECT * FROM clone_plate_detection WHERE status = 'pending' ORDER BY detection_time DESC LIMIT 50`
    } else {
      generatedSql.value = `SELECT hp, kkmc, gcsj, fxlx FROM pass_record ORDER BY gcsj DESC LIMIT 100`
    }
    editMode.value = false
    ElMessage.warning('AI 服务暂不可用，已使用模板 SQL')
  } finally {
    text2sqlLoading.value = false
  }
}

// 执行 SQL (使用 Vanna 服务直接执行)
const executeSql = async () => {
  if (!generatedSql.value.trim()) return
  
  queryLoading.value = true
  const startTime = Date.now()
  
  try {
    const res = await executeQueryVanna(generatedSql.value)
    
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

// 导出全部数据
const exportLoading = ref(false)

const exportData = async () => {
  if (totalCount.value === 0) {
    ElMessage.warning('没有可导出的数据')
    return
  }
  
  // 数据量检查
  if (totalCount.value > 100000) {
    ElMessage.warning(`数据量过大（${totalCount.value.toLocaleString()} 条），请缩小查询范围后再导出`)
    return
  }
  
  exportLoading.value = true
  ElMessage.info(`正在导出 ${totalCount.value.toLocaleString()} 条数据，请稍候...`)
  
  try {
    let allData: any[] = []
    
    if (queryType.value === 'clone') {
      // 套牌嫌疑：一次性获取全部
      const params: Record<string, any> = {
        page: 1,
        pageSize: Math.min(totalCount.value, 100000)
      }
      if (filters.cloneStatus) params.status = filters.cloneStatus
      if (filters.plateNumber) params.plateNumber = filters.plateNumber
      if (filters.dateRange && filters.dateRange.length === 2) {
        params.startTime = formatLocalDateTime(filters.dateRange[0] as Date)
        params.endTime = formatLocalDateTime(filters.dateRange[1] as Date)
      }
      
      const res = await queryClonePlates(params)
      if (res.code === 200 && res.data?.list) {
        allData = res.data.list
      }
    } else {
      // 明细查询：根据数据源分别获取
      if (dataSource.value === 'mysql') {
        // MySQL 数据
        const params: Record<string, any> = {
          page: 1,
          pageSize: Math.min(totalCount.value, 100000)
        }
        if (filters.dateRange && filters.dateRange.length === 2) {
          params.startTime = formatLocalDateTime(filters.dateRange[0] as Date)
          params.endTime = formatLocalDateTime(filters.dateRange[1] as Date)
        }
        if (filters.checkpointId) params.checkpointId = filters.checkpointId
        if (filters.direction) params.direction = filters.direction
        
        const res = await queryRecords(params)
        if (res.code === 200 && res.data?.list) {
          allData = res.data.list
        }
      } else if (dataSource.value === 'hbase') {
        // HBase 数据 - 分批获取
        const batchSize = 1000
        let page = 1
        let lastRowKey = ''
        
        while (allData.length < totalCount.value) {
          const params: Record<string, any> = {
            source: 'hbase',
            page: page,
            size: batchSize
          }
          if (filters.dateRange && filters.dateRange.length === 2) {
            params.startTime = formatLocalDateTime(filters.dateRange[0] as Date)
            params.endTime = formatLocalDateTime(filters.dateRange[1] as Date)
          }
          if (filters.checkpointId) params.checkpointId = filters.checkpointId
          if (filters.direction) params.direction = filters.direction
          if (lastRowKey) params.lastRowKey = lastRowKey
          
          const res = await queryRecords(params)
          if (res.code === 200 && res.data?.list?.length > 0) {
            allData.push(...res.data.list)
            lastRowKey = res.data.nextRowKey || ''
            if (!res.data.hasMoreHistory) break
            page++
          } else {
            break
          }
        }
      } else {
        // 混合查询：使用已缓存的数据 + 继续加载
        allData = [...mysqlData.value, ...hbaseDataCache.value]
        // 如果还有更多 HBase 数据，继续加载
        while (hasMoreHbaseData.value && allData.length < totalCount.value) {
          await loadMoreHbaseData()
          allData = [...mysqlData.value, ...hbaseDataCache.value]
        }
      }
    }
    
    if (allData.length === 0) {
      ElMessage.warning('获取数据失败')
      return
    }
    
    // 动态导入 xlsx
    const XLSX = await import('xlsx')
    
    // 准备导出数据
    const exportRows = allData.map((row: any) => {
      if (queryType.value === 'clone') {
        return {
          '车牌号': row.plateNumber,
          '首次通过卡口': row.firstCheckpoint,
          '首次时间': row.firstTime,
          '二次通过卡口': row.secondCheckpoint,
          '二次时间': row.secondTime,
          '间隔时间(分钟)': row.timeGap,
          '状态': row.status
        }
      }
      const direction = row.direction === '1' ? '进城' : row.direction === '2' ? '出城' : (row.direction || '')
      return {
        '车牌号': row.plateNumber || row.hp,
        '卡口名称': checkpointNameMap.value[row.checkpointId] || row.checkpointName || row.kkmc,
        '通行时间': row.passTime || row.gcsj,
        '通行方向': direction,
        '车辆类型': row.vehicleType || row.clppxh,
        '号牌种类': row.plateType || row.hpzl,
        '行政区划': row.district || row.xzqhmc
      }
    })
    
    // 创建工作表
    const ws = XLSX.utils.json_to_sheet(exportRows)
    
    // 设置列宽
    ws['!cols'] = [
      { wch: 12 }, // 车牌号
      { wch: 20 }, // 卡口名称
      { wch: 20 }, // 通行时间
      { wch: 10 }, // 通行方向
      { wch: 15 }, // 车辆类型
      { wch: 12 }, // 号牌种类
      { wch: 15 }, // 行政区划
    ]
    
    const wb = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(wb, ws, queryType.value === 'clone' ? '套牌嫌疑' : '通行记录')
    
    // 下载文件
    const fileName = `${queryType.value === 'clone' ? '套牌嫌疑' : '通行记录'}_${new Date().toLocaleDateString('zh-CN').replace(/\//g, '-')}.xlsx`
    XLSX.writeFile(wb, fileName)
    
    ElMessage.success(`成功导出 ${exportRows.length.toLocaleString()} 条数据`)
  } catch (err) {
    console.error('导出失败:', err)
    ElMessage.error('导出失败，请重试')
  } finally {
    exportLoading.value = false
  }
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
    padding: 16px 20px;
    background: #fafbfc;
    border-bottom: 1px solid #e5e6eb;

    .result-info {
      display: flex;
      align-items: center;
      gap: 16px;

      .result-count {
        font-weight: 600;
        font-size: 15px;
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

  .result-table {
    :deep(.el-table__body-wrapper) {
      min-height: 300px;
    }
    
    :deep(.el-table__cell) {
      padding: 12px 8px;
    }
  }

  .load-more-section {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 16px;
    padding: 16px;
    background: linear-gradient(180deg, #f0f7ff 0%, #fff 100%);
    border-top: 1px dashed #d9ecff;

    .load-more-hint {
      font-size: 13px;
      color: #909399;
    }
  }

  .pagination-wrapper {
    display: flex;
    justify-content: flex-end;
    padding: 16px 20px;
    background: #fafbfc;
    border-top: 1px solid #e5e6eb;

    .simple-pagination {
      display: flex;
      align-items: center;
      gap: 12px;

      .page-info {
        color: #606266;
        font-size: 14px;
        min-width: 60px;
        text-align: center;
      }
    }
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
</style>
