import React, { useRef, useState } from 'react';
import {
  ActionType,
  FooterToolbar,
  PageContainer,
  ProColumns,
  ProDescriptions,
  ProDescriptionsItemProps,
  ProTable,
  ModalForm,
} from '@ant-design/pro-components';
import { Button, Divider, Drawer, message, Tag, Popconfirm, Space } from 'antd';
import {
  queryAutoApiConfigDetail,
  queryAutoApiConfigList,
  addAutoApiConfig,
  updateAutoApiConfig,
  deleteAutoApiConfig,
  batchDeleteAutoApiConfig,
  batchMoveAutoApiConfig,
  FlowController,
} from './services/flowController';
import ApiConfigForm from './components/ControllerForm';
import DirectoryTreeLayout from '@/components/DirectoryTreeLayout';
import DirectoryTreeSelect from '@/components/DirectoryTreeSelect';
import { extensionRegistry } from '@/utils/extensionRegistry';

/**
 * 添加配置
 */
const handleAdd = async (fields: Partial<FlowController>) => {
  const hide = message.loading('正在添加');
  try {
    await addAutoApiConfig(fields);
    hide();
    message.success('添加成功');
    return true;
  } catch (error) {
    hide();
    message.error('添加失败请重试！');
    return false;
  }
};

/**
 * 更新配置
 */
const handleUpdate = async (id: string, fields: Partial<FlowController>) => {
  const hide = message.loading('正在更新');
  try {
    await updateAutoApiConfig(id, fields);
    hide();
    message.success('更新成功');
    return true;
  } catch (error) {
    hide();
    message.error('更新失败请重试！');
    return false;
  }
};

/**
 * 删除配置
 */
const handleRemove = async (selectedRows: FlowController[]) => {
  const hide = message.loading('正在删除');
  if (!selectedRows?.length) return true;
  try {
    await batchDeleteAutoApiConfig(selectedRows.map((row) => row.id));
    hide();
    message.success('删除成功，即将刷新');
    return true;
  } catch (error) {
    hide();
    message.error('删除失败，请重试');
    return false;
  }
};

