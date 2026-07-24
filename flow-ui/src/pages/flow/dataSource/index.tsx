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
  DataSourceDO,
  deleteDataSource,
  disableDataSource,
  enableDataSource,
  queryDataSourcePage,
  testDataSourceConnection,
} from '@/services/flow/dataSource';

import '@/styles/fullHeightTable.css';

/**
 * 删除数据源
 */
const handleRemove = async (selectedRows: DataSourceDO[]) => {
  const hide = message.loading('正在删除');
  if (!selectedRows?.length) return true;
  try {
    // 后端暂无批量删除接口时逐条删除
    await Promise.all(selectedRows.map((row) => deleteDataSource(row.id)));
    hide();
    message.success('删除成功，即将刷新');
    return true;
  } catch (error: any) {
    hide();
    if (!error?.message?.includes('DEMO_RESTRICTED')) {
      message.error('删除失败，请重试');
    }
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

  const isSystemRow = (record: DataSourceDO) =>
    !!record.isSystem || record.isSystem === 1 || record.code === '[DEFAULT]';

  const columns: ProColumns<DataSourceDO>[] = [
    {
      title: '名称',
      dataIndex: 'name',
      tip: '数据源名称',
      width: 180,
      formItemProps: {
        rules: [
          {
            required: true,
            message: '名称为必填项',
          },
        ],
      },
      render: (_, record) => (
        <span>
          {record.name}
          {isSystemRow(record) && (
            <Tag color="gold" style={{ marginLeft: 6 }}>
              系统
            </Tag>
          )}
          {record.wallConfig?.enabled && (
            <Tag color="red" style={{ marginLeft: 6 }}>
              墙已开
            </Tag>
          )}
        </span>
      ),
    },
    {
      title: '数据源编码',
      dataIndex: 'code',
      tip: '全局唯一编码，用于跨环境关联',
      width: 150,
      copyable: true,
      ellipsis: true,
      search: false,
      hideInForm: true,
    },
    {
      title: '数据库类型',
      dataIndex: 'dbType',
      valueType: 'text',
      width: 120,
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
      width: 200,
      ellipsis: true,
    },
    {
      title: '用户名',
      dataIndex: 'username',
      valueType: 'text',
      width: 120,
      search: false,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
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
      width: 180,
      search: false,
    },
    {
      title: '操作',
      dataIndex: 'option',
      valueType: 'option',
      width: 300,
      render: (_, record) => {
        const system = isSystemRow(record);
        const ops: React.ReactNode[] = [
          <a
            key="edit"
            onClick={() => {
              handleUpdateModalVisible(true);
              setStepFormValues(record);
            }}
          >
            {system ? '安全配置' : '编辑'}
          </a>,
          <Divider type="vertical" key="d1" />,
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
                console.log('测试连接异常：', error);
              } finally {
                hide();
                actionRef.current?.reload();
              }
            }}
          >
            测试连接
          </a>,
        ];
        if (!system) {
          ops.push(
            <Divider type="vertical" key="d2" />,
            <Popconfirm
              key="delete"
              title="确定要删除吗？"
              onConfirm={async () => {
                try {
                  await deleteDataSource(record.id);
                  actionRef.current?.reload();
                } catch (error) {
                  // 错误已通过全局拦截器展示
                }
              }}
            >
              <a>删除</a>
            </Popconfirm>,
            <Divider type="vertical" key="d3" />,
            record.status == 1 ? (
              <a
                key="disable"
                onClick={async () => {
                  try {
                    await disableDataSource(record.id);
                    actionRef.current?.reload();
                    message.success('已禁用数据源');
                  } catch (error) {
                    // 错误已通过全局拦截器展示
                  }
                }}
              >
                禁用
              </a>
            ) : (
              <a
                key="enable"
                onClick={async () => {
                  try {
                    await enableDataSource(record.id);
                    actionRef.current?.reload();
                    message.success('已启用数据源');
                  } catch (error) {
                    // 错误已通过全局拦截器展示
                  }
                }}
              >
                启用
              </a>
            ),
          );
        }
        return ops;
      },
    },
  ];

  return (
    <PageContainer
      className="fh-container"
      style={{ height: 'calc(100vh - 26px)', overflow: 'hidden' }}
      header={{
        title: '动态数据源管理',
      }}
    >

      <ProTable<DataSourceDO>
        className="fh-table"
        headerTitle="数据源列表"
        actionRef={actionRef}
        rowKey="id"
        tableLayout="fixed"
        scroll={{ x: 1420, y: 100000 }}
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
          getCheckboxProps: (record) => ({
            disabled: isSystemRow(record),
          }),
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
              try {
                const removable = selectedRowsState.filter((r) => !isSystemRow(r));
                if (!removable.length) {
                  message.warning('系统默认数据源不可删除');
                  return;
                }
                await handleRemove(removable);
                setSelectedRows([]);
                actionRef.current?.reloadAndRest?.();
              } catch (error) {
                // 错误已通过 handleRemove 捕获并提示
              }
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
