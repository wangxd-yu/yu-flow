import React from 'react';
import { Tag, Tooltip } from 'antd';
import type { AssetHealth } from '@/services/flow/assetMetrics';

const COLOR: Record<string, string> = {
  ok: 'success',
  warn: 'warning',
  error: 'error',
  empty: 'default',
};

const LABEL: Record<string, string> = {
  ok: '健康',
  warn: '告警',
  error: '异常',
  empty: '无数据',
};

export type ProbeHealthHint = {
  status?: string;
  message?: string;
};

/** 指标健康；可选叠加宿主路由探活（fail 时抬升为异常展示） */
export function renderHealthTag(h?: AssetHealth | null, probe?: ProbeHealthHint | null) {
  if (!h && !probe) {
    return <Tag>—</Tag>;
  }
  let key = h?.health || 'empty';
  if (probe?.status === 'fail') {
    key = 'error';
  } else if (probe?.status === 'ok' && (!h || h.health === 'empty')) {
    key = 'ok';
  }
  const rate =
    h?.successRate == null ? '—' : `${(h.successRate * 100).toFixed(0)}%`;
  const probeLine =
    probe?.status && probe.status !== 'unknown'
      ? ` | 探活 ${probe.status}${probe.message ? `：${probe.message}` : ''}`
      : '';
  return (
    <Tooltip
      title={`近 ${h?.window || '24h'}：成功率 ${rate}，调用 ${h?.totalCalls ?? 0}，连续失败 ${h?.consecutiveFail ?? 0}${probeLine}`}
    >
      <Tag color={COLOR[key] || 'default'}>{LABEL[key] || key}</Tag>
    </Tooltip>
  );
}
