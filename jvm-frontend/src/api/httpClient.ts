function resolveBrowserBaseUrl(): string {
  const configured =
    import.meta.env.VITE_SIDECAR_URL?.replace(/\/$/, '');

  if (configured) {
    return configured;
  }

  if (
    typeof window !== 'undefined' &&
    window.location.protocol.startsWith('http')
  ) {
    return window.location.origin;
  }

  return 'http://127.0.0.1:8899';
}

const BROWSER_BASE_URL = resolveBrowserBaseUrl();

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export async function getJson<T>(
  path: string,
  signal?: AbortSignal,
): Promise<T> {
  if (window.ahaythorus?.get) {
    return (await window.ahaythorus.get(path)) as T;
  }

  const response = await fetch(
    `${BROWSER_BASE_URL}${path}`,
    {
      method: 'GET',
      headers: {
        Accept: 'application/json',
      },
      signal,
    },
  );

  if (!response.ok) {
    const body = await response.text();

    throw new ApiError(
      response.status,
      body || `${response.status} ${response.statusText}`,
    );
  }

  return (await response.json()) as T;
}

export async function getConfiguredBaseUrl(): Promise<string> {
  if (window.ahaythorus?.getBaseUrl) {
    return window.ahaythorus.getBaseUrl();
  }

  return BROWSER_BASE_URL;
}
