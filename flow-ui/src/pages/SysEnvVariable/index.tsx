import React, { useRef, useState } from 'react';
import {
  ActionType,
  ModalForm,
  PageContainer,
  ProColumns,
  ProFormDependency,
  ProFormSwitch,
  ProFormText,
  ProFormTextArea,
  ProTable,
} from '@ant-design/pro-components';
import { useAccess } from '@umijs/max';
import { Alert, Button, message, Popconfirm, Tag, Typography } from 'antd';
import { DeleteOutlined, EditOutlined, LockOutlined, PlusOutlined } from '@ant-design/icons';
import {
  createEnvVariable,
  deleteEnvVariable,
  listEnvVariables,
  updateEnvVariable,
  type EnvVariable,
} from '@/services/flow/envVariable';
import ExtractModal from './components/ExtractModal';
import '@/styles/fullHeightTable.css';

const CODE_PATTERN = /^[A-Z][A-Z0-9_]{0,63}$/;

/**
 * 环境变量：每个环境各自维护值，发布包只带变量名。
 */
const SysEnvVariable: React.FC = () => {
  const access = useAccess() as Record<string, boolean>;
  const canWrite = !!access.canEnvWrite;
  const actionRef = useRef<ActionType>();
  const [modalOpen, setModalOpen] = useState(false);
  const [current, setCurrent] = useState<EnvVariable | undefined>();
  const [extractOpen, setExtractOpen] = useState(false);

  const openEditor = (row?: EnvVariable) => {
    setCurrent(row);
    setModalOpen(true);
  };

  const columns: ProColumns<EnvVariable>[] = [
    {
      title: '关键字',
      dataIndex: 'keyword',
      hideInTable: true,
      fieldProps: { placeholder: '变量名或说明' },
    },
    {
      title: '变量名',
      dataIndex: 'code',
      width: 240,
      search: false,
      render: (_, r) => (
        <Typography.Text code copyable={{ text: `\${env.${r.code}}` }}>
          {r.code}
        </Typography.Text>
      ),
    },
    {
      title: '值',
      dataIndex: 'value',
      search: false,
      ellipsis: true,
      render: (_, r) => {
        if (r.secret) {
          return r.valueSet ? (
            <Tag icon={<LockOutlined />}>已设置（敏感）</Tag>
          ) : (
            <Tag color="warning">未设置</Tag>
          );
        }
        return r.valueSet ? r.value : <Tag color="warning">未设置</Tag>;
      },
    },
    {
      title: '敏感',
      dataIndex: 'secret',
      width: 80,
      search: false,
      render: (_, r) => (r.secret ? <Tag color="red">是</Tag> : <Tag>否</Tag>),
    },
    {
      title: '说明',
      dataIndex: 'remark',
      search: false,
      ellipsis: true,
    },
    {
      title: '更新',
      dataIndex: 'updateTime',
      width: 200,
      search: false,
      render: (_, r) => `${r.updateTime || '-'}${r.updateBy ? ` · ${r.updateBy}` : ''}`,
    },
    {
      title: '操作',
      valueType: 'option',
      width: 160,
      hideInTable: !canWrite,
      render: (_, r) => [
        <Button key="edit" type="link" icon={<EditOutlined />} onClick={() => openEditor(r)}>
          编辑
        </Button>,
        <Popconfirm
          key="delete"
          title="确认删除该变量？"
          description="引用它的接口 / 任务执行时会报「环境变量未配置」"
          onConfirm={async () => {
            await deleteEnvVariable(r.id);
            message.success('已删除');
            actionRef.current?.reload();
          }}
        >
          <Button type="link" danger icon={<DeleteOutlined />}>
            删除
          </Button>
        </Popconfirm>,
      ],
    },
  ];

  return (
    <PageContainer className="fh-container" style={{ height: 'calc(100vh - 26px)', overflow: 'hidden' }}>
      <Alert
        type="info"
        showIcon
        closable
        style={{ marginBottom: 16 }}
        message="每个环境各自维护变量值，发布包只携带变量名；导入时缺少的变量会在预检中列出。"
        description={
          <span>
            节点输入参数里写 <Typography.Text code>$.env.变量名</Typography.Text>；httpRequest 的 URL、请求头、Query、Body、认证字段里可直接写{' '}
            <Typography.Text code>{'${env.变量名}'}</Typography.Text>。敏感变量在执行日志与三方调用日志中显示为 ******。
          </span>
        }
      />

      <ProTable<EnvVariable>
        className="fh-table"
        headerTitle="环境变量"
        rowKey="id"
        actionRef={actionRef}
        tableLayout="fixed"
        scroll={{ x: 1000, y: 100000 }}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 20 }}
        request={async (params) => {
          const data = await listEnvVariables(params.keyword);
          return { data, success: true, total: data.length };
        }}
        toolBarRender={() =>
          canWrite
            ? [
                <Button key="extract" onClick={() => setExtractOpen(true)}>
                  从编排提取
                </Button>,
                <Button key="add" type="primary" icon={<PlusOutlined />} onClick={() => openEditor()}>
                  新建
                </Button>,
              ]
            : []
        }
        columns={columns}
      />

      <ExtractModal
        open={extractOpen}
        onCancel={() => setExtractOpen(false)}
        onDone={() => actionRef.current?.reload()}
      />

      <ModalForm
        title={current ? `编辑变量 ${current.code}` : '新建变量'}
        open={modalOpen}
        onOpenChange={setModalOpen}
        width={560}
        layout="vertical"
        modalProps={{ destroyOnClose: true, maskClosable: false }}
        initialValues={
          current
            ? { code: current.code, secret: current.secret, value: current.secret ? undefined : current.value, remark: current.remark }
            : { secret: false }
        }
        onFinish={async (values) => {
          const payload = {
            code: values.code,
            secret: !!values.secret,
            remark: values.remark ?? '',
            // 敏感变量编辑时留空表示保留原值
            value: current?.secret && values.secret && !values.value ? undefined : values.value ?? '',
          };
          if (current) {
            await updateEnvVariable(current.id, payload);
          } else {
            await createEnvVariable(payload);
          }
          message.success('已保存');
          actionRef.current?.reload();
          return true;
        }}
      >
        <ProFormText
          name="code"
          label="变量名"
          placeholder="如 PAY_BASE_URL"
          disabled={!!current}
          tooltip="被编排引用，创建后不可修改"
          rules={[
            { required: true, message: '请输入变量名' },
            { pattern: CODE_PATTERN, message: '大写字母开头，只能包含大写字母、数字、下划线' },
          ]}
          normalize={(v?: string) => (v || '').toUpperCase()}
        />
        <ProFormSwitch name="secret" label="敏感变量" tooltip="开启后加密存储，页面不回显，执行日志中脱敏" />
        <ProFormDependency name={['secret']}>
          {({ secret }) =>
            secret ? (
              <ProFormText.Password
                name="value"
                label="值"
                placeholder={current?.secret && current.valueSet ? '留空保留原值' : '请输入'}
                fieldProps={{ autoComplete: 'new-password' }}
              />
            ) : (
              <ProFormTextArea name="value" label="值" fieldProps={{ rows: 3 }} />
            )
          }
        </ProFormDependency>
        <ProFormTextArea
          name="remark"
          label="说明"
          placeholder="如：支付网关地址，生产为 https://pay.xxx.com"
          tooltip="随发布包导出，目标环境导入预检时会显示，提示运维该填什么"
          fieldProps={{ rows: 2 }}
        />
      </ModalForm>
    </PageContainer>
  );
};

export default SysEnvVariable;
