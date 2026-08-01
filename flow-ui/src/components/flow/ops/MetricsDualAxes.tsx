import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { DualAxes } from '@ant-design/plots';
import { Empty } from 'antd';
import dayjs from 'dayjs';
import type { MetricsWindow } from '@/services/flow/assetMetrics';
import './MetricsDualAxes.less';

export type MetricsSeriesPoint = {
  time: string;
  success: number;
  fail: number;
  p95Ms?: number | null;
};

export type SeriesVisible = {
  success: boolean;
  fail: boolean;
  p95: boolean;
};

function fmtAxisTime(raw: string, win: MetricsWindow, bucket: string) {
  if (!raw) return '';
  const d = dayjs(raw.includes('T') ? raw : raw.replace(' ', 'T'));
  if (!d.isValid()) return raw;
  if (win === '7d' || win === '30d') return d.format('MM-DD');
  if (bucket === 'hour') return d.format('HH:00');
  return d.format('HH:mm');
}

export function aggregateByHour(points: MetricsSeriesPoint[]): MetricsSeriesPoint[] {
  const map = new Map<string, MetricsSeriesPoint>();
  for (const p of points) {
    const d = dayjs(p.time.includes('T') ? p.time : p.time.replace(' ', 'T'));
    if (!d.isValid()) continue;
    const key = d.startOf('hour').format('YYYY-MM-DD HH:mm:ss');
    const cur = map.get(key) || { time: key, success: 0, fail: 0, p95Ms: 0 };
    cur.success += p.success;
    cur.fail += p.fail;
    cur.p95Ms = Math.max(cur.p95Ms ?? 0, p.p95Ms ?? 0);
    map.set(key, cur);
  }
  return Array.from(map.values()).sort((a, b) => a.time.localeCompare(b.time));
}

export function aggregateByDay(points: MetricsSeriesPoint[]): MetricsSeriesPoint[] {
  const map = new Map<string, MetricsSeriesPoint>();
  for (const p of points) {
    const d = dayjs(p.time.includes('T') ? p.time : p.time.replace(' ', 'T'));
    if (!d.isValid()) continue;
    const key = d.startOf('day').format('YYYY-MM-DD HH:mm:ss');
    const cur = map.get(key) || { time: key, success: 0, fail: 0, p95Ms: 0 };
    cur.success += p.success;
    cur.fail += p.fail;
    cur.p95Ms = Math.max(cur.p95Ms ?? 0, p.p95Ms ?? 0);
    map.set(key, cur);
  }
  return Array.from(map.values()).sort((a, b) => a.time.localeCompare(b.time));
}

export function prepareChartPoints(
  points: MetricsSeriesPoint[],
  window: MetricsWindow,
  granularity?: string,
): { chartPoints: MetricsSeriesPoint[]; displayBucket: string } {
  const gran = granularity || '';
  // 7d / 30d → 按天，一天一根柱子；后端已按天聚合则直接用，否则本地兜底
  if (window === '7d' || window === '30d') {
    if (gran === 'day') {
      return { chartPoints: points, displayBucket: 'day' };
    }
    return { chartPoints: aggregateByDay(points), displayBucket: 'day' };
  }
  // 24h → 按小时聚合
  if (window === '24h') {
    if (gran !== 'hour' || points.length > 36) {
      return { chartPoints: aggregateByHour(points), displayBucket: 'hour' };
    }
    return { chartPoints: points, displayBucket: 'hour' };
  }
  // 15m / 1h → 保持分钟粒度
  return {
    chartPoints: points,
    displayBucket: gran === 'hour' ? 'hour' : 'minute',
  };
}

export type MetricsDualAxesProps = {
  points: MetricsSeriesPoint[];
  window: MetricsWindow;
  granularity?: string;
  height?: number;
  callAxisTitle?: string;
  showLegend?: boolean;
  emptyDescription?: string;
};

