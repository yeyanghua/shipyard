<script setup lang="ts">
/**
 * 监控面板 (M13 Phase 3) — chart.js 可视化.
 *
 * <p>V1 demo 全部用 mock 数据 (V1.5 接 Prometheus 后端).
 *
 * <p>设计:
 * <ul>
 *   <li>4 个核心 metric 卡片 (今日构建 / 成功率 / 平均时长 / 队列中)</li>
 *   <li>构建趋势 (7 天折线图, 成功/失败/取消 堆叠)</li>
 *   <li>项目活跃度 (Top 5 项目构建数柱状图)</li>
 *   <li>环境部署频率 (饼图)</li>
 *   <li>镜像构建时长分布 (横向条形图)</li>
 * </ul>
 */
import { ref } from 'vue';
import { Line, Bar, Doughnut } from 'vue-chartjs';
import {
  Chart as ChartJS,
  Title, Tooltip, Legend, Filler,
  LineElement, PointElement, BarElement, ArcElement,
  CategoryScale, LinearScale, TimeScale,
} from 'chart.js';
import type { ChartData, ChartOptions } from 'chart.js';

ChartJS.register(Title, Tooltip, Legend, Filler,
  LineElement, PointElement, BarElement, ArcElement,
  CategoryScale, LinearScale, TimeScale);

// 主题色 (跟 design system 一致)
const accent = '#06b6d4';
const success = '#10b981';
const warning = '#f59e0b';
const danger = '#ef4444';
const textMuted = '#9ca3af';
const gridColor = 'rgba(255, 255, 255, 0.06)';

// 通用 chart options (暗色风格) — 用 any 简化类型, 因为 ChartOptions 泛型复杂
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const baseOptions = (extra: any = {}): any => ({
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: {
      labels: { color: textMuted, font: { family: 'Manrope', size: 11 }, usePointStyle: true, padding: 12 },
    },
    tooltip: {
      backgroundColor: '#1a2030',
      titleColor: '#f5f6fa',
      bodyColor: '#d1d5db',
      borderColor: 'rgba(255,255,255,0.06)',
      borderWidth: 1,
      padding: 10,
      cornerRadius: 4,
    },
  },
  scales: {
    x: {
      grid: { color: gridColor, drawBorder: false },
      ticks: { color: textMuted, font: { family: 'JetBrains Mono', size: 11 } },
    },
    y: {
      grid: { color: gridColor, drawBorder: false },
      ticks: { color: textMuted, font: { family: 'JetBrains Mono', size: 11 } },
      beginAtZero: true,
    },
  },
  ...extra,
});

const trendData = ref<ChartData<'line'>>({
  labels: ['8/3', '8/4', '8/5', '8/6', '8/7', '8/8', '8/9'],
  datasets: [
    { label: '成功', data: [12, 19, 15, 22, 18, 25, 21], borderColor: success, backgroundColor: success + '20', fill: true, tension: 0.4, pointRadius: 3, pointHoverRadius: 5 },
    { label: '失败', data: [3, 2, 4, 1, 3, 2, 1], borderColor: danger, backgroundColor: danger + '20', fill: true, tension: 0.4, pointRadius: 3, pointHoverRadius: 5 },
    { label: '取消', data: [1, 0, 2, 0, 1, 0, 0], borderColor: textMuted, backgroundColor: textMuted + '20', fill: true, tension: 0.4, pointRadius: 3, pointHoverRadius: 5 },
  ],
});

const projectData = ref<ChartData<'bar'>>({
  labels: ['shipyard-backend', 'shipyard-web', 'infra-tools', 'ml-experiment', 'docs-site'],
  datasets: [{ label: '本周构建数', data: [42, 38, 24, 18, 7], backgroundColor: [accent, accent + 'cc', accent + '99', accent + '66', accent + '33'], borderRadius: 4 }],
});

const envData = ref<ChartData<'doughnut'>>({
  labels: ['Dev', 'Staging', 'Production'],
  datasets: [{ data: [62, 28, 14], backgroundColor: [success, warning, danger], borderWidth: 0 }],
});

const buildTimeData = ref<ChartData<'bar'>>({
  labels: ['<1m', '1-3m', '3-5m', '5-10m', '10-30m', '>30m'],
  datasets: [{ label: '构建数量', data: [18, 45, 32, 22, 8, 2], backgroundColor: accent, borderRadius: 4 }],
});

const metrics = ref({
  buildsToday: 21,
  successRate: 92.5,
  avgDuration: '3m 24s',
  inQueue: 2,
});

