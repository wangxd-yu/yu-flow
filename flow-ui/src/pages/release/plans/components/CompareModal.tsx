import React, { useEffect, useState } from 'react';
import { Alert, Modal, Select, Space, Table, Tabs, Tag, Typography } from 'antd';
import {
  compareReleases,
  pageReleases,
  RELEASE_ASSET_LABELS,
  type Release,
  type ReleaseCompareEntry,
  type ReleaseCompareResult,
} from '@/services/flow/releasePlan';

interface Props {
  base?: Release;
  onCancel: () => void;
}

const actionTag = (a?: string) =>
  !a ? '-' : a === 'OFFLINE' ? <Tag color="volcano">下线</Tag> : <Tag color="blue">更新</Tag>;

/** 与另一个版本单对比：新增 / 移除 / 内容变化的资产 */
const CompareModal: React.FC<Props> = ({ base, onCancel }) => {
  const [others, setOthers] = useState<Release[]>([]);
  const [targetId, setTargetId] = useState<string>();
  const [result, setResult] = useState<ReleaseCompareResult>();
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!base) return;
    setResult(undefined);
    setTargetId(undefined);
    pageReleases({ current: 1, pageSize: 100 }).then((r) => setOthers(r.data.filter((x) => x.id !== base.id)));
  }, [base]);

  useEffect(() => {
    if (!base || !targetId) return;
    setLoading(true);
    compareReleases(base.id, targetId)
      .then(setResult)
      .finally(() => setLoading(false));
  }, [base, targetId]);

  const table = (rows: ReleaseCompareEntry[]) => (
    <Table<ReleaseCompareEntry>
      size="small"
      rowKey={(r) => `${r.assetType}:${r.assetId}`}
      dataSource={rows}
      loading={loading}
      pagination={{ pageSize: 20, hideOnSinglePage: true }}
      columns={[
        { title: '类型', dataIndex: 'assetType', width: 100, render: (t) => RELEASE_ASSET_LABELS[t as keyof typeof RELEASE_ASSET_LABELS] || t },
        { title: '名称', dataIndex: 'assetName', ellipsis: true },
        { title: result?.baseCode || '基准', dataIndex: 'baseAction', width: 110, render: actionTag },
        { title: result?.targetCode || '对比', dataIndex: 'targetAction', width: 110, render: actionTag },
      ]}
    />
  );

  return (
    <Modal title={`版本对比 · ${base?.code || ''}`} width={860} open={!!base} onCancel={onCancel} footer={null} destroyOnClose>
      <Space direction="vertical" size={12} style={{ width: '100%' }}>
        <Space>
          <Typography.Text>对比版本</Typography.Text>
          <Select
            style={{ width: 320 }}
            placeholder="选择另一个版本单"
            value={targetId}
            onChange={setTargetId}
            options={others.map((r) => ({ value: r.id, label: `${r.code}${r.name ? ` · ${r.name}` : ''}` }))}
          />
        </Space>
        {result && !result.contentComparable && (
          <Alert type="warning" showIcon message="有版本单尚未冻结，只比较资产清单，无法判断内容是否变化。" />
        )}
        {result && (
          <Tabs
            items={[
              { key: 'changed', label: `内容或动作变化（${result.changed.length}）`, children: table(result.changed) },
              { key: 'added', label: `仅 ${result.targetCode} 有（${result.onlyInTarget.length}）`, children: table(result.onlyInTarget) },
              { key: 'removed', label: `仅 ${result.baseCode} 有（${result.onlyInBase.length}）`, children: table(result.onlyInBase) },
            ]}
            tabBarExtraContent={<Typography.Text type="secondary">相同 {result.unchangedCount} 项</Typography.Text>}
          />
        )}
      </Space>
    </Modal>
  );
};

export default CompareModal;
