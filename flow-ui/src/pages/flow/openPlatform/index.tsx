import React, { useEffect, useRef, useState } from 'react';
import { PageContainer, ProColumns, ProTable, ActionType } from '@ant-design/pro-components';
import { Button, Divider, Form, Input, Modal, Popconfirm, Tag, Tooltip, message } from 'antd';
import { useLocation } from '@umijs/max';
import {
  createOpenPlatform,
  deleteOpenPlatform,
  pageOpenPlatforms,
  updateOpenPlatform,
  type OpenPlatform,
} from '@/services/flow/openPlatformService';
import OpenPlatformDrawer from './components/OpenPlatformDrawer';
import { batchAssetHealth, type AssetHealth } from '@/services/flow/assetMetrics';
import { renderHealthTag } from '@/components/flow/AssetHealthTag';
import { AssetTypeBadge } from '@/components/flow/ops';
import '@/styles/fullHeightTable.css';

/** 列宽合计 → scroll.x（含 fixed/ellipsis 场景） */
const TABLE_SCROLL_X = 180 + 160 + 100 + 100 + 90 + 90 + 140 + 170 + 200;

const OpenPlatformPage: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const location = useLocation();
  const [createOpen, setCreateOpen] = useState(false);
  const [form] = Form.useForm();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [currentId, setCurrentId] = useState<string>();
  const [drawerTab, setDrawerTab] = useState<string>();
  const [healthMap, setHealthMap] = useState<Record<string, AssetHealth>>({});

  useEffect(() => {
    const params = new URLSearchParams(location.search || '');
    const pid = params.get('platformId');
    const tab = params.get('tab') || undefined;
    if (pid) {
      setCurrentId(pid);
      setDrawerTab(tab);
      setDrawerOpen(true);
    }
  }, [location.search]);

  const columns: ProColumns<OpenPlatform>[] = [
    {
      title: '平台名称',
      dataIndex: 'name',
      width: 180,
      render: (_, r) => (
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
          <AssetTypeBadge type="PLATFORM" />
          <a
            onClick={() => {
              setCurrentId(r.id);
              setDrawerOpen(true);
            }}
          >
            {r.name}
          </a>
        </span>
      ),
    },
    { title: '编码', dataIndex: 'code', width: 160 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      valueEnum: {
        0: { text: '停用', status: 'Default' },
        1: { text: '启用', status: 'Success' },
      },
      render: (_, r) =>
        r.status === 1 ? <Tag color="success">启用</Tag> : <Tag>停用</Tag>,
    },
    {
      title: '运行健康',
      dataIndex: 'health',
      width: 100,
      search: false,
      render: (_, r) => renderHealthTag(r.id ? healthMap[r.id] : undefined),
    },
    {
      title: '凭证数',
      dataIndex: 'credentialCount',
      width: 90,
      search: false,
    },
    {
      title: '授权接口',
      dataIndex: 'grantCount',
      width: 90,
      search: false,
    },
    {
      title: '联系人',
      dataIndex: 'contact',
      width: 140,
      search: false,
      render: (text) => {
        if (!text || text === '-') return '-';
        const s = String(text);
        return (
          <Tooltip title={s} placement="topLeft">
            <span
              style={{
                display: 'block',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
                maxWidth: 120,
                cursor: 'pointer',
              }}
            >
              {s}
            </span>
          </Tooltip>
        );
      },
    },
    { title: '创建时间', dataIndex: 'createTime', width: 170, search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 200,
      fixed: 'right',
      render: (_, record) => [
        <a
          key="edit"
          onClick={() => {
            setCurrentId(record.id);
            setDrawerOpen(true);
          }}
        >
          配置
        </a>,
        <Divider key="d1" type="vertical" />,
        <a
          key="toggle"
          onClick={async () => {
            try {
              await updateOpenPlatform(record.id!, {
                ...record,
                status: record.status === 1 ? 0 : 1,
              });
              message.success(record.status === 1 ? '已停用' : '已启用');
              actionRef.current?.reload();
            } catch (e: any) {
              message.error(e?.message || '操作失败');
            }
          }}
        >
          {record.status === 1 ? '停用' : '启用'}
        </a>,
        <Divider key="d2" type="vertical" />,
        <Popconfirm
          key="del"
          title="确认删除该平台？凭证与授权将一并清除"
          onConfirm={async () => {
            await deleteOpenPlatform(record.id!);
            message.success('已删除');
            actionRef.current?.reload();
          }}
        >
          <a style={{ color: '#ff4d4f' }}>删除</a>
        </Popconfirm>,
      ],
    },
  ];

  return (
    <PageContainer
      className="fh-container"
      header={{ title: '开放平台' }}
      style={{ height: 'calc(100vh - 26px)', overflow: 'hidden' }}
    >
      <ProTable<OpenPlatform>
        className="fh-table"
        headerTitle="平台列表"
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        tableLayout="fixed"
        scroll={{ x: TABLE_SCROLL_X, y: 100000 }}
        search={{ labelWidth: 80 }}
        pagination={{
          defaultPageSize: 20,
          showSizeChanger: true,
          showQuickJumper: true,
        }}
        toolBarRender={() => [
          <Button key="add" type="primary" onClick={() => setCreateOpen(true)}>
            新建平台
          </Button>,
        ]}
        request={async (params) => {
          const data = await pageOpenPlatforms({
            name: params.name,
            code: params.code,
            status: params.status,
            page: (params.current || 1) - 1,
            size: params.pageSize || 20,
          });
          const items = data?.items || [];
          try {
            const health = await batchAssetHealth(
              items.filter((i) => i.id).map((i) => ({ assetType: 'PLATFORM' as const, assetId: i.id! })),
            );
            const map: Record<string, AssetHealth> = {};
            (health || []).forEach((h) => {
              map[h.assetId] = h;
            });
            setHealthMap(map);
          } catch {
            setHealthMap({});
          }
          return {
            data: items,
            success: true,
            total: data?.total || 0,
          };
        }}
      />

      <Modal
        title="新建开放平台"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={async () => {
          const values = await form.validateFields();
          try {
            const created = await createOpenPlatform({ ...values, status: 1 });
            message.success('创建成功');
            setCreateOpen(false);
            form.resetFields();
            actionRef.current?.reload();
            if (created?.id) {
              setCurrentId(created.id);
              setDrawerOpen(true);
            }
          } catch (e: any) {
            message.error(e?.message || '创建失败');
          }
        }}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="平台名称" rules={[{ required: true }]}>
            <Input placeholder="如：电商中台" />
          </Form.Item>
          <Form.Item name="code" label="唯一编码" rules={[{ required: true }]}>
            <Input placeholder="如：mall-core" />
          </Form.Item>
          <Form.Item name="contact" label="联系人">
            <Input />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      {drawerOpen && currentId && (
        <OpenPlatformDrawer
          platformId={currentId}
          open={drawerOpen}
          defaultTab={drawerTab}
          onClose={() => {
            setDrawerOpen(false);
            setCurrentId(undefined);
            setDrawerTab(undefined);
            actionRef.current?.reload();
          }}
        />
      )}
    </PageContainer>
  );
};

export default OpenPlatformPage;
