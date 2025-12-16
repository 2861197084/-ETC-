<template>
  <div class="sql-editor">
    <div class="editor-header">
      <span class="editor-title">SQL 查询语句</span>
      <div class="editor-actions">
        <el-button type="primary" :icon="CaretRight" @click="executeQuery" :loading="loading">
          执行查询
        </el-button>
        <el-button :icon="Delete" @click="clearSql">清空</el-button>
      </div>
    </div>
    <div class="editor-content">
      <Codemirror
        v-model="sqlContent"
        :style="{ height: editorHeight }"
        :extensions="extensions"
        :autofocus="true"
        placeholder="请输入 SQL 查询语句..."
        @ready="handleReady"
      />
    </div>
    <div class="quick-queries">
      <span class="quick-label">快捷查询：</span>
      <el-tag
        v-for="query in quickQueries"
        :key="query.label"
        type="info"
        effect="plain"
        class="quick-tag"
        @click="applyQuickQuery(query.sql)"
      >
        {{ query.label }}
      </el-tag>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, shallowRef } from 'vue'
import { Codemirror } from 'vue-codemirror'
import { sql } from '@codemirror/lang-sql'
import { oneDark } from '@codemirror/theme-one-dark'
import { CaretRight, Delete } from '@element-plus/icons-vue'

defineOptions({ name: 'SqlEditor' })

interface Props {
  modelValue?: string
  height?: string
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  height: '200px',
  loading: false
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'execute', sql: string): void
}>()

const sqlContent = ref(props.modelValue)
const editorHeight = ref(props.height)
const editorView = shallowRef()

// CodeMirror 扩展
const extensions = [sql(), oneDark]

// 预设快捷查询
const quickQueries = [
  {
    label: '营收 Top10 站点',
    sql: `SELECT station_name, SUM(fee) as total_revenue
FROM etc_transactions
WHERE DATE(create_time) = CURDATE()
GROUP BY station_id, station_name
ORDER BY total_revenue DESC
LIMIT 10;`
  },
  {
    label: '早高峰流量统计',
    sql: `SELECT station_name, COUNT(*) as vehicle_count
FROM etc_transactions
WHERE DATE(create_time) = CURDATE()
  AND HOUR(create_time) BETWEEN 7 AND 9
GROUP BY station_id, station_name
ORDER BY vehicle_count DESC;`
  },
  {
    label: '今日超速车辆',
    sql: `SELECT plate_number, speed, station_name, create_time
FROM etc_transactions
WHERE DATE(create_time) = CURDATE()
  AND speed > 120
ORDER BY speed DESC
LIMIT 50;`
  },
  {
    label: '外地车辆占比',
    sql: `SELECT 
  CASE WHEN is_local = 1 THEN '本地车辆' ELSE '外地车辆' END as category,
  COUNT(*) as count,
  ROUND(COUNT(*) * 100.0 / SUM(COUNT(*)) OVER(), 2) as percentage
FROM etc_transactions
WHERE DATE(create_time) = CURDATE()
GROUP BY is_local;`
  }
]

const handleReady = (payload: { view: any }) => {
  editorView.value = payload.view
}

const executeQuery = () => {
  emit('execute', sqlContent.value)
}

const clearSql = () => {
  sqlContent.value = ''
  emit('update:modelValue', '')
}

const applyQuickQuery = (querySql: string) => {
  sqlContent.value = querySql
  emit('update:modelValue', querySql)
}
</script>

<style lang="scss" scoped>
.sql-editor {
  background: var(--el-bg-color);
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid var(--el-border-color-light);

  .editor-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 16px;
    background: var(--el-fill-color-light);
    border-bottom: 1px solid var(--el-border-color-light);

    .editor-title {
      font-weight: 600;
      color: var(--el-text-color-primary);
    }

    .editor-actions {
      display: flex;
      gap: 8px;
    }
  }

  .editor-content {
    :deep(.cm-editor) {
      font-size: 14px;
      font-family: 'Fira Code', 'Consolas', monospace;

      .cm-scroller {
        overflow: auto;
      }

      .cm-gutters {
        background: #21252b;
        border-right: 1px solid #3e4451;
      }
    }
  }

  .quick-queries {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
    padding: 12px 16px;
    background: var(--el-fill-color-lighter);
    border-top: 1px solid var(--el-border-color-light);

    .quick-label {
      color: var(--el-text-color-secondary);
      font-size: 13px;
    }

    .quick-tag {
      cursor: pointer;
      transition: all 0.3s;

      &:hover {
        background: var(--el-color-primary-light-9);
        border-color: var(--el-color-primary);
        color: var(--el-color-primary);
      }
    }
  }
}
</style>
