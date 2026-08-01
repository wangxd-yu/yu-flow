import React from 'react';
import { ProColumns } from '@ant-design/pro-components';
import { Divider, message, Popconfirm, Switch, Tooltip, Tag, Modal, Input } from 'antd';
import {
  queryMqTaskPage,
  createMqTask,
  updateMqTask,
  batchDeleteMqTask,
  enableMqTask,
  disableMqTask,
  updateMqTaskLogEnabled,
  simulateMqTask,
  getMqTask,
  publishMqTask,
  unpublishMqTask,
  getRunningMqTaskIds,
  FlowMqTask,
} from '@/services/flow/mqTask';
import MqTaskForm from './components/MqTaskForm';
import AssetDirectoryListShell, {
  type AssetListShellContext,
} from '@/components/flow/AssetDirectoryListShell';
import { renderHealthTag } from '@/components/flow/AssetHealthTag';

// ── CRUD 工具函数 ──

const handleAdd = async (fields: Partial<FlowMqTask>) => {
  const hide = message.loading('正在添加');
  try {
    await createMqTask(fields);
    hide();
    message.success('添加成功');
    return true;
  } catch (e: any) {
    hide();
    message.error(e?.message || '添加失败，请重试');
    return false;
  }
};

const handleUpdate = async (id: string, fields: Partial<FlowMqTask>) => {
  const hide = message.loading('正在更新');
  try {
    await updateMqTask(id, fields);
    hide();
    message.success('更新成功');
    return true;
  } catch (e: any) {
    hide();
    message.error(e?.message || '更新失败，请重试');
    return false;
  }
};

const handleRemove = async (selectedRows: FlowMqTask[]) => {
  const hide = message.loading('正在删除');
  if (!selectedRows?.length) return true;
  try {
    await batchDeleteMqTask(selectedRows.map((row) => row.id));
    hide();
    message.success('删除成功');
    return true;
  } catch {
    hide();
    message.error('删除失败，请重试');
    return false;
  }
};

/** 模拟触发：弹窗输入模拟消息体后调用 simulate */
const openSimulateModal = (record: FlowMqTask) => {
  let simMessage = '{"demo":true}';
  Modal.confirm({
    title: `模拟触发 - ${record.name}`,
    width: 520,
    content: (
      <div style={{ marginTop: 12 }}>
        <div style={{ marginBottom: 8, color: 'rgba(0,0,0,0.65)' }}>
          将以线上已发布版本执行一次，结果可在「查看日志」中回放。
        </div>
        <Input.TextArea
          rows={4}
          defaultValue={simMessage}
          placeholder="模拟消息体（原样作为 $.mq.message 注入）"
          onChange={(e) => {
            simMessage = e.target.value;
          }}
        />
      </div>
    ),
    okText: '触发',
    onOk: async () => {
      try {
        await simulateMqTask(record.id, simMessage);
        message.success('已触发模拟消息，请稍后在执行日志查看结果');
      } catch (e: any) {
        message.error(e?.message || '模拟触发失败');
      }
    },
  });
};

// ── 列定义 ──

/**
 * 消费状态：区分「本来就不该跑」与「应该跑却没跑」。
 * 后者是真正需要告警的故障（发布后订阅注册失败 / 重启后未恢复 / rebalance 卡死）。
 */
const renderConsumerStatus = (record: FlowMqTask, running: boolean) => {
  const shouldRun = record.publishStatus === 1 && !!record.enabled;
  if (!shouldRun) {
    return (
      <Tooltip title={record.enabled ? '任务未发布，不会订阅' : '任务已停用，不会订阅'}>
        <Tag style={{ margin: 0 }}>未订阅</Tag>
      </Tooltip>
    );
  }
  return running ? (
    <Tooltip title="消费订阅存活">
      <Tag color="success" style={{ margin: 0 }}>消费中</Tag>
    </Tooltip>
  ) : (
    <Tooltip title="任务已发布且已启用，但订阅未存活——消息不会被消费。可尝试重新发布，并检查 MQ 连接可用性">
      <Tag color="error" style={{ margin: 0 }}>已中断</Tag>
    </Tooltip>
  );
};

