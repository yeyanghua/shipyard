/// <reference types="vitest" />
import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';
import { fileURLToPath, URL } from 'node:url';

/**
 * Vite 配置.
 *
 * <p>dev server: 5173 端口, /api/* 代理到 shipyard 后端 (http://localhost:8080).
 *
 * <p>SSR 暂不启用 (V1 demo 用 CSR 足够, 后续 milestone 看是否需要).
 */
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const backendUrl = env.VITE_SHIPYARD_API_URL ?? 'http://localhost:8080';

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      port: 5173,
      host: '0.0.0.0',
      strictPort: false,
      proxy: {
        // V1 demo: shipyard 后端跑在 8080
        '/api': {
          target: backendUrl,
          changeOrigin: true,
          // SSE 流式响应不能用 buffered response
          configure: (proxy) => {
            proxy.on('proxyRes', (proxyRes) => {
              proxyRes.headers['x-proxied-from'] = backendUrl;
            });
          },
        },
      },
    },
    build: {
      target: 'es2022',
      sourcemap: mode !== 'production',
      rollupOptions: {
        output: {
          // 拆 chunk: vue / pinia / axios / 业务代码分离
          manualChunks: {
            'vue-vendor': ['vue', 'vue-router', 'pinia'],
            'http-vendor': ['axios'],
            'time-vendor': ['dayjs'],
          },
        },
      },
    },
    test: {
      globals: true,
      environment: 'happy-dom',
      coverage: {
        provider: 'v8',
        reporter: ['text', 'html', 'lcov'],
        include: ['src/**/*.{ts,vue}'],
        exclude: ['src/**/__tests__/**', 'src/**/*.test.{ts,vue}', 'src/main.ts'],
        thresholds: {
          // 跟 shipyard/worker 一致: 70% 门槛 (CONTRIBUTING.md "Coverage Thresholds")
          lines: 70,
          functions: 70,
          branches: 60,
          statements: 70,
        },
      },
    },
  };
});
