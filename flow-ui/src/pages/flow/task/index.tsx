import React, { useCallback, useMemo, useState } from 'react';
import { ProColumns } from '@ant-design/pro-components';
import { Divider, message, Popconfirm, Switch, Tooltip, Tag } from 'antd';
import { history, useAccess } from '@umijs/max';
import {
  queryTaskPage,
  createTask,
  updateTask,
  batchDeleteTask,
  enableTask,
  disableTask,
  runTaskNow,
  getTask,
  publishTask,
  unpublishTask,
  FlowTask,
} from '@/services/flow/taskService';
import TaskForm from './components/TaskForm';
import { confirmTaskPublish } from './components/confirmTaskPublish';
import AssetDirectoryListShell, {
  type AssetListShellContext,
} from '@/components/flow/AssetDirectoryListShell';
import { renderHealthTag } from '@/components/flow/AssetHealthTag';
import { analyzeCron, formatCronTime } from '@/utils/cron';

// ── CRUD 工具函数 ──

const handleAdd = async (fields: Partial<FlowTask>) => {
  const hide = message.loading('正在添加');
  try {
    await createTask(fields);
    hide();
    message.success('添加成功');
    return true;
  } catch (e: any) {
    hide();
    message.error(e?.message || '添加失败，请重试');
    return false;
  }
};

const handleUpdate = async (id: string, fields: Partial<FlowTask>) => {
  const hide = message.loading('正在更新');
  try {
    await updateTask(id, fields);
    hide();
    message.success('更新成功');
    return true;
  } catch (e: any) {
    hide();
    message.error(e?.message || '更新失败，请重试');
    return false;
  }
};

const handleRemove = async (selectedRows: FlowTask[]) => {
  if (!selectedRows?.length) return true;
  const hide = message.loading('正在删除');
  try {
    await batchDeleteTask(selectedRows.map((row) => row.id));
    hide();
    message.success('删除成功');
    return true;
  } catch (e: any) {
    hide();
    message.error(e?.message || '删除失败，请重试');
    return false;
  }
};

// ── Cron 展示：附下次执行时间，避免只能靠肉眼推演表达式 ──

const CronCell: React.FC<{ cron?: string }> = ({ cron }) => {
  const analysis = useMemo(
    () => (cron ? analyzeCron(cron, { count: 3 }) : null),
    [cron],
  );
  if (!cron || !analysis) return <span style={{ color: '#bfbfbf' }}>—</span>;
  const tip =
    analysis.status === 'ok' ? (
      <div>
        <div style={{ marginBottom: 4 }}>接下来 3 次执行（已发布且启用时生效）：</div>
        {analysis.nextRuns.map((d) => (
          <div key={d.getTime()}>{formatCronTime(d)}</div>
        ))}
      </div>
    ) : (
      <div>
        <div>Cron: {cron}</div>
        <div>{analysis.message}</div>
      </div>
    );
  return (
    <Tooltip title={tip}>
      <code
        style={{
          fontSize: 12,
          color: analysis.status === 'invalid' ? '#ff4d4f' : '#722ed1',
        }}
      >
        {cron}
      </code>
    </Tooltip>
  );
};

/** 启停开关：请求期间锁定，避免连点导致状态与后端不一致 */
const EnabledSwitch: React.FC<{
  record: FlowTask;
  onDone: () => void;
}> = ({ record, onDone }) => {
  const [loading, setLoading] = useState(false);
  return (
    <Switch
      size="small"
      checked={!!record.enabled}
      loading={loading}
      disabled={loading}
      onChange={async (checked) => {
        setLoading(true);
        try {
          if (checked) {
            await enableTask(record.id);
          } else {
            await disableTask(record.id);
          }
          message.success(checked ? '已启用' : '已停用');
          onDone();
        } catch (e: any) {
          message.error(e?.message || '操作失败');
        } finally {
          setLoading(false);
        }
      }}
    />
  );
};

// ── 列定义 ──

