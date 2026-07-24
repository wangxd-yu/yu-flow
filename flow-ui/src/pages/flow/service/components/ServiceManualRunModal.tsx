/**
 * 服务手动调用弹窗：JSON 入参 → POST /{id}/run → 展示业务输出
 */
import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Modal, Space, Typography, message } from 'antd';
import { PlayCircleOutlined } from '@ant-design/icons';
import CodeEditor from '@/components/flow/flow-editor/components/CodeEditor';
import {
  getServiceFlow,
  runServiceFlow,
  type FlowServiceFlow,
} from '@/services/flow/serviceFlowService';
import {
  buildSampleInputFromContract,
  parseServiceContract,
} from '@/services/flow/serviceContract';

const { Text } = Typography;

function unwrapResult(res: any): any {
  if (res == null) return res;
  if (typeof res === 'object' && 'code' in res) {
    if (res.code === 0) return res.data;
    throw new Error(res.msg || '调用失败');
  }
  return res;
}

function pretty(value: unknown): string {
  if (value === undefined) return '';
  if (typeof value === 'string') {
    try {
      return JSON.stringify(JSON.parse(value), null, 2);
    } catch {
      return value;
    }
  }
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}

export interface ServiceManualRunModalProps {
  open: boolean;
  serviceId?: string;
  serviceName?: string;
  /** 表单侧可直接传入当前草稿契约 JSON，避免再请求 */
  contractJson?: string;
  onClose: () => void;
}

const ServiceManualRunModal: React.FC<ServiceManualRunModalProps> = ({
  open,
  serviceId,
  serviceName,
  contractJson,
  onClose,
}) => {
  const [loadingMeta, setLoadingMeta] = useState(false);
  const [running, setRunning] = useState(false);
  const [meta, setMeta] = useState<FlowServiceFlow | null>(null);
  const [inputJson, setInputJson] = useState('{\n  \n}');
  const [outputJson, setOutputJson] = useState('');
  const [errorText, setErrorText] = useState<string | null>(null);

  const resetOutput = useCallback(() => {
    setOutputJson('');
    setErrorText(null);
  }, []);

  useEffect(() => {
    if (!open || !serviceId) return;

    let cancelled = false;
    const applyContract = (raw?: string | null) => {
      const sample = buildSampleInputFromContract(parseServiceContract(raw));
      setInputJson(sample);
    };

    resetOutput();

    if (contractJson != null) {
      applyContract(contractJson);
      setMeta(null);
      return;
    }

    setLoadingMeta(true);
    getServiceFlow(serviceId)
      .then((res) => {
        if (cancelled) return;
        const detail = (res as any)?.data || res;
        setMeta(detail);
        applyContract(detail?.contract);
      })
      .catch((e: any) => {
        if (cancelled) return;
        message.error(e?.message || '加载服务失败');
        applyContract(null);
      })
      .finally(() => {
        if (!cancelled) setLoadingMeta(false);
      });

    return () => {
      cancelled = true;
    };
  }, [open, serviceId, contractJson, resetOutput]);

  const handleRun = useCallback(async () => {
    if (!serviceId) {
      message.warning('服务尚未保存，无法手动调用');
      return;
    }
    let parsed: Record<string, unknown> = {};
    try {
      const trimmed = inputJson.trim();
      if (trimmed) {
        const value = JSON.parse(trimmed);
        if (value == null || typeof value !== 'object' || Array.isArray(value)) {
          message.error('入参必须是 JSON 对象');
          return;
        }
        parsed = value as Record<string, unknown>;
      }
    } catch {
      message.error('入参 JSON 格式不正确');
      return;
    }

    setRunning(true);
    resetOutput();
    try {
      const res = await runServiceFlow(serviceId, parsed);
      const data = unwrapResult(res);
      setOutputJson(pretty(data));
      message.success('调用成功');
    } catch (e: any) {
      const msg = e?.message || '调用失败';
      setErrorText(msg);
      message.error(msg);
    } finally {
      setRunning(false);
    }
  }, [serviceId, inputJson, resetOutput]);

  const titleName = serviceName || meta?.name || serviceId || '服务';

  return (
    <Modal
      title={`手动调用：${titleName}`}
      open={open}
      onCancel={onClose}
      width={720}
      destroyOnClose
      footer={
        <Space>
          <Button onClick={onClose}>关闭</Button>
          <Button
            type="primary"
            icon={<PlayCircleOutlined />}
            loading={running || loadingMeta}
            disabled={!serviceId}
            onClick={handleRun}
          >
            运行
          </Button>
        </Space>
      }
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 12 }}
        message="执行服务端已保存的草稿 DSL，并按草稿契约校验入参；返回业务输出（非 Trace）。未保存的表单修改不会生效。"
      />

      <div style={{ marginBottom: 8 }}>
        <Text strong>入参（$.service.input）</Text>
      </div>
      <div style={{ marginBottom: 16, border: '1px solid #f0f0f0', borderRadius: 6, overflow: 'hidden' }}>
        <CodeEditor
          value={inputJson}
          onChange={setInputJson}
          language="json"
          height="220px"
          showFormat
        />
      </div>

      <div style={{ marginBottom: 8 }}>
        <Text strong>输出</Text>
      </div>
      {errorText ? (
        <Alert type="error" showIcon message={errorText} style={{ marginBottom: 8 }} />
      ) : null}
      <div style={{ border: '1px solid #f0f0f0', borderRadius: 6, overflow: 'hidden', minHeight: 160 }}>
        <CodeEditor
          value={outputJson || (running ? '运行中…' : '')}
          onChange={() => undefined}
          language="json"
          height="200px"
          readOnly
          showFormat={false}
          placeholder="运行后在此展示业务返回"
        />
      </div>
    </Modal>
  );
};

export default ServiceManualRunModal;
