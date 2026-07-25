import {
  DrawerForm,
  ProFormDigit,
  ProFormGroup,
  ProFormSelect,
  ProFormText,
} from '@ant-design/pro-components';
import { Alert, Button, message } from 'antd';
import { useForm } from 'antd/es/form/Form';
import React, { useMemo, useState } from 'react';

import {
  addDataSource,
  testConnectionByParams,
  testDataSourceConnection,
  updateDataSource,
} from '@/services/flow/dataSource';
import DataSourceWallForm, { defaultWallConfig } from './DataSourceWallForm';

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
  const isSystem = !!values?.isSystem || values?.isSystem === 1;

  const initialValues = useMemo(
    () => ({
      ...defaultWallConfig(),
      ...values,
      wallConfig: {
        ...defaultWallConfig(),
        ...(values?.wallConfig || {}),
      },
      isSystem: values?.isSystem ?? 0,
    }),
    [values],
  );

  const handleSubmit = async (formValues: any) => {
    try {
      const hide = message.loading(isEdit ? '正在更新...' : '正在添加...');
      const payload = {
        ...formValues,
        wallConfig: {
          ...defaultWallConfig(),
          ...(formValues.wallConfig || {}),
        },
      };
      if (isEdit) {
        await updateDataSource(values.id!, payload);
      } else {
        await addDataSource(payload);
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

  const handleTestConnection = async () => {
    if (isSystem) {
      setTestLoading(true);
      try {
        const res = await testDataSourceConnection(values.id!);
        if (res) {
          message.success('连接测试成功');
        } else {
          message.error('连接测试失败');
        }
      } catch (e: any) {
        console.error('测试连接异常：', e);
      } finally {
        setTestLoading(false);
      }
      return;
    }

    const formValues = form.getFieldsValue();
    const { driverClassName, url, username, password } = formValues;

    if (!driverClassName || !url || !username) {
      message.warning('请先填写驱动类名、连接URL 和用户名后再测试');
      return;
    }

    setTestLoading(true);
    try {
      const res = await testConnectionByParams({
        id: values.id,
        driverClassName,
        url,
        username,
        password: password || undefined,
      });

      const testResult = (res as any)?.data || res;
      if (testResult?.success) {
        message.success('连接测试成功');
      } else {
        message.error(`连接测试失败：${testResult?.message || '未知错误'}`);
      }
    } catch (e: any) {
      console.error('测试连接异常：', e);
    } finally {
      setTestLoading(false);
    }
  };

  return (
    <DrawerForm
      title={
        isSystem
          ? '编辑系统默认数据源（仅安全墙）'
          : isEdit
            ? '编辑数据源'
            : '新增数据源'
      }
      width="70%"
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
      {isSystem && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16, width: '100%' }}
          message="系统默认数据源"
          description="连接信息来自 spring.datasource，只读展示；可配置 SQL 安全墙。该数据源不可删除或停用。"
        />
      )}

      <ProFormText
        name="name"
        label="数据源名称"
        placeholder="请输入数据源名称"
        rules={[{ required: true, message: '请输入数据源名称!' }]}
        disabled={isEdit && !isSystem}
        colProps={{ span: 8 }}
      />
      <ProFormText
        name="code"
        label="数据源编码"
        placeholder="请输入数据源编码，如 erp_master_db"
        tooltip="全局唯一编码，创建后不可修改"
        disabled={isEdit || isSystem}
        rules={
          isSystem
            ? []
            : [
                { required: true, message: '请输入数据源编码!' },
                {
                  pattern: /^[a-z][a-z0-9_]*$/,
                  message: '只能包含小写字母、数字和下划线，且不能以数字开头',
                },
                { max: 50, message: '编码长度不能超过50个字符' },
              ]
        }
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
        rules={isSystem ? [] : [{ required: true, message: '请选择数据库类型!' }]}
        disabled={isSystem}
        colProps={{ span: 8 }}
      />
      <ProFormText
        name="driverClassName"
        label="驱动类名"
        placeholder="请输入驱动类名"
        rules={isSystem ? [] : [{ required: true, message: '请输入驱动类名!' }]}
        disabled={isSystem}
        colProps={{ span: 8 }}
      />
      <ProFormText
        name="url"
        label="连接URL"
        placeholder="请输入连接URL"
        rules={isSystem ? [] : [{ required: true, message: '请输入连接URL!' }]}
        disabled={isSystem}
        colProps={{ span: 8 }}
      />
      <ProFormText
        name="username"
        label="用户名"
        placeholder="请输入用户名"
        rules={isSystem ? [] : [{ required: true, message: '请输入用户名!' }]}
        disabled={isSystem}
        colProps={{ span: 8 }}
      />

      {!isSystem && (
        <ProFormText.Password
          name="password"
          label="密码"
          placeholder={isEdit ? '若不修改密码请留空' : '请输入密码'}
          rules={isEdit ? [] : [{ required: true, message: '请输入密码!' }]}
          colProps={{ span: 8 }}
        />
      )}

      {!isSystem && (
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
      )}

      {!isSystem && (
        <ProFormSelect
          name="status"
          label="状态"
          valueEnum={{
            1: '启用',
            0: '禁用',
          }}
          rules={[{ required: true, message: '请选择状态!' }]}
          colProps={{ span: 8 }}
          normalize={(val: any) =>
            val !== undefined && val !== null ? Number(val) : val
          }
        />
      )}

      <DataSourceWallForm />
    </DrawerForm>
  );
};

export default DataSourceForm;
