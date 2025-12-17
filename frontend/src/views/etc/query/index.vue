<template>
  <div class="query-page">
    <div class="page-header">
      <h2 class="page-title">离线数据查询</h2>
      <p class="page-desc">支持快速筛选和自定义 SQL 查询，分析交通数据</p>
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
              <el-radio-button value="revenue">营收统计</el-radio-button>
              <el-radio-button value="violation">违章查询</el-radio-button>
              <el-radio-button value="speed">超速记录</el-radio-button>
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

              <!-- 站点选择 -->
              <el-form-item label="收费站" v-if="['traffic', 'revenue'].includes(queryType)">
                <el-select v-model="filters.stationId" placeholder="全部站点" clearable style="width: 180px">
                  <el-option label="徐州东站" value="1" />
                  <el-option label="铜山收费站" value="2" />
                  <el-option label="贾汪收费站" value="3" />
                  <el-option label="新沂收费站" value="4" />
                  <el-option label="邳州收费站" value="5" />
                </el-select>
              </el-form-item>

              <!-- 车辆类型 -->
              <el-form-item label="车辆类型" v-if="['traffic', 'revenue', 'speed'].includes(queryType)">
                <el-select v-model="filters.vehicleType" placeholder="全部类型" clearable style="width: 140px">
                  <el-option label="小型车" value="1" />
                  <el-option label="中型车" value="2" />
                  <el-option label="大型车" value="3" />
                  <el-option label="特大型车" value="4" />
                </el-select>
              </el-form-item>

              <!-- 车牌号 -->
              <el-form-item label="车牌号" v-if="['violation', 'speed', 'clone'].includes(queryType)">
                <el-input v-model="filters.plateNumber" placeholder="输入车牌号" clearable style="width: 140px" />
              </el-form-item>

              <!-- 违章类型 -->
              <el-form-item label="违章类型" v-if="queryType === 'violation'">
                <el-select v-model="filters.violationType" placeholder="全部类型" clearable style="width: 160px">
                  <el-option label="超速" value="speeding" />
                  <el-option label="闯禁区" value="forbidden" />
                  <el-option label="逆行" value="reverse" />
                  <el-option label="占用应急车道" value="emergency" />
                </el-select>
              </el-form-item>

              <!-- 速度阈值 -->
              <el-form-item label="速度阈值" v-if="queryType === 'speed'">
                <el-input-number v-model="filters.speedThreshold" :min="60" :max="200" :step="10" />
                <span class="unit-text">km/h 以上</span>
              </el-form-item>

              <!-- 统计维度 -->
              <el-form-item label="统计维度" v-if="['traffic', 'revenue'].includes(queryType)">
                <el-select v-model="filters.groupBy" style="width: 140px">
                  <el-option label="按站点" value="station" />
                  <el-option label="按小时" value="hour" />
                  <el-option label="按天" value="day" />
                  <el-option label="按车型" value="vehicle" />
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
import { ref, reactive } from 'vue'
import { 
  Search, Refresh, Download, Printer, Clock, 
  MagicStick, Document, CaretRight 
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

defineOptions({ name: 'EtcQuery' })

const activeTab = ref('quick')
const queryType = ref('traffic')
const queryLoading = ref(false)
const text2sqlLoading = ref(false)

// 筛选条件
const filters = reactive({
  dateRange: [],
  stationId: '',
  vehicleType: '',
  plateNumber: '',
  violationType: '',
  speedThreshold: 120,
  groupBy: 'station'
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

// 快捷查询
const handleQuickQuery = async () => {
  queryLoading.value = true
  const startTime = Date.now()
  
  await new Promise(resolve => setTimeout(resolve, 600))
  
  // 根据查询类型生成模拟数据
  const result = generateMockData(queryType.value)
  tableColumns.value = result.columns
  queryResult.value = result.data
  totalCount.value = result.total
  queryTime.value = Date.now() - startTime
  
  // 添加到历史
  addToHistory('quick', getQueryDesc())
  
  queryLoading.value = false
  ElMessage.success(`查询完成，共 ${result.total} 条记录`)
}

// Text2SQL
const handleText2Sql = async () => {
  if (!naturalLanguageQuery.value.trim()) {
    ElMessage.warning('请输入查询描述')
    return
  }
  
  text2sqlLoading.value = true
  await new Promise(resolve => setTimeout(resolve, 1200))
  
  // 模拟生成 SQL
  const query = naturalLanguageQuery.value.toLowerCase()
  if (query.includes('车流量') || query.includes('流量')) {
    generatedSql.value = `SELECT 
  s.station_name AS 站点名称,
  COUNT(*) AS 车流量,
  SUM(CASE WHEN v.type = 1 THEN 1 ELSE 0 END) AS 小型车,
  SUM(CASE WHEN v.type IN (2,3,4) THEN 1 ELSE 0 END) AS 大型车
FROM etc_records r
JOIN stations s ON r.station_id = s.id
JOIN vehicles v ON r.vehicle_id = v.id
WHERE r.pass_time >= CURDATE()
GROUP BY s.id
ORDER BY 车流量 DESC;`
  } else if (query.includes('超速')) {
    generatedSql.value = `SELECT 
  r.plate_number AS 车牌号,
  r.speed AS 时速,
  s.station_name AS 检测站点,
  r.pass_time AS 检测时间
FROM etc_records r
JOIN stations s ON r.station_id = s.id
WHERE r.speed > 120
  AND r.pass_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
ORDER BY r.speed DESC
LIMIT 100;`
  } else if (query.includes('套牌')) {
    generatedSql.value = `SELECT 
  a.plate_number AS 车牌号,
  a.station_name AS 位置A,
  a.pass_time AS 时间A,
  b.station_name AS 位置B,
  b.pass_time AS 时间B,
  TIMESTAMPDIFF(MINUTE, a.pass_time, b.pass_time) AS 间隔分钟
FROM etc_records_view a
JOIN etc_records_view b 
  ON a.plate_number = b.plate_number 
  AND a.id < b.id
  AND TIMESTAMPDIFF(MINUTE, a.pass_time, b.pass_time) < 10
  AND a.station_id != b.station_id
WHERE a.pass_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
ORDER BY a.pass_time DESC;`
  } else {
    generatedSql.value = `SELECT * FROM etc_records 
WHERE pass_time >= CURDATE()
ORDER BY pass_time DESC
LIMIT 100;`
  }
  
  text2sqlLoading.value = false
  editMode.value = false
}

// 执行 SQL
const executeSql = async () => {
  if (!generatedSql.value.trim()) return
  
  queryLoading.value = true
  const startTime = Date.now()
  
  await new Promise(resolve => setTimeout(resolve, 800))
  
  const result = generateMockData('traffic')
  tableColumns.value = result.columns
  queryResult.value = result.data
  totalCount.value = result.total
  queryTime.value = Date.now() - startTime
  
  addToHistory('sql', naturalLanguageQuery.value.substring(0, 30) + '...')
  
  queryLoading.value = false
}

// 生成模拟数据
const generateMockData = (type: string) => {
  switch (type) {
    case 'traffic':
      return {
        columns: [
          { prop: 'station', label: '站点名称', width: 160 },
          { prop: 'total', label: '总车流', width: 100, sortable: true },
          { prop: 'small', label: '小型车', width: 100 },
          { prop: 'large', label: '大型车', width: 100 },
          { prop: 'avgSpeed', label: '平均车速', width: 100 }
        ],
        data: [
          { station: '徐州东站', total: 12580, small: 9850, large: 2730, avgSpeed: '78 km/h' },
          { station: '铜山收费站', total: 8960, small: 7200, large: 1760, avgSpeed: '82 km/h' },
          { station: '贾汪收费站', total: 6540, small: 5100, large: 1440, avgSpeed: '75 km/h' },
          { station: '新沂收费站', total: 5230, small: 4200, large: 1030, avgSpeed: '80 km/h' },
          { station: '邳州收费站', total: 4890, small: 3900, large: 990, avgSpeed: '79 km/h' }
        ],
        total: 5
      }
    case 'revenue':
      return {
        columns: [
          { prop: 'station', label: '站点名称', width: 160 },
          { prop: 'revenue', label: '营收(元)', width: 120, sortable: true },
          { prop: 'count', label: '交易笔数', width: 100 },
          { prop: 'avgFee', label: '平均费用', width: 100 }
        ],
        data: [
          { station: '徐州东站', revenue: 156780, count: 4520, avgFee: '34.68' },
          { station: '铜山收费站', revenue: 98560, count: 2890, avgFee: '34.10' },
          { station: '贾汪收费站', revenue: 76540, count: 2250, avgFee: '34.02' },
          { station: '新沂收费站', revenue: 65890, count: 1960, avgFee: '33.62' },
          { station: '邳州收费站', revenue: 58760, count: 1780, avgFee: '33.01' }
        ],
        total: 5
      }
    case 'speed':
      return {
        columns: [
          { prop: 'plate', label: '车牌号', width: 120 },
          { prop: 'speed', label: '时速', width: 100, sortable: true },
          { prop: 'station', label: '检测站点', width: 140 },
          { prop: 'time', label: '检测时间', width: 160 },
          { prop: 'type', label: '车辆类型', width: 100 }
        ],
        data: [
          { plate: '苏C·A1234', speed: 158, station: '徐州东站', time: '2025-12-17 14:32', type: '小型车' },
          { plate: '苏C·B5678', speed: 152, station: '铜山收费站', time: '2025-12-17 13:28', type: '小型车' },
          { plate: '鲁D·C9012', speed: 145, station: '贾汪收费站', time: '2025-12-17 12:15', type: '小型车' },
          { plate: '苏C·D3456', speed: 138, station: '新沂收费站', time: '2025-12-17 11:42', type: '中型车' }
        ],
        total: 4
      }
    case 'clone':
      return {
        columns: [
          { prop: 'plate', label: '车牌号', width: 120 },
          { prop: 'stationA', label: '位置A', width: 130 },
          { prop: 'timeA', label: '时间A', width: 150 },
          { prop: 'stationB', label: '位置B', width: 130 },
          { prop: 'timeB', label: '时间B', width: 150 },
          { prop: 'interval', label: '间隔', width: 80 }
        ],
        data: [
          { plate: '苏C·X7890', stationA: '徐州东站', timeA: '14:28:32', stationB: '铜山收费站', timeB: '14:35:18', interval: '7分钟' },
          { plate: '鲁D·Y4567', stationA: '贾汪收费站', timeA: '13:15:45', stationB: '新沂收费站', timeB: '13:22:10', interval: '6分钟' }
        ],
        total: 2
      }
    default:
      return {
        columns: [
          { prop: 'plate', label: '车牌号', width: 120 },
          { prop: 'type', label: '违章类型', width: 100 },
          { prop: 'station', label: '检测站点', width: 140 },
          { prop: 'time', label: '时间', width: 160 }
        ],
        data: [
          { plate: '苏C·V1234', type: '超速', station: '徐州东站', time: '2025-12-17 14:20' },
          { plate: '鲁D·W5678', type: '占用应急车道', station: '铜山收费站', time: '2025-12-17 13:45' }
        ],
        total: 2
      }
  }
}

const getQueryDesc = () => {
  const typeMap: Record<string, string> = {
    traffic: '车流量统计',
    revenue: '营收统计',
    violation: '违章查询',
    speed: '超速记录',
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
  filters.stationId = ''
  filters.vehicleType = ''
  filters.plateNumber = ''
  filters.violationType = ''
  filters.speedThreshold = 120
  filters.groupBy = 'station'
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
</style>
