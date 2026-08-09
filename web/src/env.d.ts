/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 后端 API URL. 留空时 Vite dev proxy 兜底 (/api → http://localhost:8080) */
  readonly VITE_SHIPYARD_API_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue';
  // Vite 官方 vue-shim 模板: {} 是空 props/空 data 的合法占位.
  /* eslint-disable @typescript-eslint/no-explicit-any, @typescript-eslint/ban-types */
  const component: DefineComponent<{}, {}, any>;
  /* eslint-enable @typescript-eslint/no-explicit-any, @typescript-eslint/ban-types */
  export default component;
}
