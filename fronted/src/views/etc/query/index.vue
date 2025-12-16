<template>
  <div class="query-page">
    <div class="page-header">
      <h2 class="page-title">数据查询中心</h2>
      <p class="page-desc">支持自定义 SQL 查询，快速分析 ETC 交易数据</p>
    </div>

    <!-- SQL 编辑器 -->
    <SqlEditor
      v-model="sqlQuery"
      height="180px"
      :loading="queryLoading"
      @execute="handleExecuteQuery"
    />

    <!-- 查询结果 -->
    <div class="query-result">
      <div class="result-header">
        <div class="result-info">
          <span v-if="queryResult.length > 0" class="result-count">
            共 {{ totalCount }} 条记录，当前显示 {{ queryResult.length }} 条
          </span>
          <span v-if="queryTime" class="query-time">
            查询耗时: {{ queryTime }}ms
          </span>
        </div>
        <div class="result-actions">
          <el-button :icon="Download" @click="exportData" :disabled="queryResult.length === 0">
            导出 Excel
          </el-button>
          <el-button :icon="Refresh" @click="refreshQuery" :disabled="!sqlQuery">
            刷新
          </el-button>
        </div>
      </div>

      <!-- 结果表格 -->
      <el-table
        v-loading="queryLoading"
        :data="queryResult"
        stripe
        border
        max-height="500"
        class="result-table"
        empty-text="暂无数据，请执行查询"
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

      <!-- 分页 -->
      <div class="pagination-wrapper" v-if="totalCount > 0">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[20, 50, 100, 200]"
          :total="totalCount"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handlePageSizeChange"
          @current-change="handlePageChange"
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
        <el-button text type="danger" size="small" @click="clearHistory">
          清空历史
        </el-button>
      </div>
      <div class="history-list">
        <div
          v-for="(item, index) in queryHistory"
          :key="index"
          class="history-item"
          @click="useHistoryQuery(item)"
        >
          <span class="history-sql">{{ item.sql.substring(0, 60) }}...</span>
          <span class="history-time">{{ item.time }}</span>
        </div>
        <el-empty v-if="queryHistory.length === 0" description="暂无查询历史" :image-size="60" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { Download, Refresh, Clock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { SqlEditor } from '@/components/business/etc'

defineOptions({ name: 'EtcQuery' })

interface TableColumn {
  prop: string
  label: string
  width?: number
  sortable?: boolean
}

interface QueryHistoryItem {
  sql: string
  time: string
}

const sqlQuery = ref('')
const queryLoading = ref(false)
const queryTime = ref<number | null>(null)
const queryResult = ref<any[]>([])
const tableColumns = ref<TableColumn[]>([])
const currentPage = ref(1)
const pageSize = ref(20)
const totalCount = ref(0)

const queryHistory = ref<QueryHistoryItem[]>([
  {
    sql: 'SELECT station_name, SUM(fee) as total_revenue FROM etc_transactions WHERE DATE(create_time) = CURDATE() GROUP BY station_id ORDER BY total_revenue DESC LIMIT 10;',
    time: '10:32:15'
  },
  {
    sql: 'SELECT plate_number, speed, station_name FROM etc_transactions WHERE speed > 120 ORDER BY speed DESC LIMIT 50;',
    time: '09:45:22'
  }
])

// 模拟查询执行
const handleExecuteQuery = async (sql: string) => {
  if (!sql.trim()) {
    ElMessage.warning('请输入 SQL 查询语句')
    return
  }

  queryLoading.value = true
  const startTime = Date.now()

  try {
    // 模拟网络请求延迟
    await new Promise((resolve) => setTimeout(resolve, 800 + Math.random() * 500))

    // 根据 SQL 生成模拟数据
    const mockData = generateMockData(sql)
    queryResult.value = mockData.data
    tableColumns.value = mockData.columns
    totalCount.value = mockData.total
    queryTime.value = Date.now() - startTime

    // 添加到历史记录
    addToHistory(sql)

    ElMessage.success(`查询成功，共 ${mockData.total} 条记录`)
  } catch (error) {
    ElMessage.error('查询执行失败')
  } finally {
    queryLoading.value = false
  }
}

// 生成模拟数据
const generateMockData = (sql: string) => {
  const sqlLower = sql.toLowerCase()

  // 营收查询
  if (sqlLower.includes('revenue') || sqlLower.includes('fee')) {
    return {
      columns: [
        { prop: 'rank', label: '排名', width: 80 },
        { prop: 'station_name', label: '站点名称', width: 200 },
        { prop: 'total_revenue', label: '总营收(元)', width: 150, sortable: true },
        { prop: 'transaction_count', label: '交易笔数', width: 120, sortable: true },
        { prop: 'avg_fee', label: '平均费用(元)', width: 120 }
      ],
      data: [
        { rank: 1, station_name: '京沪高速入口', total_revenue: 156789, transaction_count: 4523, avg_fee: 34.67 },
        { rank: 2, station_name: '京承高速入口', total_revenue: 143256, transaction_count: 4102, avg_fee: 34.92 },
        { rank: 3, station_name: '京藏高速入口', total_revenue: 138765, transaction_count: 3987, avg_fee: 34.80 },
        { rank: 4, station_name: '京港澳高速入口', total_revenue: 125432, transaction_count: 3654, avg_fee: 34.33 },
        { rank: 5, station_name: '京哈高速入口', total_revenue: 118976, transaction_count: 3421, avg_fee: 34.78 },
        { rank: 6, station_name: '机场高速入口', total_revenue: 108543, transaction_count: 3156, avg_fee: 34.39 },
        { rank: 7, station_name: '京开高速入口', total_revenue: 98765, transaction_count: 2876, avg_fee: 34.34 },
        { rank: 8, station_name: '京津高速入口', total_revenue: 89234, transaction_count: 2598, avg_fee: 34.35 },
        { rank: 9, station_name: '六环高速入口', total_revenue: 78654, transaction_count: 2287, avg_fee: 34.39 },
        { rank: 10, station_name: '五环高速入口', total_revenue: 67890, transaction_count: 1976, avg_fee: 34.36 }
      ],
      total: 10
    }
  }

  // 超速查询
  if (sqlLower.includes('speed') && sqlLower.includes('>')) {
    return {
      columns: [
        { prop: 'plate_number', label: '车牌号', width: 120 },
        { prop: 'speed', label: '时速(km/h)', width: 120, sortable: true },
        { prop: 'station_name', label: '检测站点', width: 180 },
        { prop: 'create_time', label: '检测时间', width: 180 },
        { prop: 'vehicle_type', label: '车辆类型', width: 100 }
      ],
      data: Array.from({ length: 20 }, (_, i) => ({
        plate_number: `京${'ABCDEFG'[Math.floor(Math.random() * 7)]}·${String(Math.floor(Math.random() * 90000) + 10000)}`,
        speed: Math.floor(Math.random() * 40) + 121,
        station_name: ['京沪高速', '京承高速', '京藏高速', '京港澳高速'][Math.floor(Math.random() * 4)] + '检测点',
        create_time: `2025-12-14 ${String(Math.floor(Math.random() * 24)).padStart(2, '0')}:${String(Math.floor(Math.random() * 60)).padStart(2, '0')}:${String(Math.floor(Math.random() * 60)).padStart(2, '0')}`,
        vehicle_type: ['小型车', '中型车', '大型车'][Math.floor(Math.random() * 3)]
      })).sort((a, b) => b.speed - a.speed),
      total: 156
    }
  }

  // 默认：车流量查询
  return {
    columns: [
      { prop: 'station_name', label: '站点名称', width: 200 },
      { prop: 'vehicle_count', label: '车辆数量', width: 120, sortable: true },
      { prop: 'local_count', label: '本地车辆', width: 120 },
      { prop: 'foreign_count', label: '外地车辆', width: 120 },
      { prop: 'avg_speed', label: '平均车速', width: 100 },
      { prop: 'update_time', label: '更新时间', width: 180 }
    ],
    data: Array.from({ length: 15 }, (_, i) => {
      const localCount = Math.floor(Math.random() * 3000) + 1000
      const foreignCount = Math.floor(Math.random() * 1500) + 500
      return {
        station_name: `收费站${i + 1}`,
        vehicle_count: localCount + foreignCount,
        local_count: localCount,
        foreign_count: foreignCount,
        avg_speed: Math.floor(Math.random() * 30) + 70,
        update_time: '2025-12-14 14:30:00'
      }
    }),
    total: 162
  }
}

// 添加到历史记录
const addToHistory = (sql: string) => {
  const now = new Date()
  const time = now.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })

  // 检查是否已存在相同的查询
  const existingIndex = queryHistory.value.findIndex((item) => item.sql === sql)
  if (existingIndex > -1) {
    queryHistory.value.splice(existingIndex, 1)
  }

  queryHistory.value.unshift({ sql, time })

  // 最多保留 10 条
  if (queryHistory.value.length > 10) {
    queryHistory.value.pop()
  }
}

