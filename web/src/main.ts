/**
 * shipyard Web 入口.
 *
 * <p>Vue 3 + Pinia + Vue Router 4 + Axios.
 * V1 范围: 8 个核心页面 + 后端 API proxy (M4 才接实际接口).
 */
import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import { router } from './router';
import './assets/main.css';

const app = createApp(App);
app.use(createPinia());
app.use(router);
app.mount('#app');
