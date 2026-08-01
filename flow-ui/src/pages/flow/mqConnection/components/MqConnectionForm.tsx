import {
  DrawerForm,
  ProFormDependency,
  ProFormDigit,
  ProFormSelect,
  ProFormText,
  ProFormTextArea,
} from '@ant-design/pro-components';
import { Button, message } from 'antd';
import { useForm } from 'antd/es/form/Form';
import React, { useMemo, useState } from 'react';

import {
  createMqConnection,
  MqConnection,
  testMqConnection,
  updateMqConnection,
} from '@/services/flow/mqConnection';

export type MqConnectionFormProps = {
  onCancel: () => void;
  onSubmit: (success: boolean) => Promise<boolean>;
  modalVisible: boolean;
  values?: Partial<MqConnection>;
  isEdit?: boolean;
};

const MqConnectionForm: React.FC<MqConnectionFormProps> = (props) => {
  const { modalVisible, onCancel, onSubmit, values = {}, isEdit } = props;
  const [form] = useForm();
  const [testLoading, setTestLoading] = useState(false);

  const initialValues = useMemo(
    () => ({
      mqType: 'RABBITMQ',
      ...values,
      // 后端 enabled 为 Boolean，兼容历史 0/1 写法统一归为布尔（否则 Select 匹配不上选项）
      enabled: values.enabled === undefined ? true : Boolean(Number(values.enabled)),
      // 编辑态密码不回显，留空表示不修改
      password: undefined,
    }),
    [values],
  );

  const handleSubmit = async (formValues: any) => {
    const hide = message.loading(isEdit ? '正在更新...' : '正在添加...');
    try {
      if (isEdit) {
        await updateMqConnection(values.id!, formValues);
      } else {
        await createMqConnection(formValues);
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
    const { mqType, servers } = formValues;
    if (!mqType || !servers) {
      message.warning('请先填写 MQ 类型与服务器地址后再测试');
      return;
    }
    setTestLoading(true);
    try {
      const res = await testMqConnection({ id: values.id, ...formValues });
      const ok = (res as any)?.data ?? res;
      if (ok) {
        message.success('连接测试成功');
      } else {
        message.error('连接测试失败');
      }
    } catch (e: any) {
      console.error('测试连接异常：', e);
    } finally {
      setTestLoading(false);
    }
  };

  return (
    <DrawerForm
      title={isEdit ? '编辑 MQ 连接' : '新建 MQ 连接'}
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
      labelCol={{ style: { width: '110px' } }}
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
          <Button
            key="test-connection"
            loading={testLoading}
            onClick={handleTestConnection}
          >
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
        placeholder="如 order_rabbit"
        tooltip="全局唯一编码，流程节点与 MQ 任务通过 code 引用，创建后不可修改"
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
      <ProFormSelect
        name="mqType"
        label="MQ 类型"
        valueEnum={{
          RABBITMQ: 'RabbitMQ',
          KAFKA: 'Kafka',
        }}
        rules={[{ required: true, message: '请选择 MQ 类型!' }]}
        colProps={{ span: 12 }}
      />
      <ProFormText
        name="servers"
        label="服务器地址"
        placeholder="host:port，多个用逗号分隔"
        tooltip="RabbitMQ 填 host:5672；Kafka 填 bootstrap.servers（host:9092,host2:9092）"
        rules={[{ required: true, message: '请输入服务器地址!' }]}
        colProps={{ span: 12 }}
      />
      <ProFormDependency name={['mqType']}>
        {({ mqType }) =>
          mqType === 'RABBITMQ' ? (
            <ProFormText
              name="virtualHost"
              label="虚拟主机"
              placeholder="默认 /"
              colProps={{ span: 12 }}
            />
          ) : null
        }
      </ProFormDependency>
      <ProFormText
        name="username"
        label="用户名"
        placeholder="可选"
        colProps={{ span: 12 }}
      />
      <ProFormText.Password
        name="password"
        label="密码"
        placeholder={isEdit ? '若不修改密码请留空' : '可选'}
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

export default MqConnectionForm;
