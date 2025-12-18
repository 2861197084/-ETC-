<template>
  <div class="owner-records-page">
    <!-- Tab 切换 -->
    <el-tabs v-model="activeTab" class="records-tabs">
      <!-- 通行记录 -->
      <el-tab-pane label="通行记录" name="records">
        <div class="filter-bar">
          <el-select v-model="filter.vehicleId" placeholder="选择车辆" clearable style="width: 140px">
            <el-option label="苏C·12345" value="1" />
            <el-option label="苏C·67890" value="2" />
          </el-select>
          <el-date-picker
            v-model="filter.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 240px"
          />
          <el-button type="primary" @click="searchRecords">查询</el-button>
        </div>
        <el-table :data="passRecords" stripe>
          <el-table-column prop="plateNumber" label="车牌号" width="120" />
          <el-table-column prop="entryStation" label="入口站" width="140" />
          <el-table-column prop="exitStation" label="出口站" width="140" />
          <el-table-column prop="entryTime" label="入站时间" width="160" />
          <el-table-column prop="exitTime" label="出站时间" width="160" />
          <el-table-column prop="fee" label="费用" width="100">
            <template #default="{ row }">
              <span class="fee-text">¥{{ row.fee }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default>
              <el-button type="primary" link size="small">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pagination-wrapper">
          <el-pagination
            :total="100"
            :page-size="10"
            layout="total, prev, pager, next"
          />
        </div>
      </el-tab-pane>

      <!-- 异常提醒 -->
      <el-tab-pane name="alerts">
        <template #label>
          <span>
            异常提醒
            <el-badge :value="3" type="danger" />
          </span>
        </template>
        <div class="alert-list">
          <div v-for="alert in alerts" :key="alert.id" class="alert-card" :class="alert.type">
            <div class="alert-header">
              <el-tag :type="getAlertTagType(alert.type)" size="small">
                {{ getAlertLabel(alert.type) }}
              </el-tag>
              <span class="alert-time">{{ alert.time }}</span>
            </div>
            <div class="alert-content">{{ alert.content }}</div>
            <div class="alert-actions" v-if="alert.type === 'clone'">
              <el-button type="primary" size="small">申诉</el-button>
              <el-button size="small">忽略</el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- 通行账单 -->
      <el-tab-pane label="通行账单" name="bills">
        <div class="bill-summary">
          <div class="summary-header">
            <el-date-picker v-model="billMonth" type="month" placeholder="选择月份" />
          </div>
          <div class="summary-cards">
            <div class="summary-card">
              <div class="card-value">¥{{ billSummary.total }}</div>
              <div class="card-label">本月总费用</div>
            </div>
            <div class="summary-card">
              <div class="card-value">{{ billSummary.trips }}</div>
              <div class="card-label">通行次数</div>
            </div>
            <div class="summary-card">
              <div class="card-value">{{ billSummary.distance }}km</div>
              <div class="card-label">总里程</div>
            </div>
          </div>
        </div>
        <el-table :data="billDetails" stripe>
          <el-table-column prop="date" label="日期" width="120" />
          <el-table-column prop="route" label="路线" />
          <el-table-column prop="distance" label="里程" width="100">
            <template #default="{ row }">{{ row.distance }}km</template>
          </el-table-column>
          <el-table-column prop="fee" label="费用" width="100">
            <template #default="{ row }">
              <span class="fee-text">¥{{ row.fee }}</span>
            </template>
          </el-table-column>
        </el-table>
        <div class="bill-actions">
          <el-button type="primary" :icon="Download">导出账单</el-button>
          <el-button :icon="Printer">打印</el-button>
        </div>
      </el-tab-pane>

      <!-- 智能查询 -->
      <el-tab-pane label="智能查询" name="query">
        <div class="smart-query">
          <div class="query-input-wrapper">
            <el-input
              v-model="queryText"
              type="textarea"
              :rows="3"
              placeholder="用自然语言描述您想查询的内容，例如：&#10;- 查询上个月所有超过50元的通行记录&#10;- 统计今年每月的通行费用&#10;- 找出最常走的路线"
            />
            <el-button type="primary" @click="handleSmartQuery" :loading="queryLoading">
              <el-icon><MagicStick /></el-icon>
              智能查询
            </el-button>
          </div>
          <div v-if="generatedSql" class="sql-preview">
            <div class="sql-header">
              <span>生成的查询语句</span>
              <el-button type="primary" link size="small" @click="editSql = !editSql">
                {{ editSql ? '取消编辑' : '编辑' }}
              </el-button>
            </div>
            <el-input
              v-if="editSql"
              v-model="generatedSql"
              type="textarea"
              :rows="3"
            />
            <pre v-else class="sql-code">{{ generatedSql }}</pre>
            <el-button type="primary" @click="executeQuery">执行查询</el-button>
          </div>
          <div v-if="queryResult.length > 0" class="query-result">
            <el-table :data="queryResult" stripe max-height="300">
              <el-table-column
                v-for="col in resultColumns"
                :key="col"
                :prop="col"
                :label="col"
              />
            </el-table>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { Download, Printer, MagicStick } from '@element-plus/icons-vue'

defineOptions({ name: 'OwnerRecords' })

const activeTab = ref('records')

// 筛选条件
const filter = reactive({
  vehicleId: '',
  dateRange: []
})

// 通行记录
const passRecords = ref([
  { plateNumber: '苏C·12345', entryStation: '徐州东站', exitStation: '铜山收费站', entryTime: '2025-12-17 14:28', exitTime: '2025-12-17 14:52', fee: 25 },
  { plateNumber: '苏C·12345', entryStation: '铜山收费站', exitStation: '徐州东站', entryTime: '2025-12-17 09:10', exitTime: '2025-12-17 09:35', fee: 25 },
  { plateNumber: '苏C·12345', entryStation: '北站卡口', exitStation: '南站卡口', entryTime: '2025-12-16 18:30', exitTime: '2025-12-16 18:55', fee: 15 }
])

// 异常提醒
const alerts = ref([
  { id: 1, type: 'clone', content: '您的车辆 苏C·12345 于 2025-12-16 14:32 在铜山卡口被检测，与您同时段的位置存在冲突，可能存在套牌风险，请核实。', time: '2小时前' },
  { id: 2, type: 'violation', content: '您的车辆 苏C·12345 于 2025-12-15 行驶速度超过限速20%，请注意安全驾驶。', time: '1天前' },
  { id: 3, type: 'notice', content: '您的 12 月账单已生成，本月通行费用共计 ¥356.50。', time: '2天前' }
])

// 账单相关
const billMonth = ref('')
const billSummary = reactive({
  total: 356.50,
  trips: 28,
  distance: 1280
})
const billDetails = ref([
  { date: '2025-12-17', route: '徐州东站 → 铜山收费站', distance: 28, fee: 25 },
  { date: '2025-12-17', route: '铜山收费站 → 徐州东站', distance: 28, fee: 25 },
  { date: '2025-12-16', route: '北站卡口 → 南站卡口', distance: 15, fee: 15 }
])

// 智能查询
const queryText = ref('')
const queryLoading = ref(false)
const generatedSql = ref('')
const editSql = ref(false)
const queryResult = ref<any[]>([])
const resultColumns = ref<string[]>([])

const searchRecords = () => {
  // TODO: 搜索记录
}

const getAlertTagType = (type: string) => {
  const types: Record<string, string> = {
    clone: 'danger',
    violation: 'warning',
    notice: 'info'
  }
  return types[type] || 'info'
}

const getAlertLabel = (type: string) => {
  const labels: Record<string, string> = {
    clone: '疑似套牌',
    violation: '违章提醒',
    notice: '系统通知'
  }
  return labels[type] || '通知'
}

const handleSmartQuery = async () => {
  if (!queryText.value.trim()) return
  
  queryLoading.value = true
  // 模拟 Text2SQL
  await new Promise(resolve => setTimeout(resolve, 1000))
  generatedSql.value = `SELECT date, route, fee FROM pass_records 
WHERE plate_number = '苏C·12345' 
  AND fee > 50 
  AND date >= DATE_SUB(NOW(), INTERVAL 1 MONTH)
ORDER BY date DESC;`
  queryLoading.value = false
}

const executeQuery = async () => {
  // 模拟查询结果
  resultColumns.value = ['date', 'route', 'fee']
  queryResult.value = [
    { date: '2025-12-10', route: '徐州东站 → 连云港', fee: 85 },
    { date: '2025-12-05', route: '徐州东站 → 南京', fee: 120 }
  ]
}
</script>

<style lang="scss" scoped>
.owner-records-page {
  padding: 20px;
  background: #f5f7fa;
  min-height: calc(100vh - 120px);

  .records-tabs {
    background: #fff;
    border-radius: 8px;
    padding: 16px;

    :deep(.el-tabs__header) {
      margin-bottom: 20px;
    }
  }
}

.filter-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.fee-text {
  color: #52c41a;
  font-weight: 500;
}

// 异常提醒
.alert-list {
  .alert-card {
    padding: 16px;
    border-radius: 8px;
    margin-bottom: 12px;
    border: 1px solid #f0f0f0;

    &.clone {
      border-left: 3px solid #ff4d4f;
      background: #fff2f0;
    }

    &.violation {
      border-left: 3px solid #faad14;
      background: #fffbe6;
    }

    &.notice {
      border-left: 3px solid #1890ff;
      background: #e6f7ff;
    }

    .alert-header {
      display: flex;
      justify-content: space-between;
      margin-bottom: 8px;

      .alert-time {
        font-size: 12px;
        color: #8c8c8c;
      }
    }

    .alert-content {
      font-size: 14px;
      color: #595959;
      line-height: 1.6;
    }

    .alert-actions {
      margin-top: 12px;
      display: flex;
      gap: 8px;
    }
  }
}

// 账单
.bill-summary {
  margin-bottom: 20px;

  .summary-header {
    margin-bottom: 16px;
  }

  .summary-cards {
    display: flex;
    gap: 16px;

    .summary-card {
      flex: 1;
      padding: 20px;
      background: linear-gradient(135deg, #e6f7ff 0%, #bae7ff 100%);
      border-radius: 8px;
      text-align: center;

      .card-value {
        font-size: 28px;
        font-weight: 700;
        color: #1890ff;
      }

      .card-label {
        font-size: 13px;
        color: #595959;
        margin-top: 4px;
      }
    }
  }
}

.bill-actions {
  margin-top: 16px;
  display: flex;
  gap: 8px;
}

// 智能查询
.smart-query {
  .query-input-wrapper {
    display: flex;
    gap: 12px;
    align-items: flex-start;
    margin-bottom: 20px;

    .el-textarea {
      flex: 1;
    }
  }

  .sql-preview {
    background: #fafafa;
    border-radius: 8px;
    padding: 16px;
    margin-bottom: 20px;

    .sql-header {
      display: flex;
      justify-content: space-between;
      margin-bottom: 12px;
      font-weight: 500;
    }

    .sql-code {
      background: #1f2329;
      color: #52c41a;
      padding: 12px;
      border-radius: 4px;
      font-family: 'Fira Code', monospace;
      font-size: 13px;
      margin-bottom: 12px;
      overflow-x: auto;
    }
  }

  .query-result {
    margin-top: 20px;
  }
}
</style>
