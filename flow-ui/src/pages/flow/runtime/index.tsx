import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import type { ProColumns } from '@ant-design/pro-components';
import { Alert, Button, Empty, Radio, Segmented, Space, Spin, Tabs, Tag, Tooltip, Typography, message } from 'antd';
import {
  AppstoreOutlined,
  ArrowRightOutlined,
  PercentageOutlined,
  ReloadOutlined,
  ThunderboltOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { DualAxes } from '@ant-design/plots';
import { history } from '@umijs/max';
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
import '@/styles/fullHeightTable.css';
import './index.less';

const { Text } = Typography;

const WINDOWS: MetricsWindow[] = ['15m', '1h', '24h', '7d', '30d'];

const AUTO_REFRESH_OPTIONS = [
  { label: '手动', value: 0 },
  { label: '30s', value: 30 },
  { label: '60s', value: 60 },
];

const ASSET_TYPE_LABEL: Record<MetricsAssetType, string> = {
  API: '接口',
  TASK: '任务',
  SERVICE: '服务',
  PLATFORM: '开放平台',
};

const ASSET_TYPE_COLOR: Record<MetricsAssetType, string> = {
  API: 'blue',
  TASK: 'geekblue',
  SERVICE: 'cyan',
  PLATFORM: 'purple',
};

function openAsset(item: AssetMetricsRankItem) {
  const tab = 'runtime';
  if (item.assetType === 'API') {
    history.push(`/flow/api?apiId=${encodeURIComponent(item.assetId)}&tab=${tab}`);
  } else if (item.assetType === 'TASK') {
    history.push(`/flow/task?taskId=${encodeURIComponent(item.assetId)}&tab=${tab}`);
  } else if (item.assetType === 'PLATFORM') {
    history.push(`/flow/open-platform?platformId=${encodeURIComponent(item.assetId)}&tab=${tab}`);
  } else {
    history.push(`/flow/service?serviceId=${encodeURIComponent(item.assetId)}&tab=${tab}`);
  }
}

function fmtPct(v?: number | null) {
  if (v == null || Number.isNaN(v)) return '—';
  return `${(v * 100).toFixed(1)}%`;
}

function fmtMs(v?: number | null) {
  if (v == null || Number.isNaN(v)) return '—';
  return `${v}ms`;
}

/** P95 延迟分档着色：>=1s 危险，>=500ms 告警，其余中性 */
function p95Tone(v?: number | null) {
  if (v == null || Number.isNaN(v)) return 'muted';
  if (v >= 1000) return 'danger';
  if (v >= 500) return 'warn';
  return 'muted';
}

/** 大数值中文单位压缩：12345 → 1.2万 */
function fmtNum(v?: number | null) {
  const n = v ?? 0;
  if (n >= 1e8) return `${(n / 1e8).toFixed(1)}亿`;
  if (n >= 1e4) return `${(n / 1e4).toFixed(1)}万`;
  return String(n);
}

function fmtClock(ts: number) {
  if (!ts) return '—';
  const d = new Date(ts);
  const p = (x: number) => String(x).padStart(2, '0');
  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}

type KpiTone = 'blue' | 'cyan' | 'green' | 'red' | 'orange';

const GRANULARITY_LABEL: Record<string, string> = {
  minute: '每分钟',
  hour: '每小时',
  day: '每天',
};

/** 行内展开：懒加载资产时序，用 @ant-design/charts 双轴折线绘制趋势 */
const RowTrend: React.FC<{ item: AssetMetricsRankItem; window: MetricsWindow }> = ({ item, window }) => {
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

  const points = series?.points || [];
  const n = points.length;

  // 双轴数据：左轴=调用次数（成功/失败，长表 colorField=类型）；右轴=P95(ms)
  const callData = points.flatMap((p) => [
    { time: p.time, 类型: '成功', 次数: p.success },
    { time: p.time, 类型: '失败', 次数: p.fail },
  ]);
  const p95Data = points.map((p) => ({ time: p.time, 指标: 'P95', ms: p.p95Ms ?? 0 }));

  const config = {
    xField: 'time',
    height: 220,
    autoFit: true,
    legend: { color: { position: 'top' as const, layout: { justifyContent: 'flex-start' as const } } },
    tooltip: { title: (d: any) => d.time },
    children: [
      {
        data: callData,
        type: 'line' as const,
        yField: '次数',
        colorField: '类型',
        shapeField: 'smooth',
        scale: { color: { range: ['#52c41a', '#ff4d4f'] } },
        style: { lineWidth: 2 },
        axis: { y: { title: '调用次数', titleFill: '#8c8c8c' } },
      },
      {
        data: p95Data,
        type: 'line' as const,
        yField: 'ms',
        colorField: '指标',
        shapeField: 'smooth',
        scale: { color: { range: ['#faad14'] } },
        style: { lineWidth: 2, lineDash: [4, 4] },
        axis: { y: { position: 'right' as const, title: 'P95 (ms)', titleFill: '#8c8c8c' } },
      },
    ],
  };

  return (
    <div className="rt-detail">
      <div className="rt-detail-chart">
        {loading ? (
          <div className="rt-detail-loading">
            <Spin size="small" />
          </div>
        ) : error ? (
          <Alert type="error" showIcon message={error} banner />
        ) : !n ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={<span className="rt-empty-desc">时间窗内无趋势数据</span>} />
        ) : (
          <>
            <DualAxes {...config} />
            <div className="rt-trend-caption">
              横轴为时间 · {GRANULARITY_LABEL[series?.granularity || ''] || series?.granularity || ''}一个点 · 共 {n} 点
            </div>
          </>
        )}
      </div>
      <div className="rt-detail-side">
        <a className="rt-detail-link" onClick={() => openAsset(item)}>
          查看资产详情 <ArrowRightOutlined />
        </a>
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

  // 自动刷新
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
      tone: 'blue',
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
      hint: kpi.totalCalls > 0 ? `成功 ${fmtNum(kpi.totalCalls - dataset.reduce((s, r) => s + (r.failCount || 0), 0))}` : undefined,
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
          <span className="rt-dot err" />异常 {kpi.error}
          <span className="rt-dot warn" />告警 {kpi.warn}
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
              <Tag color={ASSET_TYPE_COLOR[r.assetType]} style={{ marginInlineEnd: 0 }}>
                {ASSET_TYPE_LABEL[r.assetType] || r.assetType}
              </Tag>
              <span className="runtime-asset-text">
                <a
                  className="runtime-asset-name"
                  onClick={() => openAsset(r)}
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
        title: '连续失败',
        dataIndex: 'consecutiveFail',
        width: 96,
        align: 'right',
        render: (_, r) => (
          <span className={`runtime-num ${(r.consecutiveFail || 0) > 0 ? 'danger' : 'muted'}`}>
            {r.consecutiveFail ?? 0}
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
    /** 仅纵向吸顶；不设 scroll.x / fixed，避免空数据时底部常驻横向滚动条 */
    scroll: { y: 100000 },
    className: 'fh-table runtime-table',
    size: 'middle' as const,
    rowClassName: (r: AssetMetricsRankItem) =>
      r.health === 'error' ? 'rt-row-error' : r.health === 'warn' ? 'rt-row-warn' : '',
    expandable: {
      columnWidth: 36,
      expandedRowClassName: () => 'rt-detail-row',
      expandedRowRender: (r: AssetMetricsRankItem) => (
        <RowTrend item={r} window={window} />
      ),
    },
  };

  const renderEmpty = (desc: string) => (
    <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={<span className="rt-empty-desc">{desc}</span>} />
  );

  return (
    <PageContainer
      className="fh-container fh-runtime runtime-page"
      header={{
        title: '运行中心',
        subTitle: '按时间窗查看资产健康、延迟与异常排行',
      }}
      style={{ height: 'calc(100vh - 26px)', overflow: 'hidden' }}
    >
      <div className="runtime-shell">
        <div className="runtime-toolbar">
          <div className="runtime-toolbar-group">
            <span className="runtime-toolbar-label">时间窗</span>
            <Radio.Group
              optionType="button"
              buttonStyle="solid"
              size="small"
              value={window}
              onChange={(e) => setWindow(e.target.value)}
              options={WINDOWS.map((w) => ({ label: w, value: w }))}
            />
          </div>

          {activeTab === 'rank' && (
            <>
              <div className="runtime-toolbar-group">
                <span className="runtime-toolbar-label">资产</span>
                <Segmented
                  size="small"
                  value={assetType}
                  onChange={(v) => setAssetType(v as MetricsAssetType)}
                  options={[
                    { label: '接口', value: 'API' },
                    { label: '任务', value: 'TASK' },
                    { label: '服务', value: 'SERVICE' },
                    { label: '开放平台', value: 'PLATFORM' },
                  ]}
                />
              </div>
              <div className="runtime-toolbar-group">
                <span className="runtime-toolbar-label">排序</span>
                <Segmented
                  size="small"
                  value={orderBy}
                  onChange={(v) => setOrderBy(v as typeof orderBy)}
                  options={[
                    { label: '错误率', value: 'errorRate' },
                    { label: 'P95', value: 'p95' },
                    { label: '调用量', value: 'calls' },
                  ]}
                />
              </div>
            </>
          )}

          <div className="runtime-meta">
            <span className="runtime-updated">
              更新于 {fmtClock(lastUpdated)}
            </span>
            <Segmented
              size="small"
              value={autoRefresh}
              onChange={(v) => setAutoRefresh(v as number)}
              options={AUTO_REFRESH_OPTIONS}
            />
            <Tooltip title="立即刷新">
              <Button
                size="small"
                type="text"
                icon={<ReloadOutlined spin={loading} />}
                onClick={() => load()}
              />
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
            className="fh-tabs"
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
                      locale={{ emptyText: renderEmpty(`${ASSET_TYPE_LABEL[assetType]}在近 ${window} 暂无调用数据`) }}
                    />
                  </div>
                ),
              },
              {
                key: 'anomalies',
                label: (
                  <Space size={4}>
                    <span>异常资产</span>
                    {anomalies.length > 0 && <Tag color="orange">{anomalies.length}</Tag>}
                  </Space>
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
