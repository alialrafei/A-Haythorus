import {
  contextBridge,
  ipcRenderer,
} from 'electron';

contextBridge.exposeInMainWorld('ahaythorus', {
  get: (path: string) =>
    ipcRenderer.invoke('sidecar:get', path),

  getBaseUrl: () =>
    ipcRenderer.invoke('sidecar:base-url'),
});