/** 调用量堆叠柱 + P95 折线；图例可开关；浮窗固定成功/失败/P95 */
const MetricsDualAxes: React.FC<MetricsDualAxesProps> = ({
  points,
  window,
  granularity,
  height = 200,
  callAxisTitle = '调用',
  showLegend = true,
  emptyDescription = '时间窗内无趋势数据',
}) => {
  const [seriesVisible, setSeriesVisible] = useState<SeriesVisible>({
    success: true,
    fail: true,
    p95: true,
  });
  const chartWrapRef = useRef<HTMLDivElement>(null);
  const [chartWidth, setChartWidth] = useState(0);

  const toggleSeries = useCallback((key: keyof SeriesVisible) => {
    setSeriesVisible((prev) => {
      const next = { ...prev, [key]: !prev[key] };
      if (!next.success && !next.fail && !next.p95) return prev;
      return next;
    });
  }, []);

  const { chartPoints, displayBucket } = useMemo(
    () => prepareChartPoints(points, window, granularity),
    [points, window, granularity],
  );

  useEffect(() => {
    const el = chartWrapRef.current;
    if (!el || typeof ResizeObserver === 'undefined') return undefined;
    const ro = new ResizeObserver((entries) => {
      const w = Math.floor(entries[0]?.contentRect?.width || 0);
      if (w > 0) setChartWidth(w);
    });
    ro.observe(el);
    return () => ro.disconnect();
  }, [chartPoints.length]);

  const callData: Array<{ time: string; type: string; value: number }> = [];
  for (const p of chartPoints) {
    if (seriesVisible.success) callData.push({ time: p.time, type: '成功', value: p.success });
    if (seriesVisible.fail) callData.push({ time: p.time, type: '失败', value: p.fail });
  }
  const p95Data = seriesVisible.p95
    ? chartPoints.map((p) => ({ time: p.time, metric: 'P95', value: p.p95Ms ?? 0 }))
    : [];
  const barMaxW = chartPoints.length <= 28 ? 22 : chartPoints.length <= 48 ? 14 : 8;

  const pointByTime = useMemo(() => new Map(chartPoints.map((p) => [p.time, p])), [chartPoints]);

  const config = useMemo(() => {
    const children: any[] = [];
    if (callData.length) {
      children.push({
        data: callData,
        type: 'interval',
        encode: { x: 'time', y: 'value', color: 'type' },
        transform: [{ type: 'stackY' }],
        style: { maxWidth: barMaxW, radiusTopLeft: 2, radiusTopRight: 2 },
        axis: {
          y: {
            title: callAxisTitle,
            titleFill: '#94a3b8',
            titleFontSize: 11,
            labelFill: '#94a3b8',
            grid: true,
            gridStrokeOpacity: 0.3,
          },
        },
        tooltip: {
          title: (d: any) => fmtAxisTime(d?.time, window, displayBucket),
          items: [
            (d: any) => ({ name: d?.type || '调用', value: String(d?.value ?? '') }),
          ],
        },
      });
    }
    if (p95Data.length) {
      children.push({
        data: p95Data,
        type: 'line',
        encode: { x: 'time', y: 'value', color: 'metric', shape: 'smooth' },
        style: { lineWidth: 2.2 },
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
          title: (d: any) => fmtAxisTime(d?.time, window, displayBucket),
          items: [(d: any) => ({ name: 'P95', value: `${d?.value ?? ''} ms` })],
        },
      });
    }

    return {
      xField: 'time',
      height,
      paddingLeft: 44,
      paddingRight: 48,
      paddingBottom: 28,
      legend: false,
      scale: {
        color: {
          domain: ['成功', '失败', 'P95'],
          range: ['#34d399', '#f87171', '#f59e0b'],
        },
      },
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
      tooltip: { shared: true },
      children,
    };
  }, [callData, p95Data, chartPoints, window, displayBucket, seriesVisible, height, callAxisTitle, barMaxW, pointByTime]);

  const n = chartPoints.length;
  const hasData = callData.length > 0 || p95Data.length > 0;

  return (
    <div className="yf-metrics-chart">
      {showLegend && (
        <div className="yf-metrics-chart-head">
          <div className="yf-metrics-chart-meta">
            {n} 点 · 每点{' '}
            {displayBucket === 'day' ? '1 天' : displayBucket === 'hour' ? '1 小时' : '1 分钟'}
          </div>
          <div className="yf-metrics-legend" role="group" aria-label="图例开关">
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
                className={`yf-lg${seriesVisible[key] ? '' : ' is-off'}`}
                onClick={() => toggleSeries(key)}
              >
                <i className={icon} />
                {label}
              </button>
            ))}
          </div>
        </div>
      )}
      <div className="yf-metrics-canvas" ref={chartWrapRef}>
        {!n ? (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={emptyDescription}
            style={{ padding: '24px 0' }}
          />
        ) : !hasData ? (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description="请至少勾选一条图例系列"
            style={{ padding: '24px 0' }}
          />
        ) : chartWidth > 0 ? (
          <DualAxes {...config} autoFit={false} width={chartWidth} />
        ) : null}
      </div>
    </div>
  );
};

export default MetricsDualAxes;
