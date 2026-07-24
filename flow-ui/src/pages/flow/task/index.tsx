import React, { useEffect, useRef, useState } from 'react';
import {
  ActionType,
  PageContainer,
  ProColumns,
  ProTable,
} from '@ant-design/pro-components';
import { Button, Divider, message, Popconfirm, Switch, Tooltip, Tag } from 'antd';
import { history, useLocation } from '@umijs/max';
import {
  queryTaskPage,
  createTask,
  updateTask,
  batchDeleteTask,
  enableTask,
  disableTask,
  updateTaskLogEnabled,
  runTaskNow,
  getTask,
  publishTask,
  unpublishTask,
  FlowTask,
} from '@/services/flow/taskService';
import TaskForm from './components/TaskForm';
import { confirmTaskPublish } from './components/confirmTaskPublish';
import DirectoryTreeLayout from '@/components/DirectoryTreeLayout';
import { batchAssetHealth, type AssetHealth } from '@/services/flow/assetMetrics';
import { renderHealthTag } from '@/components/flow/AssetHealthTag';
import '@/styles/fullHeightTable.css';

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
  const hide = message.loading('正在删除');
  if (!selectedRows?.length) return true;
  try {
    await batchDeleteTask(selectedRows.map((row) => row.id));
    hide();
    message.success('删除成功');
    return true;
  } catch {
    hide();
    message.error('删除失败，请重试');
    return false;
  }
};

// ── 主组件 ──

