import React, { useRef } from 'react';
import { ActionType, PageContainer, ProColumns, ProTable } from '@ant-design/pro-components';
import { Tag } from 'antd';
import { AlertEvent, pageAlertEvents } from '@/services/flow/alertService';
import { AssetTypeBadge, openAssetDeepLink } from '@/components/flow/ops';
import '@/styles/fullHeightTable.css';

const STATUS_MAP: Record<string, { color: string; text: string }> = {
  SUCCESS: { color: 'success', text: '成功' },
  FAIL: { color: 'error', text: '失败' },
  SUPPRESSED: { color: 'default', text: '静默' },
};

const AlertHistoryPage: React.FC = () => {
  const actionRef = useRef<ActionType>();

  const columns: ProColumns<AlertEvent>[] = [
    {
      title: '时间',
      dataIndex: 'firedAt',
      valueType: 'dateTimeRange',
      hideInTable: true,
    },
    {
      title: '时间',
      dataIndex: 'firedAt',
      width: 170,
      search: false,
    },
    {
      title: '规则',
      dataIndex: 'ruleName',
      width: 140,
      search: false,
      ellipsis: true,
      render: (_, r) => r.ruleName || 'SysConfig兜底',
    },
    {
      title: '资产类型',
      dataIndex: 'assetType',
      width: 110,
      valueEnum: {
        API: { text: '接口' },
        TASK: { text: '任务' },
        SERVICE: { text: '服务' },
        PLATFORM: { text: '开放平台' },
      },
      render: (_, r) => (r.assetType ? <AssetTypeBadge type={r.assetType} /> : '-'),
    },
    {
      title: '资产',
      dataIndex: 'assetName',
      width: 200,
      search: false,
      ellipsis: true,
      render: (_, r) =>
        r.assetId ? (
          <a
            onClick={() =>
              openAssetDeepLink({
                assetType: r.assetType || 'API',
                assetId: r.assetId!,
                tab: 'runtime',
              })
            }
            title={r.assetId}
          >
            {r.assetName || r.assetId}
          </a>
        ) : (
          r.assetName || '-'
        ),
    },
    {
      title: '健康度',
      dataIndex: 'health',
      width: 90,
      search: false,
      render: (_, r) =>
        r.health === 'error' ? (
          <Tag color="error">异常</Tag>
        ) : r.health === 'warn' ? (
          <Tag color="warning">告警</Tag>
        ) : r.health === 'ok' ? (
          <Tag color="success">健康</Tag>
        ) : (
          r.health || '-'
        ),
    },
    {
      title: '失败数',
      dataIndex: 'failCount',
      width: 80,
      search: false,
    },
    {
      title: '错误率',
      dataIndex: 'errorRate',
      width: 90,
      search: false,
      render: (_, r) =>
        r.errorRate == null ? '-' : `${(r.errorRate * 100).toFixed(1)}%`,
    },
    {
      title: '通道',
      dataIndex: 'channelType',
      width: 100,
      search: false,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      valueEnum: {
        SUCCESS: { text: '成功' },
        FAIL: { text: '失败' },
        SUPPRESSED: { text: '静默' },
      },
      render: (_, r) => {
        const m = STATUS_MAP[r.status] || { color: 'default', text: r.status };
        return <Tag color={m.color}>{m.text}</Tag>;
      },
    },
    {
      title: '错误信息',
      dataIndex: 'errorMsg',
      search: false,
      ellipsis: true,
    },
  ];

  return (
    <PageContainer
      className="fh-container"
      style={{ height: 'calc(100vh - 26px)', overflow: 'hidden' }}
    >
      <ProTable<AlertEvent>
        className="fh-table"
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        request={pageAlertEvents}
        search={{ labelWidth: 'auto' }}
        tableLayout="fixed"
        pagination={{ defaultPageSize: 20 }}
        scroll={{ x: 1200, y: 100000 }}
      />
    </PageContainer>
  );
};

export default AlertHistoryPage;
