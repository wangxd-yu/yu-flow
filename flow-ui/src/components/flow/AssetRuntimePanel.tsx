import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Empty, Spin, Typography } from 'antd';
import { ReloadOutlined, RightOutlined } from '@ant-design/icons';
import { DualAxes } from '@ant-design/plots';
import dayjs from 'dayjs';
import { history, request } from '@umijs/max';
import {
  getAssetMetricsSeries,
  getAssetMetricsSummary,
  type AssetMetricsSummary,
  type MetricsAssetType,
  type MetricsWindow,
} from '@/services/flow/assetMetrics';
import { queryTaskLogPage } from '@/services/flow/taskService';
import { queryServiceLogPage } from '@/services/flow/serviceFlowService';
import { pageOpenCallLogs } from '@/services/flow/openPlatformService';
import './AssetRuntimePanel.less';

const { Link } = Typography;

const WINDOWS: MetricsWindow[] = ['15m', '1h', '24h', '7d', '30d'];
const RECENT_LOG_LIMIT = 6;

type SeriesPoint = {
  time: string;
  success: number;
  fail: number;
  skipped: number;
  p95Ms?: number | null;
};

export interface AssetRuntimePanelProps {
  assetType: MetricsAssetType;
  assetId?: string;
  logPath?: string;
  logQueryKey?: string;
}

type RecentLogItem = {
  id: string;
  ok: boolean;
  statusText: string;
  costMs?: number | null;
  createTime?: string;
  subtitle?: string;
};

function fmtRate(v?: number | null) {
  if (v == null || Number.isNaN(v)) return '—';
  return `${(v * 100).toFixed(1)}%`;
}

function fmtMs(v?: number | null) {
  if (v == null || Number.isNaN(v)) return '—';
  if (v >= 1000) return `${(v / 1000).toFixed(2)}s`;
  return `${Math.round(v)}ms`;
}

function fmtNum(v?: number | null) {
  const n = v ?? 0;
  if (n >= 1e8) return `${(n / 1e8).toFixed(1)}亿`;
  if (n >= 1e4) return `${(n / 1e4).toFixed(1)}万`;
  return String(n);
}

function fmtTime(ms?: number | null) {
  if (!ms) return '—';
  try {
    return dayjs(ms).format('MM-DD HH:mm:ss');
  } catch {
    return '—';
  }
}

function parseLogTime(raw?: string) {
  if (!raw) return null;
  const d = dayjs(raw.includes('T') ? raw : raw.replace(' ', 'T'));
  return d.isValid() ? d : null;
}

function fmtRelative(raw?: string) {
  const d = parseLogTime(raw);
  if (!d) return '—';
  const mins = dayjs().diff(d, 'minute');
  if (mins < 1) return '刚刚';
  if (mins < 60) return `${mins} 分钟前`;
  const hours = dayjs().diff(d, 'hour');
  if (hours < 24) return `${hours} 小时前`;
  return d.format('MM-DD HH:mm');
}

function fmtAxisTime(raw: string, win: MetricsWindow, bucket: string): string {
  if (!raw) return '';
  const d = dayjs(raw.includes('T') ? raw : raw.replace(' ', 'T'));
  if (!d.isValid()) return raw;
  if (win === '7d' || win === '30d') return d.format('MM-DD');
  if (bucket === 'hour') return d.format('HH:00');
  return d.format('HH:mm');
}

/** 按小时聚合（前端兜底：即使后端仍返回分钟，24h/7d/30d 也清晰可读） */
function aggregateByHour(points: SeriesPoint[]): SeriesPoint[] {
  const map = new Map<string, SeriesPoint>();
  for (const p of points) {
    const d = dayjs(p.time.includes('T') ? p.time : p.time.replace(' ', 'T'));
    if (!d.isValid()) continue;
    const key = d.startOf('hour').format('YYYY-MM-DD HH:mm:ss');
    const cur = map.get(key) || {
      time: key,
      success: 0,
      fail: 0,
      skipped: 0,
      p95Ms: 0,
    };
    cur.success += p.success;
    cur.fail += p.fail;
    cur.skipped += p.skipped;
    cur.p95Ms = Math.max(cur.p95Ms ?? 0, p.p95Ms ?? 0);
    map.set(key, cur);
  }
  return Array.from(map.values()).sort((a, b) => a.time.localeCompare(b.time));
}

function rateTone(v?: number | null): '' | 'ok' | 'warn' | 'bad' {
  if (v == null || Number.isNaN(v)) return '';
  const pct = v * 100;
  return pct >= 99 ? 'ok' : pct >= 95 ? 'warn' : 'bad';
}