const TaskManagement: React.FC = () => {
  const location = useLocation();
  const actionRef = useRef<ActionType>();
  const [formVisible, setFormVisible] = useState<boolean>(false);
  const [currentRow, setCurrentRow] = useState<Partial<FlowTask>>({});
  const [isEditMode, setIsEditMode] = useState<boolean>(false);
  const [selectedRowsState, setSelectedRows] = useState<FlowTask[]>([]);
  const [healthMap, setHealthMap] = useState<Record<string, AssetHealth>>({});
  const [formInitialTab, setFormInitialTab] = useState<string | undefined>();

  useEffect(() => {
    const params = new URLSearchParams(location.search || '');
    const taskId = params.get('taskId');
    const tab = params.get('tab') || undefined;
    if (!taskId) return;
    let cancelled = false;
    (async () => {
      try {
        const detail: any = await getTask(taskId);
        if (cancelled) return;
        setCurrentRow(detail?.data || detail || { id: taskId });
        setIsEditMode(true);
        setFormInitialTab(tab || 'runtime');
        setFormVisible(true);
      } catch {
        if (!cancelled) message.error('打开任务详情失败');
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [location.search]);

  const handleAddAction = (directoryId?: string) => {
    setCurrentRow({ directoryId });
    setIsEditMode(false);
    setFormInitialTab(undefined);
    setFormVisible(true);
  };

  const handleEditAction = async (record: FlowTask) => {
    try {
      const detail: any = await getTask(record.id);
      setCurrentRow(detail?.data || detail || record);
      setIsEditMode(true);
      setFormInitialTab(undefined);
      setFormVisible(true);
    } catch {
      message.error('加载任务详情失败');
    }
  };

  const handleFormSubmit = async (values: Partial<FlowTask>) => {
    if (isEditMode && currentRow?.id) {
      const ok = await handleUpdate(currentRow.id, values);
      if (ok) {
        setFormVisible(false);
        actionRef.current?.reload();
      }
    } else {
      const ok = await handleAdd({
        ...values,
        directoryId: values.directoryId || currentRow?.directoryId,
      });
      if (ok) {
        setFormVisible(false);
        actionRef.current?.reload();
      }
    }
  };

  const columns: ProColumns<FlowTask>[] = [
    {
      title: '任务名称',
      dataIndex: 'name',
      ellipsis: true,
      width: 200,
      ellipsis: true,
      render: (_, record) => (
        <a onClick={() => handleEditAction(record)} title={record.name}>
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
      render: (_, record) => (
        <Tooltip title={`Cron: ${record.cron}`}>
          <code style={{ fontSize: 12, color: '#722ed1' }}>{record.cron}</code>
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
                await enableTask(record.id);
              } else {
                await disableTask(record.id);
              }
              message.success(checked ? '已启用' : '已停用');
              actionRef.current?.reload();
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
      title: '运行健康',
      dataIndex: 'runtimeHealth',
      width: 100,
      hideInSearch: true,
      render: (_, record) => renderHealthTag(healthMap[record.id]),
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
              await updateTaskLogEnabled(record.id, checked);
              message.success(checked ? '已开启日志' : '已关闭日志');
              actionRef.current?.reload();
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
        <a key="edit" onClick={() => handleEditAction(record)}>
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
                actionRef.current?.reload();
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
                actionRef.current?.reload();
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
          <a
            key="run"
            onClick={async () => {
              try {
                await runTaskNow(record.id);
                message.success('已触发执行，请稍后查看任务日志');
              } catch (e: any) {
                message.error(e?.message || '触发失败');
              }
            }}
          >
            立即执行
          </a>
        ) : (
          <Tooltip key="run" title="请先发布后再立即执行；草稿可用「调试运行」验证">
            <span style={{ color: 'rgba(0,0,0,0.25)', cursor: 'not-allowed' }}>立即执行</span>
          </Tooltip>
        ),
        <Divider key="d3" type="vertical" />,
        <a
          key="logs"
          onClick={() => history.push(`/log/task?taskId=${record.id}`)}
        >
          查看日志
        </a>,
        <Divider key="d4" type="vertical" />,
        <Popconfirm
          key="delete"
          title="确定删除该任务？"
          onConfirm={async () => {
            await handleRemove([record]);
            actionRef.current?.reload();
          }}
        >
          <a style={{ color: '#ff4d4f' }}>删除</a>
        </Popconfirm>,
      ],
    },
  ];

  return (
    <PageContainer
      className="fh-container"
      header={{ title: '任务管理' }}
      style={{
        height: 'calc(100vh - 26px)',
        overflow: 'hidden',
      }}
    >
      <DirectoryTreeLayout bizType="task" height="calc(100vh - 90px)">
        {(selectedDirectoryId, selectedDirectoryName) => (
          <ProTable<FlowTask>
            className="fh-table fh-table-fit"
            headerTitle={`任务列表 (${selectedDirectoryName || '全部'})`}
            tableLayout="fixed"
            scroll={{ x: 1400, y: 100000 }}
            pagination={{
              defaultPageSize: 20,
              showSizeChanger: true,
              showQuickJumper: true,
              style: { marginBottom: 0 },
            }}
            actionRef={actionRef}
            rowKey="id"
            search={{ labelWidth: 80 }}
            toolBarRender={() => [
              <Button
                key="add"
                type="primary"
                onClick={() => handleAddAction(selectedDirectoryId)}
              >
                新建任务
              </Button>,
              selectedRowsState?.length > 0 && (
                <Popconfirm
                  key="batchDelete"
                  title={`确定删除选中的 ${selectedRowsState.length} 个任务？`}
                  onConfirm={async () => {
                    await handleRemove(selectedRowsState);
                    setSelectedRows([]);
                    actionRef.current?.reload();
                  }}
                >
                  <Button danger>批量删除</Button>
                </Popconfirm>
              ),
            ]}
            params={{ directoryId: selectedDirectoryId }}
            request={async (params = {}) => {
              const { current, pageSize, directoryId, name, publishStatus } = params as any;
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
              const items: FlowTask[] = data?.items || [];
              try {
                const health = await batchAssetHealth(
                  items.filter((i) => i.id).map((i) => ({ assetType: 'TASK' as const, assetId: i.id })),
                );
                const map: Record<string, AssetHealth> = {};
                (health || []).forEach((h) => {
                  map[h.assetId] = h;
                });
                setHealthMap(map);
              } catch {
                setHealthMap({});
              }
              return {
                data: items,
                success: true,
                total: data?.total || 0,
              };
            }}
            columns={columns}
            rowSelection={{
              onChange: (_, selectedRows) => setSelectedRows(selectedRows),
            }}
          />
        )}
      </DirectoryTreeLayout>

      {formVisible && (
        <TaskForm
          visible={formVisible}
          isEdit={isEditMode}
          initialValues={currentRow}
          initialTab={formInitialTab}
          onCancel={() => {
            setFormVisible(false);
            setFormInitialTab(undefined);
          }}
          onSubmit={handleFormSubmit}
          onPublished={(detail) => {
            setCurrentRow(detail);
            actionRef.current?.reload();
          }}
        />
      )}
    </PageContainer>
  );
};

export default TaskManagement;
