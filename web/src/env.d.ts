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
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const component: DefineComponent<{}, {}, any>;
  export default component;
}
