import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import type { ProColumns } from '@ant-design/pro-components';
import { Alert, Empty, Spin, Tabs, Tooltip, message } from 'antd';
import {
  AppstoreOutlined,
  ArrowRightOutlined,
  PercentageOutlined,
  ReloadOutlined,
  ThunderboltOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  getAssetMetricsSeries,
  getMetricsAnomalies,
  getMetricsRank,
  type AssetMetricsRankItem,
  type AssetMetricsSeries,
  type MetricsAssetType,
  type MetricsWindow,
} from '@/services/flow/assetMetrics';
import { renderHealthTag } from '@/components/flow/AssetHealthTag';
import {
  SoftSegmented,
  AssetTypeBadge,
  ASSET_TYPE_LABEL,
  openAssetDeepLink,
  MetricsDualAxes,
} from '@/components/flow/ops';
import '@/styles/fullHeightTable.css';
import './index.less';

const WINDOWS: MetricsWindow[] = ['15m', '1h', '24h', '7d', '30d'];

const AUTO_REFRESH_OPTIONS = [
  { label: '手动', value: 0 },
  { label: '30s', value: 30 },
  { label: '60s', value: 60 },
];

const ASSET_TYPE_OPTIONS: Array<{ label: string; value: MetricsAssetType }> = [
  { label: '接口', value: 'API' },
  { label: '任务', value: 'TASK' },
  { label: '服务', value: 'SERVICE' },
  { label: '开放平台', value: 'PLATFORM' },
];

const ORDER_OPTIONS: Array<{ label: string; value: 'errorRate' | 'p95' | 'calls' }> = [
  { label: '错误率', value: 'errorRate' },
  { label: 'P95', value: 'p95' },
  { label: '调用量', value: 'calls' },
];

function fmtPct(v?: number | null) {
  if (v == null || Number.isNaN(v)) return '—';
  return `${(v * 100).toFixed(1)}%`;
}

function fmtMs(v?: number | null) {
  if (v == null || Number.isNaN(v)) return '—';
  if (v >= 1000) return `${(v / 1000).toFixed(2)}s`;
  return `${Math.round(v)}ms`;
}

function p95Tone(v?: number | null) {
  if (v == null || Number.isNaN(v)) return 'muted';
  if (v >= 1000) return 'danger';
  if (v >= 500) return 'warn';
  return 'muted';
}

function fmtNum(v?: number | null) {
  const n = v ?? 0;
  if (n >= 1e8) return `${(n / 1e8).toFixed(1)}亿`;
  if (n >= 1e4) return `${(n / 1e4).toFixed(1)}万`;
  return String(n);
}

function fmtClock(ts: number) {
  if (!ts) return '—';
  return dayjs(ts).format('HH:mm:ss');
}

type KpiTone = 'slate' | 'cyan' | 'green' | 'red' | 'orange';

/** 行内展开：堆叠调用柱 + P95 折线 */
const RowTrend: React.FC<{ item: AssetMetricsRankItem; window: MetricsWindow }> = ({
  item,
  window,
}) => {
  const [loading, setLoading] = useState(true);
  const [series, setSeries] = useState<AssetMetricsSeries | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    setError(null);
    getAssetMetricsSeries(item.assetType as MetricsAssetType, item.assetId, window)
      .then((s) => alive && setSeries(s))
      .catch((e) => alive && setError(e?.message || '加载趋势失败'))
      .finally(() => alive && setLoading(false));
    return () => {
      alive = false;
    };
  }, [item.assetType, item.assetId, window]);

  return (
    <div className="rt-detail">
      <div className="rt-detail-main">
        <div className="rt-detail-head">
          <div className="rt-detail-title">调用量 / P95</div>
        </div>
        <div className="rt-detail-chart">
          {loading ? (
            <div className="rt-detail-loading">
              <Spin size="small" />
            </div>
          ) : error ? (
            <Alert type="error" showIcon message={error} banner />
          ) : (
            <MetricsDualAxes
              points={(series?.points || []).map((p) => ({
                time: p.time,
                success: p.success,
                fail: p.fail,
                p95Ms: p.p95Ms,
              }))}
              window={window}
              granularity={series?.granularity}
              height={180}
              callAxisTitle="调用"
            />
          )}
        </div>
      </div>
      <div className="rt-detail-side">
        <button
          type="button"
          className="rt-detail-link"
          onClick={() =>
            openAssetDeepLink({ assetType: item.assetType, assetId: item.assetId })
          }
        >
          资产运行详情 <ArrowRightOutlined />
        </button>
      </div>
    </div>
  );
};