// 使用历史查询
const useHistoryQuery = (item: QueryHistoryItem) => {
  sqlQuery.value = item.sql
}

// 清空历史
const clearHistory = () => {
  queryHistory.value = []
}

// 刷新查询
const refreshQuery = () => {
  if (sqlQuery.value) {
    handleExecuteQuery(sqlQuery.value)
  }
}

// 导出数据
const exportData = () => {
  ElMessage.success('数据导出成功')
  // TODO: 实现真正的导出逻辑
}

// 分页处理
const handlePageChange = (page: number) => {
  currentPage.value = page
  // TODO: 重新查询
}

const handlePageSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  // TODO: 重新查询
}
</script>

<style lang="scss" scoped>
.query-page {
  padding: 24px;

  .page-header {
    margin-bottom: 24px;

    .page-title {
      margin: 0 0 8px;
      font-size: 22px;
      font-weight: 600;
      color: var(--el-text-color-primary);
    }

    .page-desc {
      margin: 0;
      font-size: 14px;
      color: var(--el-text-color-secondary);
    }
  }
}

.query-result {
  margin-top: 24px;
  background: var(--el-bg-color);
  border-radius: 8px;
  border: 1px solid var(--el-border-color-light);
  overflow: hidden;

  .result-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 16px;
    background: var(--el-fill-color-light);
    border-bottom: 1px solid var(--el-border-color-light);

    .result-info {
      display: flex;
      align-items: center;
      gap: 16px;

      .result-count {
        color: var(--el-text-color-primary);
        font-size: 14px;
      }

      .query-time {
        color: var(--el-text-color-secondary);
        font-size: 13px;
      }
    }

    .result-actions {
      display: flex;
      gap: 8px;
    }
  }

  .result-table {
    width: 100%;
  }

  .pagination-wrapper {
    display: flex;
    justify-content: flex-end;
    padding: 16px;
    border-top: 1px solid var(--el-border-color-light);
  }
}

.query-history {
  margin-top: 24px;
  background: var(--el-bg-color);
  border-radius: 8px;
  border: 1px solid var(--el-border-color-light);
  overflow: hidden;

  .history-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 16px;
    background: var(--el-fill-color-light);
    border-bottom: 1px solid var(--el-border-color-light);

    .history-title {
      display: flex;
      align-items: center;
      gap: 8px;
      font-weight: 600;
      color: var(--el-text-color-primary);
    }
  }

  .history-list {
    padding: 8px;
    max-height: 200px;
    overflow-y: auto;

    .history-item {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 10px 12px;
      border-radius: 6px;
      cursor: pointer;
      transition: background 0.3s;

      &:hover {
        background: var(--el-fill-color-light);
      }

      .history-sql {
        flex: 1;
        font-size: 13px;
        color: var(--el-text-color-primary);
        font-family: 'Fira Code', 'Consolas', monospace;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .history-time {
        margin-left: 16px;
        font-size: 12px;
        color: var(--el-text-color-secondary);
      }
    }
  }
}
</style>
