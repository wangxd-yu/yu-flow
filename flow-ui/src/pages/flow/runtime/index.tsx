import React, { useCallback, useEffect, useState } from 'react';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import type { ProColumns } from '@ant-design/pro-components';
import { Radio, Space, Tabs, Typography, message } from 'antd';
import { history } from '@umijs/max';
import {
  getMetricsAnomalies,
  getMetricsRank,
  type AssetMetricsRankItem,
  type MetricsAssetType,
  type MetricsWindow,
} from '../services/assetMetrics';
import { renderHealthTag } from '../components/AssetHealthTag';

const { Text } = Typography;

const WINDOWS: MetricsWindow[] = ['15m', '1h', '24h', '7d', '30d'];

function openAsset(item: AssetMetricsRankItem) {
  if (item.assetType === 'API') {
    history.push('/flow/api');
  } else if (item.assetType === 'TASK') {
    history.push('/flow/task');
  } else {
    history.push('/flow/service');
  }
}

const RuntimeCenterPage: React.FC = () => {
  const [window, setWindow] = useState<MetricsWindow>('24h');
  const [assetType, setAssetType] = useState<MetricsAssetType>('API');
  const [orderBy, setOrderBy] = useState<'errorRate' | 'p95' | 'calls'>('errorRate');
  const [rank, setRank] = useState<AssetMetricsRankItem[]>([]);
  const [anomalies, setAnomalies] = useState<AssetMetricsRankItem[]>([]);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [r, a] = await Promise.all([
        getMetricsRank({ assetType, window, orderBy, limit: 30 }),
        getMetricsAnomalies({ window, limit: 30 }),
      ]);
      setRank(r || []);
      setAnomalies(a || []);
    } catch (e: any) {
      message.error(e?.message || '加载运行中心失败');
    } finally {
      setLoading(false);
    }
  }, [assetType, window, orderBy]);

  useEffect(() => {
    load();
  }, [load]);

  const columns: ProColumns<AssetMetricsRankItem>[] = [
    {
      title: '资产',
      dataIndex: 'assetName',
      render: (_, r) => (
        <a onClick={() => openAsset(r)}>
          [{r.assetType}] {r.assetName || r.assetId}
        </a>
      ),
    },
    {
      title: '健康',
      dataIndex: 'health',
      width: 90,
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
    { title: '调用', dataIndex: 'totalCalls', width: 90 },
    { title: '成功', dataIndex: 'successCount', width: 80 },
    { title: '失败', dataIndex: 'failCount', width: 80 },
    {
      title: '成功率',
      dataIndex: 'successRate',
      width: 90,
      render: (_, r) =>
        r.successRate == null ? '—' : `${(r.successRate * 100).toFixed(1)}%`,
    },
    {
      title: '错误率',
      dataIndex: 'errorRate',
      width: 90,
      render: (_, r) =>
        r.errorRate == null ? '—' : `${(r.errorRate * 100).toFixed(1)}%`,
    },
    {
      title: 'P95',
      dataIndex: 'p95Ms',
      width: 90,
      render: (_, r) => (r.p95Ms == null ? '—' : `${r.p95Ms}ms`),
    },
    { title: '连续失败', dataIndex: 'consecutiveFail', width: 90 },
  ];

  return (
    <PageContainer header={{ title: '运行中心' }}>
      <Space style={{ marginBottom: 16 }} wrap>
        <Text type="secondary">时间窗</Text>
        <Radio.Group
          optionType="button"
          buttonStyle="solid"
          value={window}
          onChange={(e) => setWindow(e.target.value)}
          options={WINDOWS.map((w) => ({ label: w, value: w }))}
        />
      </Space>

      <Tabs
        items={[
          {
            key: 'rank',
            label: '资产排行',
            children: (
              <>
                <Space style={{ marginBottom: 12 }} wrap>
                  <Radio.Group
                    value={assetType}
                    onChange={(e) => setAssetType(e.target.value)}
                    optionType="button"
                    options={[
                      { label: '接口', value: 'API' },
                      { label: '任务', value: 'TASK' },
                      { label: '服务', value: 'SERVICE' },
                    ]}
                  />
                  <Radio.Group
                    value={orderBy}
                    onChange={(e) => setOrderBy(e.target.value)}
                    optionType="button"
                    options={[
                      { label: '按错误率', value: 'errorRate' },
                      { label: '按 P95', value: 'p95' },
                      { label: '按调用量', value: 'calls' },
                    ]}
                  />
                </Space>
                <ProTable<AssetMetricsRankItem>
                  rowKey={(r) => `${r.assetType}:${r.assetId}`}
                  search={false}
                  toolBarRender={false}
                  pagination={false}
                  loading={loading}
                  dataSource={rank}
                  columns={columns}
                />
              </>
            ),
          },
          {
            key: 'anomalies',
            label: '异常资产',
            children: (
              <ProTable<AssetMetricsRankItem>
                rowKey={(r) => `${r.assetType}:${r.assetId}`}
                search={false}
                toolBarRender={false}
                pagination={false}
                loading={loading}
                dataSource={anomalies}
                columns={columns}
                locale={{ emptyText: `时间窗 ${window} 内暂无异常资产` }}
              />
            ),
          },
        ]}
      />
    </PageContainer>
  );
};

export default RuntimeCenterPage;
