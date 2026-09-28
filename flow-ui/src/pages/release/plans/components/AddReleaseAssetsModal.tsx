import React, { useEffect, useState } from 'react';
import { Input, Modal, Segmented, Space, Switch, Table, Tag, message } from 'antd';
import {
  addReleaseItems,
  RELEASE_ASSET_LABELS,
  searchReleaseAssets,
  type Release,
  type ReleaseAssetOption,
  type ReleaseAssetType,
} from '@/services/flow/releasePlan';

interface Props {
  open: boolean;
  releaseId: string;
  existingKeys: Set<string>;
  onCancel: () => void;
  onAdded: (release: Release) => void;
}

const TYPES = Object.keys(RELEASE_ASSET_LABELS) as ReleaseAssetType[];

/** 版本单详情里按类型搜索并勾选资产 */
const AddReleaseAssetsModal: React.FC<Props> = ({ open, releaseId, existingKeys, onCancel, onAdded }) => {
  const [assetType, setAssetType] = useState<ReleaseAssetType>('API');
  const [keyword, setKeyword] = useState('');
  const [options, setOptions] = useState<ReleaseAssetOption[]>([]);
  const [loading, setLoading] = useState(false);
  const [selected, setSelected] = useState<string[]>([]);
  const [withDeps, setWithDeps] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) return;
    setLoading(true);
    const timer = setTimeout(() => {
      searchReleaseAssets(assetType, keyword)
        .then(setOptions)
        .finally(() => setLoading(false));
    }, 250);
    return () => clearTimeout(timer);
  }, [open, assetType, keyword]);

  useEffect(() => setSelected([]), [assetType]);

  const submit = async () => {
    if (!selected.length) {
      message.warning('请勾选资产');
      return;
    }
    setSubmitting(true);
    try {
      const release = await addReleaseItems(
        releaseId,
        selected.map((assetId) => ({ assetType, assetId })),
        withDeps,
      );
      message.success('已加入');
      setSelected([]);
      onAdded(release);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      title="加入资产"
      width={860}
      open={open}
      onCancel={onCancel}
      onOk={submit}
      okText={`加入${selected.length ? `（${selected.length}）` : ''}`}
      confirmLoading={submitting}
      destroyOnClose
    >
      <Space direction="vertical" size={12} style={{ width: '100%' }}>
        <Segmented
          value={assetType}
          onChange={(v) => setAssetType(v as ReleaseAssetType)}
          options={TYPES.map((t) => ({ value: t, label: RELEASE_ASSET_LABELS[t] }))}
        />
        <Space style={{ width: '100%', justifyContent: 'space-between' }}>
          <Input.Search
            allowClear
            placeholder="按名称搜索（最多显示 50 条）"
            style={{ width: 360 }}
            onSearch={setKeyword}
            onChange={(e) => !e.target.value && setKeyword('')}
          />
          <Space>
            <Switch checked={withDeps} onChange={setWithDeps} />
            <span>同时加入引用的资产</span>
          </Space>
        </Space>
        <Table<ReleaseAssetOption>
          size="small"
          rowKey="assetId"
          loading={loading}
          dataSource={options}
          pagination={false}
          scroll={{ y: 360 }}
          rowSelection={{
            selectedRowKeys: selected,
            onChange: (keys) => setSelected(keys as string[]),
            getCheckboxProps: (r) => ({
              disabled:
                existingKeys.has(`${r.assetType}:${r.assetId}`) ||
                (r.assetType === 'SYS_CONFIG' && !!r.detail?.includes('不随包导出')),
            }),
          }}
          columns={[
            { title: '名称', dataIndex: 'name', ellipsis: true },
            { title: '说明', dataIndex: 'detail', ellipsis: true },
            {
              title: '状态',
              width: 200,
              render: (_, r) => {
                if (existingKeys.has(`${r.assetType}:${r.assetId}`)) return <Tag>已在版本单中</Tag>;
                if (!r.publishable) return <Tag>无需发布</Tag>;
                if (!r.published) return <Tag color="error">未发布</Tag>;
                if (r.unpublishedChanges) return <Tag color="warning">有未发布的修改</Tag>;
                return <Tag color="success">已发布</Tag>;
              },
            },
          ]}
        />
      </Space>
    </Modal>
  );
};

export default AddReleaseAssetsModal;
