import {
  ActionType,
  FooterToolbar,
  PageContainer,
  ProColumns,
  ProDescriptions,
  ProDescriptionsItemProps,
  ProTable,
} from '@ant-design/pro-components';
import { Badge, Button, Divider, Drawer, message, Popconfirm, Tag, Tooltip } from 'antd';
import React, { useRef, useState } from 'react';
import DataSourceForm from './components/DataSourceForm';
import {
  addDataSource,
  batchDeleteDataSource,
  DataSourceDO,
  deleteDataSource,
  disableDataSource,
  enableDataSource,
  queryDataSourcePage,
  testDataSourceConnection,
  updateDataSource,
} from './services/dataSource';

/**
 * 添加数据源
 */
const handleAdd = async (fields: DataSourceDO) => {
  const hide = message.loading('正在添加');
  try {
    await addDataSource(fields);
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
 * 更新数据源
 */
const handleUpdate = async (id: string, fields: Partial<DataSourceDO>) => {
  const hide = message.loading('正在更新');
  try {
    await updateDataSource(id, fields);
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
 * 删除数据源
 */
const handleRemove = async (selectedRows: DataSourceDO[]) => {
  const hide = message.loading('正在删除');
  if (!selectedRows?.length) return true;
  try {
    await batchDeleteDataSource(selectedRows.map((row) => row.id));
    hide();
    message.success('删除成功，即将刷新');
    return true;
  } catch (error) {
    hide();
    message.error('删除失败，请重试');
    return false;
  }
};

const DataSourceList: React.FC = () => {
  const [createModalVisible, handleModalVisible] = useState<boolean>(false);
  const [updateModalVisible, handleUpdateModalVisible] =
    useState<boolean>(false);
  const [stepFormValues, setStepFormValues] = useState<Partial<DataSourceDO>>();
  const actionRef = useRef<ActionType>();
  const [row, setRow] = useState<DataSourceDO>();
  const [selectedRowsState, setSelectedRows] = useState<DataSourceDO[]>([]);

  const columns: ProColumns<DataSourceDO>[] = [
    {
      title: '名称',
      dataIndex: 'name',
      tip: '数据源名称',
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
      title: '数据源编码',
      dataIndex: 'code',
      tip: '全局唯一编码，用于跨环境关联',
      copyable: true,
      ellipsis: true,
      search: false,
      hideInForm: true,
    },
    {
      title: '数据库类型',
      dataIndex: 'dbType',
      valueType: 'text',
      valueEnum: {
        mysql: { text: 'MySQL' },
        postgresql: { text: 'PostgreSQL' },
        highgo: { text: 'HighGo' },
      },
      render: (_, record) => {
        const colorMap: Record<string, string> = {
          mysql: 'blue',
          postgresql: 'green',
          highgo: 'orange',
        };
        return (
          <Tag color={colorMap[record.dbType || 'mysql']}>{record.dbType}</Tag>
        );
      },
    },
    {
      title: 'URL',
      dataIndex: 'url',
      valueType: 'text',
      ellipsis: true,
    },
    {
      title: '用户名',
      dataIndex: 'username',
      valueType: 'text',
      search: false,
    },
    {
      title: '状态',
      dataIndex: 'status',
      valueEnum: {
        0: { text: '禁用', status: 'Error' },
        1: { text: '启用', status: 'Success' },
      },
    },
    {
      title: '健康度',
      dataIndex: 'healthStatus',
      search: false,
      width: 100,
      render: (_, record) => {
        const { healthStatus, errorCount, lastErrorMsg } = record;

        if (healthStatus === 'HEALTHY') {
          return <Badge status="success" text="正常" />;
        }

        if (healthStatus === 'UNHEALTHY') {
          const tipContent = (
            <span>
              连续失败 <strong>{errorCount ?? 0}</strong> 次
              {lastErrorMsg ? `。报错：${lastErrorMsg}` : ''}
            </span>
          );
          return (
            <Tooltip title={tipContent} color="red">
              <Badge status="error" text="异常" style={{ cursor: 'help' }} />
            </Tooltip>
          );
        }

        if (healthStatus === 'CIRCUIT_OPEN') {
          const tipContent = (
            <span>
              已熔断（连续失败 <strong>{errorCount ?? 0}</strong> 次），系统已暂停自动重连，每 5 分钟探测一次
              {lastErrorMsg ? `。最近报错：${lastErrorMsg}` : ''}
            </span>
          );
          return (
            <Tooltip title={tipContent} color="orange">
              <Badge color="orange" text="已熔断" style={{ cursor: 'help' }} />
            </Tooltip>
          );
        }

        // UNKNOWN 或未返回
        return <Badge status="default" text="未知" />;
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
      width: '300px',
      render: (_, record) => [
        <a
          key="edit"
          onClick={() => {
            handleUpdateModalVisible(true);
            setStepFormValues(record);
          }}
        >
          编辑
        </a>,
        <Divider type="vertical" />,
        <Popconfirm
          key="delete"
          title="确定要删除吗？"
          onConfirm={async () => {
            await deleteDataSource(record.id);
            actionRef.current?.reload();
          }}
        >
          <a>删除</a>
        </Popconfirm>,
        <Divider type="vertical" />,
        <a
          key="test"
          onClick={async () => {
            const hide = message.loading('正在测试连接...', 0);
            try {
              const result = await testDataSourceConnection(record.id);
              if (result) {
                message.success('连接测试成功');
              } else {
                message.error('连接测试失败');
              }
            } catch (error: any) {
              // 错误已经在 request.ts 的拦截器中通过 message.error 弹出了，
              // 这里只需要捕获异常，防止其变成未处理的 Promise Rejection 导致页面白屏报错。
              console.log('测试连接异常：', error);
            } finally {
              hide();
              // 测试完毕后，不管成功与否都刷新列表，以更新健康度显示
              actionRef.current?.reload();
            }
          }}
        >
          测试连接
        </a>,
        <Divider type="vertical" />,
        record.status == 1 ? (
          <a
            key="disable"
            onClick={async () => {
              await disableDataSource(record.id);
              actionRef.current?.reload();
              message.success('已禁用数据源');
            }}
          >
            禁用
          </a>
        ) : (
          <a
            key="enable"
            onClick={async () => {
              await enableDataSource(record.id);
              actionRef.current?.reload();
              message.success('已启用数据源');
            }}
          >
            启用
          </a>
        ),
      ],
    },
  ];

  return (
    <PageContainer
      header={{
        title: '动态数据源管理',
      }}
    >
      <ProTable<DataSourceDO>
        headerTitle="数据源列表"
        actionRef={actionRef}
        rowKey="id"
        search={{
          labelWidth: 120,
        }}
        toolBarRender={() => [
          <Button
            key="1"
            type="primary"
            onClick={() => {
              handleModalVisible(true);
              setStepFormValues(undefined);
            }}
          >
            新建数据源
          </Button>,
        ]}
        request={async (params = {}) => {
          const { current, pageSize, directoryId, ...restParams } = params as any;

          const data = await queryDataSourcePage({
            ...restParams,
            directoryId,
            page: (current || 1) - 1,
            size: pageSize || 20,
          });
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
      />
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
        </FooterToolbar>
      )}
      <DataSourceForm
        onSubmit={async (success) => {
          if (success) {
            handleModalVisible(false);
            setStepFormValues(undefined);
            if (actionRef.current) {
              actionRef.current.reload();
            }
          }
          return true;
        }}
        onCancel={() => {
          handleModalVisible(false);
          setStepFormValues(undefined);
        }}
        modalVisible={createModalVisible}
        values={stepFormValues || {}}
      />
      {stepFormValues && Object.keys(stepFormValues).length ? (
        <DataSourceForm
          onSubmit={async (success) => {
            if (success) {
              handleUpdateModalVisible(false);
              setStepFormValues({});
              if (actionRef.current) {
                actionRef.current.reload();
              }
            }
            return true;
          }}
          onCancel={() => {
            handleUpdateModalVisible(false);
            setStepFormValues({});
          }}
          modalVisible={updateModalVisible}
          values={stepFormValues}
          isEdit={true}
        />
      ) : null}
      <Drawer
        width={600}
        open={!!row}
        onClose={() => {
          setRow(undefined);
        }}
        closable={false}
      >
        {row?.name && (
          <ProDescriptions<DataSourceDO>
            column={2}
            title={row?.name}
            request={async () => ({
              data: row || {},
            })}
            params={{
              id: row?.id,
            }}
            columns={
              columns.filter(
                (item) => item.dataIndex !== 'option',
              ) as ProDescriptionsItemProps<DataSourceDO>[]
            }
          />
        )}
      </Drawer>
    </PageContainer>
  );
};

export default DataSourceList;
