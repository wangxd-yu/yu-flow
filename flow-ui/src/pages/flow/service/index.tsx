import React, { useEffect, useRef, useState } from 'react';
import {
  ActionType,
  PageContainer,
  ProColumns,
  ProTable,
} from '@ant-design/pro-components';
import { Button, Divider, message, Popconfirm, Switch, Tag, Tooltip } from 'antd';
import { history, useLocation } from '@umijs/max';
import {
  queryServiceFlowPage,
  createServiceFlow,
  updateServiceFlow,
  batchDeleteServiceFlow,
  enableServiceFlow,
  disableServiceFlow,
  updateServiceFlowLogEnabled,
  getServiceFlow,
  publishServiceFlow,
  unpublishServiceFlow,
  FlowServiceFlow,
} from '@/services/flow/serviceFlowService';
import ServiceFlowForm from './components/ServiceFlowForm';
import ServiceManualRunModal from './components/ServiceManualRunModal';
import { confirmServiceUnpublish } from './components/confirmServiceUnpublish';
import DirectoryTreeLayout from '@/components/DirectoryTreeLayout';
import TableEmpty from '@/components/TableEmpty';
import { batchAssetHealth, type AssetHealth } from '@/services/flow/assetMetrics';
import { renderHealthTag } from '@/components/flow/AssetHealthTag';
import '@/styles/fullHeightTable.css';

const handleAdd = async (fields: Partial<FlowServiceFlow>) => {
  const hide = message.loading('正在添加');
  try {
    await createServiceFlow(fields);
    hide();
    message.success('添加成功');
    return true;
  } catch {
    hide();
    message.error('添加失败，请重试');
    return false;
  }
};

const handleUpdate = async (id: string, fields: Partial<FlowServiceFlow>) => {
  const hide = message.loading('正在更新');
  try {
    await updateServiceFlow(id, fields);
    hide();
    message.success('更新成功');
    return true;
  } catch {
    hide();
    message.error('更新失败，请重试');
    return false;
  }
};

const handleRemove = async (selectedRows: FlowServiceFlow[]) => {
  const hide = message.loading('正在删除');
  if (!selectedRows?.length) return true;
  try {
    await batchDeleteServiceFlow(selectedRows.map((row) => row.id));
    hide();
    message.success('删除成功');
    return true;
  } catch (e: any) {
    hide();
    // 引用拦截等业务错误已由 request 拦截器提示；此处兜底
    if (!e?.message) message.error('删除失败，请重试');
    return false;
  }
};

