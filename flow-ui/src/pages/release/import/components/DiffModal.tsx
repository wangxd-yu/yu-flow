import React, { useEffect, useState } from 'react';
import { Alert, Collapse, Empty, Modal, Spin, Typography } from 'antd';
import { getReleaseItemDiff, type FieldDiff } from '@/services/flow/releaseImport';
import { lineDiff, prettyIfJson } from '@/utils/lineDiff';

interface Props {
  target?: { digest: string; assetType: string; key: string; name?: string };
  onCancel: () => void;
}

const LINE_STYLE: Record<string, React.CSSProperties> = {
  add: { background: '#e6ffed', color: '#135200' },
  del: { background: '#ffeef0', color: '#a8071a' },
  same: {},
};

const pre: React.CSSProperties = {
  margin: 0,
  maxHeight: 420,
  overflow: 'auto',
  fontSize: 12,
  fontFamily: 'Consolas, Monaco, monospace',
  whiteSpace: 'pre-wrap',
  wordBreak: 'break-all',
};

function FieldView({ diff }: { diff: FieldDiff }) {
  const before = prettyIfJson(diff.before);
  const after = prettyIfJson(diff.after);
  const lines = lineDiff(before, after);
  if (!lines) {
    return (
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
        <pre style={{ ...pre, background: '#fff1f0' }}>{before || '（空）'}</pre>
        <pre style={{ ...pre, background: '#f6ffed' }}>{after || '（空）'}</pre>
      </div>
    );
  }
  return (
    <pre style={pre}>
      {lines.map((l, i) => (
        <div key={i} style={LINE_STYLE[l.type]}>
          {l.type === 'add' ? '+ ' : l.type === 'del' ? '- ' : '  '}
          {l.text}
        </div>
      ))}
    </pre>
  );
}

/** 发布包内容与本环境当前线上内容的逐字段差异（- 本环境 / + 发布包） */
const DiffModal: React.FC<Props> = ({ target, onCancel }) => {
  const [diffs, setDiffs] = useState<FieldDiff[]>();
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!target) return;
    setLoading(true);
    setDiffs(undefined);
    getReleaseItemDiff(target.digest, target.assetType, target.key)
      .then(setDiffs)
      .finally(() => setLoading(false));
  }, [target]);

  return (
    <Modal
      title={`差异 · ${target?.name || target?.key || ''}`}
      width={980}
      open={!!target}
      onCancel={onCancel}
      footer={null}
      destroyOnClose
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 12 }}
        message={
          <span>
            <Typography.Text type="danger">- 本环境当前线上内容</Typography.Text>　
            <Typography.Text type="success">+ 发布包内容</Typography.Text>
          </span>
        }
      />
      <Spin spinning={loading}>
        {diffs && diffs.length === 0 && <Empty description="没有字段差异" />}
        {diffs && diffs.length > 0 && (
          <Collapse
            defaultActiveKey={diffs.slice(0, 3).map((d) => d.field)}
            items={diffs.map((d) => ({ key: d.field, label: d.field, children: <FieldView diff={d} /> }))}
          />
        )}
      </Spin>
    </Modal>
  );
};

export default DiffModal;
