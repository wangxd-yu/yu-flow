import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Descriptions,
  Drawer,
  Input,
  List,
  Modal,
  Popconfirm,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import { DeleteOutlined, DownloadOutlined, LockOutlined, PlusOutlined, UnlockOutlined } from '@ant-design/icons';
import {
  checkRelease,
  exportReleasePackage,
  freezeRelease,
  getRelease,
  RELEASE_ASSET_LABELS,
  RELEASE_STATUS,
  removeReleaseItem,
  unfreezeRelease,
  updateRelease,
  type Release,
  type ReleaseCheckResult,
  type ReleaseItem,
} from '@/services/flow/releasePlan';
import AddReleaseAssetsModal from './AddReleaseAssetsModal';
import OfflineItemModal from './OfflineItemModal';
import ScanChangesModal from './ScanChangesModal';

interface Props {
  releaseId?: string;
  canWrite: boolean;
  onClose: () => void;
  onChanged: () => void;
}

function itemStatus(item: ReleaseItem, frozen: boolean) {
  if (item.action === 'OFFLINE') {
    return item.exists ? <Tag>当前环境仍存在</Tag> : <Tag>当前环境已删除</Tag>;
  }
  if (!item.exists) return <Tag color="error">已删除</Tag>;
  if (frozen && item.drifted) {
    return (
      <Tooltip title="冻结后内容发生了变化，需解冻后重新冻结">
        <Tag color="error">已变化</Tag>
      </Tooltip>
    );
  }
  if (!item.publishable) return <Tag>无需发布</Tag>;
  if (!item.published) return <Tag color="error">未发布</Tag>;
  if (item.unpublishedChanges) return <Tag color="warning">有未发布的修改</Tag>;
  return <Tag color="success">已发布</Tag>;
}

