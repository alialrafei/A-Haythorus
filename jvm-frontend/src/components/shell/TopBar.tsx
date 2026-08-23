import React from 'react';
import { Icon } from '@blueprintjs/core';
import { formatRelativeTime } from '../../utils/format';

export type ThemeMode = 'dark' | 'light';

export function TopBar({
  title,
  subtitle,
  refreshing,
  error,
  lastUpdated,
  theme,
  onRefresh,
  onToggleTheme,
}: {
  title: string;
  subtitle: string;
  refreshing: boolean;
  error: string | null;
  lastUpdated: number | null;
  theme: ThemeMode;
  onRefresh: () => void;
  onToggleTheme: () => void;
}) {
  return (
    <header className="topbar">
      <div className="topbar-title">
        <h1>{title}</h1>
        <p>{subtitle}</p>
      </div>

      <div className="topbar-actions">
        <div
          className={`connection-pill ${
            error ? 'connection-error' : ''
          }`}
        >
          <span className="connection-dot" />
          <span>
            {error
              ? 'Sidecar unavailable'
              : `Live · ${formatRelativeTime(lastUpdated)}`}
          </span>
        </div>

        <button
          className="icon-button"
          onClick={onRefresh}
          title="Refresh now"
          disabled={refreshing}
        >
          <Icon
            icon="refresh"
            size={17}
            className={refreshing ? 'spin' : ''}
          />
        </button>

        <button
          className="icon-button"
          onClick={onToggleTheme}
          title="Toggle theme"
        >
          <Icon
            icon={theme === 'dark' ? 'flash' : 'moon'}
            size={17}
          />
        </button>
      </div>
    </header>
  );
}
