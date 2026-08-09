<script setup lang="ts">
/**
 * CommandPalette - 全局命令面板 (Cmd+K / Ctrl+K).
 *
 * <p>类似 Linear / GitHub 的命令面板, 模糊搜索 + 命令跳转.
 * <p>命令来源:
 * <ul>
 *   <li>静态命令 (导航 + 操作): 写死</li>
 *   <li>动态命令 (项目 / 环境): 启动时拉取 + 实时过滤</li>
 * </ul>
 */
import { computed, nextTick, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useEventListener } from '@vueuse/core';
import Fuse from 'fuse.js';
import { ElMessage } from 'element-plus';
import { Search } from '@element-plus/icons-vue';
import { projectsApi, envsApi, type Project, type Env, ApiError } from '@/api';

const router = useRouter();

// 状态
const visible = ref(false);
const query = ref('');
const inputRef = ref<HTMLInputElement | null>(null);
const activeIndex = ref(0);

// 静态命令
interface CommandItem {
  id: string;
  type: 'nav' | 'action' | 'project' | 'env';
  label: string;
  desc?: string;
  to?: string;          // router push
  shortcut?: string;
  keywords?: string[];
  icon?: string;
}

const staticCommands: CommandItem[] = [
  { id: 'nav-dashboard',   type: 'nav', label: '总览',          to: '/',          shortcut: 'G H', keywords: ['home', 'overview'] },
  { id: 'nav-projects',    type: 'nav', label: '项目列表',      to: '/projects',  shortcut: 'G P', keywords: ['list'] },
  { id: 'nav-new-project', type: 'nav', label: '新建项目',      to: '/projects/new', keywords: ['create', 'add'] },
  { id: 'nav-envs',        type: 'nav', label: '环境列表',      to: '/envs',      shortcut: 'G E', keywords: ['env', 'list'] },
  { id: 'nav-monitoring',  type: 'nav', label: '监控面板',      to: '/monitoring', keywords: ['metric', 'grafana'] },
  { id: 'nav-activity',    type: 'nav', label: '活动日志',      to: '/activity',  keywords: ['log', 'event', 'audit'] },
  { id: 'nav-notifications', type: 'nav', label: '通知中心',    to: '/notifications', keywords: ['bell', 'inbox'] },
  { id: 'nav-ai',          type: 'nav', label: 'AI 诊断',       to: '/ai/diagnosis', shortcut: 'G A', keywords: ['smart'] },
  { id: 'action-search',   type: 'action', label: '搜索 (聚焦此面板)', desc: 'Cmd+K', shortcut: '⌘K', keywords: ['find'] },
  { id: 'action-docs',     type: 'action', label: '查看文档',     desc: '打开 GitHub README', keywords: ['help', 'readme'] },
];

// 动态命令 (project + env)
const projects = ref<Project[]>([]);
const envs = ref<Env[]>([]);
const dynamicCommands = computed<CommandItem[]>(() => [
  ...projects.value.map((p) => ({
    id: `project-${p.id}`,
    type: 'project' as const,
    label: p.displayName,
    desc: p.name + (p.projectType ? ` · ${p.projectType}` : ''),
    to: `/projects/${p.id}`,
    keywords: [p.name, p.displayName, p.projectType],
  })),
  ...envs.value.map((e) => ({
    id: `env-${e.id}`,
    type: 'env' as const,
    label: e.displayName,
    desc: e.name + (e.isProduction ? ' · PROD' : ' · DEV'),
    to: `/envs/${e.id}/variables`,
    keywords: [e.name, e.displayName],
  })),
]);

const allCommands = computed<CommandItem[]>(() => [
  ...staticCommands,
  ...dynamicCommands.value,
]);

// 模糊搜索
const fuse = computed(() => new Fuse(allCommands.value, {
  keys: ['label', 'desc', 'keywords'],
  threshold: 0.4,
  includeScore: true,
}));

const filtered = computed<CommandItem[]>(() => {
  const q = query.value.trim();
  if (!q) {
    // 默认: 静态命令 + 前 5 个项目
    return [
      ...staticCommands,
      ...dynamicCommands.value.slice(0, 5),
    ];
  }
  return fuse.value.search(q).map((r) => r.item);
});

