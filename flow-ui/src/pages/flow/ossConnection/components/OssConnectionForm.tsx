import {
  DrawerForm,
  ProFormSelect,
  ProFormSwitch,
  ProFormText,
  ProFormTextArea,
} from '@ant-design/pro-components';
import { Button, message } from 'antd';
import { useForm } from 'antd/es/form/Form';
import React, { useMemo, useState } from 'react';

import {
  createOssConnection,
  OssConnection,
  testOssConnection,
  updateOssConnection,
} from '@/services/flow/ossConnection';

export type OssConnectionFormProps = {
  onCancel: () => void;
  onSubmit: (success: boolean) => Promise<boolean>;
  modalVisible: boolean;
  values?: Partial<OssConnection>;
  isEdit?: boolean;
};

const OssConnectionForm: React.FC<OssConnectionFormProps> = (props) => {
  const { modalVisible, onCancel, onSubmit, values = {}, isEdit } = props;
  const [form] = useForm();
  const [testLoading, setTestLoading] = useState(false);

  const initialValues = useMemo(
    () => ({
      pathStyle: true,
      publicAccessMode: 'ANON',
      privateDownloadMode: 'STREAM',
      presignExpireSeconds: 300,
      ...values,
      enabled: values.enabled === undefined ? true : Boolean(Number(values.enabled)),
      secretKey: undefined,
    }),
    [values],
  );

  const handleSubmit = async (formValues: any) => {
    const hide = message.loading(isEdit ? '正在更新...' : '正在添加...');
    try {
      if (isEdit) {
        await updateOssConnection(values.id!, formValues);
      } else {
        await createOssConnection(formValues);
      }
      hide();
      message.success(isEdit ? '更新成功' : '添加成功');
      onSubmit(true);
      return true;
    } catch (e: any) {
      hide();
      if (!e?.message?.includes('DEMO_RESTRICTED')) {
        message.error(e?.message || (isEdit ? '更新失败' : '添加失败'));
      }
      return false;
    }
  };

  const handleTestConnection = async () => {
    const formValues = form.getFieldsValue();
    const { endpoint } = formValues;
    if (!endpoint) {
      message.warning('请先填写 Endpoint 后再测试');
      return;
    }
    setTestLoading(true);
    try {
      const res = await testOssConnection({ id: values.id, ...formValues });
      if (res?.success) {
        message.success(res.message || '连接测试成功');
      } else {
        message.error(res?.message || '连接测试失败');
      }
    } catch (e: any) {
      console.error('测试连接异常：', e);
    } finally {
      setTestLoading(false);
    }
  };

  return (
    <DrawerForm
      title={isEdit ? '编辑 OSS 连接' : '新建 OSS 连接'}
      width="60%"
      layout="horizontal"
      open={modalVisible}
      form={form}
      onOpenChange={(visible) => {
        if (!visible) {
          onCancel();
        }
      }}
      initialValues={initialValues}
      onFinish={handleSubmit}
      drawerProps={{
        destroyOnClose: true,
      }}
      labelCol={{ style: { width: '130px' } }}
      grid={true}
      rowProps={{
        gutter: [16, 16],
      }}
      submitter={{
        searchConfig: {
          submitText: '提交',
          resetText: '取消',
        },
        render: (_, dom) => [
          <Button key="test-connection" loading={testLoading} onClick={handleTestConnection}>
            测试连接
          </Button>,
          ...dom,
        ],
      }}
    >
      <ProFormText
        name="name"
        label="连接名称"
        placeholder="请输入连接名称"
        rules={[{ required: true, message: '请输入连接名称!' }]}
        colProps={{ span: 12 }}
      />
      <ProFormText
        name="code"
        label="连接编码"
        placeholder="如 minio_main"
        tooltip="全局唯一编码，上传配置通过 connectionCode 引用，创建后不可修改"
        disabled={isEdit}
        rules={
          isEdit
            ? []
            : [
                { required: true, message: '请输入连接编码!' },
                {
                  pattern: /^[a-z][a-z0-9_]*$/,
                  message: '只能包含小写字母、数字和下划线，且不能以数字开头',
                },
                { max: 50, message: '编码长度不能超过50个字符' },
              ]
        }
        colProps={{ span: 12 }}
      />
      <ProFormText
        name="endpoint"
        label="Endpoint"
        placeholder="如 http://127.0.0.1:9000"
        rules={[{ required: true, message: '请输入 Endpoint!' }]}
        colProps={{ span: 12 }}
      />
      <ProFormText
        name="accessKey"
        label="Access Key"
        placeholder="可选"
        colProps={{ span: 12 }}
      />
      <ProFormText.Password
        name="secretKey"
        label="Secret Key"
        placeholder={
          isEdit
            ? values.hasSecretKey
              ? '已配置，留空表示不修改'
              : '若不修改请留空'
            : '可选'
        }
        colProps={{ span: 12 }}
      />
      <ProFormText name="region" label="Region" placeholder="可选" colProps={{ span: 12 }} />
      <ProFormSwitch
        name="pathStyle"
        label="Path Style"
        tooltip="MinIO 等兼容 S3 通常开启"
        colProps={{ span: 12 }}
      />
      <ProFormText
        name="publicBucket"
        label="公有桶"
        placeholder="PUBLIC 场景默认桶"
        colProps={{ span: 12 }}
      />
      <ProFormText
        name="privateBucket"
        label="私有桶"
        placeholder="PRIVATE 场景默认桶"
        colProps={{ span: 12 }}
      />
      <ProFormText
        name="publicBaseUrl"
        label="公有访问基址"
        placeholder="如 https://cdn.example.com"
        colProps={{ span: 12 }}
      />
      <ProFormText
        name="keyPrefix"
        label="Key 前缀"
        placeholder="可选，如 uploads/"
        colProps={{ span: 12 }}
      />
      <ProFormSelect
        name="publicAccessMode"
        label="公有访问模式"
        valueEnum={{
          ANON: '匿名直链',
          NGINX_PROXY: 'Nginx 代理',
        }}
        colProps={{ span: 12 }}
      />
      <ProFormSelect
        name="privateDownloadMode"
        label="隐私下载模式"
        tooltip="STREAM=服务端流式下载；PRESIGN=302 跳转 MinIO 预签名 URL"
        valueEnum={{
          STREAM: '流式下载',
          PRESIGN: '预签名 URL',
        }}
        colProps={{ span: 12 }}
      />
      <ProFormText
        name="presignExpireSeconds"
        label="预签名有效期(秒)"
        placeholder="默认 300"
        fieldProps={{ type: 'number', min: 1 }}
        colProps={{ span: 12 }}
      />
      <ProFormSelect
        name="enabled"
        label="状态"
        options={[
          { label: '启用', value: true },
          { label: '停用', value: false },
        ]}
        rules={[{ required: true, message: '请选择状态!' }]}
        colProps={{ span: 12 }}
      />
      <ProFormTextArea
        name="info"
        label="备注"
        placeholder="可选：连接用途说明"
        fieldProps={{ rows: 2 }}
        colProps={{ span: 24 }}
      />
    </DrawerForm>
  );
};

export default OssConnectionForm;