const buildTaskColumns = (
  ctx: AssetListShellContext<FlowTask>,
  canWrite: boolean,
): ProColumns<FlowTask>[] => [
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
    title: 'Cron 表达式',
    dataIndex: 'cron',
    width: 180,
    ellipsis: true,
    hideInSearch: true,
    render: (_, record) => <CronCell cron={record.cron} />,
  },
  {
    title: '启用状态',
    dataIndex: 'enabled',
    width: 90,
    hideInSearch: true,
    render: (_, record) => {
      if (!canWrite) {
        return record.enabled === false
          ? <Tag>已停用</Tag>
          : <Tag color="success">已启用</Tag>;
      }
      return <EnabledSwitch record={record} onDone={ctx.reload} />;
    },
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
      const mode = record.logMode || (record.logEnabled === false ? 'OFF' : (record.logEnabled === true ? 'ALL' : 'SYSTEM_DEFAULT'));
      switch (mode) {
        case 'ALL':
          return <Tag color="blue">全量记录</Tag>;
        case 'ERROR_ONLY':
          return <Tag color="warning">仅错误</Tag>;
        case 'OFF':
          return <Tag color="default">完全关闭</Tag>;
        case 'SYSTEM_DEFAULT':
        default:
          return <Tag color="cyan">继承全局</Tag>;
      }
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
    width: canWrite ? 380 : 150,
    fixed: 'right',
    render: (_, record) => {
      const logs = (
        <a key="logs" onClick={() => history.push(`/log/task?taskId=${record.id}`)}>
          查看日志
        </a>
      );
      if (!canWrite) {
        return [
          <a key="detail" onClick={() => ctx.openEdit(record)}>
            查看
          </a>,
          <Divider key="d0" type="vertical" />,
          logs,
        ];
      }
      return [
        <a key="edit" onClick={() => ctx.openEdit(record)}>
          编辑
        </a>,
        <Divider key="d1" type="vertical" />,
        record.publishStatus === 1 ? (
          <Popconfirm
            key="unpublish"
            title="确认下线该任务？下线后定时调度将停止。"
            onConfirm={async () => {
              try {
                await unpublishTask(record.id);
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
          <a
            key="publish"
            onClick={async () => {
              const ok = await confirmTaskPublish(record);
              if (!ok) return;
              try {
                const { confirmPublishWithGate } = await import(
                  '@/components/flow/release/confirmPublishWithGate'
                );
                const envCode = await confirmPublishWithGate({
                  assetType: 'TASK',
                  assetId: record.id,
                  assetName: record.name,
                });
                if (!envCode) return;
                await publishTask(record.id, envCode);
                message.success('发布成功');
                ctx.reload();
              } catch (e: any) {
                message.error(e?.message || '发布失败');
              }
            }}
          >
            发布
          </a>
        ),
        <Divider key="d2" type="vertical" />,
        record.publishStatus === 1 ? (
          <Popconfirm
            key="run"
            title="立即执行一次线上版本？"
            description={
              record.enabled === false
                ? '该任务当前已停用，手动触发仍会真实执行一次。'
                : '将按已发布快照真实执行，可能产生数据变更。'
            }
            okText="立即执行"
            onConfirm={async () => {
              try {
                await runTaskNow(record.id);
                message.success('已触发执行，请稍后查看任务日志');
              } catch (e: any) {
                message.error(e?.message || '触发失败');
              }
            }}
          >
            <a>立即执行</a>
          </Popconfirm>
        ) : (
          <Tooltip key="run" title="请先发布后再立即执行；草稿可用「调试运行」验证">
            <span style={{ color: 'rgba(0,0,0,0.25)', cursor: 'not-allowed' }}>立即执行</span>
          </Tooltip>
        ),
        <Divider key="d3" type="vertical" />,
        logs,
        <Divider key="d4" type="vertical" />,
        <Popconfirm
          key="delete"
          title="确定删除该任务？"
          description={record.publishStatus === 1 ? '任务已发布，删除后线上调度一并移除。' : undefined}
          onConfirm={() => ctx.removeAndReload([record])}
        >
          <a style={{ color: '#ff4d4f' }}>删除</a>
        </Popconfirm>,
      ];
    },
  },
];

// ── 主组件 ──

const TaskManagement: React.FC = () => {
  const access = useAccess();
  const canWrite = !!access.canTaskWrite;

  const buildColumns = useCallback(
    (ctx: AssetListShellContext<FlowTask>) => buildTaskColumns(ctx, canWrite),
    [canWrite],
  );

  return (
    <AssetDirectoryListShell<FlowTask>
      bizType="task"
      pageTitle="任务管理"
      entityLabel="任务"
      listTitle="任务列表"
      emptyHint="用 Cron 表达式定时执行编排流程，支持手动触发与执行日志"
      metricsAssetType="TASK"
      transferAssetType="TASK"
      deepLinkParam="taskId"
      canWrite={canWrite}
      fitColumns={false}
      scrollX={1480}
      fetchDetail={getTask}
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
        const result = await queryTaskPage({
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
      renderForm={(form) => (
        <TaskForm
          visible={form.visible}
          isEdit={form.isEdit}
          initialValues={form.currentRow}
          initialTab={form.initialTab}
          readOnly={!canWrite}
          onCancel={form.close}
          onSubmit={form.submit}
          onPublished={form.onPublished}
        />
      )}
    />
  );
};

export default TaskManagement;
