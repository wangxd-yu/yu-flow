/**
 * 角色管理 —— 角色 CRUD + 权限勾选（右侧 Drawer + 紧凑权限网格）
 */
import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  PageContainer,
  ProTable,
  DrawerForm,
  ProFormText,
  ProFormSelect,
  ProForm,
} from '@ant-design/pro-components';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { Button, Checkbox, Popconfirm, Space, Tag, Tooltip, message } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { Access, useAccess, request } from '@umijs/max';
import '@/styles/fullHeightTable.css';

type SysRole = {
  id: string;
  roleCode: string;
  roleName: string;
  status?: number;
  isBuiltin?: number;
  remark?: string;
  permCodes?: string[];
  permCount?: number;
  createTime?: string;
};

type SysPerm = {
  id: string;
  permCode: string;
  permName: string;
  groupCode?: string;
  remark?: string;
};

const GROUP_LABEL: Record<string, string> = {
  home: '首页',
  flow: '流程资产',
  ops: '运行观测',
  infra: '基础设施',
  sys: '平台设置',
};

const GROUP_ORDER = ['home', 'flow', 'ops', 'infra', 'sys'];

async function listRoles(): Promise<SysRole[]> {
  const res: any = await request('/flow-api/sys-roles', { method: 'GET' });
  const data = res?.data !== undefined ? res.data : res;
  return Array.isArray(data) ? data : [];
}

async function listPermissions(): Promise<SysPerm[]> {
  const res: any = await request('/flow-api/sys-roles/permissions', { method: 'GET' });
  const data = res?.data !== undefined ? res.data : res;
  return Array.isArray(data) ? data : [];
}

