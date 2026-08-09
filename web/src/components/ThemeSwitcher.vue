<script setup lang="ts">
/**
 * ThemeSwitcher — 明暗主题切换器.
 *
 * <p>顶栏按钮 + 下拉: 3 选 1 (light / dark / auto).
 * <p>auto 模式显示月亮/太阳动态图标 (取决于 systemTheme).
 */
import { computed } from 'vue';
import { ElMessage } from 'element-plus';
import { Check } from '@element-plus/icons-vue';
import { useThemeStore, type ThemeMode } from '@/stores/theme';

const theme = useThemeStore();

const options: { value: ThemeMode; label: string; desc: string; icon: string }[] = [
  { value: 'light', label: '亮色',   desc: '强制 light theme',      icon: '☀️' },
  { value: 'dark',  label: '暗色',   desc: '强制 dark theme',       icon: '🌙' },
  { value: 'auto',  label: '跟随系统', desc: '匹配 OS prefers-color-scheme', icon: '⚙️' },
];

const currentLabel = computed(() => options.find((o) => o.value === theme.mode)?.label ?? '');
const currentIcon = computed(() => options.find((o) => o.value === theme.mode)?.icon ?? '');

/** 顶栏按钮显示的 icon: auto 模式时随系统切换 */
const buttonIcon = computed(() => {
  if (theme.mode === 'auto') {
    return theme.effective === 'dark' ? '🌙' : '☀️';
  }
  return currentIcon.value;
});

function pick(mode: ThemeMode) {
  theme.setMode(mode);
  const opt = options.find((o) => o.value === mode);
  ElMessage.success(`主题已切换: ${opt?.label}`);
}
</script>

<template>
  <el-dropdown trigger="click" @command="(cmd: ThemeMode) => pick(cmd)">
    <button class="theme-btn" :title="`当前主题: ${currentLabel} (点击切换)`">
      <span class="theme-icon">{{ buttonIcon }}</span>
      <span class="theme-label">{{ currentLabel }}</span>
    </button>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item
          v-for="opt in options"
          :key="opt.value"
          :command="opt.value"
          :disabled="theme.mode === opt.value"
        >
          <div class="theme-opt">
            <span class="theme-opt-icon">{{ opt.icon }}</span>
            <div class="theme-opt-text">
              <div class="theme-opt-label">
                {{ opt.label }}
                <el-icon v-if="theme.mode === opt.value" class="theme-opt-check"><Check /></el-icon>
              </div>
              <div class="theme-opt-desc">{{ opt.desc }}</div>
            </div>
          </div>
        </el-dropdown-item>
        <el-dropdown-item divided disabled>
          <div class="theme-status">
            <span class="status-dot" :class="theme.effective"></span>
            <span>当前生效: <code>{{ theme.effective }}</code></span>
            <span v-if="theme.mode === 'auto'" class="muted">(跟随系统)</span>
          </div>
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<style scoped>
.theme-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  background: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  color: var(--color-text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all var(--transition-fast);
}
.theme-btn:hover {
  border-color: var(--color-border-strong);
  color: var(--color-text-primary);
}
.theme-icon { font-size: 13px; }
.theme-label { font-weight: 500; }
@media (max-width: 1100px) { .theme-label { display: none; } }

.theme-opt {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 200px;
  padding: 4px 0;
}
.theme-opt-icon { font-size: 18px; flex-shrink: 0; }
.theme-opt-text { flex: 1; min-width: 0; }
.theme-opt-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-primary);
  display: flex;
  align-items: center;
  gap: 6px;
}
.theme-opt-check { color: var(--color-accent); font-size: 12px; }
.theme-opt-desc { font-size: 11px; color: var(--color-text-muted); margin-top: 2px; }

.theme-status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: var(--color-text-muted);
}
.status-dot {
  width: 6px; height: 6px;
  border-radius: 50%;
  background: var(--color-text-muted);
}
.status-dot.dark { background: var(--color-accent); }
.status-dot.light { background: var(--color-warning); }
</style>