const timeRange = ref('7d');
</script>

<template>
  <div class="page">
    <header class="page-header">
      <div>
        <h1>监控面板</h1>
        <p class="muted">构建 / 部署 / 资源 实时指标 · V1 mock 数据 (V1.5 接 Prometheus)</p>
      </div>
      <div class="header-actions">
        <el-select v-model="timeRange" placeholder="时间范围" style="width: 140px">
          <el-option label="最近 1 小时" value="1h" />
          <el-option label="最近 24 小时" value="24h" />
          <el-option label="最近 7 天" value="7d" selected />
          <el-option label="最近 30 天" value="30d" />
        </el-select>
        <el-button>导出</el-button>
      </div>
    </header>

    <!-- ===== KPI 卡片 ===== -->
    <section class="kpi-grid">
      <div class="kpi-card">
        <div class="kpi-label">今日构建</div>
        <div class="kpi-value">{{ metrics.buildsToday }}</div>
        <div class="kpi-trend up">↑ 12% 比昨日</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-label">成功率</div>
        <div class="kpi-value">{{ metrics.successRate }}<span class="unit">%</span></div>
        <div class="kpi-trend up">↑ 1.2% 本周</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-label">平均构建时长</div>
        <div class="kpi-value text-mono">{{ metrics.avgDuration }}</div>
        <div class="kpi-trend down">↓ 18s 本周</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-label">队列中</div>
        <div class="kpi-value">{{ metrics.inQueue }}</div>
        <div class="kpi-trend muted">等待执行</div>
      </div>
    </section>

    <!-- ===== 构建趋势 ===== -->
    <section class="card chart-card">
      <div class="card-header">
        <h2>构建趋势</h2>
        <span class="muted">最近 7 天</span>
      </div>
      <div class="chart-container" style="height: 280px;">
        <Line :data="trendData" :options="baseOptions()" />
      </div>
    </section>

    <!-- ===== 双列: 项目活跃度 + 环境部署 ===== -->
    <div class="grid-2">
      <section class="card chart-card">
        <div class="card-header">
          <h2>项目活跃度</h2>
          <span class="muted">本周构建数 Top 5</span>
        </div>
        <div class="chart-container" style="height: 260px;">
          <Bar :data="projectData" :options="baseOptions({ indexAxis: 'y' } as Partial<ChartOptions>)" />
        </div>
      </section>

      <section class="card chart-card">
        <div class="card-header">
          <h2>环境部署分布</h2>
          <span class="muted">本周</span>
        </div>
        <div class="chart-container" style="height: 260px;">
          <Doughnut :data="envData" :options="baseOptions({ scales: undefined } as Partial<ChartOptions>)" />
        </div>
      </section>
    </div>

    <!-- ===== 构建时长分布 ===== -->
    <section class="card chart-card">
      <div class="card-header">
        <h2>构建时长分布</h2>
        <span class="muted">V1.5 接 Prometheus histogram</span>
      </div>
      <div class="chart-container" style="height: 240px;">
        <Bar :data="buildTimeData" :options="baseOptions()" />
      </div>
    </section>
  </div>
</template>

<style scoped>
.page {
  max-width: 1280px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-4);
  flex-wrap: wrap;
}
.page-header h1 {
  font-size: 24px;
  font-weight: 700;
  margin: 0;
}
.muted { color: var(--color-text-muted); font-size: 12px; margin-top: 4px; }
.header-actions { display: flex; gap: 8px; }

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-4);
}
@media (max-width: 900px) { .kpi-grid { grid-template-columns: repeat(2, 1fr); } }
.kpi-card {
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: var(--space-5);
}
.kpi-label {
  font-size: 11px;
  color: var(--color-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  font-weight: 500;
  margin-bottom: 8px;
}
.kpi-value {
  font-family: var(--font-display);
  font-size: 32px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  line-height: 1.1;
}
.kpi-value .unit { font-size: 18px; color: var(--color-text-muted); margin-left: 2px; }
.kpi-trend { font-size: 11px; margin-top: 6px; font-family: var(--font-mono); }
.kpi-trend.up { color: var(--color-success); }
.kpi-trend.down { color: var(--color-success); }
.kpi-trend.muted { color: var(--color-text-muted); }

.chart-card .card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-4);
}
.chart-card h2 { font-size: 14px; font-weight: 600; margin: 0; }

.grid-2 {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: var(--space-4);
}
@media (max-width: 900px) { .grid-2 { grid-template-columns: 1fr; } }
</style>
