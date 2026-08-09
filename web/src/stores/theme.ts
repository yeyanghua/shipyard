/**
 * Theme store — 明暗主题切换.
 *
 * <p>三种模式:
 * <ul>
 *   <li><b>light</b> — 强制亮色</li>
 *   <li><b>dark</b> — 强制暗色</li>
 *   <li><b>auto</b> — 跟随系统 (prefers-color-scheme)</li>
 * </ul>
 *
 * <p>持久化到 localStorage; 切换时立即更新 <html> class + Element Plus 暗色变量.
 */
import { defineStore } from 'pinia';
import { computed, ref, watch } from 'vue';

export type ThemeMode = 'light' | 'dark' | 'auto';
const STORAGE_KEY = 'shipyard:theme';

function detectSystemTheme(): 'light' | 'dark' {
  if (typeof window === 'undefined' || !window.matchMedia) return 'light';
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

function readStored(): ThemeMode {
  if (typeof window === 'undefined') return 'auto';
  const raw = window.localStorage.getItem(STORAGE_KEY);
  if (raw === 'light' || raw === 'dark' || raw === 'auto') return raw;
  return 'auto';
}

function applyTheme(effective: 'light' | 'dark') {
  const html = document.documentElement;
  if (effective === 'dark') {
    html.classList.add('dark');
  } else {
    html.classList.remove('dark');
  }
  // 同步 color-scheme (影响浏览器原生控件 / 滚动条)
  html.style.colorScheme = effective;
}

export const useThemeStore = defineStore('theme', () => {
  const mode = ref<ThemeMode>(readStored());
  const systemTheme = ref<'light' | 'dark'>(detectSystemTheme());

  /** 当前实际生效的主题 (考虑 auto 模式) */
  const effective = computed<'light' | 'dark'>(() => {
    if (mode.value === 'auto') return systemTheme.value;
    return mode.value;
  });

  // 初始化: 应用 + 监听系统变化 (auto 模式)
  applyTheme(effective.value);
  if (typeof window !== 'undefined' && window.matchMedia) {
    const mq = window.matchMedia('(prefers-color-scheme: dark)');
    const handler = (e: MediaQueryListEvent) => {
      systemTheme.value = e.matches ? 'dark' : 'light';
    };
    mq.addEventListener('change', handler);
  }

  // watch 同步到 DOM
  watch(effective, (v) => applyTheme(v));
  watch(mode, (v) => {
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(STORAGE_KEY, v);
    }
  });

  /** 切换下一档: light → dark → auto → light */
  function cycle() {
    const order: ThemeMode[] = ['light', 'dark', 'auto'];
    const idx = order.indexOf(mode.value);
    mode.value = order[(idx + 1) % order.length];
  }

  function setMode(next: ThemeMode) {
    mode.value = next;
  }

  return { mode, effective, cycle, setMode };
});
