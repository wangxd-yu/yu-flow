import React, { useRef, useState } from 'react';
import {
  ActionType,
  PageContainer,
  ProColumns,
  ProTable,
} from '@ant-design/pro-components';
import {
  Badge, Button, Drawer, message, Popconfirm, Tag, Typography,
} from 'antd';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  ClockCircleOutlined,
  EyeOutlined,
  SyncOutlined,
} from '@ant-design/icons';
import FlowEditor from '@/pages/flow/controller/components/FlowEditor';
import { FlowTrace } from '@/pages/flow/controller/components/debugger/FlowDebugger';
import {
  queryTaskLogPage,
  getTaskLog,
  getTask,
  clearTaskLog,
  FlowTaskLog,
} from '../../flow/task/services/taskService';

const { Text } = Typography;

const STATUS_MAP: Record<string, { status: any; text: string }> = {
  SUCCESS: { status: 'success', text: '成功' },
  FAILED: { status: 'error', text: '失败' },
  RUNNING: { status: 'processing', text: '运行中' },
};

const formatDuration = (ms?: number) => {
  if (ms == null) return '-';
  if (ms < 1000) return `${ms} ms`;
  return `${(ms / 1000).toFixed(2)} s`;
};

const TaskLogPage: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const [drawerVisible, setDrawerVisible] = useState(false);
  const [currentLog, setCurrentLog] = useState<FlowTaskLog | null>(null);
  const [currentDsl, setCurrentDsl] = useState('');
  const [currentTrace, setCurrentTrace] = useState<FlowTrace | null>(null);

  const urlParams = new URLSearchParams(window.location.search);
  const initialTaskId = urlParams.get('taskId') || undefined;

  const handleViewTrace = async (record: FlowTaskLog) => {
    if (!record.hasTrace) {
      message.warning('该任务日志没有关联的追踪快照数据');
      return;
    }

    try {
      const detail = await getTaskLog(record.id);
      const log: FlowTaskLog = (detail as any)?.data || detail;
      if (!log?.traceData) {
        message.warning('未找到该任务日志的追踪快照数据');
        return;
      }

      const trace: FlowTrace & { dslSnapshot?: string } = JSON.parse(log.traceData);
      let dslContent = trace.dslSnapshot || '';
      if (!dslContent && log.taskId) {
        try {
          const taskInfo = await getTask(log.taskId);
          dslContent = taskInfo?.dslContent || '';
        } catch (e) {
          console.error('getTask failed:', e);
        }
      }
      if (!dslContent) {
        message.warning('无法加载任务 DSL，画布将显示为空');
      }

      setCurrentLog(log);
      setCurrentTrace(trace);
      setCurrentDsl(dslContent);
      setDrawerVisible(true);
    } catch (e) {
      message.error('解析执行快照失败');
      console.error(e);
    }
  };

  const columns: ProColumns<FlowTaskLog>[] = [
    {
      title: '序号',
      valueType: 'index',
      width: 60,
      fixed: 'left',
      search: false,
    },
    {
      title: '任务名称',
      dataIndex: 'taskName',
      width: 160,
      fixed: 'left',
      ellipsis: true,
      render: (_, record) => (
        <Text strong style={{ color: '#1677ff' }}>
          {record.taskName || '未命名'}
        </Text>
      ),
    },
    {
      title: '触发类型',
      dataIndex: 'triggerType',
      width: 110,
      valueEnum: {
        CRON: { text: 'CRON' },
        MANUAL: { text: 'MANUAL' },
      },
      render: (_, record) => (
        <Tag color={record.triggerType === 'MANUAL' ? 'blue' : 'purple'}>
          {record.triggerType}
        </Tag>
      ),
    },
    {
      title: '执行状态',
      dataIndex: 'status',
      width: 120,
      valueEnum: {
        SUCCESS: { text: '成功', status: 'Success' },
        FAILED: { text: '失败', status: 'Error' },
        RUNNING: { text: '运行中', status: 'Processing' },
      },
      render: (_, record) => {
        if (record.status === 'SUCCESS') {
          return (
            <Badge
              status="success"
              text={
                <Tag icon={<CheckCircleOutlined />} color="success" style={{ marginInlineEnd: 0 }}>
                  执行成功
                </Tag>
              }
            />
          );
        }
        if (record.status === 'FAILED') {
          return (
            <Badge
              status="error"
              text={
                <Tag icon={<CloseCircleOutlined />} color="error" style={{ marginInlineEnd: 0 }}>
                  执行失败
                </Tag>
              }
            />
          );
        }
        if (record.status === 'RUNNING') {
          return (
            <Badge
              status="processing"
              text={
                <Tag icon={<SyncOutlined spin />} color="processing" style={{ marginInlineEnd: 0 }}>
                  运行中
                </Tag>
              }
            />
          );
        }
        const s = STATUS_MAP[record.status || ''];
        return <Badge status={s?.status || 'default'} text={s?.text || record.status} />;
      },
    },
    {
      title: '耗时',
      dataIndex: 'costTimeMs',
      width: 100,
      search: false,
      render: (_, record) => {
        const ms = record.costTimeMs;
        if (ms == null) return '-';
        let color = '#52c41a';
        if (ms >= 1000) color = '#ff4d4f';
        else if (ms >= 200) color = '#faad14';
        return (
          <span style={{ color }}>
            <ClockCircleOutlined style={{ marginRight: 4 }} />
            {formatDuration(ms)}
          </span>
        );
      },
    },
    {
      title: '执行时间',
      dataIndex: 'createTime',
      width: 180,
      search: false,
      render: (_, record) => record.createTime || '-',
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
        title: '任务日志',
        subTitle: '监控与追溯定时/手动任务的执行状态、耗时及流程编排快照',
      }}
    >
      <ProTable<FlowTaskLog>
        className="fh-table"
        rowKey="id"
        actionRef={actionRef}
        headerTitle={initialTaskId ? `任务日志（taskId=${initialTaskId}）` : '全部任务日志'}
        tableLayout="fixed"
        scroll={{ x: 900, y: 100000 }}
        search={{
          labelWidth: 'auto',
          defaultCollapsed: false,
        }}
        toolBarRender={() => [
          initialTaskId && (
            <Popconfirm
              key="clear"
              title="确定清空该任务的全部日志？"
              onConfirm={async () => {
                await clearTaskLog(initialTaskId);
                message.success('日志已清空');
                actionRef.current?.reload();
              }}
            >
              <Button danger>清空日志</Button>
            </Popconfirm>
          ),
        ]}
        params={{ taskId: initialTaskId }}
        request={async (params) => {
          const result = await queryTaskLogPage({
            taskId: params.taskId || initialTaskId,
            taskName: params.taskName,
            status: params.status,
            triggerType: params.triggerType,
            page: (params.current || 1) - 1,
            size: params.pageSize || 20,
          });
          const data = (result as any)?.data || result;
          return {
            data: data?.items || [],
            success: true,
            total: data?.total || 0,
          };
        }}
        columns={columns}
        rowClassName={(record) =>
          record.status === 'FAILED' ? 'task-log-row-fail' : ''
        }
        pagination={{
          defaultPageSize: 20,
          showSizeChanger: true,
          pageSizeOptions: ['10', '20', '50', '100'],
        }}
        options={{
          density: true,
          fullScreen: true,
          reload: true,
          setting: true,
        }}
      />

      {drawerVisible && (
        <Drawer
          title={`任务快照复原 - ${currentLog?.taskName || ''}`}
          width="100%"
          open={drawerVisible}
          onClose={() => setDrawerVisible(false)}
          styles={{ body: { padding: 0 } }}
          destroyOnClose
        >
          {currentTrace ? (
            <FlowEditor
              value={currentDsl}
              isEdit={false}
              readonlyTrace={currentTrace}
            />
          ) : null}
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
        .task-log-row-fail td {
          background-color: #fff2f0 !important;
        }
        .task-log-row-fail:hover td {
          background-color: #ffebe8 !important;
        }
      `}</style>
    </PageContainer>
  );
};

export default TaskLogPage;
