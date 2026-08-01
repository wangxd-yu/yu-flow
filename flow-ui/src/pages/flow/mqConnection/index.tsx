import {
  ActionType,
  FooterToolbar,
  PageContainer,
  ProColumns,
  ProTable,
} from '@ant-design/pro-components';
import { Badge, Button, Divider, message, Popconfirm, Tag, Tooltip } from 'antd';
import React, { useRef, useState } from 'react';
import MqConnectionForm from './components/MqConnectionForm';
import {
  batchDeleteMqConnection,
  deleteMqConnection,
  disableMqConnection,
  enableMqConnection,
  MqConnection,
  queryMqConnectionPage,
  testMqConnectionById,
} from '@/services/flow/mqConnection';

import '@/styles/fullHeightTable.css';

/**
 * 批量删除 MQ 连接
 */
const handleRemove = async (selectedRows: MqConnection[]) => {
  const hide = message.loading('正在删除');
  if (!selectedRows?.length) return true;
  try {
    await batchDeleteMqConnection(selectedRows.map((row) => row.id));
    hide();
    message.success('删除成功，即将刷新');
    return true;
  } catch (error: any) {
    hide();
    if (!error?.message?.includes('DEMO_RESTRICTED')) {
      message.error(error?.message || '删除失败，请重试');
    }
    return false;
  }
};

const MqConnectionList: React.FC = () => {
  const [createModalVisible, handleModalVisible] = useState<boolean>(false);
  const [updateModalVisible, handleUpdateModalVisible] =
    useState<boolean>(false);
  const [stepFormValues, setStepFormValues] = useState<Partial<MqConnection>>();
  const actionRef = useRef<ActionType>();
  const [selectedRowsState, setSelectedRows] = useState<MqConnection[]>([]);

  const columns: ProColumns<MqConnection>[] = [
    {
      title: '名称',
      dataIndex: 'name',
      tip: 'MQ 连接名称',
      width: 200,
      ellipsis: true,
    },
    {
      title: '连接编码',
      dataIndex: 'code',
      tip: '全局唯一编码，流程节点与 MQ 任务通过 code 引用',
      width: 140,
      copyable: true,
      ellipsis: true,
      search: false,
    },
    {
      title: 'MQ 类型',
      dataIndex: 'mqType',
      width: 110,
      valueEnum: {
        RABBITMQ: { text: 'RabbitMQ' },
        KAFKA: { text: 'Kafka' },
      },
      render: (_, record) => (
        <Tag
          color={record.mqType === 'KAFKA' ? 'geekblue' : 'orange'}
          style={{ margin: 0 }}
        >
          {record.mqType === 'KAFKA' ? 'Kafka' : 'RabbitMQ'}
        </Tag>
      ),
    },
    {
      title: '服务器地址',
      dataIndex: 'servers',
      valueType: 'text',
      width: 220,
      ellipsis: true,
      search: false,
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 80,
      ellipsis: true,
      // 后端 enabled 为 Boolean，用 Map 保留布尔 key（对象字面量会退化为字符串 key 而匹配不上）
      valueEnum: new Map([
        [false, { text: '停用', status: 'Error' }],
        [true, { text: '启用', status: 'Success' }],
      ]),
    },
    {
      title: '健康度',
      dataIndex: 'healthStatus',
      search: false,
      width: 90,
      ellipsis: true,
      render: (_, record) => {
        const { healthStatus, lastErrorMsg } = record;
        if (healthStatus === 'HEALTHY') {
          return <Badge status="success" text="正常" />;
        }
        if (healthStatus === 'UNHEALTHY') {
          return (
            <Tooltip
              title={lastErrorMsg ? `最近报错：${lastErrorMsg}` : '连接异常'}
              color="red"
            >
              <Badge status="error" text="异常" style={{ cursor: 'help' }} />
            </Tooltip>
          );
        }
        return <Badge status="default" text="未知" />;
      },
    },
    {
      title: '最近测试时间',
      dataIndex: 'lastTestTime',
      valueType: 'dateTime',
      width: 160,
      ellipsis: true,
      search: false,
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      valueType: 'dateTime',
      width: 160,
      ellipsis: true,
      search: false,
    },
    {
      title: '操作',
      dataIndex: 'option',
      valueType: 'option',
      width: 240,
      fixed: 'right',
      render: (_, record) => (
        <span style={{ display: 'inline-flex', alignItems: 'center', whiteSpace: 'nowrap' }}>
          <a
            key="edit"
            onClick={() => {
              handleUpdateModalVisible(true);
              setStepFormValues(record);
            }}
          >
            编辑
          </a>
          <Divider type="vertical" key="d1" />
          <a
            key="test"
            onClick={async () => {
              const hide = message.loading('正在测试连接...', 0);
              try {
                const result = await testMqConnectionById(record.id);
                const ok = (result as any)?.data ?? result;
                if (ok) {
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
          </a>
          <Divider type="vertical" key="d2" />
          <Popconfirm
            key="delete"
            title="确定要删除吗？删除前请确认没有 MQ 任务或流程节点引用该连接。"
            onConfirm={async () => {
              try {
                await deleteMqConnection(record.id);
                actionRef.current?.reload();
              } catch (error) {
                // 错误已通过全局拦截器展示
              }
            }}
          >
            <a>删除</a>
          </Popconfirm>
          <Divider type="vertical" key="d3" />
          {Number(record.enabled) === 1 ? (
            <a
              key="disable"
              onClick={async () => {
                try {
                  await disableMqConnection(record.id);
                  actionRef.current?.reload();
                  message.success('已停用连接，相关订阅将停止');
                } catch (error) {
                  // 错误已通过全局拦截器展示
                }
              }}
            >
              停用
            </a>
          ) : (
            <a
              key="enable"
              onClick={async () => {
                try {
                  await enableMqConnection(record.id);
                  actionRef.current?.reload();
                  message.success('已启用连接');
                } catch (error) {
                  // 错误已通过全局拦截器展示
                }
              }}
            >
              启用
            </a>
          )}
        </span>
      ),
    },
  ];

  return (
    <PageContainer
      className="fh-container"
      style={{ height: 'calc(100vh - 26px)', overflow: 'hidden' }}
      header={{
        title: 'MQ 连接管理',
      }}
    >
      <ProTable<MqConnection>
        className="fh-table"
        headerTitle="MQ 连接列表"
        actionRef={actionRef}
        rowKey="id"
        tableLayout="fixed"
        scroll={{ x: 1400, y: 100000 }}
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
            新建连接
          </Button>,
        ]}
        request={async (params = {}) => {
          const { current, pageSize, ...restParams } = params as any;
          const result = await queryMqConnectionPage({
            ...restParams,
            page: (current || 1) - 1,
            size: pageSize || 20,
          });
          const data = (result as any)?.data || result;
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
      <MqConnectionForm
        onSubmit={async (success) => {
          if (success) {
            handleModalVisible(false);
            setStepFormValues(undefined);
            actionRef.current?.reload();
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
        <MqConnectionForm
          onSubmit={async (success) => {
            if (success) {
              handleUpdateModalVisible(false);
              setStepFormValues({});
              actionRef.current?.reload();
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
    </PageContainer>
  );
};

export default MqConnectionList;
