import React, { useRef, useState } from 'react';
import {
  ActionType,
  PageContainer,
  ProColumns,
  ProTable,
} from '@ant-design/pro-components';
import { request } from '@umijs/max';
import { Tag, Drawer, Typography, Button, Space, message } from 'antd';
import { EyeOutlined } from '@ant-design/icons';
import FlowEditor from '@/components/flow/FlowEditor';
import { FlowTrace } from '@/components/flow/debugger/FlowDebugger';
import { LogStatusTag, LogDuration, LogDeepLinkBar } from '../shared';
import '../shared/logPageLayout.css';
import SimpleTraceViewer from './SimpleTraceViewer';

const { Text } = Typography;

const API_BASE = '/flow-api/log/execution';

// ============================
// 类型定义
// ============================
interface ExecutionLogListDTO {
  id: string;
  apiId: string;
  apiName: string;
  url: string;
  /** 接口类型：FLOW / DB / JSON / STRING */
  serviceType?: string;
  method: string;
  status: 'SUCCESS' | 'ERROR';
  costTimeMs: number;
  hasTrace: boolean;
  createTime: string;
}

// ============================
// API 请求
// ============================
const queryExecutionLogPage = async (params: any) => {
  const { current, pageSize, createTime, ...rest } = params;

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
      startTime,
      endTime,
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
  // Umi-request 拦截器已自动剥离 R 外壳，res 即为 FlowApiDO 对象
  return await request(`/flow-api/api/${apiId}`);
};

const getExecutionLogDetail = async (id: string) => {
  return await request(`${API_BASE}/${id}`);
};

// ============================
// 主组件
// ============================
const ExecutionLog: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const [drawerVisible, setDrawerVisible] = useState(false);
  const [currentLog, setCurrentLog] = useState<ExecutionLogListDTO | null>(null);
  const [currentApiDsl, setCurrentApiDsl] = useState<string>('');
  const [currentTrace, setCurrentTrace] = useState<FlowTrace | null>(null);
  /** 完整详情（供 SimpleTraceViewer 使用） */
  const [currentDetail, setCurrentDetail] = useState<any>(null);
  /** 当前快照的 serviceType（决定用哪种查看器） */
  const [currentServiceType, setCurrentServiceType] = useState<string>('');

  const urlParams = new URLSearchParams(window.location.search);
  const initialApiId = urlParams.get('apiId') || undefined;

  const handleViewTrace = async (record: ExecutionLogListDTO) => {
    if (!record.hasTrace) {
      message.warning('该执行日志没有关联的追踪快照数据');
      return;
    }

    try {
      // 列表使用的是轻量级 DTO，需调详情接口获取 traceData 大字段
      const detail = await getExecutionLogDetail(record.id);
      if (!detail || !detail.traceData) {
        message.warning('未找到该执行日志的追踪快照数据');
        return;
      }

      // 检测 serviceType：优先用列表中返回的字段，兜底从 trace nodeType 推断
      let detectedType = record.serviceType || detail.serviceType || '';
      if (!detectedType) {
        try {
          const trace = JSON.parse(detail.traceData);
          const nodeType = trace?.stepLogs?.[0]?.nodeType;
          if (nodeType === 'database') detectedType = 'DB';
          else if (nodeType === 'json') detectedType = 'JSON';
          else if (nodeType === 'string') detectedType = 'STRING';
          else if (trace?.dslSnapshot || trace?.dslContentHash) detectedType = 'FLOW';
        } catch { /* ignore */ }
      }

      setCurrentServiceType(detectedType);
      setCurrentLog(record);
      setCurrentDetail(detail);

      if (detectedType === 'FLOW' || !detectedType) {
        // FLOW 类型：走 X6 画布回放
        const trace: FlowTrace & { dslSnapshot?: string; dslContentHash?: string } = JSON.parse(detail.traceData);
        let dslContent = trace.dslSnapshot || '';
        if (!dslContent) {
          try {
            const apiInfo = await getApiConfig(record.apiId);
            dslContent = apiInfo?.dslContent || '';
          } catch (e) {
            console.error('getApiConfig failed:', e);
          }
        }
        if (!dslContent) {
          message.warning('无法加载 API 配置，画布将显示为空');
        }
        setCurrentTrace(trace);
        setCurrentApiDsl(dslContent);
      } else {
        // 非 FLOW 类型：走 SimpleTraceViewer
        setCurrentTrace(null);
        setCurrentApiDsl('');
      }

      setDrawerVisible(true);
    } catch (e) {
      message.error('解析执行快照失败');
      console.error(e);
    }
  };

  const columns: ProColumns<ExecutionLogListDTO>[] = [
    {
      title: '序号',
      valueType: 'index',
      width: 60,
      fixed: 'left',
    },
    {
      title: '接口名称',
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
      title: '接口 ID',
      dataIndex: 'apiId',
      hideInTable: true,
      initialValue: initialApiId,
      fieldProps: {
        placeholder: '按接口 ID 筛选',
      },
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
          <LogStatusTag kind="success" text="执行成功" />
        ) : (
          <LogStatusTag kind="error" text="执行失败" />
        ),
    },
    {
      title: '耗时',
      dataIndex: 'costTimeMs',
      width: 100,
      search: false,
      render: (_, record) => <LogDuration ms={record.costTimeMs} />,
    },
    {
      title: '执行时间',
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
          disabled={!record.hasTrace}
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
        title: '接口日志',
        subTitle: '监控与追溯生产环境下接口的调用状态、耗时及请求上下文',
      }}
    >
      <ProTable<ExecutionLogListDTO>
        className="fh-table"
        headerTitle={
          initialApiId ? (
            <LogDeepLinkBar label={`apiId=${initialApiId}`} clearPath="/log/execution" />
          ) : (
            '执行记录'
          )
        }
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
        params={{ apiId: initialApiId }}
        request={async (params) => {
          const result = await queryExecutionLogPage({
            ...params,
            apiId: params.apiId || initialApiId,
          });
          return result;
        }}
        columns={columns}
        rowClassName={(record) =>
          record.status === 'ERROR' ? 'log-row-fail' : ''
        }
        options={{
          density: true,
          fullScreen: true,
          reload: true,
          setting: true,
        }}
      />

      {drawerVisible && (
        <Drawer
          title={`API 快照复原 - ${currentLog?.apiName}`}
          width={currentServiceType === 'FLOW' || !currentServiceType ? '100%' : '80%'}
          open={drawerVisible}
          onClose={() => setDrawerVisible(false)}
          styles={{ body: { padding: 0 } }}
          destroyOnClose
        >
          {(currentServiceType === 'FLOW' || !currentServiceType) && currentTrace ? (
            /* FLOW 类型：X6 可视化画布回放 */
            <FlowEditor
              value={currentApiDsl}
              isEdit={false}
              readonlyTrace={currentTrace}
            />
          ) : currentDetail ? (
            /* 非 FLOW 类型：SimpleTraceViewer */
            <SimpleTraceViewer detail={currentDetail} />
          ) : null}
        </Drawer>
      )}
    </PageContainer>
  );
};

export default ExecutionLog;