const AutoApiConfigList: React.FC = () => {
  const [createModalVisible, handleModalVisible] = useState<boolean>(false);
  const actionRef = useRef<ActionType>();
  const [row, setRow] = useState<FlowController>();
  const [selectedRowsState, setSelectedRows] = useState<FlowController[]>([]);
  const [batchMoveModalVisible, setBatchMoveModalVisible] = useState<boolean>(false);

  // 状态定义
  const [formVisible, setFormVisible] = useState<boolean>(false);
  const [currentRow, setCurrentRow] = useState<Partial<FlowController>>({});
  const [isEditMode, setIsEditMode] = useState<boolean>(false);

  // 新建配置
  const handleAddAction = () => {
    setCurrentRow({});
    setIsEditMode(false);
    setFormVisible(true);
  };

  // 编辑配置
  const handleEdit = async (record: FlowController) => {
    const hide = message.loading('正在获取详情');
    try {
      const detail = await queryAutoApiConfigDetail(record.id);
      hide();
      setCurrentRow(detail);
      setIsEditMode(true);
      setFormVisible(true);
    } catch (error) {
      hide();
      message.error('获取详情失败，请重试');
    }
  };

  const columns: ProColumns<FlowController>[] = [
    {
      title: '名称',
      dataIndex: 'name',
      tip: '配置名称',
      formItemProps: {
        rules: [
          {
            required: true,
            message: '名称为必填项',
          },
        ],
      },
    },
    {
      title: '所属目录',
      dataIndex: 'directoryName',
      hideInSearch: true,
      render: (_, record) => record.directoryName ? <Tag color="blue">{record.directoryName}</Tag> : '-',
    },
    {
      title: '请求方法',
      dataIndex: 'method',
      valueEnum: {
        GET: { text: 'GET', status: 'GET' },
        POST: { text: 'POST', status: 'POST' },
        PUT: { text: 'PUT', status: 'PUT' },
        DELETE: { text: 'DELETE', status: 'DELETE' },
      },
      render: (_, record) => {
        const colorMap = {
          GET: 'blue',
          POST: 'green',
          PUT: 'orange',
          DELETE: 'red',
        };
        return <Tag color={colorMap[record.method || 'GET']}>{record.method}</Tag>;
      },
    },
    {
      title: 'URL',
      dataIndex: 'url',
      valueType: 'text',
    },
    {
      title: '发布状态',
      dataIndex: 'publishStatus',
      valueEnum: {
        0: { text: '未发布', status: 'Default' },
        1: { text: '已发布', status: 'Success' },
      },
    },
    {
      title: '实现方式',
      dataIndex: 'serviceType',
      valueEnum: {
        FLOW: { text: '逻辑编排', status: 'Processing' }, // 蓝色
        DB: { text: '数据库', status: 'Success' }, // 绿色
        JSON: { text: '静态 JSON', status: 'Warning' }, // 橙色
        STRING: { text: '静态文本', status: 'Default' }, // 灰色
      },
    },
    {
      title: '标签',
      dataIndex: 'tags',
      valueType: 'text',
      search: false,
      render: (_, record) => {
        if (!record.tags) {
          return '-';
        }

        let tagsArray: string[] = [];

        if (Array.isArray(record.tags)) {
          tagsArray = record.tags;
        }
        else if (typeof record.tags === 'string') {
          tagsArray = (record.tags as string).split(',').map((tag: string) => tag.trim()).filter((tag: string) => tag.length > 0);
        }

        if (tagsArray.length === 0) {
          return '-';
        }

        return tagsArray.map((tag: string) => (
          <Tag key={tag} color="blue" style={{ marginBottom: '4px' }}>
            {tag}
          </Tag>
        ));
      },
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      valueType: 'dateTime',
      search: false,
    },
    {
      title: '操作',
      dataIndex: 'option',
      valueType: 'option',
      render: (_, record) => (
        <>
          <a onClick={() => handleEdit(record)}>编辑</a>
          <Divider type="vertical" />
          <Popconfirm
            title="确认删除该配置吗？"
            onConfirm={async () => {
              await deleteAutoApiConfig(record.id);
              actionRef.current?.reload();
            }}
          >
            <a>删除</a>
          </Popconfirm>
        </>
      )
    }
  ];

  return (
    <PageContainer
      header={{
        title: 'API配置管理',
      }}
      style={{
        height: '100vh',
        overflow: 'hidden',
      }}
    >
      <DirectoryTreeLayout height="calc(100vh - 100px)">
        {(selectedDirectoryId, selectedDirectoryName) => (
          <ProTable<FlowController>
            headerTitle={`API配置列表 (${selectedDirectoryName || '全部'})`}
            scroll={{ y: 'calc(100vh - 380px)', x: 'max-content' }}
            pagination={{
              defaultPageSize: 20,
              showSizeChanger: true,
              showQuickJumper: true,
              style: { marginBottom: 0 },
            }}
            actionRef={actionRef}
            rowKey="id"
            search={{
              labelWidth: 120,
            }}
            toolBarRender={() => [
              <Button
                key="1"
                type="primary"
                onClick={handleAddAction}
              >
                新建配置
              </Button>
            ]}
            params={{ directoryId: selectedDirectoryId }}
            request={async (params = {}, sort, filter) => {
              const { current, pageSize, directoryId, ...restParams } = params as any;
              const data = await queryAutoApiConfigList({
                ...restParams,
                directoryId,
                page: (current || 1) - 1,
                size: pageSize || 20,
              });

              if (data) {
                console.log("data------", data)
              }
              return {
                data: data?.items || [],
                success: true,
                total: data?.total,
              };
            }}
            columns={columns}
            rowSelection={{
              onChange: (_, selectedRows) => setSelectedRows(selectedRows),
            }}
            tableAlertOptionRender={() => {
              return (
                <Space size={16}>
                  <a
                    onClick={() => {
                      setBatchMoveModalVisible(true);
                    }}
                  >
                    批量移动
                  </a>
                </Space>
              );
            }}
          />
        )}
      </DirectoryTreeLayout>

      {selectedRowsState?.length > 0 && (
        <FooterToolbar
          extra={
            <div>
              已选择{' '}
              <a style={{ fontWeight: 600 }}>{selectedRowsState.length}</a>{' '}
              项&nbsp;&nbsp;
            </div>
          }
        >
          <Button
            onClick={async () => {
              await handleRemove(selectedRowsState);
              setSelectedRows([]);
              actionRef.current?.reloadAndRest?.();
            }}
          >
            批量删除
          </Button>
          <Button
            type="primary"
            onClick={async () => {
              await updateAutoApiConfig(selectedRowsState[0].id, { publishStatus: 1 });
              setSelectedRows([]);
              actionRef.current?.reloadAndRest?.();
            }}
          >
            批量发布
          </Button>
        </FooterToolbar>
      )}
      <ApiConfigForm
        isEdit={isEditMode}
        modalVisible={formVisible}
        onCancel={() => setFormVisible(false)}
        onSubmit={(success) => {
          if (success) {
            setFormVisible(false);
            actionRef.current?.reload();
          }
        }}
        values={currentRow}
        addonDebuggerComponent={extensionRegistry.get('ProFlowDebugger')}
      />      <Drawer
        width={600}
        open={!!row}
        onClose={() => {
          setRow(undefined);
        }}
        closable={false}
      >
        {row?.name && (
          <ProDescriptions<FlowController>
            column={2}
            title={row?.name}
            request={async () => ({
              data: row || {},
            })}
            params={{
              id: row?.id,
            }}
            columns={columns.filter((item) => item.dataIndex !== 'option') as ProDescriptionsItemProps<FlowController>[]}
          />
        )}
      </Drawer>

      <ModalForm
        title="批量移动至"
        width="400px"
        open={batchMoveModalVisible}
        onOpenChange={setBatchMoveModalVisible}
        modalProps={{
          destroyOnClose: true,
        }}
        onFinish={async (values) => {
          const targetDir = values.targetDirectoryId || '0';
          const selectedRowKeys = selectedRowsState.map(r => r.id);
          try {
            await batchMoveAutoApiConfig(selectedRowKeys, targetDir);
            message.success('批量移动成功');
            setBatchMoveModalVisible(false);
            actionRef.current?.clearSelected?.();
            setSelectedRows([]);
            actionRef.current?.reload();
            return true;
          } catch (error) {
            message.error('批量移动失败');
            return false;
          }
        }}
      >
        <DirectoryTreeSelect />
      </ModalForm>
    </PageContainer>
  );
};

export default AutoApiConfigList;
