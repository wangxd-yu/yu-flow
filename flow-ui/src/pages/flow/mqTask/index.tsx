import React, { useCallback, useEffect, useState } from 'react';
import { useAccess } from '@umijs/max';
import { ProColumns } from '@ant-design/pro-components';
import { DownOutlined } from '@ant-design/icons';
import { Divider, Dropdown, message, Popconfirm, Switch, Tooltip, Tag, Modal } from 'antd';
import type { MenuProps } from 'antd';
import {
  queryMqTaskPage,
  createMqTask,
  updateMqTask,
  batchDeleteMqTask,
  enableMqTask,
  disableMqTask,
  getMqTask,
  publishMqTask,
  unpublishMqTask,
  republishMqTask,
  getRunningMqTaskIds,
  getMqTaskBacklog,
  FlowMqTask,
} from '@/services/flow/mqTask';
import { queryMqConnectionOptions } from '@/services/flow/mqConnection';
import MqTaskForm from './components/MqTaskForm';
import MqSimulateModal from './components/MqSimulateModal';
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

// ── 列定义 ──

/** 日志策略：老数据只有 logEnabled 布尔值，按其真假映射到四态枚举 */
const resolveLogMode = (record: FlowMqTask) => {
  if (record.logMode) return record.logMode;
  if (record.logEnabled === false) return 'OFF';
  if (record.logEnabled === true) return 'ALL';
  return 'SYSTEM_DEFAULT';
};

const LOG_MODE_TAGS: Record<string, { color: string; text: string }> = {
  ALL: { color: 'blue', text: '全量记录' },
  ERROR_ONLY: { color: 'warning', text: '仅错误' },
  OFF: { color: 'default', text: '完全关闭' },
  SYSTEM_DEFAULT: { color: 'cyan', text: '继承全局' },
};

/**
 * 消费状态：区分「本来就不该跑」与「应该跑却没跑」。
 * 后者是真正需要告警的故障（发布后订阅注册失败 / 重启后未恢复 / rebalance 卡死）。
 */
