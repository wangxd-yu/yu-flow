import React, { useRef, useState } from 'react';
import {
  ActionType,
  PageContainer,
  ProColumns,
  ProTable,
} from '@ant-design/pro-components';
import {
  Badge, Button, Drawer, message, Popconfirm, Space, Tag,
} from 'antd';
import {
  queryTaskLogPage,
  getTaskLog,
  clearTaskLog,
  FlowTaskLog,
} from '../../flow/task/services/taskService';

// 状态颜色映射
const STATUS_MAP: Record<string, { status: any; text: string }> = {
  SUCCESS: { status: 'success', text: '成功' },
  FAILED: { status: 'error', text: '失败' },
  RUNNING: { status: 'processing', text: '运行中' },
};

// ── 日志详情 Drawer ──
const TaskLogDetailDrawer: React.FC<{
  visible: boolean;
  logId: string | null;
  onClose: () => void;
}> = ({ visible, logId, onClose }) => {
  const [log, setLog] = useState<FlowTaskLog | null>(null);

  React.useEffect(() => {
    if (visible && logId) {
      getTaskLog(logId)
        .then((data: any) => setLog(data?.data || data))
        .catch(() => message.error('加载日志详情失败'));
    } else {
      setLog(null);
    }
  }, [visible, logId]);

  let parsedTrace: any = null;
  if (log?.traceData) {
    try {
      parsedTrace = JSON.parse(log.traceData);
    } catch {
      parsedTrace = log.traceData;
    }
  }

  return (
    <Drawer
      title={`任务日志详情 — ${log?.taskName || ''}`}
      width={800}
      open={visible}
      onClose={onClose}
      destroyOnClose
    >
      {log && (
        <Space direction="vertical" style={{ width: '100%' }} size={8}>
          <div><b>任务ID：</b><code>{log.taskId}</code></div>
          <div>
            <b>触发类型：</b>
            <Tag color={log.triggerType === 'MANUAL' ? 'blue' : 'purple'}>
              {log.triggerType}
            </Tag>
          </div>
          <div>
            <b>执行状态：</b>
            <Badge
              status={STATUS_MAP[log.status || '']?.status || 'default'}
              text={STATUS_MAP[log.status || '']?.text || log.status}
            />
          </div>
          <div><b>耗时：</b>{log.costTimeMs != null ? `${log.costTimeMs} ms` : '-'}</div>
          <div><b>执行时间：</b>{log.createTime}</div>
          {log.errorMsg && (
            <div>
              <b>错误信息：</b>
              <pre style={{
                background: '#fff2f0',
                border: '1px solid #ffccc7',
                padding: 8,
                borderRadius: 4,
                fontSize: 12,
                marginTop: 4,
                whiteSpace: 'pre-wrap',
              }}>
                {log.errorMsg}
              </pre>
            </div>
          )}
          {parsedTrace && (
            <div>
              <b>FlowTrace 快照：</b>
              <pre style={{
                background: '#f6ffed',
                border: '1px solid #b7eb8f',
                padding: 8,
                borderRadius: 4,
                fontSize: 11,
                marginTop: 4,
                maxHeight: 500,
                overflow: 'auto',
              }}>
                {typeof parsedTrace === 'string'
                  ? parsedTrace
                  : JSON.stringify(parsedTrace, null, 2)}
              </pre>
            </div>
          )}
        </Space>
      )}
    </Drawer>
  );
};

// ── 主组件 ──

const TaskLogPage: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const [detailVisible, setDetailVisible] = useState(false);
  const [selectedLogId, setSelectedLogId] = useState<string | null>(null);

  // URL 参数：从任务列表页跳转时带入 taskId
  const urlParams = new URLSearchParams(window.location.search);
  const initialTaskId = urlParams.get('taskId') || undefined;

  const columns: ProColumns<FlowTaskLog>[] = [
    {
      title: '任务名称',
      dataIndex: 'taskName',
      ellipsis: true,
    },
    {
      title: '触发类型',
      dataIndex: 'triggerType',
      width: 100,
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
      width: 100,
      valueEnum: {
        SUCCESS: { text: '成功', status: 'Success' },
        FAILED: { text: '失败', status: 'Error' },
        RUNNING: { text: '运行中', status: 'Processing' },
      },
      render: (_, record) => {
        const s = STATUS_MAP[record.status || ''];
        return <Badge status={s?.status || 'default'} text={s?.text || record.status} />;
      },
    },
    {
      title: '耗时',
      dataIndex: 'costTimeMs',
      width: 100,
      hideInSearch: true,
      render: (_, record) =>
        record.costTimeMs != null
          ? record.costTimeMs < 1000
            ? `${record.costTimeMs} ms`
            : `${(record.costTimeMs / 1000).toFixed(2)} s`
          : '-',
    },
    {
      title: '有 Trace',
      dataIndex: 'hasTrace',
      width: 80,
      hideInSearch: true,
      render: (_, record) =>
        record.hasTrace ? <Tag color="green">有</Tag> : <Tag color="default">无</Tag>,
    },
    {
      title: '执行时间',
      dataIndex: 'createTime',
      width: 160,
      hideInSearch: true,
    },
    {
      title: '操作',
      dataIndex: 'option',
      valueType: 'option',
      width: 120,
      render: (_, record) => [
        <a
          key="detail"
          onClick={() => {
            setSelectedLogId(record.id);
            setDetailVisible(true);
          }}
        >
          查看详情
        </a>,
      ],
    },
  ];

  return (
    <PageContainer header={{ title: '任务日志' }}>
      <ProTable<FlowTaskLog>
        rowKey="id"
        actionRef={actionRef}
        headerTitle={initialTaskId ? `任务日志（taskId=${initialTaskId}）` : '全部任务日志'}
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
            size: params.pageSize || 10,
          });
          const data = (result as any)?.data || result;
          return {
            data: data?.items || [],
            success: true,
            total: data?.total || 0,
          };
        }}
        columns={columns}
        pagination={{ pageSize: 10, showSizeChanger: true }}
      />

      <TaskLogDetailDrawer
        visible={detailVisible}
        logId={selectedLogId}
        onClose={() => setDetailVisible(false)}
      />
    </PageContainer>
  );
};

export default TaskLogPage;