// 键盘导航
function onKeyDown(e: KeyboardEvent) {
  if (!visible.value) return;
  if (e.key === 'ArrowDown') {
    e.preventDefault();
    activeIndex.value = Math.min(filtered.value.length - 1, activeIndex.value + 1);
  } else if (e.key === 'ArrowUp') {
    e.preventDefault();
    activeIndex.value = Math.max(0, activeIndex.value - 1);
  } else if (e.key === 'Enter') {
    e.preventDefault();
    selectItem(filtered.value[activeIndex.value]);
  } else if (e.key === 'Escape') {
    e.preventDefault();
    visible.value = false;
  }
}

// 选中
async function selectItem(item: CommandItem | undefined) {
  if (!item) return;
  if (item.type === 'action' && item.id === 'action-docs') {
    window.open('https://github.com/yeyanghua/shipyard', '_blank');
    visible.value = false;
    return;
  }
  if (item.to) {
    await router.push(item.to);
    visible.value = false;
    query.value = '';
    activeIndex.value = 0;
  } else {
    ElMessage.info(`命令: ${item.label} (V1.5 接入)`);
  }
}

// 全局快捷键: Cmd/Ctrl + K
useEventListener('keydown', (e: KeyboardEvent) => {
  if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
    e.preventDefault();
    visible.value = true;
    nextTick(() => inputRef.value?.focus());
  } else if (e.key === 'Escape' && visible.value) {
    visible.value = false;
  }
});

// 打开时重置 + 聚焦
watch(visible, (v) => {
  if (v) {
    query.value = '';
    activeIndex.value = 0;
    nextTick(() => inputRef.value?.focus());
    // 拉取动态命令
    loadDynamic();
  }
});

async function loadDynamic() {
  try {
    const [p, e] = await Promise.all([
      projectsApi.list({ page: 1, size: 50 }),
      envsApi.list({ page: 1, size: 50 }),
    ]);
    projects.value = p.records;
    envs.value = e.records;
  } catch (err) {
    console.warn('[CommandPalette] load failed:', (err as ApiError).message);
  }
}

// 类型 icon
function typeIcon(t: CommandItem['type']): string {
  return t === 'project' ? '📦' : t === 'env' ? '🌐' : t === 'action' ? '⚡' : '🔗';
}
function typeLabel(t: CommandItem['type']): string {
  return t === 'project' ? '项目' : t === 'env' ? '环境' : t === 'action' ? '操作' : '导航';
}

// 类型分组展示
const grouped = computed(() => {
  const groups: Record<string, CommandItem[]> = {
    导航: [],
    操作: [],
    项目: [],
    环境: [],
  };
  for (const item of filtered.value) {
    if (item.type === 'nav') groups['导航'].push(item);
    else if (item.type === 'action') groups['操作'].push(item);
    else if (item.type === 'project') groups['项目'].push(item);
    else if (item.type === 'env') groups['环境'].push(item);
  }
  return groups;
});

// 找到 item 的全局 index (用于键盘导航)
function globalIndexOf(item: CommandItem): number {
  return filtered.value.findIndex((i) => i.id === item.id);
}
</script>

<template>
  <Teleport to="body">
    <Transition name="cmd-fade">
      <div v-if="visible" class="cmd-mask" @click.self="visible = false">
        <div class="cmd-panel" @keydown="onKeyDown">
          <!-- 输入框 -->
          <div class="cmd-input-row">
            <el-icon class="cmd-search-icon"><Search /></el-icon>
            <input
              ref="inputRef"
              v-model="query"
              class="cmd-input"
              placeholder="搜索命令 / 项目 / 环境..."
              autocomplete="off"
              spellcheck="false"
            />
            <kbd class="cmd-esc">ESC</kbd>
          </div>

          <!-- 结果列表 -->
          <div class="cmd-results">
            <div v-if="filtered.length === 0" class="cmd-empty">
              <p>没找到 "<code>{{ query }}</code>" 相关结果</p>
              <p class="hint">试试搜: 项目名 / 环境名 / 命令</p>
            </div>
            <template v-else>
              <div v-for="(items, group) in grouped" :key="group">
                <div v-if="items.length > 0" class="cmd-group">
                  <div class="cmd-group-label">{{ group }}</div>
                  <div
                    v-for="item in items"
                    :key="item.id"
                    :class="['cmd-item', { active: globalIndexOf(item) === activeIndex }]"
                    @click="selectItem(item)"
                    @mouseenter="activeIndex = globalIndexOf(item)"
                  >
                    <span class="cmd-item-icon">{{ typeIcon(item.type) }}</span>
                    <div class="cmd-item-body">
                      <div class="cmd-item-label">{{ item.label }}</div>
                      <div v-if="item.desc" class="cmd-item-desc">{{ item.desc }}</div>
                    </div>
                    <span v-if="item.shortcut" class="cmd-item-shortcut">{{ item.shortcut }}</span>
                    <span v-else class="cmd-item-type">{{ typeLabel(item.type) }}</span>
                  </div>
                </div>
              </div>
            </template>
          </div>

          <!-- 底部提示 -->
          <div class="cmd-footer">
            <span><kbd>↑</kbd><kbd>↓</kbd> 导航</span>
            <span><kbd>↵</kbd> 选择</span>
            <span><kbd>esc</kbd> 关闭</span>
            <span class="cmd-branding">shipyard command palette</span>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.cmd-mask {
  position: fixed;
  inset: 0;
  background: var(--el-mask-color);
  z-index: 2000;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: 12vh;
  backdrop-filter: blur(4px);
}