const ReleaseDetailDrawer: React.FC<Props> = ({ releaseId, canWrite, onClose, onChanged }) => {
  const [release, setRelease] = useState<Release>();
  const [loading, setLoading] = useState(false);
  const [check, setCheck] = useState<ReleaseCheckResult | null>(null);
  const [addOpen, setAddOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [remarkOpen, setRemarkOpen] = useState(false);
  const [remark, setRemark] = useState('');
  const [scanOpen, setScanOpen] = useState(false);
  const [offlineOpen, setOfflineOpen] = useState(false);

  const load = useCallback(async () => {
    if (!releaseId) return;
    setLoading(true);
    try {
      setRelease(await getRelease(releaseId));
    } finally {
      setLoading(false);
    }
  }, [releaseId]);

  useEffect(() => {
    setCheck(null);
    setRelease(undefined);
    load();
  }, [load]);

  const apply = (next: Release) => {
    setRelease(next);
    onChanged();
  };

  const draft = release?.status === 'DRAFT';
  const frozen = !!release && !draft;
  const existingKeys = useMemo(
    () => new Set((release?.items || []).map((i) => `${i.assetType}:${i.assetId}`)),
    [release],
  );
  const driftedCount = (release?.items || []).filter((i) => i.drifted).length;

  const run = async (fn: () => Promise<void>) => {
    setBusy(true);
    try {
      await fn();
    } finally {
      setBusy(false);
    }
  };

  const doCheck = () =>
    run(async () => {
      setCheck(await checkRelease(release!.id));
    });

  const doFreeze = () =>
    run(async () => {
      const r = await freezeRelease(release!.id);
      setCheck(r);
      if (r.frozen) {
        message.success('已冻结，可以导出发布包');
        await load();
        onChanged();
      } else {
        message.error('检查未通过，请先处理下方的错误项');
      }
    });

  const doUnfreeze = () =>
    run(async () => {
      apply(await unfreezeRelease(release!.id));
      setCheck(null);
      message.success('已解冻，可继续调整资产');
    });

  const doExport = () =>
    run(async () => {
      try {
        await exportReleasePackage(release!.id);
        message.success('发布包已下载，请交给运维在目标环境导入');
        await load();
        onChanged();
      } catch (e: any) {
        message.error(e?.message || '导出失败');
      }
    });

  const actions = !release || !canWrite ? null : (
    <Space>
      {draft && (
        <>
          <Button icon={<PlusOutlined />} onClick={() => setAddOpen(true)}>
            加入资产
          </Button>
          <Button onClick={() => setScanOpen(true)}>扫描变更</Button>
          <Button onClick={() => setOfflineOpen(true)}>加入下线项</Button>
          <Button onClick={doCheck} loading={busy}>
            冻结前检查
          </Button>
          <Button type="primary" icon={<LockOutlined />} onClick={doFreeze} loading={busy}>
            冻结
          </Button>
        </>
      )}
      {frozen && (
        <>
          <Popconfirm
            title="解冻后可继续调整资产"
            description={release.status === 'EXPORTED' ? '已导出的发布包作废，需重新冻结导出' : undefined}
            onConfirm={doUnfreeze}
          >
            <Button icon={<UnlockOutlined />} loading={busy}>
              解冻
            </Button>
          </Popconfirm>
          <Button type="primary" icon={<DownloadOutlined />} onClick={doExport} loading={busy} disabled={driftedCount > 0}>
            {release.status === 'EXPORTED' ? '重新下载发布包' : '导出发布包'}
          </Button>
        </>
      )}
    </Space>
  );

  return (
    <Drawer
      title={release ? `版本单 ${release.code}` : '版本单'}
      width={960}
      open={!!releaseId}
      onClose={onClose}
      extra={actions}
      destroyOnClose
    >
      {release && (
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Descriptions size="small" column={3} bordered>
            <Descriptions.Item label="状态">
              <Tag color={RELEASE_STATUS[release.status].color}>{RELEASE_STATUS[release.status].text}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="名称">{release.name || '-'}</Descriptions.Item>
            <Descriptions.Item label="来源环境">{release.sourceEnv || '-'}</Descriptions.Item>
            <Descriptions.Item label="冻结">
              {release.frozenTime ? `${release.frozenTime} · ${release.frozenBy || ''}` : '-'}
            </Descriptions.Item>
            <Descriptions.Item label="导出">
              {release.exportedTime ? `${release.exportedTime} · ${release.exportedBy || ''}` : '-'}
            </Descriptions.Item>
            <Descriptions.Item label="包摘要">
              {release.packageDigest ? (
                <Typography.Text copyable={{ text: release.packageDigest }} code>
                  {release.packageDigest.slice(0, 12)}
                </Typography.Text>
              ) : (
                '-'
              )}
            </Descriptions.Item>
            <Descriptions.Item label="发布说明" span={3}>
              <Space align="start">
                <Typography.Paragraph style={{ marginBottom: 0, whiteSpace: 'pre-wrap' }}>
                  {release.remark || <Typography.Text type="secondary">未填写（会写进发布包的 CHANGELOG.md）</Typography.Text>}
                </Typography.Paragraph>
                {canWrite && (
                  <Typography.Link
                    onClick={() => {
                      setRemark(release.remark || '');
                      setRemarkOpen(true);
                    }}
                  >
                    编辑
                  </Typography.Link>
                )}
              </Space>
            </Descriptions.Item>
          </Descriptions>

          {driftedCount > 0 && (
            <Alert
              type="error"
              showIcon
              message={`有 ${driftedCount} 个资产在冻结后发生了变化，无法导出。请解冻后重新冻结。`}
            />
          )}

          {check && (
            <Alert
              type={check.passed ? (check.issues.length ? 'warning' : 'success') : 'error'}
              showIcon
              message={
                check.passed
                  ? check.issues.length
                    ? '检查通过，但有需要留意的提示'
                    : '检查通过'
                  : '检查未通过，请处理错误项后再冻结'
              }
              description={
                check.issues.length > 0 && (
                  <List
                    size="small"
                    dataSource={check.issues}
                    renderItem={(i) => (
                      <List.Item style={{ padding: '4px 0' }}>
                        <Space>
                          <Tag color={i.level === 'ERROR' ? 'error' : 'warning'}>{i.level === 'ERROR' ? '错误' : '提示'}</Tag>
                          {i.assetType && <span>{RELEASE_ASSET_LABELS[i.assetType]}「{i.assetName || i.assetId}」</span>}
                          <span>{i.message}</span>
                        </Space>
                      </List.Item>
                    )}
                  />
                )
              }
            />
          )}

          <Table<ReleaseItem>
            size="small"
            rowKey="id"
            loading={loading}
            dataSource={release.items || []}
            pagination={false}
            title={() => `资产清单（${release.items?.length || 0} 项）`}
            columns={[
              {
                title: '类型',
                dataIndex: 'assetType',
                width: 100,
                filters: Object.entries(RELEASE_ASSET_LABELS).map(([value, text]) => ({ value, text })),
                onFilter: (v, r) => r.assetType === v,
                render: (t: ReleaseItem['assetType']) => RELEASE_ASSET_LABELS[t] || t,
              },
              {
                title: '名称',
                dataIndex: 'assetName',
                ellipsis: true,
                render: (_, r) => (
                  <Space direction="vertical" size={0}>
                    <span>{r.assetName || r.assetId}</span>
                    {(r.detail || r.assetKey) && (
                      <Typography.Text type="secondary">{r.detail || r.assetKey}</Typography.Text>
                    )}
                  </Space>
                ),
              },
              {
                title: '动作',
                dataIndex: 'action',
                width: 80,
                filters: [
                  { value: 'UPSERT', text: '更新' },
                  { value: 'OFFLINE', text: '下线' },
                ],
                onFilter: (v, r) => r.action === v,
                render: (a) => (a === 'OFFLINE' ? <Tag color="volcano">下线</Tag> : <Tag color="blue">更新</Tag>),
              },
              {
                title: '来源',
                dataIndex: 'origin',
                width: 100,
                render: (o) =>
                  o === 'DEPENDENCY' ? <Tag>依赖补齐</Tag> : o === 'SCAN' ? <Tag color="cyan">变更扫描</Tag> : <Tag>手工加入</Tag>,
              },
              { title: '状态', width: 140, render: (_, r) => itemStatus(r, frozen) },
              {
                title: '操作',
                width: 80,
                hidden: !(draft && canWrite),
                render: (_, r) => (
                  <Popconfirm
                    title="从版本单移除？"
                    onConfirm={async () => apply(await removeReleaseItem(release.id, r.id))}
                  >
                    <Button type="link" danger size="small" icon={<DeleteOutlined />} />
                  </Popconfirm>
                ),
              },
            ]}
          />
        </Space>
      )}

      {release && (
        <>
          <ScanChangesModal
            open={scanOpen}
            releaseId={release.id}
            onCancel={() => setScanOpen(false)}
            onAdded={(next) => {
              apply(next);
              setScanOpen(false);
            }}
          />
          <OfflineItemModal
            open={offlineOpen}
            releaseId={release.id}
            onCancel={() => setOfflineOpen(false)}
            onAdded={(next) => {
              apply(next);
              setOfflineOpen(false);
            }}
          />
        </>
      )}

      {release && (
        <AddReleaseAssetsModal
          open={addOpen}
          releaseId={release.id}
          existingKeys={existingKeys}
          onCancel={() => setAddOpen(false)}
          onAdded={(next) => {
            apply(next);
            setAddOpen(false);
          }}
        />
      )}

      <Modal
        title="编辑发布说明"
        open={remarkOpen}
        onCancel={() => setRemarkOpen(false)}
        onOk={async () => {
          apply({ ...(await updateRelease(release!.id, { remark })), items: release?.items });
          setRemarkOpen(false);
        }}
      >
        <Input.TextArea
          rows={8}
          value={remark}
          onChange={(e) => setRemark(e.target.value)}
          placeholder="本轮变更内容、上线注意事项等；会写进发布包的 CHANGELOG.md 给运维阅读"
        />
      </Modal>
    </Drawer>
  );
};

export default ReleaseDetailDrawer;