function p95Tone(v?: number | null): '' | 'warn' | 'bad' {
  if (v == null || Number.isNaN(v)) return '';
  if (v >= 1000) return 'bad';
  if (v >= 500) return 'warn';
  return '';
}

function logHref(assetType: MetricsAssetType, assetId: string, logPath?: string, logQueryKey?: string) {
  if (logPath) {
    const key = logQueryKey || 'id';
    return `${logPath}?${key}=${encodeURIComponent(assetId)}`;
  }
  if (assetType === 'PLATFORM') {
    return `/flow/open-platform?platformId=${encodeURIComponent(assetId)}&tab=callLogs`;
  }
  const path =
    assetType === 'API' ? '/log/execution' : assetType === 'TASK' ? '/log/task' : '/log/service';
  const key = logQueryKey || (assetType === 'API' ? 'apiId' : assetType === 'TASK' ? 'taskId' : 'serviceId');
  return `${path}?${key}=${encodeURIComponent(assetId)}`;
}

async function fetchRecentLogs(assetType: MetricsAssetType, assetId: string): Promise<RecentLogItem[]> {
  if (assetType === 'API') {
    const result = await request('/flow-api/log/execution/page', {
      method: 'GET',
      params: { apiId: assetId, page: 0, size: RECENT_LOG_LIMIT },
    });
    return (result?.items || []).map((r: any) => ({
      id: r.id,
      ok: r.status === 'SUCCESS',
      statusText: r.status === 'SUCCESS' ? '成功' : '失败',
      costMs: r.costTimeMs,
      createTime: r.createTime,
      subtitle: r.method || undefined,
    }));
  }
  if (assetType === 'TASK') {
    const result = await queryTaskLogPage({ taskId: assetId, page: 0, size: RECENT_LOG_LIMIT });
    const data = (result as any)?.data || result;
    return (data?.items || []).map((r: any) => ({
      id: r.id,
      ok: r.status === 'SUCCESS',
      statusText:
        r.status === 'SUCCESS' ? '成功' : r.status === 'SKIPPED' ? '跳过' : r.status === 'RUNNING' ? '运行中' : '失败',
      costMs: r.costTimeMs,
      createTime: r.createTime,
      subtitle: r.triggerType,
    }));
  }
  if (assetType === 'SERVICE') {
    const result = await queryServiceLogPage({ serviceId: assetId, page: 0, size: RECENT_LOG_LIMIT });
    const data = (result as any)?.data || result;
    return (data?.items || []).map((r: any) => ({
      id: r.id,
      ok: r.status === 'SUCCESS',
      statusText:
        r.status === 'SUCCESS' ? '成功' : r.status === 'SKIPPED' ? '跳过' : r.status === 'RUNNING' ? '运行中' : '失败',
      costMs: r.costTimeMs,
      createTime: r.createTime,
      subtitle: r.triggerType,
    }));
  }
  const data = await pageOpenCallLogs(assetId, { page: 0, size: RECENT_LOG_LIMIT });
  return (data?.items || []).map((r) => {
    const ok = (r.status ?? 0) >= 200 && (r.status ?? 0) < 400 && !r.errorCode;
    return {
      id: r.id,
      ok,
      statusText: ok ? '成功' : r.errorCode || String(r.status ?? '失败'),
      costMs: r.costMs,
      createTime: r.createTime,
      subtitle: [r.method, r.path].filter(Boolean).join(' '),
    };
  });
}

const StatCard: React.FC<{
  label: string;
  value: React.ReactNode;
  tone?: '' | 'ok' | 'warn' | 'bad';
}> = ({ label, value, tone = '' }) => (
  <div className={`arp-stat${tone ? ` tone-${tone}` : ''}`}>
    <div className="arp-stat-label">{label}</div>
    <div className="arp-stat-value">{value}</div>
  </div>
);

