import React, { useRef, useState } from 'react';
import {
  ActionType,
  ModalForm,
  PageContainer,
  ProColumns,
  ProFormText,
  ProFormTextArea,
  ProTable,
} from '@ant-design/pro-components';
import { useAccess, useLocation } from '@umijs/max';
import { Alert, Button, Popconfirm, Tag, message } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import {
  createRelease,
  deleteRelease,
  pageReleases,
  RELEASE_STATUS,
  type Release,
} from '@/services/flow/releasePlan';
import CompareModal from './components/CompareModal';
import ReleaseDetailDrawer from './components/ReleaseDetailDrawer';
import '@/styles/fullHeightTable.css';

/**
 * 版本单列表：开发 / 测试环境维护一轮上线的资产清单，冻结后导出发布包交给运维。
 */
const ReleasePlans: React.FC = () => {
  const access = useAccess() as Record<string, boolean>;
  const canWrite = !!access.canReleasePkgWrite;
  const location = useLocation();
  const actionRef = useRef<ActionType>();
  const [createOpen, setCreateOpen] = useState(false);
  const [detailId, setDetailId] = useState<string | undefined>(
    () => new URLSearchParams(location.search).get('id') || undefined,
  );
  const [compareBase, setCompareBase] = useState<Release>();

  const columns: ProColumns<Release>[] = [
    { title: '关键字', dataIndex: 'keyword', hideInTable: true, fieldProps: { placeholder: '版本号或名称' } },
    {
      title: '版本号',
      dataIndex: 'code',
      search: false,
      width: 180,
      render: (_, r) => <a onClick={() => setDetailId(r.id)}>{r.code}</a>,
    },
    { title: '名称', dataIndex: 'name', search: false, ellipsis: true },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      valueType: 'select',
      valueEnum: Object.fromEntries(Object.entries(RELEASE_STATUS).map(([k, v]) => [k, { text: v.text }])),
      render: (_, r) => <Tag color={RELEASE_STATUS[r.status].color}>{RELEASE_STATUS[r.status].text}</Tag>,
    },
    { title: '资产数', dataIndex: 'itemCount', search: false, width: 90 },
    { title: '来源环境', dataIndex: 'sourceEnv', search: false, width: 100 },
    { title: '创建', dataIndex: 'createTime', search: false, width: 200, render: (_, r) => `${r.createTime || ''} · ${r.createBy || ''}` },
    { title: '最近导出', dataIndex: 'exportedTime', search: false, width: 170 },
    {
      title: '操作',
      valueType: 'option',
      width: 140,
      render: (_, r) => [
        <a key="open" onClick={() => setDetailId(r.id)}>
          详情
        </a>,
        <a key="compare" onClick={() => setCompareBase(r)}>
          对比
        </a>,
        canWrite && r.status !== 'EXPORTED' && (
          <Popconfirm
            key="delete"
            title={`删除版本单 ${r.code}？`}
            onConfirm={async () => {
              await deleteRelease(r.id);
              message.success('已删除');
              actionRef.current?.reload();
            }}
          >
            <a style={{ color: '#ff4d4f' }}>删除</a>
          </Popconfirm>
        ),
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
        message="流程：新建版本单 → 开发中把改动的资产加入（各列表页勾选后「加入版本单」）→ 测试通过后冻结 → 导出发布包交给运维，在目标环境导入。"
      />
      <ProTable<Release>
        className="fh-table"
        headerTitle="版本单"
        rowKey="id"
        actionRef={actionRef}
        tableLayout="fixed"
        scroll={{ x: 1100, y: 100000 }}
        search={{ labelWidth: 'auto' }}
        request={(params) => pageReleases(params)}
        toolBarRender={() =>
          canWrite
            ? [
                <Button key="add" type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
                  新建版本单
                </Button>,
              ]
            : []
        }
        columns={columns}
      />

      <ModalForm<{ code: string; name?: string; remark?: string }>
        title="新建版本单"
        open={createOpen}
        onOpenChange={setCreateOpen}
        width={520}
        modalProps={{ destroyOnClose: true }}
        onFinish={async (values) => {
          const created = await createRelease(values);
          message.success('已创建');
          actionRef.current?.reload();
          setDetailId(created.id);
          return true;
        }}
      >
        <ProFormText
          name="code"
          label="版本号"
          placeholder="如 v2026.10"
          rules={[
            { required: true, message: '请输入版本号' },
            { pattern: /^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/, message: '只能包含字母、数字、点、下划线、中划线' },
          ]}
        />
        <ProFormText name="name" label="名称" placeholder="如 十月迭代" />
        <ProFormTextArea
          name="remark"
          label="发布说明"
          placeholder="本轮变更内容、上线注意事项；会写进发布包的 CHANGELOG.md"
          fieldProps={{ rows: 4 }}
        />
      </ModalForm>

      <CompareModal base={compareBase} onCancel={() => setCompareBase(undefined)} />

      <ReleaseDetailDrawer
        releaseId={detailId}
        canWrite={canWrite}
        onClose={() => setDetailId(undefined)}
        onChanged={() => actionRef.current?.reload()}
      />
    </PageContainer>
  );
};

export default ReleasePlans;
