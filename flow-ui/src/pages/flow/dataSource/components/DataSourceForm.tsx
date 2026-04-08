import {
  DrawerForm,
  ProFormDigit,
  ProFormGroup,
  ProFormSelect,
  ProFormText,
} from '@ant-design/pro-components';
import { Button, message } from 'antd';
import { useForm } from 'antd/es/form/Form';
import React, { useState } from 'react';

import {
  addDataSource,
  testConnectionByParams,
  updateDataSource,
} from '../services/dataSource';

export type FormProps = {
  onCancel: () => void;
  onSubmit: (values: any) => Promise<boolean>;
  modalVisible: boolean;
  values?: Partial<any>;
  isEdit?: boolean;
};

const DataSourceForm: React.FC<FormProps> = (props) => {
  const { modalVisible, onCancel, onSubmit, values = {}, isEdit } = props;
  const [form] = useForm();
  const [testLoading, setTestLoading] = useState(false);

  const handleSubmit = async (formValues: any) => {
    try {
      const hide = message.loading(isEdit ? '正在更新...' : '正在添加...');
      if (isEdit) {
        await updateDataSource(values.id!, formValues);
      } else {
        await addDataSource(formValues);
      }
      hide();
      message.success(isEdit ? '更新成功' : '添加成功');
      onSubmit(true);
      return true;
    } catch (error) {
      message.error(isEdit ? '更新失败' : '添加失败');
      onSubmit(false);
      return false;
    }
  };

  /** 点击"测试连接"按钮：收集当前表单值，调用独立测试接口 */
  const handleTestConnection = async () => {
    const formValues = form.getFieldsValue();
    const { driverClassName, url, username, password } = formValues;

    if (!driverClassName || !url || !username) {
      message.warning('请先填写驱动类名、连接URL 和用户名后再测试');
      return;
    }

    setTestLoading(true);
    try {
      const res = await testConnectionByParams({
        id: values.id, // 编辑模式下传原有id，方便后端查库兜底
        driverClassName,
        url,
        username,
        // 编辑模式下密码可能为空（不修改），此时传 undefined，后端用库中密码
        password: password || undefined,
      });

      if (res?.success) {
        message.success('连接测试成功 ✅');
      } else {
        message.error(`连接测试失败：${res?.message || '未知错误'}`);
      }
    } catch (e: any) {
      message.error(`连接测试异常：${e?.message || '请求失败'}`);
    } finally {
      setTestLoading(false);
    }
  };

  return (
    <DrawerForm
      title={isEdit ? '编辑数据源' : '新增数据源'}
      width="70%"
      layout="horizontal"
      open={modalVisible}
      form={form}
      onOpenChange={(visible) => {
        if (!visible) {
          onCancel();
        }
      }}
      initialValues={values}
      onFinish={handleSubmit}
      drawerProps={{
        destroyOnClose: true,
      }}
      labelCol={{ style: { width: '90px' } }}
      grid={true}
      rowProps={{
        gutter: [16, 16],
      }}
      submitter={{
        searchConfig: {
          submitText: '提交',
          resetText: '取消',
        },
        // 在系统默认「取消 / 提交」按钮前插入「测试连接」按钮
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
        label="数据源名称"
        placeholder="请输入数据源名称"
        rules={[{ required: true, message: '请输入数据源名称!' }]}
        disabled={isEdit}
        colProps={{ span: 8 }}
      />
      <ProFormText
        name="code"
        label="数据源编码"
        placeholder="请输入数据源编码，如 erp_master_db"
        tooltip="全局唯一编码，创建后不可修改"
        disabled={isEdit}
        rules={[
          { required: true, message: '请输入数据源编码!' },
          {
            pattern: /^[a-z][a-z0-9_]*$/,
            message: '只能包含小写字母、数字和下划线，且不能以数字开头',
          },
          { max: 50, message: '编码长度不能超过50个字符' },
        ]}
        colProps={{ span: 8 }}
      />
      <ProFormSelect
        name="dbType"
        label="数据库类型"
        valueEnum={{
          mysql: 'MySQL',
          postgresql: 'PostgreSQL',
          highgo: 'HighGo',
        }}
        rules={[{ required: true, message: '请选择数据库类型!' }]}
        colProps={{ span: 8 }}
      />
      <ProFormText
        name="driverClassName"
        label="驱动类名"
        placeholder="请输入驱动类名"
        rules={[{ required: true, message: '请输入驱动类名!' }]}
        colProps={{ span: 8 }}
      />
      <ProFormText
        name="url"
        label="连接URL"
        placeholder="请输入连接URL"
        rules={[{ required: true, message: '请输入连接URL!' }]}
        colProps={{ span: 8 }}
      />
      <ProFormText
        name="username"
        label="用户名"
        placeholder="请输入用户名"
        rules={[{ required: true, message: '请输入用户名!' }]}
        colProps={{ span: 8 }}
      />

      {/* ===== 密码框：编辑模式下 placeholder 变为提示，且不必填 ===== */}
      <ProFormText.Password
        name="password"
        label="密码"
        placeholder={isEdit ? '若不修改密码请留空' : '请输入密码'}
        rules={isEdit ? [] : [{ required: true, message: '请输入密码!' }]}
        colProps={{ span: 8 }}
      />

      <ProFormGroup title="连接池配置" collapsible>
        <ProFormDigit
          name="initialSize"
          label="初始连接数"
          min={1}
          max={100}
          initialValue={5}
          colProps={{ span: 8 }}
        />
        <ProFormDigit
          name="minIdle"
          label="最小空闲连接"
          min={1}
          max={100}
          initialValue={5}
          colProps={{ span: 8 }}
        />
        <ProFormDigit
          name="maxActive"
          label="最大活动连接"
          min={1}
          max={100}
          initialValue={20}
          colProps={{ span: 8 }}
        />
      </ProFormGroup>

      <ProFormSelect
        name="status"
        label="状态"
        valueEnum={{
          1: '启用',
          0: '禁用',
        }}
        rules={[{ required: true, message: '请选择状态!' }]}
        colProps={{ span: 8 }}
        normalize={(val: any) => (val !== undefined && val !== null ? Number(val) : val)}
      />
    </DrawerForm>
  );
};

export default DataSourceForm;

