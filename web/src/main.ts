/**
 * shipyard Web 入口.
 *
 * <p>技术栈: Vue 3 + Pinia + Vue Router 4 + Axios + Element Plus + chart.js.
 *
 * <h2>字体 (Vite 编译时 bundle, 不走外网)</h2>
 * <ul>
 *   <li>Manrope Variable — display + body</li>
 *   <li>JetBrains Mono Variable — code / mono</li>
 *   <li>Noto Sans SC — 中文</li>
 * </ul>
 *
 * <h2>设计语言</h2>
 * Industrial Precision (Linear + Vercel + Drone CI) — 暗色深蓝黑 + 青色 accent + sharp 边角.
 * Element Plus 主题深度覆盖, 跟设计系统 token 一致.
 */
import { createApp } from 'vue';
import { createPinia } from 'pinia';
import ElementPlus from 'element-plus';
import * as ElementPlusIconsVue from '@element-plus/icons-vue';
import App from './App.vue';
import { router } from './router';

// 字体
import '@fontsource-variable/manrope';
import '@fontsource-variable/jetbrains-mono';
import '@fontsource/noto-sans-sc/400.css';
import '@fontsource/noto-sans-sc/500.css';
import '@fontsource/noto-sans-sc/700.css';

// Element Plus + 暗色变量
import 'element-plus/dist/index.css';
import 'element-plus/theme-chalk/dark/css-vars.css';

// 全局设计系统 (CSS 变量 + 自定义组件类 + Element Plus 主题覆盖)
import './assets/main.css';

// 启用 Element Plus 暗色模式 (在 main.css 之后, 让主题覆盖生效)
document.documentElement.classList.add('dark');

const app = createApp(App);
app.use(createPinia());
app.use(router);
app.use(ElementPlus);

// 全局注册所有 Element Plus 图标 (V1 demo: 用得多, 全量注册省事)
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component);
}

app.mount('#app');
