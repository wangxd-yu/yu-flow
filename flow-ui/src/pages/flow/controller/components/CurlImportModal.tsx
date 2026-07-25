/**
 * 从 cURL 导入 — 纯前端解析，预览后写入 method/path/请求契约（不改服务实现）。
 */
import React, { useEffect, useMemo, useState } from 'react';
import { Alert, Input, Modal, Space, Table, Tag, Typography } from 'antd';
import type { BodyType, SchemaNode } from '@/components/flow/ApiContractDesigner/types';
import { parseCurl, type CurlImportDraft } from '@/utils/parseCurl';

export interface CurlImportApplyPayload {
  method: string;
  path: string;
  query: SchemaNode[];
  headers: SchemaNode[];
  bodyType: BodyType;
  body: SchemaNode[];
  rawBody?: string;
}

interface Props {
  open: boolean;
  onCancel: () => void;
  onApply: (payload: CurlImportApplyPayload) => void;
}

const CurlImportModal: React.FC<Props> = ({ open, onCancel, onApply }) => {
  const [text, setText] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [draft, setDraft] = useState<CurlImportDraft | null>(null);

  useEffect(() => {
    if (!open) return;
    setText('');
    setError(null);
    setDraft(null);
  }, [open]);

  useEffect(() => {
    if (!open) return;
    if (!text.trim()) {
      setError(null);
      setDraft(null);
      return;
    }
    const timer = setTimeout(() => {
      const r = parseCurl(text);
      if (r.error) {
        setError(r.error);
        setDraft(null);
      } else {
        setError(null);
        setDraft(r.draft || null);
      }
    }, 280);
    return () => clearTimeout(timer);
  }, [text, open]);

  const previewRows = useMemo(() => {
    if (!draft) return [];
    const rows: Array<{ key: string; kind: string; detail: string }> = [
      { key: 'm', kind: 'Method', detail: draft.method },
      { key: 'p', kind: 'Path', detail: draft.path },
    ];
    if (draft.query.length) {
      rows.push({
        key: 'q',
        kind: 'Query',
        detail: draft.query.map((n) => `${n.name}=${n.defaultValue ?? ''}`).join(', '),
      });
    }
    if (draft.headers.length) {
      rows.push({
        key: 'h',
        kind: 'Headers',
        detail: draft.headers.map((n) => n.name).join(', '),
      });
    }
    rows.push({
      key: 'b',
      kind: 'Body',
      detail: draft.bodyType === 'none'
        ? 'none'
        : `${draft.bodyType}${draft.body.length ? `（${draft.body.length} 字段）` : ''}`,
    });
    return rows;
  }, [draft]);

  return (
    <Modal
      title="从 cURL 导入"
      open={open}
      onCancel={onCancel}
      okText="填入表单"
      okButtonProps={{ disabled: !draft }}
      onOk={() => {
        if (!draft) return;
        onApply({
          method: draft.method,
          path: draft.path,
          query: draft.query,
          headers: draft.headers,
          bodyType: draft.bodyType,
          body: draft.body,
          rawBody: draft.rawBody,
        });
      }}
      width={720}
      destroyOnClose
    >
      <Space direction="vertical" size={12} style={{ width: '100%' }}>
        <Alert
          type="info"
          showIcon
          message="仅填充 Method / Path 与「API 文档定义」请求侧；不会改动服务实现画布。"
          description="支持常见 -X / -H / -d / --data-raw / -G / -F(非文件)。复杂转义或文件上传可能需手动补全。"
        />
        <Input.TextArea
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder={`粘贴 cURL，例如：\ncurl -X POST 'https://host/api/orders?x=1' \\\n  -H 'Content-Type: application/json' \\\n  -d '{"id":1}'`}
          autoSize={{ minRows: 6, maxRows: 14 }}
          style={{ fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace', fontSize: 13 }}
        />
        {error && <Alert type="error" showIcon message={error} />}
        {draft?.warnings?.length ? (
          <Alert
            type="warning"
            showIcon
            message="部分内容已降级处理"
            description={(
              <ul style={{ margin: 0, paddingLeft: 18 }}>
                {draft.warnings.map((w) => (
                  <li key={w}>{w}</li>
                ))}
              </ul>
            )}
          />
        ) : null}
        {draft && (
          <>
            <Typography.Text type="secondary">将写入预览</Typography.Text>
            <Table
              size="small"
              pagination={false}
              rowKey="key"
              dataSource={previewRows}
              columns={[
                {
                  title: '',
                  dataIndex: 'kind',
                  width: 96,
                  render: (v: string) => <Tag style={{ margin: 0 }}>{v}</Tag>,
                },
                {
                  title: '内容',
                  dataIndex: 'detail',
                  ellipsis: true,
                  render: (v: string) => (
                    <span style={{ fontFamily: 'monospace', fontSize: 12 }}>{v}</span>
                  ),
                },
              ]}
            />
          </>
        )}
      </Space>
    </Modal>
  );
};

export default CurlImportModal;