.cmd-panel {
  width: 100%;
  max-width: 640px;
  background: var(--color-bg-elevated);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  display: flex;
  flex-direction: column;
  max-height: 70vh;
  overflow: hidden;
}

.cmd-input-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--color-border);
}
.cmd-search-icon { color: var(--color-text-muted); font-size: 16px; }
.cmd-input {
  flex: 1;
  background: transparent;
  border: 0;
  outline: 0;
  font-size: 15px;
  color: var(--color-text-primary);
  font-family: var(--font-body);
}
.cmd-input::placeholder { color: var(--color-text-muted); }
.cmd-esc {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--color-text-muted);
  background: var(--color-bg-surface);
  padding: 2px 6px;
  border-radius: 3px;
  border: 1px solid var(--color-border);
}

.cmd-results {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}
.cmd-empty {
  padding: 32px 16px;
  text-align: center;
  color: var(--color-text-muted);
}
.cmd-empty .hint { font-size: 11px; margin-top: 8px; }
.cmd-empty code {
  background: var(--color-accent-soft);
  color: var(--color-accent);
  padding: 1px 6px;
  border-radius: 3px;
}

.cmd-group { margin-bottom: 8px; }
.cmd-group:last-child { margin-bottom: 0; }
.cmd-group-label {
  font-size: 10px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--color-text-muted);
  padding: 6px 12px 4px;
}

.cmd-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background var(--transition-fast);
}
.cmd-item.active { background: var(--color-accent-soft); }
.cmd-item:hover { background: var(--color-accent-soft); }
.cmd-item-icon { font-size: 16px; width: 20px; text-align: center; flex-shrink: 0; }
.cmd-item-body { flex: 1; min-width: 0; }
.cmd-item-label {
  font-size: 13px;
  color: var(--color-text-primary);
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.cmd-item-desc {
  font-size: 11px;
  color: var(--color-text-muted);
  font-family: var(--font-mono);
  margin-top: 2px;
}
.cmd-item-shortcut {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--color-text-secondary);
  background: var(--color-bg-surface);
  padding: 2px 6px;
  border-radius: 3px;
  border: 1px solid var(--color-border);
}
.cmd-item-type {
  font-size: 10px;
  color: var(--color-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.cmd-footer {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 8px 18px;
  border-top: 1px solid var(--color-border);
  font-size: 11px;
  color: var(--color-text-muted);
  background: var(--color-bg-surface);
}
.cmd-footer kbd {
  font-family: var(--font-mono);
  font-size: 10px;
  background: var(--color-bg-elevated);
  padding: 1px 5px;
  border-radius: 3px;
  border: 1px solid var(--color-border);
  margin-right: 4px;
}
.cmd-branding {
  margin-left: auto;
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--color-accent);
  letter-spacing: 0.05em;
}

/* 过渡 */
.cmd-fade-enter-active { transition: all 0.18s cubic-bezier(0.16, 1, 0.3, 1); }
.cmd-fade-leave-active { transition: all 0.12s ease-in; }
.cmd-fade-enter-from { opacity: 0; transform: scale(0.96); }
.cmd-fade-leave-to   { opacity: 0; transform: scale(0.98); }
</style>
