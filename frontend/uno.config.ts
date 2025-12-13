import { defineConfig, presetUno, presetAttributify, transformerDirectives } from 'unocss'

export default defineConfig({
  presets: [
    presetUno(),
    presetAttributify(),
  ],
  transformers: [
    transformerDirectives(),
  ],
  theme: {
    colors: {
      // 大屏主题色
      'dashboard': {
        'bg': '#0a1628',
        'bg-dark': '#061224',
        'card': 'rgba(6, 30, 61, 0.8)',
        'border': 'rgba(30, 144, 255, 0.3)',
        'glow': '#1e90ff',
      },
      // 语义化颜色
      'primary': '#00d4ff',
      'success': '#00ff88',
      'warning': '#ffaa00',
      'danger': '#ff6b35',
      'info': '#8b5cf6',
    },
    fontFamily: {
      'din': ['DIN Alternate', 'DIN', 'sans-serif'],
      'sans': ['Source Han Sans CN', 'PingFang SC', 'Microsoft YaHei', 'sans-serif'],
    },
  },
  shortcuts: {
    // 大屏卡片样式
    'dashboard-card': 'bg-dashboard-card backdrop-blur-md border border-dashboard-border rounded-lg p-4',
    'dashboard-card-glow': 'dashboard-card shadow-[0_0_20px_rgba(30,144,255,0.2)]',
    // 标题样式
    'dashboard-title': 'text-primary font-bold text-lg tracking-wide',
    'dashboard-subtitle': 'text-white/60 text-sm',
    // 数字样式
    'dashboard-number': 'font-din text-3xl font-bold text-white',
    'dashboard-number-lg': 'font-din text-5xl font-bold text-primary',
    // Flex 布局
    'flex-center': 'flex items-center justify-center',
    'flex-between': 'flex items-center justify-between',
    'flex-col-center': 'flex flex-col items-center justify-center',
  },
})

