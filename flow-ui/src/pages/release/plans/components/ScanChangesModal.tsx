import React, { useEffect, useState } from 'react';
import { Alert, Button, DatePicker, Modal, Space, Table, Tag, Typography, message } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import {
  addOfflineItems,
  addReleaseItems,
  RELEASE_ASSET_LABELS,
  scanReleaseChanges,
  type Release,
  type ReleaseScanCandidate,
  type ReleaseScanResult,
} from '@/services/flow/releasePlan';

interface Props {
  open: boolean;
  releaseId: string;
  onCancel: () => void;
  onAdded: (release: Release) => void;
}

const keyOf = (c: ReleaseScanCandidate) => `${c.action}:${c.assetType}:${c.assetId}`;

/**
 * 扫描自上个已导出版本（或指定时间）以来的变化：有改动的资产建议更新，上个版本里已删除的资产建议下线。
 */
const ScanChangesModal: React.FC<Props> = ({ open, releaseId, onCancel, onAdded }) => {
  const [since, setSince] = useState<Dayjs | null>(null);
  const [result, setResult] = useState<ReleaseScanResult>();
  const [loading, setLoading] = useState(false);
  const [needSince, setNeedSince] = useState(false);
  const [selected, setSelected] = useState<string[]>([]);
  const [submitting, setSubmitting] = useState(false);

  const scan = async (from?: Dayjs | null) => {
    setLoading(true);
    try {
      const r = await scanReleaseChanges(releaseId, from ? from.format('YYYY-MM-DD HH:mm:ss') : undefined);
      setResult(r);
      setNeedSince(false);
      setSelected(r.candidates.filter((c) => c.selectable).map(keyOf));
    } catch (e: any) {
      // 没有基线版本时要求手工指定起点
      setNeedSince(true);
      setResult(undefined);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (open) {
      setSince(null);
      scan(null);
    }
  }, [open]);

  const submit = async () => {
    if (!result) return;
    const chosen = result.candidates.filter((c) => selected.includes(keyOf(c)));
    const upserts = chosen.filter((c) => c.action === 'UPSERT');
    const offlines = chosen.filter((c) => c.action === 'OFFLINE');
    if (!chosen.length) {
      message.warning('请勾选要加入的资产');
      return;
    }
    setSubmitting(true);
    try {
      let release: Release | undefined;
      if (upserts.length) {
        release = await addReleaseItems(
          releaseId,
          upserts.map((c) => ({ assetType: c.assetType, assetId: c.assetId })),
          true,
          'SCAN',
        );
      }
      if (offlines.length) {
        release = await addOfflineItems(
          releaseId,
          offlines.map((c) => ({ assetType: c.assetType, assetId: c.assetId, assetName: c.name, assetKey: c.assetKey })),
        );
      }
      message.success(`已加入 ${upserts.length} 个更新项、${offlines.length} 个下线项`);
      if (release) onAdded(release);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      title="扫描变更"
      width={960}
      open={open}
      onCancel={onCancel}
      onOk={submit}
      okText={`加入选中（${selected.length}）`}
      okButtonProps={{ disabled: !result }}
      confirmLoading={submitting}
      destroyOnClose
    >
      <Space direction="vertical" size={12} style={{ width: '100%' }}>
        <Space wrap>
          <Typography.Text>起始时间</Typography.Text>
          <DatePicker
            showTime
            value={since}
            onChange={setSince}
            placeholder={result?.baselineCode ? `默认：上个版本 ${result.baselineCode} 的导出时间` : '选择起始时间'}
            style={{ width: 320 }}
            disabledDate={(d) => d.isAfter(dayjs())}
          />
          <Button onClick={() => scan(since)} loading={loading} disabled={needSince && !since}>
            扫描
          </Button>
        </Space>
        {needSince && <Alert type="info" showIcon message="还没有已导出的版本可作为基线，请选择起始时间后扫描。" />}
        {result && (
          <Alert
            type="info"
            showIcon
            message={`扫描 ${result.since} 之后的变化${result.baselineCode ? `（基线：${result.baselineCode}）` : ''}，共 ${result.candidates.length} 项`}
            description="「下线」建议来自上个版本包含、但当前环境已删除的资产；页面、模型等没有删除记录的资产无法自动识别删除，需要手工加入下线项。"
          />
        )}
        <Table<ReleaseScanCandidate>
          size="small"
          rowKey={keyOf}
          loading={loading}
          dataSource={result?.candidates || []}
          pagination={{ pageSize: 50, hideOnSinglePage: true }}
          scroll={{ y: 420 }}
          rowSelection={{
            selectedRowKeys: selected,
            onChange: (keys) => setSelected(keys as string[]),
            getCheckboxProps: (r) => ({ disabled: !r.selectable }),
          }}
          columns={[
            {
              title: '动作',
              dataIndex: 'action',
              width: 80,
              render: (a) => (a === 'OFFLINE' ? <Tag color="volcano">下线</Tag> : <Tag color="blue">更新</Tag>),
            },
            { title: '类型', dataIndex: 'assetType', width: 100, render: (t) => RELEASE_ASSET_LABELS[t as keyof typeof RELEASE_ASSET_LABELS] || t },
            { title: '名称', dataIndex: 'name', ellipsis: true },
            { title: '说明', dataIndex: 'detail', ellipsis: true },
            { title: '原因', dataIndex: 'reason', ellipsis: true },
          ]}
        />
      </Space>
    </Modal>
  );
};

export default ScanChangesModal;
