import React, { useRef, useState } from 'react';
import { ActionType, PageContainer, ProColumns, ProTable } from '@ant-design/pro-components';
import { useAccess } from '@umijs/max';
import { Alert, Descriptions, Drawer, Popconfirm, Table, Tag, Typography, message } from 'antd';
import { ACTION_LABELS, ASSET_TYPE_LABELS, type TransferItem } from '@/services/flow/assetTransfer';
import {
  getReleaseImport,
  IMPORT_STATUS,
  pageReleaseImports,
  rollbackReleaseImport,
  type ReleaseImportLog,
} from '@/services/flow/releaseImport';
import '@/styles/fullHeightTable.css';

/**
 * 导入记录：查看每次导入的结果，必要时回滚最近一次导入。
 */
const ReleaseImports: React.FC = () => {
  const access = useAccess() as Record<string, boolean>;
  const canRollback = !!access.canReleaseRollback;
  const actionRef = useRef<ActionType>();
  const [detail, setDetail] = useState<ReleaseImportLog>();

  const openDetail = async (id: string) => setDetail(await getReleaseImport(id));

  const columns: ProColumns<ReleaseImportLog>[] = [
    { title: '版本号', dataIndex: 'keyword', hideInTable: true },
    {
      title: '版本号',
      dataIndex: 'releaseCode',
      search: false,
      width: 160,
      render: (_, r) => <a onClick={() => openDetail(r.id)}>{r.releaseCode}</a>,
    },
    { title: '名称', dataIndex: 'releaseName', search: false, ellipsis: true },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      valueType: 'select',
      valueEnum: Object.fromEntries(Object.entries(IMPORT_STATUS).map(([k, v]) => [k, { text: v.text }])),
      render: (_, r) => <Tag color={IMPORT_STATUS[r.status].color}>{IMPORT_STATUS[r.status].text}</Tag>,
    },
    { title: '环境', search: false, width: 140, render: (_, r) => `${r.sourceEnv || '-'} → ${r.targetEnv || '-'}` },
    {
      title: '摘要',
      dataIndex: 'summary',
      search: false,
      ellipsis: true,
      render: (_, r) => (
        <>
          {!!r.runtimeIssues?.length && <Tag color="warning">自检 {r.runtimeIssues.length} 项问题</Tag>}
          {r.summary}
        </>
      ),
    },
    { title: '导入', search: false, width: 200, render: (_, r) => `${r.importedTime || ''} · ${r.importedBy || ''}` },
    {
      title: '回滚',
      search: false,
      width: 200,
      render: (_, r) => (r.rolledBackTime ? `${r.rolledBackTime} · ${r.rolledBackBy || ''}` : '-'),
    },
    {
      title: '操作',
      valueType: 'option',
      width: 120,
      render: (_, r) => [
        <a key="detail" onClick={() => openDetail(r.id)}>
          详情
        </a>,
        canRollback && r.rollbackable && (
          <Popconfirm
            key="rollback"
            title={`回滚版本 ${r.releaseCode}？`}
            description="受影响的资产会恢复到导入前的内容与发布状态，本次新增的资产会被下线删除。"
            okButtonProps={{ danger: true }}
            onConfirm={async () => {
              await rollbackReleaseImport(r.id);
              message.success('已回滚到导入前');
              actionRef.current?.reload();
            }}
          >
            <a style={{ color: '#ff4d4f' }}>回滚</a>
          </Popconfirm>
        ),
      ],
    },
  ];

  return (
    <PageContainer className="fh-container" style={{ height: 'calc(100vh - 26px)', overflow: 'hidden' }}>
      <ProTable<ReleaseImportLog>
        className="fh-table"
        headerTitle="导入记录"
        rowKey="id"
        actionRef={actionRef}
        tableLayout="fixed"
        scroll={{ x: 1200, y: 100000 }}
        search={{ labelWidth: 'auto' }}
        request={(params) => pageReleaseImports(params)}
        columns={columns}
      />

      <Drawer title={detail ? `导入记录 ${detail.releaseCode}` : ''} width={860} open={!!detail} onClose={() => setDetail(undefined)}>
        {detail && (
          <>
            <Descriptions size="small" column={2} bordered style={{ marginBottom: 16 }}>
              <Descriptions.Item label="状态">
                <Tag color={IMPORT_STATUS[detail.status].color}>{IMPORT_STATUS[detail.status].text}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="环境">{`${detail.sourceEnv || '-'} → ${detail.targetEnv || '-'}`}</Descriptions.Item>
              <Descriptions.Item label="导入">{`${detail.importedTime || ''} · ${detail.importedBy || ''}`}</Descriptions.Item>
              <Descriptions.Item label="包摘要">
                {detail.packageDigest && (
                  <Typography.Text code copyable={{ text: detail.packageDigest }}>
                    {detail.packageDigest.slice(0, 12)}
                  </Typography.Text>
                )}
              </Descriptions.Item>
              <Descriptions.Item label="摘要" span={2}>
                {detail.summary}
              </Descriptions.Item>
            </Descriptions>
            {detail.errorMessage && (
              <Alert type="error" showIcon message="失败原因" description={detail.errorMessage} style={{ marginBottom: 16 }} />
            )}
            {!!detail.runtimeIssues?.length && (
              <Alert
                type="warning"
                showIcon
                style={{ marginBottom: 16 }}
                message="导入后运行时自检发现问题"
                description={
                  <ul style={{ margin: 0, paddingLeft: 18 }}>
                    {detail.runtimeIssues.map((i) => (
                      <li key={i}>{i}</li>
                    ))}
                  </ul>
                }
              />
            )}
            {detail.report && (
              <Table<TransferItem>
                size="small"
                rowKey={(r) => `${r.assetType}:${r.id}`}
                dataSource={detail.report.items}
                pagination={{ pageSize: 20, hideOnSinglePage: true }}
                columns={[
                  { title: '类型', dataIndex: 'assetType', width: 110, render: (t) => ASSET_TYPE_LABELS[t as keyof typeof ASSET_TYPE_LABELS] || t },
                  { title: '名称', dataIndex: 'name', ellipsis: true },
                  { title: '动作', dataIndex: 'action', width: 90, render: (a) => ACTION_LABELS[a as keyof typeof ACTION_LABELS] || a },
                  { title: '说明', dataIndex: 'message', ellipsis: true },
                ]}
              />
            )}
          </>
        )}
      </Drawer>
    </PageContainer>
  );
};

export default ReleaseImports;