const buildColumns = (
  ctx: AssetListShellContext<FlowMqTask>,
): ProColumns<FlowMqTask>[] => [
  {
    title: '任务名称',
    dataIndex: 'name',
    ellipsis: true,
    width: 200,
    render: (_, record) => (
      <a onClick={() => ctx.openEdit(record)} title={record.name}>
        {record.name}
      </a>
    ),
  },
  {
    title: '所属目录',
    dataIndex: 'directoryName',
    width: 120,
    hideInSearch: true,
    ellipsis: true,
    render: (_, record) =>
      record.directoryName ? <Tag>{record.directoryName}</Tag> : '-',
  },
  {
    title: 'MQ 连接',
    dataIndex: 'connectionCode',
    width: 130,
    ellipsis: true,
    hideInSearch: true,
    render: (_, record) =>
      record.connectionCode ? (
        <Tag color="cyan" style={{ margin: 0 }}>
          {record.connectionCode}
        </Tag>
      ) : (
        '-'
      ),
  },
  {
    title: '订阅 Topic',
    dataIndex: 'topic',
    width: 180,
    ellipsis: true,
    hideInSearch: true,
    render: (_, record) => (
      <Tooltip title={`Topic: ${record.topic}`}>
        <code style={{ fontSize: 12, color: '#13c2c2' }}>{record.topic}</code>
      </Tooltip>
    ),
  },
  {
    title: '启用状态',
    dataIndex: 'enabled',
    width: 90,
    hideInSearch: true,
    render: (_, record) => (
      <Switch
        size="small"
        checked={!!record.enabled}
        onChange={async (checked) => {
          try {
            if (checked) {
              await enableMqTask(record.id);
            } else {
              await disableMqTask(record.id);
            }
            message.success(checked ? '已启用' : '已停用，消费订阅将停止');
            ctx.reload();
          } catch {
            message.error('操作失败');
          }
        }}
      />
    ),
  },
  {
    title: '发布状态',
    dataIndex: 'publishStatus',
    width: 110,
    valueType: 'select',
    valueEnum: {
      0: { text: '未发布', status: 'Default' },
      1: { text: '已发布', status: 'Success' },
    },
    render: (_, record) => {
      if (record.publishStatus === 1 && record.hasUnpublishedChanges) {
        return (
          <Tooltip title="存在未发布的草稿修改">
            <Tag color="warning">待更新发布</Tag>
          </Tooltip>
        );
      }
      return record.publishStatus === 1
        ? <Tag color="success">已发布</Tag>
        : <Tag>未发布</Tag>;
    },
  },
  {
    title: '消费状态',
    dataIndex: 'consumerStatus',
    width: 100,
    hideInSearch: true,
    render: (_, record) => renderConsumerStatus(record, !!ctx.extraMap[record.id]),
  },
  {
    title: (
      <Tooltip title="近 24 小时消费的消息条数（含幂等跳过），点击任务名进入「运行」页看趋势">
        <span>已消费(24h)</span>
      </Tooltip>
    ),
    dataIndex: 'consumedCount',
    width: 110,
    align: 'right',
    hideInSearch: true,
    render: (_, record) => {
      const total = ctx.healthMap[record.id]?.totalCalls ?? 0;
      return (
        <span style={{ color: total > 0 ? undefined : 'rgba(0,0,0,0.25)' }}>
          {total.toLocaleString()}
        </span>
      );
    },
  },
  {
    title: '运行健康',
    dataIndex: 'runtimeHealth',
    width: 100,
    hideInSearch: true,
    render: (_, record) => renderHealthTag(ctx.healthMap[record.id]),
  },
  {
    title: '执行日志',
    dataIndex: 'logEnabled',
    width: 90,
    hideInSearch: true,
    render: (_, record) => (
      <Switch
        size="small"
        checked={!!record.logEnabled}
        onChange={async (checked) => {
          try {
            await updateMqTaskLogEnabled(record.id, checked);
            message.success(checked ? '已开启日志' : '已关闭日志');
            ctx.reload();
          } catch {
            message.error('操作失败');
          }
        }}
      />
    ),
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
    width: 160,
    hideInSearch: true,
  },
  {
    title: '操作',
    dataIndex: 'option',
    valueType: 'option',
    width: 380,
    render: (_, record) => [
      <a key="edit" onClick={() => ctx.openEdit(record)}>
        编辑
      </a>,
      <Divider key="d1" type="vertical" />,
      record.publishStatus === 1 ? (
        <Popconfirm
          key="unpublish"
          title="确认下线该任务？下线后消费订阅将停止。"
          onConfirm={async () => {
            try {
              await unpublishMqTask(record.id);
              message.success('已下线');
              ctx.reload();
            } catch (e: any) {
              message.error(e?.message || '下线失败');
            }
          }}
        >
          <a>下线</a>
        </Popconfirm>
      ) : (
        <Popconfirm
          key="publish"
          title="确认发布该任务？发布后将以当前草稿快照启动消费订阅。"
          onConfirm={async () => {
            try {
              await publishMqTask(record.id);
              message.success('发布成功');
              ctx.reload();
            } catch (e: any) {
              message.error(e?.message || '发布失败');
            }
          }}
        >
          <a>发布</a>
        </Popconfirm>
      ),
      <Divider key="d2" type="vertical" />,
      record.publishStatus === 1 ? (
        <a key="simulate" onClick={() => openSimulateModal(record)}>
          模拟触发
        </a>
      ) : (
        <Tooltip key="simulate" title="请先发布后再模拟触发；草稿可用「调试运行」验证">
          <span style={{ color: 'rgba(0,0,0,0.25)', cursor: 'not-allowed' }}>模拟触发</span>
        </Tooltip>
      ),
      <Divider key="d3" type="vertical" />,
      <a
        key="logs"
        onClick={() => ctx.openEdit(record, 'logs')}
      >
        查看日志
      </a>,
      <Divider key="d4" type="vertical" />,
      <Popconfirm
        key="delete"
        title="确定删除该任务？"
        onConfirm={() => ctx.removeAndReload([record])}
      >
        <a style={{ color: '#ff4d4f' }}>删除</a>
      </Popconfirm>,
    ],
  },
];

