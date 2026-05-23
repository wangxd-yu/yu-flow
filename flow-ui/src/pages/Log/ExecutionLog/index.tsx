import React, { useRef, useState } from 'react';
import {
  ActionType,
  PageContainer,
  ProColumns,
  ProTable,
} from '@ant-design/pro-components';
import { request } from '@umijs/max';
import { Badge, Tag, Drawer, Typography, Button, Space, message } from 'antd';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  ClockCircleOutlined,
  EyeOutlined,
} from '@ant-design/icons';
import FlowEditor from '@/pages/flow/controller/components/FlowEditor';
import { FlowTrace } from '@/pages/flow/controller/components/debugger/FlowDebugger';

const { Text } = Typography;

const API_BASE = '/flow-api/execution-logs';

// ============================
// 类型定义
// ============================
interface ExecutionLogDTO {
  id: string;
  apiId: string;
  apiName: string;
  url: string;
  method: string;
  requestParams: string;
  responseBody: string;
  status: 'SUCCESS' | 'ERROR';
  errorMsg: string;
  costTimeMs: number;
  traceData: string;
  createTime: string;
}

// ============================
// API 请求
// ============================
const queryExecutionLogPage = async (params: any) => {
  const { current, pageSize, ...rest } = params;
  const result = await request(`${API_BASE}/page`, {
    method: 'GET',
    params: {
      ...rest,
      page: (current || 1) - 1,
      size: pageSize || 20,
    },
  });
  return {
    data: result.items || [],
    success: true,
    total: result.total || 0,
  };
};

const getApiConfig = async (apiId: string) => {
  const res = await request(`/flow-api/flow-api-info/${apiId}`);
  return res.data;
};

// ============================
// 工具函数
// ============================
const formatDuration = (ms: number) => {
  if (ms == null) return '-';
  if (ms < 1000) return `${ms} ms`;
  return `${(ms / 1000).toFixed(2)} s`;
};