const RuntimeCenterPage: React.FC = () => {
  const [window, setWindow] = useState<MetricsWindow>('24h');
  const [assetType, setAssetType] = useState<MetricsAssetType>('API');
  const [orderBy, setOrderBy] = useState<'errorRate' | 'p95' | 'calls'>('errorRate');
  const [activeTab, setActiveTab] = useState<'rank' | 'anomalies'>('rank');
  const [rank, setRank] = useState<AssetMetricsRankItem[]>([]);
  const [anomalies, setAnomalies] = useState<AssetMetricsRankItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [autoRefresh, setAutoRefresh] = useState<number>(0);
  const [lastUpdated, setLastUpdated] = useState<number>(0);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [r, a] = await Promise.all([
        getMetricsRank({ assetType, window, orderBy, limit: 30 }),
        getMetricsAnomalies({ window, limit: 30 }),
      ]);
      setRank(r || []);
      setAnomalies(a || []);
      setLastUpdated(Date.now());
    } catch (e: any) {
      message.error(e?.message || '加载运行中心失败');
    } finally {
      setLoading(false);
    }
  }, [assetType, window, orderBy]);

  useEffect(() => {
    load();
  }, [load]);

  const loadRef = useRef(load);
  loadRef.current = load;
  useEffect(() => {
    if (!autoRefresh) return undefined;
    const timer = setInterval(() => loadRef.current(), autoRefresh * 1000);
    return () => clearInterval(timer);
  }, [autoRefresh]);

  const dataset = activeTab === 'rank' ? rank : anomalies;

  const kpi = useMemo(() => {
    const totalCalls = dataset.reduce((s, r) => s + (r.totalCalls || 0), 0);
    const totalFail = dataset.reduce((s, r) => s + (r.failCount || 0), 0);
    return {
      assets: dataset.length,
      totalCalls,
      errorRate: totalCalls > 0 ? totalFail / totalCalls : 0,
      healthy: dataset.filter((r) => r.health === 'ok').length,
      warn: dataset.filter((r) => r.health === 'warn').length,
      error: dataset.filter((r) => r.health === 'error').length,
    };
  }, [dataset]);

  const kpiCards: Array<{
    key: string;
    icon: React.ReactNode;
    tone: KpiTone;
    value: React.ReactNode;
    label: string;
    hint?: React.ReactNode;
  }> = [
    {
      key: 'assets',
      icon: <AppstoreOutlined />,
      tone: 'slate',
      value: kpi.assets,
      label: activeTab === 'rank' ? '监控资产' : '异常资产',
      hint: `近 ${window}`,
    },
    {
      key: 'calls',
      icon: <ThunderboltOutlined />,
      tone: 'cyan',
      value: fmtNum(kpi.totalCalls),
      label: '总调用量',
      hint:
        kpi.totalCalls > 0
          ? `成功 ${fmtNum(kpi.totalCalls - dataset.reduce((s, r) => s + (r.failCount || 0), 0))}`
          : undefined,
    },
    {
      key: 'errorRate',
      icon: <PercentageOutlined />,
      tone: kpi.errorRate > 0 ? 'red' : 'green',
      value: fmtPct(kpi.errorRate),
      label: '整体错误率',
      hint: kpi.errorRate > 0 ? '需关注' : '运行平稳',
    },
    {
      key: 'health',
      icon: <WarningOutlined />,
      tone: kpi.error > 0 ? 'red' : kpi.warn > 0 ? 'orange' : 'green',
      value: kpi.error + kpi.warn,
      label: '待关注',
      hint: (
        <>
          <span className="rt-dot err" />
          异常 {kpi.error}
          <span className="rt-dot warn" />
          告警 {kpi.warn}
        </>
      ),
    },
  ];

  const columns: ProColumns<AssetMetricsRankItem>[] = useMemo(() => {
    const cols: ProColumns<AssetMetricsRankItem>[] = [];
    if (activeTab === 'rank') {
      cols.push({
        title: '#',
        dataIndex: '__index',
        width: 52,
        align: 'center',
        render: (_, __, index) => (
          <span className={`rt-rank ${index < 3 ? `top top-${index + 1}` : ''}`}>{index + 1}</span>
        ),
      });
    }
    cols.push(
      {
        title: '资产',
        dataIndex: 'assetName',
        render: (_, r) => {
          const showId = !!r.assetId && r.assetName && r.assetName !== r.assetId;
          return (
            <span className="runtime-asset">
              <AssetTypeBadge type={r.assetType} />
              <span className="runtime-asset-text">
                <a
                  className="runtime-asset-name"
                  onClick={() =>
                    openAssetDeepLink({ assetType: r.assetType, assetId: r.assetId })
                  }
                  title={r.assetName || r.assetId}
                >
                  {r.assetName || r.assetId}
                </a>
                {showId && (
                  <span className="runtime-asset-id" title={r.assetId}>
                    {r.assetId}
                  </span>
                )}
              </span>
            </span>
          );
        },
      },
      {
        title: '健康',
        dataIndex: 'health',
        width: 96,
        render: (_, r) =>
          renderHealthTag({
            assetType: r.assetType,
            assetId: r.assetId,
            health: r.health,
            successRate: r.successRate,
            consecutiveFail: r.consecutiveFail,
            totalCalls: r.totalCalls,
            window,
          }),
      },
      {
        title: '调用',
        dataIndex: 'totalCalls',
        width: 88,
        align: 'right',
        render: (_, r) => <span className="runtime-num">{fmtNum(r.totalCalls)}</span>,
      },
      {
        title: '成功',
        dataIndex: 'successCount',
        width: 84,
        align: 'right',
        render: (_, r) => <span className="runtime-num muted">{fmtNum(r.successCount)}</span>,
      },
      {
        title: '失败',
        dataIndex: 'failCount',
        width: 84,
        align: 'right',
        render: (_, r) => (
          <span className={`runtime-num ${(r.failCount || 0) > 0 ? 'danger' : 'muted'}`}>
            {fmtNum(r.failCount)}
          </span>
        ),
      },
      {
        title: '成功率',
        dataIndex: 'successRate',
        width: 128,
        render: (_, r) => {
          const rate = r.successRate;
          const pct = rate == null || Number.isNaN(rate) ? null : Math.max(0, Math.min(100, rate * 100));
          const tone = pct == null ? '' : pct >= 99 ? 'ok' : pct >= 95 ? 'warn' : 'bad';
          return (
            <div className="rt-rate">
              <span className={`rt-rate-val ${tone}`}>{fmtPct(rate)}</span>
              <span className="rt-rate-bar">
                <i className={tone} style={{ width: `${pct ?? 0}%` }} />
              </span>
            </div>
          );
        },
      },
      {
        title: '错误率',
        dataIndex: 'errorRate',
        width: 92,
        align: 'right',
        render: (_, r) => (
          <span className={`runtime-num ${(r.errorRate || 0) > 0 ? 'danger' : ''}`}>
            {fmtPct(r.errorRate)}
          </span>
        ),
      },
      {
        title: 'P95',
        dataIndex: 'p95Ms',
        width: 92,
        align: 'right',
        render: (_, r) => <span className={`runtime-num ${p95Tone(r.p95Ms)}`}>{fmtMs(r.p95Ms)}</span>,
      },
      {
        title: (
          <Tooltip title="时间窗内最大连续失败次数（按分钟推导，出现成功即打断）">
            <span>最大连败</span>
          </Tooltip>
        ),
        dataIndex: 'maxConsecutiveFail',
        width: 96,
        align: 'right',
        render: (_, r) => (
          <span className={`runtime-num ${(r.maxConsecutiveFail || 0) > 0 ? 'danger' : 'muted'}`}>
            {r.maxConsecutiveFail ?? 0}
          </span>
        ),
      },
    );
    return cols;
  }, [activeTab, window]);

  const tableProps = {
    search: false as const,
    toolBarRender: false as const,
    pagination: false as const,
    loading,
    columns,
    tableLayout: 'fixed' as const,
    scroll: { y: 100000 },
    className: 'fh-table runtime-table',
    size: 'middle' as const,
    rowClassName: (r: AssetMetricsRankItem) =>
      r.health === 'error' ? 'rt-row-error' : r.health === 'warn' ? 'rt-row-warn' : '',
    expandable: {
      columnWidth: 36,
      expandedRowClassName: () => 'rt-detail-row',
      expandedRowRender: (r: AssetMetricsRankItem) => <RowTrend item={r} window={window} />,
    },
  };

  const renderEmpty = (desc: string) => (
    <Empty
      image={Empty.PRESENTED_IMAGE_SIMPLE}
      description={<span className="rt-empty-desc">{desc}</span>}
    />
  );

  return (
    <PageContainer
      className="fh-container runtime-page"
      header={{
        title: '运行中心',
        subTitle: '资产健康、延迟与异常排行',
      }}
      style={{ height: 'calc(100vh - 26px)', overflow: 'hidden' }}
    >
      <div className="runtime-shell">
        <div className="runtime-toolbar">
          <div className="runtime-toolbar-group">
            <span className="runtime-toolbar-label">时间窗</span>
            <SoftSegmented
              ariaLabel="时间窗"
              value={window}
              options={WINDOWS.map((w) => ({ label: w, value: w }))}
              onChange={(v) => setWindow(v as MetricsWindow)}
            />
          </div>

          {activeTab === 'rank' && (
            <>
              <div className="runtime-toolbar-group">
                <span className="runtime-toolbar-label">资产</span>
                <SoftSegmented
                  ariaLabel="资产类型"
                  value={assetType}
                  options={ASSET_TYPE_OPTIONS}
                  onChange={(v) => setAssetType(v as MetricsAssetType)}
                />
              </div>
              <div className="runtime-toolbar-group">
                <span className="runtime-toolbar-label">排序</span>
                <SoftSegmented
                  ariaLabel="排序"
                  value={orderBy}
                  options={ORDER_OPTIONS}
                  onChange={(v) => setOrderBy(v as typeof orderBy)}
                />
              </div>
            </>
          )}

          <div className="runtime-meta">
            <span className="runtime-updated">更新于 {fmtClock(lastUpdated)}</span>
            <SoftSegmented
              ariaLabel="自动刷新"
              value={autoRefresh}
              options={AUTO_REFRESH_OPTIONS}
              onChange={(v) => setAutoRefresh(v as number)}
            />
            <Tooltip title="立即刷新">
              <button
                type="button"
                className="rt-icon-btn"
                onClick={() => load()}
                aria-label="刷新"
              >
                <ReloadOutlined spin={loading} />
              </button>
            </Tooltip>
          </div>
        </div>

        <div className="runtime-kpis">
          {kpiCards.map((c) => (
            <div className={`rt-kpi tone-${c.tone}`} key={c.key}>
              <span className="rt-kpi-icon">{c.icon}</span>
              <div className="rt-kpi-main">
                <div className="rt-kpi-value">{c.value}</div>
                <div className="rt-kpi-label">{c.label}</div>
              </div>
              {c.hint != null && <div className="rt-kpi-hint">{c.hint}</div>}
            </div>
          ))}
        </div>

        <div className="runtime-body">
          <Tabs
            className="fh-tabs runtime-tabs"
            size="small"
            activeKey={activeTab}
            onChange={(k) => setActiveTab(k as 'rank' | 'anomalies')}
            items={[
              {
                key: 'rank',
                label: '资产排行',
                children: (
                  <div className="runtime-pane">
                    <ProTable<AssetMetricsRankItem>
                      {...tableProps}
                      rowKey={(r) => `${r.assetType}:${r.assetId}`}
                      dataSource={rank}
                      locale={{
                        emptyText: renderEmpty(
                          `${ASSET_TYPE_LABEL[assetType]}在近 ${window} 暂无调用数据`,
                        ),
                      }}
                    />
                  </div>
                ),
              },
              {
                key: 'anomalies',
                label: (
                  <span className="rt-tab-label">
                    异常资产
                    {anomalies.length > 0 && (
                      <span className="rt-tab-badge">{anomalies.length}</span>
                    )}
                  </span>
                ),
                children: (
                  <div className="runtime-pane">
                    <ProTable<AssetMetricsRankItem>
                      {...tableProps}
                      rowKey={(r) => `${r.assetType}:${r.assetId}`}
                      dataSource={anomalies}
                      locale={{ emptyText: renderEmpty(`近 ${window} 暂无异常资产`) }}
                    />
                  </div>
                ),
              },
            ]}
          />
        </div>
      </div>
    </PageContainer>
  );
};

export default RuntimeCenterPage;