const AssetRuntimePanel: React.FC<AssetRuntimePanelProps> = ({
  assetType,
  assetId,
  logPath,
  logQueryKey,
}) => {
  const [window, setWindow] = useState<MetricsWindow>('24h');
  const [loading, setLoading] = useState(false);
  const [summary, setSummary] = useState<AssetMetricsSummary | null>(null);
  const [seriesPoints, setSeriesPoints] = useState<SeriesPoint[]>([]);
  const [granularity, setGranularity] = useState<string>('');
  const [error, setError] = useState<string | null>(null);
  const [recentLogs, setRecentLogs] = useState<RecentLogItem[]>([]);
  const [logsLoading, setLogsLoading] = useState(false);
  const [logFilter, setLogFilter] = useState<'all' | 'fail'>('all');
  const [seriesVisible, setSeriesVisible] = useState({
    success: true,
    fail: true,
    p95: true,
  });

  const toggleSeries = useCallback((key: 'success' | 'fail' | 'p95') => {
    setSeriesVisible((prev) => {
      const next = { ...prev, [key]: !prev[key] };
      if (!next.success && !next.fail && !next.p95) return prev;
      return next;
    });
  }, []);

  const chartWrapRef = useRef<HTMLDivElement>(null);
  const [chartWidth, setChartWidth] = useState(0);
  useEffect(() => {
    const el = chartWrapRef.current;
    if (!el || typeof ResizeObserver === 'undefined') return undefined;
    const ro = new ResizeObserver((entries) => {
      const w = Math.floor(entries[0]?.contentRect?.width || 0);
      if (w > 0) setChartWidth(w);
    });
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  const loadMetrics = useCallback(async () => {
    if (!assetId) return;
    setLoading(true);
    setError(null);
    try {
      const [s, ser] = await Promise.all([
        getAssetMetricsSummary(assetType, assetId, window),
        getAssetMetricsSeries(assetType, assetId, window),
      ]);
      setSummary(s);
      setSeriesPoints(ser?.points || []);
      setGranularity(ser?.granularity || '');
    } catch (e: any) {
      setError(e?.message || '加载运行指标失败');
    } finally {
      setLoading(false);
    }
  }, [assetType, assetId, window]);

  const loadLogs = useCallback(async () => {
    if (!assetId) return;
    setLogsLoading(true);
    try {
      setRecentLogs(await fetchRecentLogs(assetType, assetId));
    } catch {
      setRecentLogs([]);
    } finally {
      setLogsLoading(false);
    }
  }, [assetType, assetId]);

  const load = useCallback(async () => {
    await Promise.all([loadMetrics(), loadLogs()]);
  }, [loadMetrics, loadLogs]);

  useEffect(() => {
    loadMetrics();
  }, [loadMetrics]);

  useEffect(() => {
    loadLogs();
  }, [loadLogs]);

  const empty = useMemo(
    () => !!summary && summary.totalCalls <= 0 && !summary.consecutiveFail,
    [summary],
  );

  // 长窗口：强制小时桶（不依赖后端是否已重启）
  const { chartPoints, displayBucket } = useMemo(() => {
    const longWin = window === '24h' || window === '7d' || window === '30d';
    if (longWin && (granularity !== 'hour' || seriesPoints.length > 36)) {
      return { chartPoints: aggregateByHour(seriesPoints), displayBucket: 'hour' };
    }
    return {
      chartPoints: seriesPoints,
      displayBucket: granularity === 'hour' ? 'hour' : 'minute',
    };
  }, [seriesPoints, window, granularity]);

  const chartConfig = useMemo(() => {
    const callData: Array<{ time: string; type: string; value: number }> = [];
    for (const p of chartPoints) {
      if (seriesVisible.success) callData.push({ time: p.time, type: '成功', value: p.success });
      if (seriesVisible.fail) callData.push({ time: p.time, type: '失败', value: p.fail });
    }
    const p95Data = seriesVisible.p95
      ? chartPoints.map((p) => ({ time: p.time, value: p.p95Ms ?? 0 }))
      : [];

    const barMaxW =
      chartPoints.length <= 28 ? 28 : chartPoints.length <= 48 ? 16 : 10;

    const children: any[] = [];
    if (callData.length) {
      children.push({
        data: callData,
        type: 'interval',
        yField: 'value',
        colorField: 'type',
        stack: true,
        scale: {
          color: {
            domain: ['成功', '失败'],
            range: ['#34d399', '#f87171'],
          },
        },
        style: {
          maxWidth: barMaxW,
          radiusTopLeft: 3,
          radiusTopRight: 3,
        },
        axis: {
          y: {
            title: '调用次数',
            titleFill: '#94a3b8',
            titleFontSize: 11,
            labelFill: '#94a3b8',
            grid: true,
            gridStrokeOpacity: 0.35,
          },
        },
        tooltip: {
          items: [{ channel: 'y', name: '次数' }],
        },
      });
    }
    if (p95Data.length) {
      children.push({
        data: p95Data,
        type: 'line',
        yField: 'value',
        shapeField: 'smooth',
        style: {
          stroke: '#f59e0b',
          lineWidth: 2.5,
        },
        axis: {
          y: {
            position: 'right',
            title: 'P95 (ms)',
            titleFill: '#94a3b8',
            titleFontSize: 11,
            labelFill: '#94a3b8',
            grid: null,
          },
        },
        tooltip: {
          items: [{ channel: 'y', name: 'P95', valueFormatter: (v: number) => `${Math.round(v)} ms` }],
        },
      });
    }

    return {
      xField: 'time',
      height: 200,
      paddingLeft: 44,
      paddingRight: 48,
      paddingBottom: 28,
      legend: false,
      axis: {
        x: {
          labelAutoRotate: false,
          labelAutoHide: true,
          labelFill: '#94a3b8',
          labelFontSize: 11,
          labelFormatter: (v: string) => fmtAxisTime(v, window, displayBucket),
          tick: false,
        },
      },
      tooltip: {
        shared: true,
        title: (d: any) => {
          const t = d?.time || d?.[0]?.time;
          return t ? fmtAxisTime(String(t), window, displayBucket) : '';
        },
      },
      interaction: {
        tooltip: {
          shared: true,
          crosshairs: true,
          crosshairsY: false,
        },
      },
      children,
    };
  }, [chartPoints, window, displayBucket, seriesVisible]);

  const filteredLogs = useMemo(() => {
    if (logFilter === 'fail') return recentLogs.filter((l) => !l.ok);
    return recentLogs;
  }, [recentLogs, logFilter]);

  const logStats = useMemo(() => {
    let ok = 0;
    let fail = 0;
    for (const l of recentLogs) {
      if (l.ok) ok += 1;
      else fail += 1;
    }
    return { ok, fail, total: recentLogs.length };
  }, [recentLogs]);

  if (!assetId) {
    return <Alert type="info" showIcon message="保存资产后可查看运行计量" />;
  }

  const showAuthFail = assetType === 'PLATFORM' || (summary?.authFailCount ?? 0) > 0;
  const ratePct =
    summary?.successRate == null ? 0 : Math.max(0, Math.min(100, summary.successRate * 100));
  const rTone = rateTone(summary?.successRate);
  const failCount = summary?.failCount ?? 0;
  const consecutiveFail = summary?.consecutiveFail ?? 0;
  const logsTitle = assetType === 'PLATFORM' ? '最近调用' : '最近执行';
  const fullLogHref = logHref(assetType, assetId, logPath, logQueryKey);

  const windowTools = (
    <div className="arp-panel-tools">
      <div className="arp-seg" role="group" aria-label="时间窗">
        {WINDOWS.map((w) => (
          <button
            key={w}
            type="button"
            className={window === w ? 'is-active' : ''}
            onClick={() => setWindow(w)}
          >
            {w}
          </button>
        ))}
      </div>
      <button
        type="button"
        className="arp-icon-btn"
        onClick={load}
        title="刷新"
        aria-label="刷新"
      >
        <ReloadOutlined spin={loading || logsLoading} />
      </button>
    </div>
  );

  return (
    <div className="arp">
      {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 10 }} />}

      <Spin spinning={loading}>
        <section className="arp-panel arp-panel-kpi">
          <div className="arp-panel-head">
            <div className="arp-panel-title">
              运行概览
              {!empty && (
                <span className="arp-panel-meta">
                  近 {window} · 最近成功 {fmtTime(summary?.lastSuccessAt)} · 最近失败{' '}
                  {fmtTime(summary?.lastFailAt)}
                </span>
              )}
            </div>
            {windowTools}
          </div>

          {empty ? (
            <Empty description={`时间窗 ${window} 内无执行`} style={{ padding: '32px 0' }} />
          ) : (
            <div className="arp-kpi-row">
              <div className={`arp-rate tone-${rTone || 'ok'}`}>
                <div className="arp-rate-top">
                  <span className="arp-rate-label">成功率</span>
                  <span className="arp-rate-value">{fmtRate(summary?.successRate)}</span>
                </div>
                <div className="arp-rate-bar">
                  <i className={rTone} style={{ width: `${ratePct}%` }} />
                </div>
              </div>

              <div className="arp-stats">
                <StatCard label="总调用" value={fmtNum(summary?.totalCalls)} />
                <StatCard label="成功" value={fmtNum(summary?.successCount)} tone="ok" />
                <StatCard label="失败" value={fmtNum(failCount)} tone={failCount > 0 ? 'bad' : ''} />
                <StatCard label="跳过" value={fmtNum(summary?.skippedCount)} />
                {showAuthFail && (
                  <StatCard
                    label="鉴权失败"
                    value={fmtNum(summary?.authFailCount)}
                    tone={(summary?.authFailCount ?? 0) > 0 ? 'bad' : ''}
                  />
                )}
                <StatCard
                  label="连续失败"
                  value={consecutiveFail}
                  tone={consecutiveFail > 0 ? 'bad' : ''}
                />
                <StatCard label="平均耗时" value={fmtMs(summary?.avgCostMs)} />
                <StatCard label="P50" value={fmtMs(summary?.p50Ms)} />
                <StatCard label="P95" value={fmtMs(summary?.p95Ms)} tone={p95Tone(summary?.p95Ms)} />
                <StatCard label="P99" value={fmtMs(summary?.p99Ms)} tone={p95Tone(summary?.p99Ms)} />
              </div>
            </div>
          )}
        </section>

        {!empty && (
          <>

            <section className="arp-panel arp-trend">
              <div className="arp-panel-head">
                <div className="arp-panel-title">
                  调用量 / P95
                  <span className="arp-panel-meta">
                    {chartPoints.length} 点 · 每点 {displayBucket === 'hour' ? '1 小时' : '1 分钟'}
                  </span>
                </div>
                <div className="arp-trend-legend" role="group" aria-label="图例开关">
                  {(
                    [
                      ['success', 'ok', '成功'],
                      ['fail', 'bad', '失败'],
                      ['p95', 'p95', 'P95'],
                    ] as const
                  ).map(([key, icon, label]) => (
                    <button
                      key={key}
                      type="button"
                      className={`arp-lg${seriesVisible[key] ? '' : ' is-off'}`}
                      onClick={() => toggleSeries(key)}
                    >
                      <i className={icon} />
                      {label}
                    </button>
                  ))}
                </div>
              </div>
              <div className="arp-trend-canvas" ref={chartWrapRef}>
                {chartPoints.length && chartConfig.children.length && chartWidth > 0 ? (
                  <DualAxes {...chartConfig} autoFit={false} width={chartWidth} />
                ) : (
                  <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description={
                      chartPoints.length ? '请至少勾选一条图例系列' : '时间窗内无趋势数据'
                    }
                    style={{ padding: '24px 0' }}
                  />
                )}
              </div>
            </section>

            <section className="arp-panel arp-activity">
              <div className="arp-panel-head">
                <div className="arp-panel-title">
                  {logsTitle}
                  <span className="arp-panel-meta">
                    {logStats.ok} 成功
                    {logStats.fail > 0 ? ` · ${logStats.fail} 失败` : ''}
                  </span>
                </div>
                <div className="arp-activity-actions">
                  <div className="arp-seg">
                    <button
                      type="button"
                      className={logFilter === 'all' ? 'is-active' : ''}
                      onClick={() => setLogFilter('all')}
                    >
                      全部
                    </button>
                    <button
                      type="button"
                      className={logFilter === 'fail' ? 'is-active is-fail' : ''}
                      onClick={() => setLogFilter('fail')}
                    >
                      失败
                    </button>
                  </div>
                  <Link className="arp-activity-more" onClick={() => history.push(fullLogHref)}>
                    全部日志 <RightOutlined />
                  </Link>
                </div>
              </div>

              <Spin spinning={logsLoading}>
                {filteredLogs.length ? (
                  <div className="arp-feed">
                    {filteredLogs.map((log) => (
                      <button
                        key={log.id}
                        type="button"
                        className={`arp-feed-item${log.ok ? '' : ' is-fail'}`}
                        onClick={() => history.push(fullLogHref)}
                      >
                        <span className={`arp-feed-badge${log.ok ? ' ok' : ' bad'}`}>
                          {log.statusText}
                        </span>
                        <span className="arp-feed-meta">
                          <span className="arp-feed-rel">{fmtRelative(log.createTime)}</span>
                          {log.subtitle ? (
                            <span className="arp-feed-sub">{log.subtitle}</span>
                          ) : null}
                        </span>
                        <span className="arp-feed-cost">{fmtMs(log.costMs)}</span>
                      </button>
                    ))}
                  </div>
                ) : (
                  <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description={logFilter === 'fail' ? '最近没有失败记录' : '暂无执行日志'}
                    style={{ padding: '12px 0' }}
                  />
                )}
              </Spin>
            </section>
          </>
        )}
      </Spin>
    </div>
  );
};

export default AssetRuntimePanel;
