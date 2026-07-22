import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Empty, Radio, Space, Spin, Statistic, Typography, theme } from 'antd';
import { history } from '@umijs/max';
import {
  getAssetMetricsSeries,
  getAssetMetricsSummary,
  type AssetMetricsSeries,
  type AssetMetricsSummary,
  type MetricsAssetType,
  type MetricsWindow,
} from '../services/assetMetrics';

const { Text, Link } = Typography;

const WINDOWS: MetricsWindow[] = ['15m', '1h', '24h', '7d', '30d'];

export interface AssetRuntimePanelProps {
  assetType: MetricsAssetType;
  assetId?: string;
  /** 跳转日志页时的 query 键名 */
  logPath?: string;
  logQueryKey?: string;
}

function fmtRate(v?: number | null) {
  if (v == null || Number.isNaN(v)) return '—';
  return `${(v * 100).toFixed(1)}%`;
}

function fmtMs(v?: number | null) {
  if (v == null || Number.isNaN(v)) return '—';
  if (v >= 1000) return `${(v / 1000).toFixed(2)}s`;
  return `${Math.round(v)}ms`;
}

function fmtTime(ms?: number | null) {
  if (!ms) return '—';
  try {
    return new Date(ms).toLocaleString();
  } catch {
    return '—';
  }
}

function logHref(assetType: MetricsAssetType, assetId: string, logPath?: string, logQueryKey?: string) {
  const path =
    logPath ||
    (assetType === 'API'
      ? '/log/execution'
      : assetType === 'TASK'
        ? '/log/task'
        : '/log/service');
  const key = logQueryKey || (assetType === 'API' ? 'apiId' : assetType === 'TASK' ? 'taskId' : 'serviceId');
  return `${path}?${key}=${encodeURIComponent(assetId)}`;
}

/** 轻量折线（无额外图表依赖） */
const MiniTrend: React.FC<{ series?: AssetMetricsSeries | null }> = ({ series }) => {
  const { token } = theme.useToken();
  const points = series?.points || [];
  if (!points.length) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="时间窗内无趋势数据" />;
  }
  const w = 640;
  const h = 160;
  const pad = 16;
  const maxY = Math.max(1, ...points.map((p) => p.success + p.fail + p.skipped));
  const n = points.length;
  const xAt = (i: number) => pad + (n <= 1 ? 0 : (i / (n - 1)) * (w - pad * 2));
  const yAt = (v: number) => h - pad - (v / maxY) * (h - pad * 2);
  const line = (key: 'success' | 'fail', color: string) => {
    const d = points
      .map((p, i) => `${i === 0 ? 'M' : 'L'} ${xAt(i)} ${yAt(p[key])}`)
      .join(' ');
    return <path d={d} fill="none" stroke={color} strokeWidth={2} />;
  };
  return (
    <div style={{ overflowX: 'auto' }}>
      <svg width={w} height={h} role="img" aria-label="调用量趋势">
        <line x1={pad} y1={h - pad} x2={w - pad} y2={h - pad} stroke={token.colorBorderSecondary} />
        {line('success', token.colorSuccess)}
        {line('fail', token.colorError)}
      </svg>
      <div style={{ display: 'flex', gap: 16, fontSize: 12, color: token.colorTextSecondary }}>
        <span>成功</span>
        <span style={{ color: token.colorError }}>失败</span>
        <span>
          {points[0]?.time} → {points[points.length - 1]?.time}（{series?.granularity}）
        </span>
      </div>
    </div>
  );
};

const AssetRuntimePanel: React.FC<AssetRuntimePanelProps> = ({
  assetType,
  assetId,
  logPath,
  logQueryKey,
}) => {
  const [window, setWindow] = useState<MetricsWindow>('24h');
  const [loading, setLoading] = useState(false);
  const [summary, setSummary] = useState<AssetMetricsSummary | null>(null);
  const [series, setSeries] = useState<AssetMetricsSeries | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!assetId) return;
    setLoading(true);
    setError(null);
    try {
      const [s, ser] = await Promise.all([
        getAssetMetricsSummary(assetType, assetId, window),
        getAssetMetricsSeries(assetType, assetId, window),
      ]);
      setSummary(s);
      setSeries(ser);
    } catch (e: any) {
      setError(e?.message || '加载运行指标失败');
    } finally {
      setLoading(false);
    }
  }, [assetType, assetId, window]);

  useEffect(() => {
    load();
  }, [load]);

  const empty = useMemo(
    () => !!summary && summary.totalCalls <= 0 && !summary.consecutiveFail,
    [summary],
  );

  if (!assetId) {
    return <Alert type="info" showIcon message="保存资产后可查看运行计量" />;
  }

  return (
    <div style={{ padding: '12px 4px 24px', maxWidth: 960 }}>
      <Space style={{ marginBottom: 16 }} wrap>
        <Text type="secondary">时间窗</Text>
        <Radio.Group
          optionType="button"
          buttonStyle="solid"
          value={window}
          onChange={(e) => setWindow(e.target.value)}
          options={WINDOWS.map((w) => ({ label: w, value: w }))}
        />
        <Button onClick={load} loading={loading}>
          刷新
        </Button>
        <Link
          onClick={() => history.push(logHref(assetType, assetId, logPath, logQueryKey))}
        >
          查看执行日志
        </Link>
      </Space>

      {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 12 }} />}

      <Spin spinning={loading}>
        {empty ? (
          <Empty description={`时间窗 ${window} 内无执行`} />
        ) : (
          <>
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fill, minmax(140px, 1fr))',
                gap: 16,
                marginBottom: 20,
              }}
            >
              <Statistic title="总调用" value={summary?.totalCalls ?? 0} />
              <Statistic title="成功" value={summary?.successCount ?? 0} />
              <Statistic title="失败" value={summary?.failCount ?? 0} />
              <Statistic title="跳过" value={summary?.skippedCount ?? 0} />
              <Statistic title="成功率" value={fmtRate(summary?.successRate)} />
              <Statistic title="平均耗时" value={fmtMs(summary?.avgCostMs)} />
              <Statistic title="P50" value={fmtMs(summary?.p50Ms)} />
              <Statistic title="P95" value={fmtMs(summary?.p95Ms)} />
              <Statistic title="P99" value={fmtMs(summary?.p99Ms)} />
              <Statistic title="连续失败" value={summary?.consecutiveFail ?? 0} />
            </div>
            <Space direction="vertical" size={4} style={{ marginBottom: 16 }}>
              <Text type="secondary">最近成功：{fmtTime(summary?.lastSuccessAt)}</Text>
              <Text type="secondary">最近失败：{fmtTime(summary?.lastFailAt)}</Text>
              <Text type="secondary" style={{ fontSize: 12 }}>
                分位数为直方图近似；计量与执行日志开关无关。
              </Text>
            </Space>
            <Text strong style={{ display: 'block', marginBottom: 8 }}>
              调用量趋势（{window}）
            </Text>
            <MiniTrend series={series} />
          </>
        )}
      </Spin>
    </div>
  );
};

export default AssetRuntimePanel;
