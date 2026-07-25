/**
 * WRAP 宿主绑定：日志策略 + 探活开关（写入 hostBinding JSON）
 */
import React, { useMemo } from 'react';
import { Alert, Form, InputNumber, Select, Space, Switch, Typography } from 'antd';
import { CloudServerOutlined } from '@ant-design/icons';

const { Text } = Typography;

export type HostWrapBinding = {
  forward?: string;
  targetPath?: string;
  probeEnabled?: boolean;
  probePath?: string;
  logMode?: 'ALL' | 'ERROR_ONLY' | 'SAMPLE' | string;
  logSamplePermille?: number;
};

export function parseHostBinding(raw?: string): HostWrapBinding {
  if (!raw) {
    return { forward: 'LOCAL', logMode: 'ERROR_ONLY', probeEnabled: true, logSamplePermille: 100 };
  }
  try {
    const o = typeof raw === 'string' ? JSON.parse(raw) : raw;
    return {
      forward: o?.forward || 'LOCAL',
      targetPath: o?.targetPath,
      probeEnabled: !!o?.probeEnabled,
      probePath: o?.probePath,
      logMode: o?.logMode || 'ALL',
      logSamplePermille: o?.logSamplePermille ?? 100,
    };
  } catch {
    return { forward: 'LOCAL', logMode: 'ALL', probeEnabled: false, logSamplePermille: 100 };
  }
}

export function stringifyHostBinding(b: HostWrapBinding): string {
  return JSON.stringify({
    forward: b.forward || 'LOCAL',
    targetPath: b.targetPath || undefined,
    probeEnabled: !!b.probeEnabled,
    probePath: b.probePath || undefined,
    logMode: b.logMode || 'ALL',
    logSamplePermille: b.logSamplePermille ?? 100,
  });
}

export interface HostWrapConfigProps {
  apiMethod?: string;
  apiUrl?: string;
  value: HostWrapBinding;
  onChange: (v: HostWrapBinding) => void;
  onProbeNow?: () => void;
  probing?: boolean;
}

const HostWrapConfig: React.FC<HostWrapConfigProps> = ({
  apiMethod, apiUrl, value, onChange, onProbeNow, probing,
}) => {
  const patch = (p: Partial<HostWrapBinding>) => onChange({ ...value, ...p });

  const logHint = useMemo(() => {
    if (value.logMode === 'ERROR_ONLY') return '仅记录失败请求（推荐高流量）';
    if (value.logMode === 'SAMPLE') return '失败必记；成功按千分比采样';
    return '在「执行日志」开启时记录全部请求（不含 body）';
  }, [value.logMode]);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12, padding: '8px 0' }}>
      <Alert
        type="info"
        showIcon
        icon={<CloudServerOutlined />}
        message="同名包裹（WRAP）— 增强宿主原接口"
        description={
          <div>
            <div>请求仍由宿主 Controller 处理；Yu Flow 叠加计量 / 可选日志 / 可选入站防护。</div>
            <div style={{ marginTop: 6 }}>
              转发：<Text code>{apiMethod || 'GET'}</Text>{' '}
              <Text code>{apiUrl || '（请在顶栏选择宿主接口路径）'}</Text>
            </div>
          </div>
        }
      />

      <Form layout="vertical" style={{ maxWidth: 520 }}>
        <Form.Item label="执行日志策略" extra={logHint}>
          <Select
            value={value.logMode || 'ALL'}
            onChange={(logMode) => patch({ logMode })}
            options={[
              { value: 'ALL', label: '全量（需打开执行日志开关）' },
              { value: 'ERROR_ONLY', label: '仅失败' },
              { value: 'SAMPLE', label: '采样（失败必记）' },
            ]}
          />
        </Form.Item>
        {value.logMode === 'SAMPLE' && (
          <Form.Item label="成功请求采样千分比" extra="100 = 约 10%；1000 = 全量成功">
            <InputNumber
              min={0}
              max={1000}
              value={value.logSamplePermille ?? 100}
              onChange={(v) => patch({ logSamplePermille: Number(v) || 0 })}
              style={{ width: '100%' }}
            />
          </Form.Item>
        )}
        <Form.Item
          label="路由探活"
          extra="定时检查宿主 MVC 是否仍注册该 path（无 HTTP 回环，不触发业务逻辑）"
        >
          <Space>
            <Switch
              checked={!!value.probeEnabled}
              onChange={(probeEnabled) => patch({ probeEnabled })}
              checkedChildren="开"
              unCheckedChildren="关"
            />
            {onProbeNow && (
              <a onClick={onProbeNow} style={{ pointerEvents: probing ? 'none' : undefined }}>
                {probing ? '探测中…' : '立即探测'}
              </a>
            )}
          </Space>
        </Form.Item>
      </Form>
    </div>
  );
};

export default HostWrapConfig;
