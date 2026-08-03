// ============================================================================
// HttpRequest 右侧高级配置（策略类，不重复编辑 URL/Body）
// ============================================================================

import React from 'react';
import { Input, InputNumber, Select, Switch } from 'antd';
import type { PropertyEditorProps } from '../../types';
import {
    PropertyField,
    PropertyFieldRow,
    PropertyHint,
    PropertySection,
    PropertySwitchRow,
} from '../../shared/PropertyPanel';

const AUTH_OPTIONS = [
    { value: 'none', label: '无鉴权' },
    { value: 'bearer', label: 'Bearer Token' },
    { value: 'basic', label: 'Basic Auth' },
    { value: 'apiKey', label: 'API Key' },
];

const API_KEY_IN_OPTIONS = [
    { value: 'header', label: 'Header' },
    { value: 'query', label: 'Query' },
];

export function HttpRequestPropertyEditor({ data, onChange }: PropertyEditorProps) {
    const timeout = typeof data.timeout === 'number' && data.timeout > 0 ? data.timeout : 30000;
    const retryCount = typeof data.retryCount === 'number' ? data.retryCount : 0;
    const retryIntervalMs =
        typeof data.retryIntervalMs === 'number' ? data.retryIntervalMs : 1000;
    const authType = (data.authType || 'none') as string;
    const apiKeyIn = (data.authApiKeyIn || 'header') as string;

    return (
        <div>
            <PropertySection
                title="鉴权"
                tip="写入请求的 Authorization / API Key；字段支持 ${变量名}（来自 inputs）。手动 Headers 中同名键优先"
            >
                <PropertyField label="类型" labelWidth={48}>
                    <Select
                        size="small"
                        value={authType}
                        options={AUTH_OPTIONS}
                        style={{ width: '100%' }}
                        onChange={(val) => onChange({ authType: val })}
                    />
                </PropertyField>
                {authType === 'bearer' && (
                    <PropertyField label="Token" tip="勿含 Bearer 前缀；可用 ${token}" labelWidth={48}>
                        <Input.Password
                            size="small"
                            allowClear
                            placeholder="access_token 或 ${token}"
                            value={data.authToken || ''}
                            onChange={(e) => onChange({ authToken: e.target.value })}
                        />
                    </PropertyField>
                )}
                {authType === 'basic' && (
                    <>
                        <PropertyField label="用户名" labelWidth={48}>
                            <Input
                                size="small"
                                allowClear
                                placeholder="username"
                                value={data.authUsername || ''}
                                onChange={(e) => onChange({ authUsername: e.target.value })}
                            />
                        </PropertyField>
                        <PropertyField label="密码" labelWidth={48}>
                            <Input.Password
                                size="small"
                                allowClear
                                placeholder="password"
                                value={data.authPassword || ''}
                                onChange={(e) => onChange({ authPassword: e.target.value })}
                            />
                        </PropertyField>
                    </>
                )}
                {authType === 'apiKey' && (
                    <>
                        <PropertyField label="位置" labelWidth={48}>
                            <Select
                                size="small"
                                value={apiKeyIn}
                                options={API_KEY_IN_OPTIONS}
                                style={{ width: '100%' }}
                                onChange={(val) => onChange({ authApiKeyIn: val })}
                            />
                        </PropertyField>
                        <PropertyField
                            label="名称"
                            tip={apiKeyIn === 'query' ? '默认 api_key' : '默认 X-API-Key'}
                            labelWidth={48}
                        >
                            <Input
                                size="small"
                                allowClear
                                placeholder={apiKeyIn === 'query' ? 'api_key' : 'X-API-Key'}
                                value={data.authApiKeyName || ''}
                                onChange={(e) => onChange({ authApiKeyName: e.target.value })}
                            />
                        </PropertyField>
                        <PropertyField label="值" tip="可用 ${apiKey}" labelWidth={48}>
                            <Input.Password
                                size="small"
                                allowClear
                                placeholder="key value"
                                value={data.authApiKeyValue || ''}
                                onChange={(e) => onChange({ authApiKeyValue: e.target.value })}
                            />
                        </PropertyField>
                    </>
                )}
                {authType !== 'none' && (
                    <PropertyHint>
                        Token / 密钥建议用 SystemVar 或上游节点映射到 inputs，再填 {'${varName}'}，避免明文落库。
                    </PropertyHint>
                )}
            </PropertySection>

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
                        tip="网络异常 / 超时 / 可选 5xx 重试；业务 fail 不重试。0 = 不重试"
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
                {retryCount > 0 && (
                    <PropertySwitchRow
                        label="5xx 时重试"
                        tip="HTTP 5xx 且未满足成功条件时，按网络异常同样重试"
                        control={
                            <Switch
                                size="small"
                                checked={!!data.retryOnServerError}
                                onChange={(checked) => onChange({ retryOnServerError: checked })}
                            />
                        }
                    />
                )}
            </PropertySection>

            <PropertySection title="安全与日志">
                <PropertySwitchRow
                    label="开启日志"
                    tip="关闭后该节点的第三方 HTTP 调用不写入三方日志"
                    control={
                        <Switch
                            size="small"
                            checked={!!data.logEnabled}
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
                            checked={!!data.ignoreSsl}
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
