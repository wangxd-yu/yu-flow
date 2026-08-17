import {
  ActionType,
  FooterToolbar,
  PageContainer,
  ProColumns,
  ProTable,
} from '@ant-design/pro-components';
import { Button, Divider, message, Popconfirm, Space, Tag, Tooltip } from 'antd';
import { useAccess } from '@umijs/max';
import React, { useMemo, useRef, useState } from 'react';
import OssIntegrationAlert from '@/components/flow/OssIntegrationAlert';
import OssSimulateUploadModal from './components/OssSimulateUploadModal';
import OssUploadProfileForm from './components/OssUploadProfileForm';
import {
  batchDeleteOssUploadProfile,
  deleteOssUploadProfile,
  OssUploadProfile,
  queryOssUploadProfilePage,
} from '@/services/flow/ossUploadProfile';
import { parseOssAccessRules } from '@/utils/ossAccessRules';

import '@/styles/fullHeightTable.css';

const handleRemove = async (selectedRows: OssUploadProfile[]) => {
  if (!selectedRows?.length) return true;
  const hide = message.loading('正在删除');
  try {
    await batchDeleteOssUploadProfile(selectedRows.map((row) => row.id));
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

const OssProfileList: React.FC = () => {
  const access = useAccess();
  const canWrite = !!access.canOssWrite;
  const [createModalVisible, handleModalVisible] = useState<boolean>(false);
  const [updateModalVisible, handleUpdateModalVisible] = useState<boolean>(false);
  const [stepFormValues, setStepFormValues] = useState<Partial<OssUploadProfile>>();
  const [simulateProfile, setSimulateProfile] = useState<Partial<OssUploadProfile> | null>(null);
  const actionRef = useRef<ActionType>();
  const [selectedRowsState, setSelectedRows] = useState<OssUploadProfile[]>([]);

  const columns: ProColumns<OssUploadProfile>[] = useMemo(() => [
    {
      title: '场景名称',
      dataIndex: 'name',
      width: canWrite ? 180 : 80,
      ellipsis: true,
    },
    {
      title: '场景编码',
      dataIndex: 'code',
      width: 140,
      copyable: true,
      ellipsis: true,
    },
    {
      title: 'OSS 连接',
      dataIndex: 'connectionCode',
      width: 140,
      ellipsis: true,
    },
    {
      title: '可见性',
      dataIndex: 'visibility',
      width: 90,
      valueEnum: {
        PUBLIC: { text: '公有' },
        PRIVATE: { text: '私有' },
      },
      render: (_, record) => (
        <Tag color={record.visibility === 'PUBLIC' ? 'blue' : 'purple'} style={{ margin: 0 }}>
          {record.visibility === 'PUBLIC' ? '公有' : '私有'}
        </Tag>
      ),
    },
    {
      title: '要求登录',
      dataIndex: 'requireAuth',
      width: 90,
      search: false,
      render: (_, record) => (record.requireAuth ? '是' : '否'),
    },
    {
      title: '访问规则',
      dataIndex: 'callerPolicy',
      width: 140,
      search: false,
      render: (_, record) => {
        const rules = parseOssAccessRules(record.callerPolicy);
        if (!rules.length) return <span style={{ color: '#bfbfbf' }}>—</span>;
        const upload = rules.filter((rule) => rule.upload).length;
        const download = rules.filter((rule) => rule.downloadScope !== 'OFF').length;
        return (
          <Tooltip
            title={rules
              .map((rule) => {
                const who =
                  rule.principals === 'ANY_AUTHENTICATED'
                    ? '已登录'
                    : rule.principals === 'OPEN_APP'
                      ? '开放应用'
                      : rule.userTypes.join('/') || rule.roles.join('/') || rule.name || '指定身份';
                const down =
                  rule.downloadScope === 'ALL'
                    ? '全部'
                    : rule.downloadScope === 'DEPT'
                      ? '部门'
                      : rule.downloadScope === 'SELF'
                        ? '本人'
                        : '不下';
                return `${who} · ${rule.upload ? '传' : '不传'} · ${down}`;
              })
              .join('；')}
          >
            <Tag color="purple" style={{ margin: 0 }}>
              {rules.length} 条 · 传{upload}/下{download}
            </Tag>
          </Tooltip>
        );
      },
    },
    {
      title: '预签名直传',
      dataIndex: 'presignUploadEnabled',
      width: 100,
      search: false,
      render: (_, record) =>
        record.presignUploadEnabled ? (
          <Tag color="cyan" style={{ margin: 0 }}>
            已开放
          </Tag>
        ) : (
          <span style={{ color: '#bfbfbf' }}>—</span>
        ),
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
      width: 180,
      fixed: 'right',
      render: (_, record) => (
        <span style={{ display: 'inline-flex', alignItems: 'center', whiteSpace: 'nowrap' }}>
          <Button
            key="simulate"
            type="link"
            size="small"
            disabled={!record.enabled}
            style={{ padding: 0, height: 'auto' }}
            onClick={() => setSimulateProfile(record)}
          >
            试上传
          </Button>
          {canWrite ? (
            <>
              <Divider type="vertical" key="d0" />
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
              <Popconfirm
                key="delete"
                title="确定要删除该上传配置吗？"
                onConfirm={async () => {
                  try {
                    await deleteOssUploadProfile(record.id);
                    message.success('删除成功');
                    actionRef.current?.reload();
                  } catch (error: any) {
                    if (!error?.message?.includes('DEMO_RESTRICTED')) {
                      message.error(error?.message || '删除失败');
                    }
                  }
                }}
              >
                <a>删除</a>
              </Popconfirm>
            </>
          ) : null}
        </span>
      ),
    },
  ], [canWrite]);

  return (
    <PageContainer
      className="fh-container"
      style={{ height: 'calc(100vh - 26px)', overflow: 'hidden' }}
      header={{
        title: 'OSS 上传配置',
      }}
    >
      <OssIntegrationAlert />
      <ProTable<OssUploadProfile>
        className="fh-table"
        headerTitle="OSS 上传配置列表"
        actionRef={actionRef}
        rowKey="id"
        tableLayout="fixed"
        scroll={{ x: 1300, y: 100000 }}
        search={{
          labelWidth: 120,
        }}
        toolBarRender={() => canWrite ? [
          <Button
            key="1"
            type="primary"
            onClick={() => {
              handleModalVisible(true);
              setStepFormValues(undefined);
            }}
          >
            新建场景
          </Button>,
        ] : []}
        request={async (params = {}) => {
          const { current, pageSize, ...restParams } = params as any;
          const result = await queryOssUploadProfilePage({
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
        rowSelection={canWrite ? {
          onChange: (_, selectedRows) => setSelectedRows(selectedRows),
        } : undefined}
      />
      {canWrite && selectedRowsState?.length > 0 && (
        <FooterToolbar
          extra={
            <div>
              已选择 <a style={{ fontWeight: 600 }}>{selectedRowsState.length}</a> 项&nbsp;&nbsp;
            </div>
          }
        >
          <Button
            onClick={async () => {
              const removed = await handleRemove(selectedRowsState);
              if (removed) {
                setSelectedRows([]);
                actionRef.current?.reloadAndRest?.();
              }
            }}
          >
            批量删除
          </Button>
        </FooterToolbar>
      )}
      <OssUploadProfileForm
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
        <OssUploadProfileForm
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
      <OssSimulateUploadModal
        open={!!simulateProfile}
        profile={simulateProfile}
        onClose={() => setSimulateProfile(null)}
      />
    </PageContainer>
  );
};

export default OssProfileList;
