<template>
  <div class="progressive-records-page">
    <!-- 搜索表单 -->
    <el-card class="search-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>渐进式通行记录查询</span>
          <el-tag type="info" size="small">MySQL + HBase</el-tag>
        </div>
      </template>
      
      <el-form :model="searchForm" label-width="80px" inline>
        <el-form-item label="车牌号">
          <el-input
            v-model="searchForm.plateNumber"
            placeholder="输入车牌号"
            clearable
            style="width: 160px"
          />
        </el-form-item>
        <el-form-item label="卡口">
          <el-select
            v-model="searchForm.checkpointId"
            placeholder="选择卡口"
            clearable
            style="width: 180px"
          >
            <el-option
              v-for="item in checkpointOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="searchForm.dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            style="width: 360px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch" :loading="loading">
            查询
          </el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 渐进式加载表格 -->
    <el-card class="table-card" shadow="never">
      <ProgressiveTable
        ref="progressiveTableRef"
        :result="queryResult"
        :loading="loading"
        show-source-badge
        show-stats
        @load-more="handleLoadMore"
      >
        <!-- MySQL 数据表格 -->
        <template #default="{ data, loading: tableLoading }">
          <el-table
            :data="data"
            v-loading="tableLoading"
            stripe
            border
            style="width: 100%"
          >
            <el-table-column prop="plateNumber" label="车牌号" width="120" fixed />
            <el-table-column prop="checkpointName" label="卡口" width="150" show-overflow-tooltip />
            <el-table-column prop="passTime" label="通行时间" width="180" />
            <el-table-column prop="direction" label="方向" width="80" />
            <el-table-column label="速度" width="100">
              <template #default="{ row }">
                {{ row.speed ? `${row.speed} km/h` : '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="laneNo" label="车道" width="60" />
            <el-table-column prop="vehicleType" label="车型" width="80" />
            <el-table-column label="ETC扣费" width="100">
              <template #default="{ row }">
                {{ row.etcDeduction ? `¥${row.etcDeduction}` : '-' }}
              </template>
            </el-table-column>
            <el-table-column label="来源" width="80">
              <template #default="{ row }">
                <el-tag :type="row.source === 'mysql' ? 'primary' : 'warning'" size="small">
                  {{ row.source === 'mysql' ? '近期' : '历史' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </template>

        <!-- HBase 历史数据表格 -->
        <template #hbase="{ data }">
          <el-table
            :data="data"
            stripe
            border
            style="width: 100%"
            class="hbase-table"
          >
            <el-table-column prop="plateNumber" label="车牌号" width="120" />
            <el-table-column prop="checkpointName" label="卡口" width="150" show-overflow-tooltip />
            <el-table-column prop="passTime" label="通行时间" width="180" />
            <el-table-column prop="direction" label="方向" width="80" />
            <el-table-column label="速度" width="100">
              <template #default="{ row }">
                {{ row.speed ? `${row.speed} km/h` : '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="laneNo" label="车道" width="60" />
            <el-table-column prop="vehicleType" label="车型" width="80" />
            <el-table-column label="ETC扣费" width="100">
              <template #default="{ row }">
                {{ row.etcDeduction ? `¥${row.etcDeduction}` : '-' }}
              </template>
            </el-table-column>
            <el-table-column label="来源" width="80">
              <template #default>
                <el-tag type="warning" size="small">历史</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </ProgressiveTable>

      <!-- 分页 -->
      <div v-if="queryResult && queryResult.source === 'mysql'" class="pagination-container">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="queryResult.mysqlTotal || 0"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handlePageChange"
          @size-change="handlePageSizeChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import ProgressiveTable from '@/components/business/etc/ProgressiveTable.vue'
import { queryRecords, type ProgressiveLoadResult, type PassRecordItem } from '@/api/admin/progressive'
import { getCheckpointOptions } from '@/api/admin/query'

// 搜索表单
const searchForm = reactive({
  plateNumber: '',
  checkpointId: '' as string,
  dateRange: null as [Date, Date] | null
})

// 状态
const loading = ref(false)
const queryResult = ref<ProgressiveLoadResult<PassRecordItem> | null>(null)
const checkpointOptions = ref<{ label: string; value: string }[]>([])
const currentPage = ref(1)
const pageSize = ref(20)
const progressiveTableRef = ref<InstanceType<typeof ProgressiveTable<PassRecordItem>> | null>(null)

// 加载卡口选项
async function loadCheckpointOptions() {
  try {
    const res = await getCheckpointOptions()
    checkpointOptions.value = res.data || []
  } catch (error) {
    console.error('加载卡口选项失败:', error)
  }
}

// 查询
async function handleSearch() {
  loading.value = true
  currentPage.value = 1
  progressiveTableRef.value?.resetHBaseData()

  try {
    const params: Record<string, unknown> = {
      page: currentPage.value,
      size: pageSize.value,
      source: 'mysql'
    }

    if (searchForm.plateNumber) {
      params.plateNumber = searchForm.plateNumber
    }
    if (searchForm.checkpointId) {
      params.checkpointId = searchForm.checkpointId
    }
    if (searchForm.dateRange) {
      params.startTime = searchForm.dateRange[0].toISOString()
      params.endTime = searchForm.dateRange[1].toISOString()
    }

    const res = await queryRecords(params as Parameters<typeof queryRecords>[0])
    queryResult.value = res.data
  } catch (error) {
    console.error('查询失败:', error)
  } finally {
    loading.value = false
  }
}

// 加载更多历史数据
async function handleLoadMore(params: { source: 'hbase'; lastRowKey?: string }) {
  progressiveTableRef.value?.setLoadingMore(true)

  try {
    const queryParams: Record<string, unknown> = {
      source: 'hbase',
      lastRowKey: params.lastRowKey,
      size: pageSize.value
    }

    if (searchForm.plateNumber) {
      queryParams.plateNumber = searchForm.plateNumber
    }
    if (searchForm.checkpointId) {
      queryParams.checkpointId = searchForm.checkpointId
    }
    if (searchForm.dateRange) {
      queryParams.startTime = searchForm.dateRange[0].toISOString()
      queryParams.endTime = searchForm.dateRange[1].toISOString()
    }

    const res = await queryRecords(queryParams as Parameters<typeof queryRecords>[0])
    
    if (res.data && res.data.list) {
      progressiveTableRef.value?.appendHBaseData(res.data.list, res.data.nextRowKey)
    }
  } catch (error) {
    console.error('加载历史数据失败:', error)
    progressiveTableRef.value?.setLoadingMore(false)
  }
}

// 重置
function handleReset() {
  searchForm.plateNumber = ''
  searchForm.checkpointId = ''
  searchForm.dateRange = null
  queryResult.value = null
  progressiveTableRef.value?.resetHBaseData()
}

// 分页变化
function handlePageChange(page: number) {
  currentPage.value = page
  handleSearch()
}

function handlePageSizeChange(size: number) {
  pageSize.value = size
  currentPage.value = 1
  handleSearch()
}

// 初始化
onMounted(() => {
  loadCheckpointOptions()
})
</script>

<style scoped lang="scss">
.progressive-records-page {
  padding: 16px;

  .search-card {
    margin-bottom: 16px;

    .card-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
  }

  .table-card {
    :deep(.el-card__body) {
      padding: 16px;
    }
  }

  .pagination-container {
    display: flex;
    justify-content: flex-end;
    margin-top: 16px;
    padding-top: 16px;
    border-top: 1px solid #f0f0f0;
  }

  .hbase-table {
    :deep(.el-table__body-wrapper) {
      background: #fffef5;
    }
  }
}
</style>
