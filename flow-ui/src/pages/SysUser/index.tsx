/**
 * 用户管理 —— RBAC 用户 / 角色绑定
 */
import React, { useRef, useState } from 'react';
import { PageContainer, ProTable, ModalForm, ProFormText, ProFormSelect } from '@ant-design/pro-components';
import { passwordComplexityValidator } from '@/utils/passwordPolicy';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { Button, Popconfirm, Space, Tag, message } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { Access, useAccess, request } from '@umijs/max';
import '@/styles/fullHeightTable.css';

type SysUser = {
  id: string;
  username: string;
  displayName?: string;
  status?: number;
  isBuiltin?: number;
  roleCodes?: string[];
  remark?: string;
  createTime?: string;
};

type SysRole = { id: string; roleCode: string; roleName: string };

async function pageUsers(params: any) {
  const res: any = await request('/flow-api/sys-users/page', { method: 'GET', params });
  const data = res?.data !== undefined ? res.data : res;
  return {
    data: data?.items || data?.content || [],
    total: data?.total || 0,
    success: true,
  };
}

async function listRoles(): Promise<SysRole[]> {
  const res: any = await request('/flow-api/sys-users/roles', { method: 'GET' });
  const data = res?.data !== undefined ? res.data : res;
  return Array.isArray(data) ? data : [];
}

const SysUserPage: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const access = useAccess();
  const [roles, setRoles] = useState<SysRole[]>([]);
  const [edit, setEdit] = useState<SysUser | null>(null);
  const [open, setOpen] = useState(false);

  React.useEffect(() => {
    listRoles().then(setRoles).catch(() => setRoles([]));
  }, []);

  const columns: ProColumns<SysUser>[] = [
    { title: '用户名', dataIndex: 'username', width: 140 },
    { title: '显示名', dataIndex: 'displayName', width: 140 },
    {
      title: '角色',
      dataIndex: 'roleCodes',
      search: false,
      render: (_, r) => (r.roleCodes || []).map((c) => <Tag key={c}>{c}</Tag>),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      valueEnum: { 1: { text: '启用', status: 'Success' }, 0: { text: '停用', status: 'Default' } },
    },
    {
      title: '内置',
      dataIndex: 'isBuiltin',
      width: 70,
      search: false,
      render: (_, r) => (r.isBuiltin === 1 ? <Tag>是</Tag> : '—'),
    },
    { title: '创建时间', dataIndex: 'createTime', width: 170, search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 160,
      render: (_, record) => (
        <Space>
          <Access accessible={!!access.canUserWrite}>
            <a
              onClick={() => {
                setEdit(record);
                setOpen(true);
              }}
            >
              编辑
            </a>
          </Access>
          <Access accessible={!!access.canUserWrite && record.isBuiltin !== 1}>
            <Popconfirm
              title="确认删除该用户？"
              onConfirm={async () => {
                await request(`/flow-api/sys-users/${record.id}`, { method: 'DELETE' });
                message.success('已删除');
                actionRef.current?.reload();
              }}
            >
              <a style={{ color: '#cf1322' }}>删除</a>
            </Popconfirm>
          </Access>
        </Space>
      ),
    },
  ];

  return (
    <PageContainer
      className="fh-container"
      style={{ height: 'calc(100vh - 26px)', overflow: 'hidden' }}
      header={{
        title: '用户管理',
        subTitle: 'RBAC 用户与角色；权限由角色绑定，菜单按 permissions 过滤',
      }}
    >
      <ProTable<SysUser>
        className="fh-table"
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        tableLayout="fixed"
        scroll={{ y: 100000 }}
        search={{ labelWidth: 'auto' }}
        request={async (params) =>
          pageUsers({
            username: params.username,
            status: params.status,
            page: params.current || 1,
            size: params.pageSize || 20,
          })
        }
        toolBarRender={() => [
          <Access key="add" accessible={!!access.canUserWrite}>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                setEdit(null);
                setOpen(true);
              }}
            >
              新建用户
            </Button>
          </Access>,
        ]}
      />

      <ModalForm
        title={edit ? '编辑用户' : '新建用户'}
        open={open}
        modalProps={{ destroyOnClose: true, onCancel: () => setOpen(false) }}
        initialValues={
          edit
            ? {
                username: edit.username,
                displayName: edit.displayName,
                status: edit.status ?? 1,
                roleCodes: edit.roleCodes || [],
                remark: edit.remark,
              }
            : { status: 1, roleCodes: ['VIEWER'] }
        }
        onFinish={async (values) => {
          const body = {
            username: values.username,
            password: values.password,
            displayName: values.displayName,
            status: values.status,
            roleCodes: values.roleCodes,
            remark: values.remark,
          };
          if (edit) {
            await request(`/flow-api/sys-users/${edit.id}`, { method: 'PUT', data: body });
            message.success('已更新');
          } else {
            if (!body.password) {
              message.error('请填写初始密码');
              return false;
            }
            await request('/flow-api/sys-users', { method: 'POST', data: body });
            message.success('已创建');
          }
          setOpen(false);
          actionRef.current?.reload();
          return true;
        }}
      >
        <ProFormText
          name="username"
          label="用户名"
          disabled={!!edit}
          rules={[{ required: true, message: '必填' }]}
        />
        <ProFormText.Password
          name="password"
          label={edit ? '新密码（留空不改）' : '初始密码'}
          tooltip="至少 8 位，需含大小写字母、数字与特殊字符"
          rules={
            edit
              ? [
                  {
                    validator: async (_, value) => {
                      if (!value) return;
                      return passwordComplexityValidator(_, value);
                    },
                  },
                ]
              : [
                  { required: true, message: '必填' },
                  { validator: passwordComplexityValidator },
                ]
          }
        />
        <ProFormText name="displayName" label="显示名" />
        <ProFormSelect
          name="roleCodes"
          label="角色"
          mode="multiple"
          options={roles.map((r) => ({ label: `${r.roleName} (${r.roleCode})`, value: r.roleCode }))}
          rules={[{ required: true, message: '至少选一个角色' }]}
        />
        <ProFormSelect
          name="status"
          label="状态"
          options={[
            { label: '启用', value: 1 },
            { label: '停用', value: 0 },
          ]}
          rules={[{ required: true }]}
        />
        <ProFormText name="remark" label="备注" />
      </ModalForm>
    </PageContainer>
  );
};

export default SysUserPage;
