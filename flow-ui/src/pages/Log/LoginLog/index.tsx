import React, { useRef } from 'react';
import {
  ActionType,
  PageContainer,
  ProColumns,
  ProTable,
} from '@ant-design/pro-components';
import { request } from '@umijs/max';
import { Tag, Tooltip, Typography } from 'antd';
import { GlobalOutlined } from '@ant-design/icons';
import { LogStatusTag, LogDuration } from '../shared';
import '@/styles/fullHeightTable.css';
import '../shared/logPageLayout.css';

const { Text } = Typography;

const API_BASE = '/flow-api/log/login';

// ============================
// 类型定义
// ============================
interface LoginLogDTO {
  id: string;
  account: string;
  ip: string;
  region: string;
  userAgent: string;
  status: 0 | 1;
  msg: string;
  duration: number;
  createTime: string;
}

// ============================
// API 请求
// ============================
const queryLoginLogPage = async (params: any) => {
  const { current, pageSize, status, account, createTime, ...rest } = params;

  // 处理时间范围
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
      account,
      status: status !== undefined && status !== '' ? status : undefined,
      startTime,
      endTime,
      page: (current || 1),
      size: pageSize || 20,
    },
  });
  return {
    data: result.items || [],
    success: true,
    total: result.total || 0,
  };
};

/** 简化 User-Agent 展示 */
const parseUserAgent = (ua: string) => {
  if (!ua) return '未知';
  if (ua.includes('Chrome')) return 'Chrome';
  if (ua.includes('Firefox')) return 'Firefox';
  if (ua.includes('Safari')) return 'Safari';
  if (ua.includes('Edge')) return 'Edge';
  if (ua.includes('MSIE') || ua.includes('Trident')) return 'IE';
  if (ua.includes('PostmanRuntime')) return 'Postman';
  return ua.length > 30 ? ua.slice(0, 30) + '...' : ua;
};

// ============================
// 主组件
// ============================
const LoginLog: React.FC = () => {
  const actionRef = useRef<ActionType>();

  const columns: ProColumns<LoginLogDTO>[] = [
    {
      title: '序号',
      valueType: 'index',
      width: 60,
      fixed: 'left',
    },
    {
      title: '登录账号',
      dataIndex: 'account',
      width: 140,
      fixed: 'left',
      render: (_, record) => (
        <Text strong style={{ color: '#1677ff' }}>
          {record.account}
        </Text>
      ),
    },
    {
      title: '登录状态',
      dataIndex: 'status',
      width: 110,
      valueType: 'select',
      valueEnum: {
        1: { text: '成功', status: 'Success' },
        0: { text: '失败', status: 'Error' },
      },
      render: (_, record) =>
        record.status === 1 ? (
          <LogStatusTag kind="success" text="登录成功" />
        ) : (
          <LogStatusTag kind="error" text="登录失败" />
        ),
    },
    {
      title: '客户端 IP',
      dataIndex: 'ip',
      width: 150,
      search: false,
      render: (_, record) => (
        <span>
          <GlobalOutlined style={{ marginRight: 6, color: '#8c8c8c' }} />
          {record.ip || '-'}
        </span>
      ),
    },
    {
      title: '归属地区',
      dataIndex: 'region',
      width: 180,
      search: false,
      ellipsis: true,
      render: (_, record) => record.region || <Text type="secondary">未知</Text>,
    },
    {
      title: '浏览器',
      dataIndex: 'userAgent',
      width: 120,
      search: false,
      render: (_, record) => (
        <Tooltip title={record.userAgent} placement="topLeft">
          <Tag>{parseUserAgent(record.userAgent)}</Tag>
        </Tooltip>
      ),
    },
    {
      title: '结果信息',
      dataIndex: 'msg',
      width: 200,
      search: false,
      ellipsis: true,
      render: (_, record) => (
        <Text type={record.status === 1 ? 'success' : 'danger'}>{record.msg}</Text>
      ),
    },
    {
      title: '耗时',
      dataIndex: 'duration',
      width: 100,
      search: false,
      render: (_, record) => <LogDuration ms={record.duration} />,
    },
    {
      title: '登录时间',
      dataIndex: 'createTime',
      width: 180,
      valueType: 'dateTimeRange',
      fieldProps: {
        placeholder: ['开始时间', '结束时间'],
      },
      render: (_, record) => record.createTime || '-',
      search: {
        transform: (value) => ({
          createTime: value,
        }),
      },
    },
  ];

  return (
    <PageContainer
      className="fh-container"
      style={{ height: 'calc(100vh - 26px)', overflow: 'hidden' }}
      header={{
        title: '登录日志',
        subTitle: '记录所有用户的登录行为，包括 IP、地区、状态和耗时',
      }}
    >
      <ProTable<LoginLogDTO>
        className="fh-table"
        headerTitle="登录记录"
        actionRef={actionRef}
        rowKey="id"
        tableLayout="fixed"
        scroll={{ x: 1240, y: 100000 }}
        search={{
          labelWidth: 'auto',
          defaultCollapsed: false,
        }}
        pagination={{
          defaultPageSize: 20,
          showSizeChanger: true,
          pageSizeOptions: ['10', '20', '50', '100'],
        }}
        request={queryLoginLogPage}
        columns={columns}
        rowClassName={(record) =>
          record.status === 0 ? 'log-row-fail' : ''
        }
        options={{
          density: true,
          fullScreen: true,
          reload: true,
          setting: true,
        }}
      />
    </PageContainer>
  );
};

export default LoginLog;
