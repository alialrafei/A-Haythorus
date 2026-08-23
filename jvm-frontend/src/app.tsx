import React from 'react';
import { createRoot } from 'react-dom/client';
import '@blueprintjs/core/lib/css/blueprint.css';
import '@blueprintjs/icons/lib/css/blueprint-icons.css';
import { MonitoringProvider } from './context/MonitoringContext';
import { AppShell } from './components/shell/AppShell';

const rootElement = document.getElementById('root');

if (!rootElement) {
  throw new Error('Renderer root element was not found.');
}

createRoot(rootElement).render(
  <React.StrictMode>
    <MonitoringProvider>
      <AppShell />
    </MonitoringProvider>
  </React.StrictMode>,
);