const renderConsumerStatus = (record: FlowMqTask, extra?: { running?: boolean }) => {
  const running = !!extra?.running;
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

interface ColumnDeps {
  canWrite: boolean;
  connectionOptions: { label: string; value: string }[];
  onSimulate: (record: FlowMqTask) => void;
}

const buildColumns = (
  ctx: AssetListShellContext<FlowMqTask>,
  deps: ColumnDeps,
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
    valueType: 'select',
    fieldProps: {
      options: deps.connectionOptions,
      showSearch: true,
      optionFilterProp: 'label',
      allowClear: true,
      placeholder: '全部连接',
    },
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
    valueType: 'select',
    valueEnum: {
      true: { text: '已启用', status: 'Success' },
      false: { text: '已停用', status: 'Default' },
    },
    render: (_, record) => (
      <Switch
        size="small"
        checked={!!record.enabled}
        disabled={!deps.canWrite}
        onChange={async (checked) => {
          try {
            if (checked) {
              await enableMqTask(record.id);
            } else {
              await disableMqTask(record.id);
            }
            message.success(checked ? '已启用' : '已停用，消费订阅将停止');
            ctx.reload();
          } catch (e: any) {
            message.error(e?.message || '操作失败');
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
    render: (_, record) => renderConsumerStatus(record, ctx.extraMap[record.id]),
  },
  {
    title: (
      <Tooltip title="Kafka=consumer lag，Rabbit=队列深度；「-」表示未知或不支持">
        <span>积压</span>
      </Tooltip>
    ),
    dataIndex: 'backlog',
    width: 90,
    align: 'right',
    hideInSearch: true,
    render: (_, record) => {
      const backlog = ctx.extraMap[record.id]?.backlog;
      if (backlog == null) {
        return <span style={{ color: 'rgba(0,0,0,0.25)' }}>-</span>;
      }
      const color = backlog > 1000 ? '#ff4d4f' : backlog > 100 ? '#faad14' : undefined;
      return <span style={{ color, fontVariantNumeric: 'tabular-nums' }}>{backlog.toLocaleString()}</span>;
    },
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
    title: (
      <Tooltip title="日志策略模式：继承全局 / 仅错误时记录 / 全量记录 / 完全关闭">
        <span>日志策略</span>
      </Tooltip>
    ),
    dataIndex: 'logMode',
    width: 100,
    hideInSearch: true,
    render: (_, record) => {
      const tag = LOG_MODE_TAGS[resolveLogMode(record)] || LOG_MODE_TAGS.SYSTEM_DEFAULT;
      return <Tag color={tag.color}>{tag.text}</Tag>;
    },
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
    width: deps.canWrite ? 220 : 140,
    fixed: 'right',
    render: (_, record) => {
      const logs = (
        <a key="logs" onClick={() => ctx.openEdit(record, 'logs')}>
          查看日志
        </a>
      );
      if (!deps.canWrite) {
        return [
          <a key="detail" onClick={() => ctx.openEdit(record)}>
            查看
          </a>,
          <Divider key="d0" type="vertical" />,
          logs,
        ];
      }

      const moreItems: MenuProps['items'] = [
        {
          key: 'simulate',
          label: '模拟触发',
          disabled: record.publishStatus !== 1,
          title: record.publishStatus !== 1
            ? '请先发布后再模拟触发；草稿可用「调试运行」验证'
            : undefined,
        },
        {
          key: 'republish',
          label: '重新发布',
          disabled: record.publishStatus !== 1,
          title: record.publishStatus !== 1
            ? '仅已发布任务可重新发布'
            : '订阅中断时重建消费订阅',
        },
        { key: 'logs', label: '查看日志' },
        { type: 'divider' },
        { key: 'delete', label: '删除', danger: true },
      ];

      const onMoreClick: MenuProps['onClick'] = ({ key }) => {
        if (key === 'simulate') {
          deps.onSimulate(record);
        } else if (key === 'republish') {
          Modal.confirm({
            title: `重新发布「${record.name}」？`,
            content: record.hasUnpublishedChanges
              ? '会按当前草稿重新生成发布快照并重建消费订阅，草稿上的改动将同时上线。'
              : '会按当前草稿重新生成发布快照并重建消费订阅，常用于修复「已中断」的订阅。',
            okText: '重新发布',
            onOk: async () => {
              try {
                await republishMqTask(record.id);
                message.success('已重新发布，消费订阅已重建');
                ctx.reload();
              } catch (e: any) {
                message.error(e?.message || '重新发布失败');
                throw e;
              }
            },
          });
        } else if (key === 'logs') {
          ctx.openEdit(record, 'logs');
        } else if (key === 'delete') {
          Modal.confirm({
            title: `确定删除「${record.name}」？`,
            content: record.publishStatus === 1
              ? '任务已发布，删除后线上消费订阅一并移除。'
              : undefined,
            okText: '删除',
            okButtonProps: { danger: true },
            onOk: () => ctx.removeAndReload([record]),
          });
        }
      };

      return [
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
        <Dropdown key="more" menu={{ items: moreItems, onClick: onMoreClick }} trigger={['click']}>
          <a onClick={(e) => e.preventDefault()}>
            更多 <DownOutlined style={{ fontSize: 10 }} />
          </a>
        </Dropdown>,
      ];
    },
  },
];

// ── 主组件 ──

const MqTaskManagement: React.FC = () => {
  const access = useAccess();
  const canWrite = !!access.canMqWrite;
  const [connectionOptions, setConnectionOptions] = useState<{ label: string; value: string }[]>([]);
  const [simulateTarget, setSimulateTarget] = useState<FlowMqTask | null>(null);

  // 连接候选用于搜索栏下拉，拉不到就退化成无选项（不影响列表）
  useEffect(() => {
    let cancelled = false;
    queryMqConnectionOptions()
      .then((res: any) => {
        if (cancelled) return;
        const list = res?.data ?? res;
        setConnectionOptions(
          (Array.isArray(list) ? list : []).map((c: any) => ({
            label: `${c.name} (${c.code})`,
            value: c.code,
          })),
        );
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, []);

  const columnsBuilder = useCallback(
    (ctx: AssetListShellContext<FlowMqTask>) =>
      buildColumns(ctx, { canWrite, connectionOptions, onSimulate: setSimulateTarget }),
    [canWrite, connectionOptions],
  );

  return (
    <AssetDirectoryListShell<FlowMqTask>
      bizType="mqtask"
      pageTitle="MQ 任务管理"
      entityLabel="MQ 任务"
      listTitle="MQ 任务列表"
      emptyHint="订阅消息队列 Topic 触发编排流程，支持模拟触发与执行日志回放"
      metricsAssetType="MQ_TASK"
      deepLinkParam="mqTaskId"
      canWrite={canWrite}
      fetchDetail={getMqTask}
      isFiltered={({ directoryId, name, publishStatus, connectionCode, enabled }) =>
        !!directoryId || !!name || publishStatus !== undefined
        || !!connectionCode || enabled !== undefined
      }
      fetchPage={async (params) => {
        const { current, pageSize, directoryId, name, publishStatus, connectionCode, enabled } = params;
        const publishParam =
          publishStatus === 0 || publishStatus === '0'
            ? 0
            : publishStatus === 1 || publishStatus === '1'
              ? 1
              : undefined;
        const enabledParam =
          enabled === true || enabled === 'true'
            ? true
            : enabled === false || enabled === 'false'
              ? false
              : undefined;
        const result = await queryMqTaskPage({
          directoryId,
          name,
          connectionCode,
          enabled: enabledParam,
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
      buildColumns={columnsBuilder}
      fitColumns={false}
      scrollX={1800}
      fetchRowExtra={async () => {
        const [runningRes, backlogRes]: any[] = await Promise.all([
          getRunningMqTaskIds(),
          getMqTaskBacklog(),
        ]);
        const runningIds = runningRes?.data ?? runningRes;
        const backlogMap = backlogRes?.data ?? backlogRes ?? {};
        const map: Record<string, { running: boolean; backlog: number | null }> = {};
        const allIds = new Set<string>([
          ...(Array.isArray(runningIds) ? runningIds : []),
          ...Object.keys(backlogMap || {}),
        ]);
        allIds.forEach((id) => {
          map[id] = {
            running: Array.isArray(runningIds) && runningIds.includes(id),
            backlog: backlogMap[id] ?? null,
          };
        });
        return map;
      }}
      renderForm={(form) => (
        <MqTaskForm
          visible={form.visible}
          isEdit={form.isEdit}
          initialValues={form.currentRow}
          initialTab={form.initialTab}
          canWrite={canWrite}
          onCancel={form.close}
          onSubmit={form.submit}
          onPublished={form.onPublished}
        />
      )}
    >
      <MqSimulateModal
        open={!!simulateTarget}
        taskId={simulateTarget?.id}
        taskName={simulateTarget?.name}
        onClose={() => setSimulateTarget(null)}
      />
    </AssetDirectoryListShell>
  );
};

export default MqTaskManagement;