// ============================
// 主组件
// ============================
const ExecutionLog: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const [drawerVisible, setDrawerVisible] = useState(false);
  const [currentLog, setCurrentLog] = useState<ExecutionLogDTO | null>(null);
  const [currentApiDsl, setCurrentApiDsl] = useState<string>('');
  const [currentTrace, setCurrentTrace] = useState<FlowTrace | null>(null);

  const handleViewTrace = async (record: ExecutionLogDTO) => {
    if (!record.traceData) {
      message.warning('该执行日志没有关联的追踪快照数据');
      return;
    }
    
    try {
      const trace: FlowTrace = JSON.parse(record.traceData);
      const apiInfo = await getApiConfig(record.apiId);
      
      setCurrentTrace(trace);
      setCurrentApiDsl(apiInfo?.dslContent || '');
      setCurrentLog(record);
      setDrawerVisible(true);
    } catch (e) {
      message.error('解析执行快照失败');
      console.error(e);
    }
  };

  const columns: ProColumns<ExecutionLogDTO>[] = [
    {
      title: '序号',
      valueType: 'index',
      width: 60,
      fixed: 'left',
    },
    {
      title: 'API 名称',
      dataIndex: 'apiName',
      width: 140,
      fixed: 'left',
      render: (_, record) => (
        <Text strong style={{ color: '#1677ff' }}>
          {record.apiName || '未命名'}
        </Text>
      ),
    },
    {
      title: '请求路径',
      dataIndex: 'url',
      width: 200,
      render: (_, record) => (
        <Space>
          <Tag color="blue">{record.method}</Tag>
          <Text copyable>{record.url}</Text>
        </Space>
      ),
    },
    {
      title: '执行状态',
      dataIndex: 'status',
      width: 110,
      valueType: 'select',
      valueEnum: {
        'SUCCESS': { text: '成功', status: 'Success' },
        'ERROR': { text: '失败', status: 'Error' },
      },
      render: (_, record) =>
        record.status === 'SUCCESS' ? (
          <Badge
            status="success"
            text={
              <Tag
                icon={<CheckCircleOutlined />}
                color="success"
                style={{ marginInlineEnd: 0 }}
              >
                执行成功
              </Tag>
            }
          />
        ) : (
          <Badge
            status="error"
            text={
              <Tag
                icon={<CloseCircleOutlined />}
                color="error"
                style={{ marginInlineEnd: 0 }}
              >
                执行失败
              </Tag>
            }
          />
        ),
    },
    {
      title: '耗时',
      dataIndex: 'costTimeMs',
      width: 100,
      search: false,
      render: (_, record) => (
        <span>
          <ClockCircleOutlined style={{ marginRight: 4, color: '#8c8c8c' }} />
          {formatDuration(record.costTimeMs)}
        </span>
      ),
    },
    {
      title: '执行时间',
      dataIndex: 'createTime',
      width: 180,
      valueType: 'dateTime',
      search: false,
    },
    {
      title: '操作',
      valueType: 'option',
      width: 120,
      fixed: 'right',
      render: (_, record) => [
        <Button
          key="view"
          type="link"
          size="small"
          icon={<EyeOutlined />}
          onClick={() => handleViewTrace(record)}
          disabled={!record.traceData}
        >
          查看快照
        </Button>,
      ],
    },
  ];

  return (
    <PageContainer
      className="fh-container"
      style={{ height: 'calc(100vh - 26px)', overflow: 'hidden' }}
      header={{
        title: 'API 调用日志',
        subTitle: '监控与追溯生产环境下 API 的调用状态、耗时及请求上下文',
      }}
    >
      <ProTable<ExecutionLogDTO>
        className="fh-table"
        headerTitle="执行记录"
        actionRef={actionRef}
        rowKey="id"
        tableLayout="fixed"
        scroll={{ x: 1000, y: 100000 }}
        search={{
          labelWidth: 'auto',
          defaultCollapsed: false,
        }}
        pagination={{
          defaultPageSize: 20,
          showSizeChanger: true,
          pageSizeOptions: ['10', '20', '50', '100'],
        }}
        request={queryExecutionLogPage}
        columns={columns}
        rowClassName={(record) =>
          record.status === 'ERROR' ? 'execution-log-row-fail' : ''
        }
        options={{
          density: true,
          fullScreen: true,
          reload: true,
          setting: true,
        }}
      />

      {drawerVisible && currentTrace && (
        <Drawer
          title={`API 快照复原 - ${currentLog?.apiName}`}
          width="100%"
          open={drawerVisible}
          onClose={() => setDrawerVisible(false)}
          styles={{ body: { padding: 0 } }}
          destroyOnClose
        >
          <FlowEditor
            value={currentApiDsl}
            isEdit={false}
            readonlyTrace={currentTrace}
          />
        </Drawer>
      )}

      <style>{`
        .fh-container.ant-pro-page-container {
          display: flex !important;
          flex-direction: column !important;
        }
        .fh-container.ant-pro-page-container > .ant-pro-grid-content,
        .fh-container.ant-pro-page-container .ant-pro-grid-content-children {
          flex: 1 !important;
          min-height: 0 !important;
          display: flex !important;
          flex-direction: column !important;
        }
        .fh-container.ant-pro-page-container .ant-pro-page-container-children-container {
          flex: 1 !important;
          min-height: 0 !important;
          display: flex !important;
          flex-direction: column !important;
          height: auto !important;
          padding-block-end: 0 !important;
        }
        .fh-table.ant-pro-table {
          display: flex;
          flex-direction: column;
          height: 100%;
          overflow: hidden;
        }
        .fh-table .ant-pro-table-search {
          flex-shrink: 0;
        }
        .fh-table > .ant-pro-card:not(.ant-pro-table-search) {
          flex: 1;
          min-height: 0;
          display: flex;
          flex-direction: column;
        }
        .fh-table > .ant-pro-card:not(.ant-pro-table-search) > .ant-pro-card-body {
          flex: 1;
          min-height: 0;
          display: flex !important;
          flex-direction: column;
          overflow: hidden;
        }
        .fh-table .ant-pro-table-list-toolbar {
          flex-shrink: 0;
        }
        .fh-table .ant-table-wrapper {
          flex: 1;
          min-height: 0;
          display: flex;
          flex-direction: column;
        }
        .fh-table .ant-spin-nested-loading {
          flex: 1;
          min-height: 0;
          display: flex;
          flex-direction: column;
        }
        .fh-table .ant-spin-container {
          flex: 1;
          min-height: 0;
          display: flex;
          flex-direction: column;
        }
        .fh-table .ant-table {
          flex: 1;
          min-height: 0;
          display: flex;
          flex-direction: column;
        }
        .fh-table .ant-table-container {
          flex: 1;
          min-height: 0;
          display: flex;
          flex-direction: column;
        }
        .fh-table .ant-table-header {
          flex-shrink: 0;
          overflow: hidden !important;
        }
        .fh-table .ant-table-body {
          flex: 1;
          min-height: 0;
          max-height: none !important;
          overflow-y: scroll !important;
        }
        .fh-table .ant-table-pagination {
          flex-shrink: 0;
          padding: 6px 0;
          margin: 0 !important;
        }
        .execution-log-row-fail td {
          background-color: #fff2f0 !important;
        }
        .execution-log-row-fail:hover td {
          background-color: #ffebe8 !important;
        }
      `}</style>
    </PageContainer>
  );
};

export default ExecutionLog;
