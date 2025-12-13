import { createApp } from 'vue'
import { createPinia } from 'pinia'
import router from './router'
import App from './App.vue'

// 样式导入
import 'virtual:uno.css'
import './assets/styles/global.scss'

// Naive UI
import { 
  create, 
  NConfigProvider, 
  NMessageProvider,
  NDialogProvider,
  NNotificationProvider,
  NButton,
  NInput,
  NSelect,
  NDatePicker,
  NDataTable,
  NCard,
  NSpace,
  NSpin,
  NEmpty,
  NTag,
  NTooltip,
  NPagination,
} from 'naive-ui'

const naive = create({
  components: [
    NConfigProvider,
    NMessageProvider,
    NDialogProvider,
    NNotificationProvider,
    NButton,
    NInput,
    NSelect,
    NDatePicker,
    NDataTable,
    NCard,
    NSpace,
    NSpin,
    NEmpty,
    NTag,
    NTooltip,
    NPagination,
  ]
})

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(naive)

app.mount('#app')
