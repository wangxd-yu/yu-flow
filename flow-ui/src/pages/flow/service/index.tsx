import React, { useRef, useState } from 'react';
import {
  ActionType,
  PageContainer,
  ProColumns,
  ProTable,
} from '@ant-design/pro-components';
import { Button, Divider, message, Popconfirm, Switch, Tag, Tooltip } from 'antd';
import { history } from '@umijs/max';
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
} from './services/serviceFlowService';
import ServiceFlowForm from './components/ServiceFlowForm';
import DirectoryTreeLayout from '@/components/DirectoryTreeLayout';

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
  const actionRef = useRef<ActionType>();
  const [formVisible, setFormVisible] = useState<boolean>(false);
  const [currentRow, setCurrentRow] = useState<Partial<FlowServiceFlow>>({});
  const [isEditMode, setIsEditMode] = useState<boolean>(false);
  const [selectedRowsState, setSelectedRows] = useState<FlowServiceFlow[]>([]);

  const handleAddAction = (directoryId?: string) => {
    setCurrentRow({ directoryId });
    setIsEditMode(false);
    setFormVisible(true);
  };

  const handleEditAction = async (record: FlowServiceFlow) => {
    try {
      const detail: any = await getServiceFlow(record.id);
      setCurrentRow(detail?.data || detail || record);
      setIsEditMode(true);
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
      const ok = await handleAdd({ ...values, directoryId: currentRow?.directoryId });
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
      render: (_, record) => (
        <a onClick={() => handleEditAction(record)}>{record.name}</a>
      ),
    },
    {
      title: '所属目录',
      dataIndex: 'directoryName',
      width: 120,
      hideInSearch: true,
      render: (_, record) =>
        record.directoryName ? <Tag>{record.directoryName}</Tag> : '-',
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
      width: 320,
      render: (_, record) => [
        <a key="edit" onClick={() => handleEditAction(record)}>
          编辑
        </a>,
        <Divider key="d1" type="vertical" />,
        record.publishStatus === 1 ? (
          <a
            key="unpublish"
            onClick={async () => {
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
                await publishServiceFlow(record.id);
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

  const fullHeightTableCSS = `
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
    .fh-container .dir-tree-layout {
      flex: 1 !important;
      min-height: 0 !important;
      height: 100% !important;
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
  `;

  return (
    <PageContainer
      className="fh-container"
      header={{ title: '服务编排' }}
      style={{
        height: 'calc(100vh - 26px)',
        overflow: 'hidden',
      }}
    >
      <style>{fullHeightTableCSS}</style>
      <DirectoryTreeLayout bizType="service" height="calc(100vh - 90px)">
        {(selectedDirectoryId, selectedDirectoryName) => (
          <ProTable<FlowServiceFlow>
            className="fh-table"
            headerTitle={`服务列表 (${selectedDirectoryName || '全部'})`}
            scroll={{ x: 'max-content', y: 100000 }}
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
              const { current, pageSize, directoryId, name } = params as any;
              const result = await queryServiceFlowPage({
                directoryId,
                name,
                page: (current || 1) - 1,
                size: pageSize || 20,
              });
              const data = (result as any)?.data || result;
              return {
                data: data?.items || [],
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
        <ServiceFlowForm
          visible={formVisible}
          isEdit={isEditMode}
          initialValues={currentRow}
          onCancel={() => setFormVisible(false)}
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

export default ServiceFlowManagement;