const ServiceFlowManagement: React.FC = () => {
  const location = useLocation();
  const actionRef = useRef<ActionType>();
  const [formVisible, setFormVisible] = useState<boolean>(false);
  const [currentRow, setCurrentRow] = useState<Partial<FlowServiceFlow>>({});
  const [isEditMode, setIsEditMode] = useState<boolean>(false);
  const [selectedRowsState, setSelectedRows] = useState<FlowServiceFlow[]>([]);
  const [manualRunOpen, setManualRunOpen] = useState(false);
  const [manualRunTarget, setManualRunTarget] = useState<FlowServiceFlow | null>(null);
  const [healthMap, setHealthMap] = useState<Record<string, AssetHealth>>({});
  const [formInitialTab, setFormInitialTab] = useState<string | undefined>();
  // 空态区分：是否处于筛选（目录 / 搜索条件）
  const [emptyFiltered, setEmptyFiltered] = useState<boolean>(false);

  useEffect(() => {
    const params = new URLSearchParams(location.search || '');
    const serviceId = params.get('serviceId');
    const tab = params.get('tab') || undefined;
    if (!serviceId) return;
    let cancelled = false;
    (async () => {
      try {
        const detail: any = await getServiceFlow(serviceId);
        if (cancelled) return;
        setCurrentRow(detail?.data || detail || { id: serviceId });
        setIsEditMode(true);
        setFormInitialTab(tab || 'runtime');
        setFormVisible(true);
      } catch {
        if (!cancelled) message.error('打开服务详情失败');
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

  const handleEditAction = async (record: FlowServiceFlow) => {
    try {
      const detail: any = await getServiceFlow(record.id);
      setCurrentRow(detail?.data || detail || record);
      setIsEditMode(true);
      setFormInitialTab(undefined);
      setFormVisible(true);
    } catch {
      message.error('加载服务详情失败');
    }
  };

  const handleFormSubmit = async (values: Partial<FlowServiceFlow>) => {
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

  const columns: ProColumns<FlowServiceFlow>[] = [
    {
      title: '服务名称',
      dataIndex: 'name',
      ellipsis: true,
      width: 200,
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
      title: '启用状态',
      dataIndex: 'enabled',
      width: 90,
      valueType: 'select',
      valueEnum: {
        true: { text: '启用' },
        false: { text: '停用' },
      },
      render: (_, record) => (
        <Switch
          size="small"
          checked={!!record.enabled}
          onChange={async (checked) => {
            try {
              if (checked) {
                await enableServiceFlow(record.id);
              } else {
                await disableServiceFlow(record.id);
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
      width: 120,
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
              await updateServiceFlowLogEnabled(record.id, checked);
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
        <Divider key="d0" type="vertical" />,
        <a
          key="run"
          onClick={() => {
            setManualRunTarget(record);
            setManualRunOpen(true);
          }}
        >
          手动调用
        </a>,
        <Divider key="d1" type="vertical" />,
        record.publishStatus === 1 ? (
          <a
            key="unpublish"
            onClick={async () => {
              const ok = await confirmServiceUnpublish(record.id, record.name);
              if (!ok) return;
              try {
                await unpublishServiceFlow(record.id);
                message.success('已下线');
                actionRef.current?.reload();
              } catch (e: any) {
                message.error(e?.message || '下线失败');
              }
            }}
          >
            下线
          </a>
        ) : (
          <a
            key="publish"
            onClick={async () => {
              try {
                const { confirmPublishWithGate } = await import(
                  '@/components/flow/release/confirmPublishWithGate'
                );
                const envCode = await confirmPublishWithGate({
                  assetType: 'SERVICE',
                  assetId: record.id,
                  assetName: record.name,
                });
                if (!envCode) return;
                await publishServiceFlow(record.id, envCode);
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
        <a
          key="logs"
          onClick={() => history.push(`/log/service?serviceId=${record.id}`)}
        >
          查看日志
        </a>,
        <Divider key="d3" type="vertical" />,
        <Popconfirm
          key="delete"
          title="确定删除该服务？"
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
      header={{ title: '服务管理' }}
      style={{
        height: 'calc(100vh - 26px)',
        overflow: 'hidden',
      }}
    >
      <DirectoryTreeLayout bizType="service" height="calc(100vh - 90px)">
        {(selectedDirectoryId, selectedDirectoryName) => (
          <ProTable<FlowServiceFlow>
            className="fh-table fh-table-fit"
            headerTitle={`服务列表 (${selectedDirectoryName || '全部'})`}
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
                新建服务
              </Button>,
              selectedRowsState?.length > 0 && (
                <Popconfirm
                  key="batchDelete"
                  title={`确定删除选中的 ${selectedRowsState.length} 个服务？`}
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
              const { current, pageSize, directoryId, name, enabled, publishStatus } = params as any;
              setEmptyFiltered(
                !!directoryId || !!name || enabled !== undefined || publishStatus !== undefined,
              );
              const enabledParam =
                enabled === true || enabled === 'true'
                  ? true
                  : enabled === false || enabled === 'false'
                    ? false
                    : undefined;
              const publishParam =
                publishStatus === 0 || publishStatus === '0'
                  ? 0
                  : publishStatus === 1 || publishStatus === '1'
                    ? 1
                    : undefined;
              const result = await queryServiceFlowPage({
                directoryId,
                name,
                enabled: enabledParam,
                publishStatus: publishParam,
                page: (current || 1) - 1,
                size: pageSize || 20,
              });
              const data = (result as any)?.data || result;
              const items: FlowServiceFlow[] = data?.items || [];
              try {
                const health = await batchAssetHealth(
                  items.filter((i) => i.id).map((i) => ({ assetType: 'SERVICE' as const, assetId: i.id })),
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
            locale={{
              emptyText: (
                <TableEmpty
                  entityName="服务"
                  filtered={emptyFiltered}
                  hint="沉淀可复用的编排流程，供接口 / 任务作为子流程调用"
                  onCreate={() => handleAddAction(selectedDirectoryId)}
                />
              ),
            }}
            rowSelection={{
              onChange: (_, selectedRows) => setSelectedRows(selectedRows),
            }}
          />
        )}
      </DirectoryTreeLayout>

      {formVisible && (
        <ServiceFlowForm
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

      <ServiceManualRunModal
        open={manualRunOpen}
        serviceId={manualRunTarget?.id}
        serviceName={manualRunTarget?.name}
        onClose={() => {
          setManualRunOpen(false);
          setManualRunTarget(null);
        }}
      />
    </PageContainer>
  );
};

export default ServiceFlowManagement;
