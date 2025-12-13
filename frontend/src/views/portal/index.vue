<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

const features = ref([
  {
    icon: '📊',
    title: '实时数据大屏',
    desc: '30秒自动刷新，全网态势一目了然',
    path: '/dashboard'
  },
  {
    icon: '🔍',
    title: '交互式查询',
    desc: '多条件组合，灵活检索历史数据',
    path: '/query'
  },
  {
    icon: '📈',
    title: '离线预测分析',
    desc: 'AI驱动的车流预测与治理建议',
    path: '/predict'
  },
])

const navigateTo = (path: string) => {
  router.push(path)
}
</script>

<template>
  <div class="portal-page">
    <!-- 背景视频/图片区域 -->
    <div class="hero-section">
      <div class="hero-overlay"></div>
      <div class="hero-content">
        <h1 class="hero-title">
          <span class="text-gradient">ETC</span>
          <span>BIGDATA PLATFORM</span>
        </h1>
        <p class="hero-subtitle">
          高速公路大数据管理平台 · 实时监控 · 智能预警 · 数据驱动
        </p>
        <div class="hero-actions">
          <button class="btn-primary" @click="navigateTo('/dashboard')">
            进入数据大屏
          </button>
          <button class="btn-outline" @click="navigateTo('/query')">
            开始查询
          </button>
        </div>
      </div>
    </div>

    <!-- 导航栏 -->
    <nav class="nav-bar">
      <div class="nav-logo">
        <span class="logo-icon">🚗</span>
        <span class="logo-text">ETC Platform</span>
      </div>
      <div class="nav-links">
        <router-link to="/dashboard">数据大屏</router-link>
        <router-link to="/query">交互式查询</router-link>
        <router-link to="/predict">离线分析</router-link>
      </div>
    </nav>

    <!-- 功能卡片 -->
    <section class="features-section">
      <div class="features-grid">
        <div 
          v-for="(feature, index) in features" 
          :key="index"
          class="feature-card"
          @click="navigateTo(feature.path)"
        >
          <div class="feature-icon">{{ feature.icon }}</div>
          <h3 class="feature-title">{{ feature.title }}</h3>
          <p class="feature-desc">{{ feature.desc }}</p>
          <div class="feature-arrow">→</div>
        </div>
      </div>
    </section>

    <!-- 底部信息 -->
    <footer class="portal-footer">
      <div class="footer-stats">
        <div class="stat-item">
          <span class="stat-value">50+</span>
          <span class="stat-label">数据/秒</span>
        </div>
        <div class="stat-item">
          <span class="stat-value">30s</span>
          <span class="stat-label">刷新周期</span>
        </div>
        <div class="stat-item">
          <span class="stat-value">24/7</span>
          <span class="stat-label">实时监控</span>
        </div>
      </div>
      <p class="footer-text">ETC 高速公路大数据管理平台 © 2024</p>
    </footer>
  </div>
</template>

<style lang="scss" scoped>
.portal-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #0a1628 0%, #061224 100%);
  position: relative;
}

.nav-bar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 40px;
  background: rgba(10, 22, 40, 0.8);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid rgba(0, 212, 255, 0.1);
}

.nav-logo {
  display: flex;
  align-items: center;
  gap: 12px;
  
  .logo-icon {
    font-size: 28px;
  }
  
  .logo-text {
    font-size: 20px;
    font-weight: 700;
    color: #00d4ff;
    letter-spacing: 2px;
  }
}

.nav-links {
  display: flex;
  gap: 32px;
  
  a {
    color: rgba(255, 255, 255, 0.7);
    font-size: 15px;
    font-weight: 500;
    transition: color 0.3s;
    
    &:hover, &.router-link-active {
      color: #00d4ff;
    }
  }
}

.hero-section {
  height: 70vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  background: url('https://images.unsplash.com/photo-1544620347-c4fd4a3d5957?w=1920') center/cover;
}

.hero-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    180deg,
    rgba(10, 22, 40, 0.85) 0%,
    rgba(10, 22, 40, 0.95) 100%
  );
}

.hero-content {
  position: relative;
  z-index: 1;
  text-align: center;
  max-width: 800px;
  padding: 0 20px;
}

.hero-title {
  font-size: clamp(36px, 8vw, 72px);
  font-weight: 800;
  line-height: 1.1;
  margin-bottom: 24px;
  
  .text-gradient {
    background: linear-gradient(90deg, #00d4ff, #00ff88);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
  }
  
  span:last-child {
    display: block;
    color: white;
    font-size: 0.6em;
    letter-spacing: 8px;
    margin-top: 8px;
  }
}

.hero-subtitle {
  font-size: 18px;
  color: rgba(255, 255, 255, 0.7);
  margin-bottom: 40px;
  letter-spacing: 2px;
}

.hero-actions {
  display: flex;
  gap: 20px;
  justify-content: center;
  flex-wrap: wrap;
}

.btn-primary {
  padding: 16px 40px;
  background: linear-gradient(90deg, #00d4ff, #00a8cc);
  border: none;
  border-radius: 8px;
  color: white;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
  
  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 10px 30px rgba(0, 212, 255, 0.3);
  }
}

.btn-outline {
  padding: 16px 40px;
  background: transparent;
  border: 2px solid rgba(0, 212, 255, 0.5);
  border-radius: 8px;
  color: #00d4ff;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
  
  &:hover {
    background: rgba(0, 212, 255, 0.1);
    border-color: #00d4ff;
  }
}

.features-section {
  padding: 80px 40px;
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 30px;
  max-width: 1200px;
  margin: 0 auto;
}

.feature-card {
  background: rgba(6, 30, 61, 0.6);
  border: 1px solid rgba(0, 212, 255, 0.2);
  border-radius: 16px;
  padding: 40px 30px;
  cursor: pointer;
  transition: all 0.3s;
  position: relative;
  overflow: hidden;
  
  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 3px;
    background: linear-gradient(90deg, #00d4ff, #00ff88);
    opacity: 0;
    transition: opacity 0.3s;
  }
  
  &:hover {
    transform: translateY(-5px);
    border-color: rgba(0, 212, 255, 0.5);
    box-shadow: 0 20px 40px rgba(0, 0, 0, 0.3);
    
    &::before {
      opacity: 1;
    }
    
    .feature-arrow {
      transform: translateX(5px);
      color: #00d4ff;
    }
  }
}

.feature-icon {
  font-size: 48px;
  margin-bottom: 20px;
}

.feature-title {
  font-size: 22px;
  font-weight: 600;
  color: white;
  margin-bottom: 12px;
}

.feature-desc {
  font-size: 15px;
  color: rgba(255, 255, 255, 0.6);
  line-height: 1.6;
  margin-bottom: 20px;
}

.feature-arrow {
  font-size: 24px;
  color: rgba(255, 255, 255, 0.3);
  transition: all 0.3s;
}

.portal-footer {
  padding: 60px 40px;
  text-align: center;
  border-top: 1px solid rgba(0, 212, 255, 0.1);
}

.footer-stats {
  display: flex;
  justify-content: center;
  gap: 80px;
  margin-bottom: 40px;
  flex-wrap: wrap;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  
  .stat-value {
    font-family: 'DIN Alternate', sans-serif;
    font-size: 48px;
    font-weight: bold;
    color: #00d4ff;
  }
  
  .stat-label {
    font-size: 14px;
    color: rgba(255, 255, 255, 0.5);
    margin-top: 8px;
  }
}

.footer-text {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.4);
}
</style>

