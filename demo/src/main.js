import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { installI18n } from './i18n'
import './styles/styles.css'
import './styles/v3.css'

createApp(App).use(router).mount('#app')
installI18n()
