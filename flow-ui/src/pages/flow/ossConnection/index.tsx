import {
  ActionType,
  FooterToolbar,
  PageContainer,
  ProColumns,
  ProTable,
} from '@ant-design/pro-components';
import { Badge, Button, Divider, message, Popconfirm, Tag, Tooltip } from 'antd';
import React, { useRef, useState } from 'react';
import OssIntegrationAlert from '@/components/flow/OssIntegrationAlert';
import OssConnectionForm from './components/OssConnectionForm';
import {
  batchDeleteOssConnection,
  deleteOssConnection,
  disableOssConnection,
  enableOssConnection,
  OssConnection,
  queryOssConnectionPage,
  testOssConnectionById,
} from '@/services/flow/ossConnection';

import '@/styles/fullHeightTable.css';

const handleRemove = async (selectedRows: OssConnection[]) => {
  const hide = message.loading('正在删除');
  if (!selectedRows?.length) return true;
  try {
    await batchDeleteOssConnection(selectedRows.map((row) => row.id));
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

const OssConnectionList: React.FC = () => {
  const [createModalVisible, handleModalVisible] = useState<boolean>(false);
  const [updateModalVisible, handleUpdateModalVisible] = useState<boolean>(false);
  const [stepFormValues, setStepFormValues] = useState<Partial<OssConnection>>();
  const actionRef = useRef<ActionType>();
  const [selectedRowsState, setSelectedRows] = useState<OssConnection[]>([]);

  const columns: ProColumns<OssConnection>[] = [
    {
      title: '名称',
      dataIndex: 'name',
      tip: 'OSS 连接名称',
      width: 200,
      ellipsis: true,
    },
    {
      title: '连接编码',
      dataIndex: 'code',
      tip: '全局唯一编码，上传场景通过 connectionCode 引用',
      width: 140,
      copyable: true,
      ellipsis: true,
      search: false,
    },
    {
      title: 'Endpoint',
      dataIndex: 'endpoint',
      width: 220,
      ellipsis: true,
      search: false,
    },
    {
      title: '公有桶',
      dataIndex: 'publicBucket',
      width: 120,
      ellipsis: true,
      search: false,
    },
    {
      title: '私有桶',
      dataIndex: 'privateBucket',
      width: 120,
      ellipsis: true,
      search: false,
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 80,
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
      search: false,
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      valueType: 'dateTime',
      width: 160,
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
                const result = await testOssConnectionById(record.id);
                if (result?.success) {
                  message.success(result.message || '连接测试成功');
                } else {
                  message.error(result?.message || '连接测试失败');
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
            title="确定要删除吗？删除前请确认没有上传场景引用该连接。"
            onConfirm={async () => {
              try {
                await deleteOssConnection(record.id);
                actionRef.current?.reload();
              } catch {
                /* 错误已通过全局拦截器展示 */
              }
            }}
          >
            <a>删除</a>
          </Popconfirm>
          <Divider type="vertical" key="d3" />
          {record.enabled ? (
            <a
              key="disable"
              onClick={async () => {
                try {
                  await disableOssConnection(record.id);
                  actionRef.current?.reload();
                  message.success('已停用连接');
                } catch {
                  /* ignore */
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
                  await enableOssConnection(record.id);
                  actionRef.current?.reload();
                  message.success('已启用连接');
                } catch {
                  /* ignore */
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
        title: '对象存储连接',
      }}
    >
      <OssIntegrationAlert />
      <ProTable<OssConnection>
        className="fh-table"
        headerTitle="对象存储连接列表"
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
          const result = await queryOssConnectionPage({
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
              已选择 <a style={{ fontWeight: 600 }}>{selectedRowsState.length}</a> 项&nbsp;&nbsp;
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
      <OssConnectionForm
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
        <OssConnectionForm
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

export default OssConnectionList;
