<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="predict-page">
    <nav class="page-nav">
      <button class="nav-back" @click="router.push('/')">← 返回首页</button>
      <h1>离线预测与建议</h1>
      <router-link to="/dashboard" class="nav-link">数据大屏 →</router-link>
    </nav>
    
    <div class="predict-container">
      <div class="predict-header card">
        <div class="weather-info">
          <div class="weather-icon">☀️</div>
          <div class="weather-detail">
            <span class="city">广东 - 深圳市</span>
            <span class="temp">17°C</span>
            <span class="desc">多云转多云</span>
          </div>
        </div>
        <div class="page-title">
          <h2>离线预测与建议</h2>
          <span class="update-time">2024-01-03 14:58:24</span>
        </div>
      </div>
      
      <div class="predict-grid">
        <div class="card flow-table">
          <h3 class="card-title"><span>当日车流量数据</span></h3>
          <p class="placeholder-text">数据加载中...</p>
        </div>
        
        <div class="card suggestions">
          <h3 class="card-title"><span>治理建议</span></h3>
          <div class="suggestion-item">
            <p>罗田主线站车流量较高，建议开放更多卡口。</p>
          </div>
        </div>
        
        <div class="card history-chart">
          <h3 class="card-title"><span>前九分钟车流量</span></h3>
          <p class="placeholder-text">图表开发中...</p>
        </div>
        
        <div class="card predict-chart">
          <h3 class="card-title"><span>下一分钟车流量预测</span></h3>
          <p class="placeholder-text">预测模型对接中...</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.predict-page {
  min-height: 100vh;
  background: #f5f7fa;
  padding: 80px 40px 40px;
}

.page-nav {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
  
  h1 {
    font-size: 24px;
    color: #00a8cc;
  }
  
  .nav-back, .nav-link {
    color: #666;
    background: none;
    border: none;
    font-size: 14px;
    cursor: pointer;
    transition: color 0.3s;
    
    &:hover {
      color: #00a8cc;
    }
  }
}

.predict-container {
  max-width: 1400px;
  margin: 0 auto;
}

.predict-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: white;
  padding: 20px 30px;
  border-radius: 12px;
  margin-bottom: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.weather-info {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 15px 20px;
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
  border-radius: 10px;
  color: white;
  
  .weather-icon {
    font-size: 40px;
  }
  
  .weather-detail {
    display: flex;
    flex-direction: column;
    
    .city {
      font-size: 14px;
      opacity: 0.9;
    }
    
    .temp {
      font-size: 28px;
      font-weight: bold;
    }
    
    .desc {
      font-size: 12px;
      opacity: 0.8;
    }
  }
}

.page-title {
  text-align: right;
  
  h2 {
    font-size: 28px;
    color: #00a8cc;
    margin-bottom: 8px;
  }
  
  .update-time {
    font-size: 14px;
    color: #999;
  }
}

.predict-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

.card {
  background: white;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  
  .card-title {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 15px;
    padding-bottom: 10px;
    border-bottom: 1px solid #eee;
    
    span {
      font-size: 16px;
      font-weight: 600;
      color: #333;
    }
  }
}

.placeholder-text {
  color: #999;
  font-size: 14px;
  padding: 40px 0;
  text-align: center;
}

.suggestion-item {
  padding: 20px;
  background: #f8f9fa;
  border-radius: 8px;
  border-left: 4px solid #00a8cc;
  
  p {
    color: #333;
    font-size: 15px;
    line-height: 1.6;
  }
}
</style>

