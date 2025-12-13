<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

// 占位数据 (后续使用)
const _loading = ref(false)
void _loading // 防止未使用警告
</script>

<template>
  <div class="query-page">
    <nav class="page-nav">
      <button class="nav-back" @click="router.push('/')">← 返回首页</button>
      <h1>交互式查询</h1>
      <router-link to="/dashboard" class="nav-link">数据大屏 →</router-link>
    </nav>
    
    <div class="query-container">
      <div class="query-filters card">
        <h2 class="card-title"><span>查询条件</span></h2>
        <p class="placeholder-text">查询功能开发中...</p>
      </div>
      
      <div class="query-results card">
        <h2 class="card-title"><span>查询结果</span></h2>
        <div class="empty-state">
          <p>请设置查询条件后点击查询</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.query-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #0a1628 0%, #061224 100%);
  padding: 80px 40px 40px;
}

.page-nav {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
  
  h1 {
    font-size: 24px;
    color: #00d4ff;
  }
  
  .nav-back, .nav-link {
    color: rgba(255, 255, 255, 0.7);
    background: none;
    border: none;
    font-size: 14px;
    cursor: pointer;
    transition: color 0.3s;
    
    &:hover {
      color: #00d4ff;
    }
  }
}

.query-container {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: 20px;
  max-width: 1600px;
  margin: 0 auto;
}

.placeholder-text {
  color: rgba(255, 255, 255, 0.5);
  font-size: 14px;
}

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  color: rgba(255, 255, 255, 0.4);
}
</style>

