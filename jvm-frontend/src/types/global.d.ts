export {};

declare global {
  interface Window {
    ahaythorus?: {
      get: (path: string) => Promise<unknown>;
      getBaseUrl: () => Promise<string>;
    };
  }

  interface ImportMetaEnv {
    readonly VITE_SIDECAR_URL?: string;
  }

  interface ImportMeta {
    readonly env: ImportMetaEnv;
  }
}
