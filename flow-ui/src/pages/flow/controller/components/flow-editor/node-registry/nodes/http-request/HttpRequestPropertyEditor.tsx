// ============================================================================
// HttpRequest 右侧高级配置（策略类，不重复编辑 URL/Body）
// ============================================================================

import React from 'react';
import { Input, InputNumber, Switch } from 'antd';
import type { PropertyEditorProps } from '../../types';
import {
    PropertyField,
    PropertyFieldRow,
    PropertyHint,
    PropertySection,
    PropertySwitchRow,
} from '../../shared/PropertyPanel';

export function HttpRequestPropertyEditor({ data, onChange }: PropertyEditorProps) {
    const timeout = typeof data.timeout === 'number' && data.timeout > 0 ? data.timeout : 30000;
    const retryCount = typeof data.retryCount === 'number' ? data.retryCount : 0;
    const retryIntervalMs =
        typeof data.retryIntervalMs === 'number' ? data.retryIntervalMs : 1000;

    return (
        <div>
            <PropertySection
                title="请求策略"
                tip="超时、重试等执行策略；请求 Method/URL/Body 请在画布节点内编辑"
            >
                <PropertyFieldRow>
                    <PropertyField
                        label="超时"
                        tip="连接/读写超时（毫秒）"
                        extra="ms"
                        labelWidth={40}
                    >
                        <InputNumber
                            size="small"
                            min={1000}
                            max={600000}
                            step={1000}
                            value={timeout}
                            style={{ width: '100%' }}
                            onChange={(val) => onChange({ timeout: val ?? 30000 })}
                        />
                    </PropertyField>
                    <PropertyField
                        label="重试"
                        tip="仅对网络异常 / 超时重试；业务 fail 不重试。0 = 不重试"
                        labelWidth={40}
                    >
                        <InputNumber
                            size="small"
                            min={0}
                            max={10}
                            value={retryCount}
                            style={{ width: '100%' }}
                            onChange={(val) => onChange({ retryCount: val ?? 0 })}
                        />
                    </PropertyField>
                </PropertyFieldRow>
                <PropertyField
                    label="重试间隔"
                    tip="两次尝试之间的等待时间"
                    extra="ms"
                    labelWidth={64}
                >
                    <InputNumber
                        size="small"
                        min={0}
                        max={60000}
                        step={500}
                        value={retryIntervalMs}
                        disabled={retryCount <= 0}
                        style={{ width: '100%' }}
                        onChange={(val) => onChange({ retryIntervalMs: val ?? 1000 })}
                    />
                </PropertyField>
            </PropertySection>

            <PropertySection title="安全与日志">
                <PropertySwitchRow
                    label="开启日志"
                    tip="关闭后该节点的第三方 HTTP 调用不写入三方日志"
                    control={
                        <Switch
                            size="small"
                            checked={data.logEnabled !== false}
                            onChange={(checked) => onChange({ logEnabled: checked })}
                        />
                    }
                />
                <PropertySwitchRow
                    label="忽略 SSL"
                    tip="自签名 / 内网 HTTPS 时开启。公网正式环境请关闭"
                    control={
                        <Switch
                            size="small"
                            checked={data.ignoreSsl !== false}
                            onChange={(checked) => onChange({ ignoreSsl: checked })}
                        />
                    }
                />
                <PropertyField
                    label="接口标识"
                    tip="写入三方日志的 apiType；留空则使用节点 ID"
                    labelWidth={64}
                >
                    <Input
                        size="small"
                        allowClear
                        placeholder="如 smart-drainage-auth"
                        value={data.apiType || ''}
                        onChange={(e) => onChange({ apiType: e.target.value })}
                    />
                </PropertyField>
                <PropertyHint>
                    成功条件仍在节点底部编辑，可引用 status / body / headers / timeMs。
                </PropertyHint>
            </PropertySection>
        </div>
    );
}

export default HttpRequestPropertyEditor;