// ── 主组件 ──

const MqTaskManagement: React.FC = () => (
  <AssetDirectoryListShell<FlowMqTask>
    bizType="mqtask"
    pageTitle="MQ 任务管理"
    entityLabel="MQ 任务"
    listTitle="MQ 任务列表"
    emptyHint="订阅消息队列 Topic 触发编排流程，支持模拟触发与执行日志回放"
    metricsAssetType="MQ_TASK"
    deepLinkParam="mqTaskId"
    fetchDetail={getMqTask}
    isFiltered={({ directoryId, name, publishStatus }) =>
      !!directoryId || !!name || publishStatus !== undefined
    }
    fetchPage={async (params) => {
      const { current, pageSize, directoryId, name, publishStatus } = params;
      const publishParam =
        publishStatus === 0 || publishStatus === '0'
          ? 0
          : publishStatus === 1 || publishStatus === '1'
            ? 1
            : undefined;
      const result = await queryMqTaskPage({
        directoryId,
        name,
        publishStatus: publishParam,
        page: (current || 1) - 1,
        size: pageSize || 20,
      });
      const data = (result as any)?.data || result;
      return { items: data?.items || [], total: data?.total || 0 };
    }}
    submitCreate={handleAdd}
    submitUpdate={handleUpdate}
    removeRows={handleRemove}
    buildColumns={buildColumns}
    scrollX={1780}
    fetchRowExtra={async () => {
      // 一次请求拿全部存活订阅，转为 taskId -> true 供消费状态列使用
      const res: any = await getRunningMqTaskIds();
      const ids = res?.data ?? res;
      const map: Record<string, boolean> = {};
      (Array.isArray(ids) ? ids : []).forEach((id: string) => {
        map[id] = true;
      });
      return map;
    }}
    renderForm={(form) => (
      <MqTaskForm
        visible={form.visible}
        isEdit={form.isEdit}
        initialValues={form.currentRow}
        initialTab={form.initialTab}
        onCancel={form.close}
        onSubmit={form.submit}
        onPublished={form.onPublished}
      />
    )}
  />
);

export default MqTaskManagement;