const SysRolePage: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const access = useAccess();
  const [perms, setPerms] = useState<SysPerm[]>([]);
  const [edit, setEdit] = useState<SysRole | null>(null);
  const [open, setOpen] = useState(false);
  const [checked, setChecked] = useState<string[]>([]);

  useEffect(() => {
    listPermissions().then(setPerms).catch(() => setPerms([]));
  }, []);

  const grouped = useMemo(() => {
    const map = new Map<string, SysPerm[]>();
    for (const p of perms) {
      const g = p.groupCode || 'other';
      if (!map.has(g)) map.set(g, []);
      map.get(g)!.push(p);
    }
    return Array.from(map.entries()).sort(([a], [b]) => {
      const ia = GROUP_ORDER.indexOf(a);
      const ib = GROUP_ORDER.indexOf(b);
      return (ia < 0 ? 99 : ia) - (ib < 0 ? 99 : ib);
    });
  }, [perms]);

  const toggleGroup = (list: SysPerm[], all: boolean) => {
    const codes = list.map((p) => p.permCode);
    if (all) {
      setChecked((prev) => Array.from(new Set([...prev, ...codes])));
    } else {
      const drop = new Set(codes);
      setChecked((prev) => prev.filter((c) => !drop.has(c)));
    }
  };

  const columns: ProColumns<SysRole>[] = [
    { title: '编码', dataIndex: 'roleCode', width: 140 },
    { title: '名称', dataIndex: 'roleName', width: 140 },
    {
      title: '权限数',
      dataIndex: 'permCount',
      width: 90,
      search: false,
      render: (_, r) => {
        const codes = r.permCodes || [];
        if (codes.includes('*')) return <Tag color="gold">全部 (*)</Tag>;
        return r.permCount ?? codes.length;
      },
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
    { title: '备注', dataIndex: 'remark', ellipsis: true, search: false },
    { title: '更新时间', dataIndex: 'updateTime', width: 170, search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 160,
      render: (_, record) => (
        <Space>
          <Access accessible={!!access.canRoleWrite}>
            <a
              onClick={() => {
                setEdit(record);
                setChecked(record.permCodes || []);
                setOpen(true);
              }}
            >
              配置
            </a>
          </Access>
          <Access accessible={!!access.canRoleWrite && record.isBuiltin !== 1}>
            <Popconfirm
              title="确认删除该角色？"
              onConfirm={async () => {
                await request(`/flow-api/sys-roles/${record.id}`, { method: 'DELETE' });
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
        title: '角色管理',
        subTitle: '配置角色权限点；用户管理中绑定角色后生效',
      }}
    >
      <ProTable<SysRole>
        className="fh-table"
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        tableLayout="fixed"
        scroll={{ y: 100000 }}
        search={false}
        pagination={false}
        request={async () => {
          const data = await listRoles();
          return { data, total: data.length, success: true };
        }}
        toolBarRender={() => [
          <Access key="add" accessible={!!access.canRoleWrite}>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                setEdit(null);
                setChecked([]);
                setOpen(true);
              }}
            >
              新建角色
            </Button>
          </Access>,
        ]}
      />

      <DrawerForm
        title={edit ? `配置角色 — ${edit.roleName}` : '新建角色'}
        open={open}
        onOpenChange={(v) => {
          if (!v) setOpen(false);
        }}
        drawerProps={{
          destroyOnClose: true,
          width: 680,
          styles: { body: { paddingTop: 12, paddingBottom: 8 } },
        }}
        grid
        rowProps={{ gutter: [12, 0] }}
        initialValues={
          edit
            ? {
                roleCode: edit.roleCode,
                roleName: edit.roleName,
                status: edit.status ?? 1,
                remark: edit.remark,
              }
            : { status: 1 }
        }
        onFinish={async (values) => {
          const body = {
            roleCode: values.roleCode,
            roleName: values.roleName,
            status: values.status,
            remark: values.remark,
            permCodes: checked,
          };
          if (edit) {
            await request(`/flow-api/sys-roles/${edit.id}`, { method: 'PUT', data: body });
            message.success('已更新');
          } else {
            await request('/flow-api/sys-roles', { method: 'POST', data: body });
            message.success('已创建');
          }
          setOpen(false);
          actionRef.current?.reload();
          return true;
        }}
      >
        <ProFormText
          name="roleCode"
          label="角色编码"
          colProps={{ span: 12 }}
          disabled={!!edit}
          placeholder="如 AUDITOR"
          rules={
            edit
              ? []
              : [
                  { required: true, message: '必填' },
                  { pattern: /^[A-Za-z][A-Za-z0-9_]{1,62}$/, message: '字母开头' },
                ]
          }
        />
        <ProFormText
          name="roleName"
          label="角色名称"
          colProps={{ span: 12 }}
          rules={[{ required: true, message: '必填' }]}
        />
        <ProFormSelect
          name="status"
          label="状态"
          colProps={{ span: 12 }}
          options={[
            { label: '启用', value: 1 },
            { label: '停用', value: 0 },
          ]}
          rules={[{ required: true }]}
        />
        <ProFormText name="remark" label="备注" colProps={{ span: 12 }} />

        <ProForm.Item style={{ marginBottom: 0 }} colProps={{ span: 24 }}>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              marginBottom: 8,
            }}
          >
            <span style={{ fontWeight: 500 }}>权限点</span>
            <Space size={4} wrap>
              <Button
                size="small"
                type="link"
                style={{ paddingInline: 4 }}
                onClick={() => setChecked(perms.map((p) => p.permCode))}
                disabled={!access.canRoleWrite}
              >
                全选
              </Button>
              <Button
                size="small"
                type="link"
                style={{ paddingInline: 4 }}
                onClick={() => setChecked([])}
                disabled={!access.canRoleWrite}
              >
                清空
              </Button>
              <Button
                size="small"
                type="link"
                style={{ paddingInline: 4 }}
                onClick={() => setChecked(['*'])}
                disabled={!access.canRoleWrite}
              >
                仅 *
              </Button>
              <Tag style={{ margin: 0 }}>已选 {checked.length}</Tag>
            </Space>
          </div>

          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
              gap: 8,
            }}
          >
            {grouped.map(([group, list]) => {
              const selectedInGroup = list.filter((p) => checked.includes(p.permCode)).length;
              const allChecked = selectedInGroup === list.length && list.length > 0;
              const indeterminate = selectedInGroup > 0 && !allChecked;
              const wide = list.length > 4;
              return (
                <div
                  key={group}
                  style={{
                    gridColumn: wide ? '1 / -1' : undefined,
                    border: '1px solid #f0f0f0',
                    borderRadius: 6,
                    padding: '6px 8px',
                    background: '#fafafa',
                  }}
                >
                  <Checkbox
                    checked={allChecked}
                    indeterminate={indeterminate}
                    disabled={!access.canRoleWrite}
                    onChange={(e) => toggleGroup(list, e.target.checked)}
                    style={{ marginBottom: 4 }}
                  >
                    <span style={{ fontWeight: 500 }}>{GROUP_LABEL[group] || group}</span>
                    <span style={{ color: '#999', marginLeft: 6, fontSize: 12 }}>
                      {selectedInGroup}/{list.length}
                    </span>
                  </Checkbox>
                  <Checkbox.Group
                    style={{ width: '100%', display: 'block' }}
                    value={checked.filter((c) => list.some((p) => p.permCode === c))}
                    onChange={(vals) => {
                      const groupCodes = new Set(list.map((p) => p.permCode));
                      const others = checked.filter((c) => !groupCodes.has(c));
                      setChecked([...others, ...(vals as string[])]);
                    }}
                    disabled={!access.canRoleWrite}
                  >
                    <div
                      style={{
                        display: 'grid',
                        gridTemplateColumns: wide
                          ? 'repeat(4, minmax(0, 1fr))'
                          : 'repeat(2, minmax(0, 1fr))',
                        gap: '0 4px',
                        width: '100%',
                      }}
                    >
                      {list.map((p) => (
                        <Checkbox
                          key={p.permCode}
                          value={p.permCode}
                          style={{
                            width: '100%',
                            marginInlineStart: 0,
                            marginInlineEnd: 0,
                            fontSize: 13,
                            lineHeight: '26px',
                          }}
                        >
                          <Tooltip title={p.permCode} placement="topLeft">
                            <span
                              style={{
                                display: 'inline-block',
                                maxWidth: '100%',
                                overflow: 'hidden',
                                textOverflow: 'ellipsis',
                                whiteSpace: 'nowrap',
                                verticalAlign: 'bottom',
                              }}
                            >
                              {p.permName}
                            </span>
                          </Tooltip>
                        </Checkbox>
                      ))}
                    </div>
                  </Checkbox.Group>
                </div>
              );
            })}
          </div>
        </ProForm.Item>
      </DrawerForm>
    </PageContainer>
  );
};

export default SysRolePage;
