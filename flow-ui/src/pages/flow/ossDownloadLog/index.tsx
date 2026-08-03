import { ActionType, PageContainer, ProColumns, ProTable } from '@ant-design/pro-components';
import { Tag } from 'antd';
import React, { useRef } from 'react';
import OssIntegrationAlert from '@/components/flow/OssIntegrationAlert';
import { OssDownloadLog, queryOssDownloadLogPage } from '@/services/flow/ossDownloadLog';

import '@/styles/fullHeightTable.css';

const resultColor: Record<string, string> = {
  SUCCESS: 'success',
  DENIED: 'warning',
  NOT_FOUND: 'default',
  ERROR: 'error',
};

const resultText: Record<string, string> = {
  SUCCESS: '成功',
  DENIED: '拒绝',
  NOT_FOUND: '未找到',
  ERROR: '错误',
};

const OssDownloadLogList: React.FC = () => {
  const actionRef = useRef<ActionType>();

  const columns: ProColumns<OssDownloadLog>[] = [
    {
      title: '对象 ID',
      dataIndex: 'objectId',
      width: 180,
      copyable: true,
      ellipsis: true,
    },
    {
      title: '下载人',
      dataIndex: 'downloadedByName',
      width: 120,
      ellipsis: true,
      search: false,
    },
    {
      title: '结果',
      dataIndex: 'result',
      width: 90,
      valueEnum: {
        SUCCESS: { text: '成功' },
        DENIED: { text: '拒绝' },
        NOT_FOUND: { text: '未找到' },
        ERROR: { text: '错误' },
      },
      render: (_, record) => (
        <Tag color={resultColor[record.result || ''] || 'default'} style={{ margin: 0 }}>
          {resultText[record.result || ''] || record.result || '-'}
        </Tag>
      ),
    },
    {
      title: '拒绝原因',
      dataIndex: 'denyReason',
      width: 200,
      ellipsis: true,
      search: false,
    },
    {
      title: '客户端 IP',
      dataIndex: 'clientIp',
      width: 130,
      search: false,
    },
    {
      title: '耗时(ms)',
      dataIndex: 'timeMs',
      width: 100,
      search: false,
    },
    {
      title: '时间',
      dataIndex: 'createTime',
      valueType: 'dateTime',
      width: 160,
      search: false,
    },
  ];

  return (
    <PageContainer
      className="fh-container"
      style={{ height: 'calc(100vh - 26px)', overflow: 'hidden' }}
      header={{
        title: 'OSS 下载',
      }}
    >
      <OssIntegrationAlert />
      <ProTable<OssDownloadLog>
        className="fh-table"
        headerTitle="OSS 下载日志"
        actionRef={actionRef}
        rowKey="id"
        tableLayout="fixed"
        scroll={{ x: 1100, y: 100000 }}
        search={{
          labelWidth: 120,
        }}
        request={async (params = {}) => {
          const { current, pageSize, ...restParams } = params as any;
          const result = await queryOssDownloadLogPage({
            ...restParams,
            page: (current || 1) - 1,
            size: pageSize || 20,
          });
          const data = (result as any)?.data || result;
          return {
            data: data?.items || [],
            success: true,
            total: data?.total,
          };
        }}
        columns={columns}
      />
    </PageContainer>
  );
};

export default OssDownloadLogList;
