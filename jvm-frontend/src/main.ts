import {
  app,
  BrowserWindow,
  ipcMain,
} from 'electron';
import path from 'node:path';
import started from 'electron-squirrel-startup';

if (started) {
  app.quit();
}

const SIDECAR_BASE_URL =
  process.env.AH_SIDECAR_URL ??
  'http://127.0.0.1:8899';

const REQUEST_TIMEOUT_MS = Number(
  process.env.AH_UI_REQUEST_TIMEOUT_MS ?? 4_000,
);

function resolveSidecarUrl(requestPath: string): URL {
  const base = new URL(SIDECAR_BASE_URL);
  const resolved = new URL(requestPath, base);

  if (resolved.origin !== base.origin) {
    throw new Error('Refusing cross-origin sidecar request.');
  }

  if (
    resolved.pathname !== '/' &&
    !resolved.pathname.startsWith('/api/v1/')
  ) {
    throw new Error(
      `Unsupported sidecar path: ${resolved.pathname}`,
    );
  }

  return resolved;
}

async function fetchSidecar(
  requestPath: string,
): Promise<unknown> {
  const url = resolveSidecarUrl(requestPath);
  const controller = new AbortController();

  const timeout = setTimeout(
    () => controller.abort(),
    REQUEST_TIMEOUT_MS,
  );

  try {
    const response = await fetch(url, {
      method: 'GET',
      headers: {
        Accept: 'application/json',
      },
      signal: controller.signal,
    });

    const body = await response.text();

    if (!response.ok) {
      throw new Error(
        `Sidecar request failed: ${response.status} ${
          body || response.statusText
        }`,
      );
    }

    return body ? JSON.parse(body) : null;
  } finally {
    clearTimeout(timeout);
  }
}

ipcMain.handle(
  'sidecar:get',
  async (_event, requestPath: string) =>
    fetchSidecar(requestPath),
);

ipcMain.handle(
  'sidecar:base-url',
  () => SIDECAR_BASE_URL,
);

function createWindow() {
  const mainWindow = new BrowserWindow({
    width: 1480,
    height: 920,
    minWidth: 1080,
    minHeight: 700,
    backgroundColor: '#0a0d14',
    autoHideMenuBar: true,
    title: 'A-Haythorus',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });

  if (MAIN_WINDOW_VITE_DEV_SERVER_URL) {
    void mainWindow.loadURL(
      MAIN_WINDOW_VITE_DEV_SERVER_URL,
    );
  } else {
    void mainWindow.loadFile(
      path.join(
        __dirname,
        `../renderer/${MAIN_WINDOW_VITE_NAME}/index.html`,
      ),
    );
  }

  if (process.env.AH_UI_DEVTOOLS === 'true') {
    mainWindow.webContents.openDevTools({
      mode: 'detach',
    });
  }
}

app.whenReady().then(() => {
  createWindow();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow();
    }
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});
