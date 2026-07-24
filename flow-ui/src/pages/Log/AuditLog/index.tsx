/**
 * 配置变更审计日志（只读）
 */
import React, { useRef } from 'react';
import { ActionType, PageContainer, ProColumns, ProTable } from '@ant-design/pro-components';
import { request } from '@umijs/max';
import { Tag, Typography } from 'antd';
import '@/styles/fullHeightTable.css';
import '../shared/logPageLayout.css';

const { Text } = Typography;
const API_BASE = '/flow-api/log/audit';

interface AuditLogDTO {
  id: string;
  action: string;
  operator: string;
  targetType?: string;
  targetId?: string;
  detail?: string;
  createTime?: string;
}

const ACTION_LABEL: Record<string, { text: string; color: string }> = {
  API_PUBLISH: { text: '接口发布', color: 'blue' },
  TASK_PUBLISH: { text: '任务发布', color: 'cyan' },
  SERVICE_PUBLISH: { text: '服务发布', color: 'geekblue' },
  SYS_CONFIG_UPDATE: { text: '系统配置', color: 'orange' },
  OPEN_SECRET_ROTATE: { text: '密钥轮换', color: 'purple' },
};

const queryPage = async (params: any) => {
  const { current, pageSize, createTime, ...rest } = params;
  let startTime: string | undefined;
  let endTime: string | undefined;
  if (createTime && Array.isArray(createTime)) {
    startTime = createTime[0];
    endTime = createTime[1];
  }
  const result = await request(`${API_BASE}/page`, {
    method: 'GET',
    params: {
      ...rest,
      startTime,
      endTime,
      page: current || 1,
      size: pageSize || 20,
    },
  });
  return {
    data: result?.items || result?.data?.items || [],
    success: true,
    total: result?.total || result?.data?.total || 0,
  };
};

const AuditLogPage: React.FC = () => {
  const actionRef = useRef<ActionType>();

  const columns: ProColumns<AuditLogDTO>[] = [
    { title: '时间', dataIndex: 'createTime', valueType: 'dateTimeRange', width: 180, hideInTable: true },
    { title: '时间', dataIndex: 'createTime', width: 170, search: false },
    {
      title: '动作',
      dataIndex: 'action',
      width: 140,
      valueEnum: {
        API_PUBLISH: { text: '接口发布' },
        TASK_PUBLISH: { text: '任务发布' },
        SERVICE_PUBLISH: { text: '服务发布' },
        SYS_CONFIG_UPDATE: { text: '系统配置' },
        OPEN_SECRET_ROTATE: { text: '密钥轮换' },
      },
      render: (_, r) => {
        const m = ACTION_LABEL[r.action] || { text: r.action, color: 'default' };
        return <Tag color={m.color}>{m.text}</Tag>;
      },
    },
    { title: '操作人', dataIndex: 'operator', width: 120 },
    { title: '目标类型', dataIndex: 'targetType', width: 120, search: false },
    { title: '目标 ID', dataIndex: 'targetId', width: 180, ellipsis: true },
    {
      title: '详情',
      dataIndex: 'detail',
      search: false,
      ellipsis: true,
      render: (_, r) => <Text type="secondary">{r.detail || '—'}</Text>,
    },
  ];

  return (
    <PageContainer
      className="fh-container"
      header={{ title: '变更审计', subTitle: '接口发布 / 系统配置 / 开放密钥轮换' }}
      style={{ height: 'calc(100vh - 26px)', overflow: 'hidden' }}
    >
      <ProTable<AuditLogDTO>
        className="fh-table"
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        search={{ labelWidth: 'auto' }}
        tableLayout="fixed"
        pagination={{ defaultPageSize: 20, showSizeChanger: true }}
        request={queryPage}
        scroll={{ x: 960, y: 100000 }}
      />
    </PageContainer>
  );
};

export default AuditLogPage;
