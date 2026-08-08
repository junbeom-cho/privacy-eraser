import './assets/main.scss'

import { createApp } from 'vue'
import App from './App.vue'
import router from './router'

// Bootstrap 5.3 은 다크 모드를 data-bs-theme 로만 켭니다. 미디어 쿼리로는 안 됩니다.
const dark = window.matchMedia('(prefers-color-scheme: dark)')
const applyTheme = () => {
  document.documentElement.dataset.bsTheme = dark.matches ? 'dark' : 'light'
}
applyTheme()
dark.addEventListener('change', applyTheme)

createApp(App).use(router).mount('#app')
