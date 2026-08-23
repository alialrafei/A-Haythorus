import React, {
  useEffect,
  useMemo,
  useState,
} from 'react';
import { Sidebar, type PageId } from './Sidebar';
import {
  TopBar,
  type ThemeMode,
} from './TopBar';
import { OverviewPage } from '../../pages/OverviewPage';
import { PodsPage } from '../../pages/PodsPage';
import { ProblemsPage } from '../../pages/ProblemsPage';
import { JvmPage } from '../../pages/JvmPage';
import { useMonitoring } from '../../context/MonitoringContext';

const pageCopy: Record<
  Exclude<PageId, 'jvm'>,
  { title: string; subtitle: string }
> = {
  overview: {
    title: 'Runtime overview',
    subtitle: 'Live JVM posture across the connected sidecars',
  },
  pods: {
    title: 'Pods & JVMs',
    subtitle: 'Explore monitored workloads and runtime instances',
  },
  problems: {
    title: 'Problems',
    subtitle: 'Prioritized findings from the JVM analysis engine',
  },
};

export function AppShell() {
  const monitoring = useMonitoring();
  const [page, setPage] = useState<PageId>('overview');
  const [selectedJvmKey, setSelectedJvmKey] =
    useState<string | null>(null);

  const [theme, setTheme] = useState<ThemeMode>(() => {
    const stored = localStorage.getItem('ahaythorus-theme');
    return stored === 'light' ? 'light' : 'dark';
  });

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem('ahaythorus-theme', theme);
  }, [theme]);

  const selectedJvm = useMemo(
    () =>
      monitoring.jvms.find(
        (node) => node.key === selectedJvmKey,
      ) ?? null,
    [monitoring.jvms, selectedJvmKey],
  );

  useEffect(() => {
    if (
      selectedJvmKey &&
      !selectedJvm &&
      monitoring.jvms.length > 0
    ) {
      setSelectedJvmKey(null);
      setPage('overview');
    }
  }, [
    selectedJvm,
    selectedJvmKey,
    monitoring.jvms.length,
  ]);

  const openJvm = (key: string) => {
    setSelectedJvmKey(key);
    setPage('jvm');
  };

  const navigate = (
    nextPage: Exclude<PageId, 'jvm'>,
  ) => {
    setPage(nextPage);
  };

  const pageKey: Exclude<PageId, 'jvm'> =
    page === 'jvm' ? 'overview' : page;

  const heading =
    page === 'jvm' && selectedJvm
      ? {
          title: `${selectedJvm.pod.app} · JVM ${selectedJvm.snapshot.pid}`,
          subtitle: `${selectedJvm.pod.namespace}/${selectedJvm.pod.name}`,
        }
      : pageCopy[pageKey];

  return (
    <div className="app-shell">
      <Sidebar
        page={page}
        snapshots={monitoring.snapshots}
        jvms={monitoring.jvms}
        selectedJvmKey={selectedJvmKey}
        onNavigate={navigate}
        onOpenJvm={openJvm}
      />

      <div className="app-main">
        <TopBar
          title={heading.title}
          subtitle={heading.subtitle}
          refreshing={monitoring.refreshing}
          error={monitoring.error}
          lastUpdated={monitoring.lastUpdated}
          theme={theme}
          onRefresh={() => void monitoring.refresh()}
          onToggleTheme={() =>
            setTheme((current: ThemeMode) =>
              current === 'dark' ? 'light' : 'dark',
            )
          }
        />

        <main className="page">
          {monitoring.error &&
          monitoring.snapshots.length > 0 ? (
            <div className="stale-banner">
              Showing the latest successful snapshot.{' '}
              {monitoring.error}
            </div>
          ) : null}

          {page === 'overview' ? (
            <OverviewPage onOpenJvm={openJvm} />
          ) : null}

          {page === 'pods' ? (
            <PodsPage onOpenJvm={openJvm} />
          ) : null}

          {page === 'problems' ? (
            <ProblemsPage onOpenJvm={openJvm} />
          ) : null}

          {page === 'jvm' && selectedJvm ? (
            <JvmPage
              node={selectedJvm}
              history={
                monitoring.historyByJvm[
                  selectedJvm.key
                ] ?? []
              }
            />
          ) : null}

          {monitoring.loading &&
          monitoring.snapshots.length === 0 ? (
            <div className="initial-loading">
              <div className="loader-orbit">
                <span />
              </div>
              <h2>Connecting to A-Haythorus</h2>
              <p>
                Waiting for the first sidecar snapshot…
              </p>
            </div>
          ) : null}
        </main>
      </div>
    </div>
  );
}
