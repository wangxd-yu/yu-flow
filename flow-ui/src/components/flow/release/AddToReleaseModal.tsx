import React, { useEffect, useState } from 'react';
import { Alert, Input, Modal, Radio, Select, Space, Switch, Typography, message } from 'antd';
import { history } from '@umijs/max';
import {
  addReleaseItems,
  createRelease,
  listDraftReleases,
  RELEASE_ASSET_LABELS,
  type Release,
  type ReleaseAssetType,
} from '@/services/flow/releasePlan';

interface Props {
  open: boolean;
  assetType: ReleaseAssetType;
  ids: string[];
  onCancel: () => void;
}

/**
 * 列表页批量操作：把勾选的资产加入某个编辑中的版本单（或顺手新建一个）。
 */
const AddToReleaseModal: React.FC<Props> = ({ open, assetType, ids, onCancel }) => {
  const [drafts, setDrafts] = useState<Release[]>([]);
  const [mode, setMode] = useState<'existing' | 'new'>('existing');
  const [releaseId, setReleaseId] = useState<string>();
  const [newCode, setNewCode] = useState('');
  const [withDeps, setWithDeps] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) return;
    listDraftReleases()
      .then((list) => {
        setDrafts(list);
        setMode(list.length ? 'existing' : 'new');
        setReleaseId(list[0]?.id);
      })
      .catch(() => setDrafts([]));
  }, [open]);

  const submit = async () => {
    if (!ids.length) {
      message.warning('请先勾选资产');
      return;
    }
    setSubmitting(true);
    try {
      let target = releaseId;
      if (mode === 'new') {
        if (!newCode.trim()) {
          message.warning('请输入版本号');
          return;
        }
        target = (await createRelease({ code: newCode.trim() })).id;
      }
      if (!target) {
        message.warning('请选择版本单');
        return;
      }
      const before = drafts.find((d) => d.id === target)?.itemCount ?? 0;
      const release = await addReleaseItems(
        target,
        ids.map((assetId) => ({ assetType, assetId })),
        withDeps,
      );
      const added = (release.items?.length ?? 0) - before;
      message.success(
        <span>
          已加入 {release.code}（新增 {Math.max(added, 0)} 项）
          <Typography.Link style={{ marginLeft: 8 }} onClick={() => history.push(`/release/plans?id=${release.id}`)}>
            查看
          </Typography.Link>
        </span>,
      );
      onCancel();
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      title={`加入版本单（${RELEASE_ASSET_LABELS[assetType]} ${ids.length} 项）`}
      open={open}
      onCancel={onCancel}
      onOk={submit}
      okText="加入"
      confirmLoading={submitting}
      destroyOnClose
    >
      <Space direction="vertical" size={12} style={{ width: '100%' }}>
        <Radio.Group value={mode} onChange={(e) => setMode(e.target.value)}>
          <Radio value="existing" disabled={!drafts.length}>
            已有版本单
          </Radio>
          <Radio value="new">新建版本单</Radio>
        </Radio.Group>
        {mode === 'existing' ? (
          <Select
            style={{ width: '100%' }}
            value={releaseId}
            onChange={setReleaseId}
            options={drafts.map((d) => ({
              value: d.id,
              label: `${d.code}${d.name ? ` · ${d.name}` : ''}（${d.itemCount} 项）`,
            }))}
          />
        ) : (
          <Input placeholder="版本号，如 v2026.10" value={newCode} onChange={(e) => setNewCode(e.target.value)} />
        )}
        <Space>
          <Switch checked={withDeps} onChange={setWithDeps} />
          <span>同时加入引用的接口 / 内部服务 / 响应模板</span>
        </Space>
        <Alert
          type="info"
          showIcon
          message="只有「编辑中」的版本单可以加入资产；冻结时会检查每个资产都已发布且没有未发布的修改。"
        />
      </Space>
    </Modal>
  );
};

export default AddToReleaseModal;
