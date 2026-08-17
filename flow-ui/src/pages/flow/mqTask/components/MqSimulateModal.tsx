/**
 * MqSimulateModal
 * 模拟触发一条消息：按已发布快照异步执行，结果落到执行日志。
 * 列表页、编辑页头部、日志回放三处共用，避免各自维护一份 Modal.confirm。
 */
import React, { useEffect, useMemo, useState } from 'react';
import { Alert, Input, Modal, message } from 'antd';
import { simulateMqTask } from '@/services/flow/mqTask';

const CODE_STYLE: React.CSSProperties = { fontFamily: 'monospace', fontSize: 13 };

const DEFAULT_BODY = '{\n  "demo": true\n}';

/** 消息头必须是 JSON 对象；返回 undefined 表示留空 */
function parseHeaders(raw: string): Record<string, any> | undefined | 'INVALID' {
  if (!raw.trim()) return undefined;
  try {
    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return 'INVALID';
    return parsed;
  } catch {
    return 'INVALID';
  }
}

export interface MqSimulateModalProps {
  open: boolean;
  taskId?: string;
  taskName?: string;
  /** 预填消息体（如从历史日志回放） */
  defaultBody?: string;
  /** 预填消息头 JSON */
  defaultHeaders?: string;
  onClose: () => void;
  /** 触发成功后的回调，如刷新日志列表 */
  onDone?: () => void;
}

const MqSimulateModal: React.FC<MqSimulateModalProps> = ({
  open, taskId, taskName, defaultBody, defaultHeaders, onClose, onDone,
}) => {
  const [body, setBody] = useState<string>(DEFAULT_BODY);
  const [headers, setHeaders] = useState<string>('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) return;
    setBody(defaultBody ?? DEFAULT_BODY);
    setHeaders(defaultHeaders ?? '');
    setSubmitting(false);
  }, [open, defaultBody, defaultHeaders]);

  const headersInvalid = useMemo(() => parseHeaders(headers) === 'INVALID', [headers]);

  // 消息体允许是纯文本，只在「看起来是 JSON 但解析不了」时提醒，不拦截提交
  const bodyLooksBroken = useMemo(() => {
    const trimmed = body.trim();
    if (!trimmed.startsWith('{') && !trimmed.startsWith('[')) return false;
    try {
      JSON.parse(trimmed);
      return false;
    } catch {
      return true;
    }
  }, [body]);

  const handleOk = async () => {
    if (!taskId) return;
    const parsed = parseHeaders(headers);
    if (parsed === 'INVALID') {
      message.error('消息头不是合法的 JSON 对象');
      return;
    }
    setSubmitting(true);
    try {
      await simulateMqTask(taskId, body, parsed);
      message.success('已触发模拟，稍后可在「执行日志」查看结果');
      onDone?.();
      onClose();
    } catch (e: any) {
      message.error(e?.message || '模拟触发失败');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      title={taskName ? `模拟触发 - ${taskName}` : '模拟触发一条消息'}
      open={open}
      width={640}
      onCancel={onClose}
      onOk={handleOk}
      okText="触发"
      okButtonProps={{ disabled: headersInvalid }}
      confirmLoading={submitting}
      destroyOnClose
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 12 }}
        message="按已发布版本异步执行一次，结果写入执行日志（触发类型 = 手动模拟）"
      />
      <div style={{ marginBottom: 8, color: 'rgba(0,0,0,0.65)' }}>
        消息体（原样作为 $.mq.message 注入，JSON 会自动解析为对象）
      </div>
      <Input.TextArea
        rows={6}
        value={body}
        onChange={(e) => setBody(e.target.value)}
        style={CODE_STYLE}
        placeholder='例如：{"orderId": "1001", "amount": 99.9}'
      />
      {bodyLooksBroken && (
        <div style={{ marginTop: 4, fontSize: 12, color: '#faad14' }}>
          消息体像 JSON 但无法解析，将按纯文本注入
        </div>
      )}
      <div style={{ margin: '12px 0 8px', color: 'rgba(0,0,0,0.65)' }}>消息头 JSON（可选）</div>
      <Input.TextArea
        rows={3}
        value={headers}
        onChange={(e) => setHeaders(e.target.value)}
        style={CODE_STYLE}
        placeholder='例如：{"x-trace-id": "abc"}'
      />
      {headersInvalid && (
        <div style={{ marginTop: 4, fontSize: 12, color: '#ff4d4f' }}>
          消息头必须是 JSON 对象
        </div>
      )}
    </Modal>
  );
};

export default MqSimulateModal;
