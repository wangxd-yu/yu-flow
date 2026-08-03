// ============================================================================
// oss/index.tsx — OSS 对象存储节点注册
// ============================================================================

import React from 'react';
import { Input, InputNumber, Select } from 'antd';
import type { DslPort } from '../../../types';
import type { NodeRegistration, PropertyEditorProps } from '../../types';
import { OssNodeComponent, OSS_LAYOUT, OSS_COLOR } from './OssNodeComponent';
import { PropertyField, PropertyHint, PropertySection } from '../../shared/PropertyPanel';
import { PAYLOAD_PORT_ID, PAYLOAD_PORT_Y } from '../../shared/usePayloadEntryPort';
import { NODE_FOOTER_HEIGHT, NODE_FOOTER_PORT_OFFSET_Y } from '../../shared/useNodeSelection';
import { queryOssConnectionOptions } from '@/services/flow/ossConnection';

const OPERATIONS = [
    { label: 'put — 上传', value: 'put' },
    { label: 'get — 下载', value: 'get' },
    { label: 'delete — 删除', value: 'delete' },
    { label: 'list — 列举', value: 'list' },
    { label: 'presignGet — 预签名', value: 'presignGet' },
];

function OssEditor({ data, onChange }: PropertyEditorProps) {
    const [connOptions, setConnOptions] = React.useState<{ label: string; value: string }[]>([]);
    React.useEffect(() => {
        queryOssConnectionOptions()
            .then((list: any[]) => {
                setConnOptions(
                    (list || []).map((c: any) => ({
                        label: `${c.name} (${c.code})`,
                        value: c.code,
                    })),
                );
            })
            .catch(() => setConnOptions([]));
    }, []);

    const op = data.operation || 'put';

    return (
        <PropertySection title="OSS 配置">
            <PropertyHint>
                引用「OSS 连接管理」中的 connectionCode。put/get/delete/presignGet 需 objectKey；get 输出
                bodyBase64（上限 5MB）。失败走 fail 软退出。
            </PropertyHint>
            <PropertyField label="操作 operation">
                <Select
                    size="small"
                    style={{ width: '100%' }}
                    value={op}
                    options={OPERATIONS}
                    onChange={(v) => onChange({ operation: v })}
                />
            </PropertyField>
            <PropertyField label="OSS 连接">
                <Select
                    size="small"
                    style={{ width: '100%' }}
                    value={data.connectionCode || undefined}
                    options={connOptions}
                    allowClear
                    showSearch
                    optionFilterProp="label"
                    getPopupContainer={() => document.body}
                    onChange={(v) => onChange({ connectionCode: v || '' })}
                />
            </PropertyField>
            <PropertyField label="bucket">
                <Input
                    size="small"
                    value={data.bucket || ''}
                    placeholder="空则用连接默认桶"
                    onChange={(e) => onChange({ bucket: e.target.value })}
                />
            </PropertyField>
            {op !== 'list' && (
                <PropertyField label="objectKey">
                    <Input
                        size="small"
                        value={data.objectKey || ''}
                        placeholder="${objectKeyVar}"
                        onChange={(e) => onChange({ objectKey: e.target.value })}
                    />
                </PropertyField>
            )}
            {op === 'put' && (
                <>
                    <PropertyField label="contentType">
                        <Input
                            size="small"
                            value={data.contentType || ''}
                            placeholder="application/octet-stream"
                            onChange={(e) => onChange({ contentType: e.target.value })}
                        />
                    </PropertyField>
                    <PropertyField label="localBytesVar" extra="上下文变量名（byte[]/Base64/字符串）">
                        <Input
                            size="small"
                            value={data.localBytesVar || ''}
                            onChange={(e) => onChange({ localBytesVar: e.target.value })}
                        />
                    </PropertyField>
                </>
            )}
            {op === 'list' && (
                <>
                    <PropertyField label="listPrefix">
                        <Input
                            size="small"
                            value={data.listPrefix || ''}
                            onChange={(e) => onChange({ listPrefix: e.target.value })}
                        />
                    </PropertyField>
                    <PropertyField label="listMaxKeys">
                        <InputNumber
                            size="small"
                            style={{ width: '100%' }}
                            min={1}
                            max={1000}
                            value={data.listMaxKeys ?? 100}
                            onChange={(v) => onChange({ listMaxKeys: v ?? 100 })}
                        />
                    </PropertyField>
                </>
            )}
            {op === 'presignGet' && (
                <PropertyField label="presignExpireSeconds">
                    <InputNumber
                        size="small"
                        style={{ width: '100%' }}
                        min={1}
                        value={data.presignExpireSeconds ?? 300}
                        onChange={(v) => onChange({ presignExpireSeconds: v ?? 300 })}
                    />
                </PropertyField>
            )}
        </PropertySection>
    );
}

const buildOssPortItems = (ports: DslPort[]) => {
    const w = OSS_LAYOUT.width;
    const seen = new Set<string>();
    const items: any[] = [];
    for (const p of ports) {
        if (seen.has(p.id) || p.id === 'in' || p.id.startsWith('in:var:')) continue;
        seen.add(p.id);
        if (p.id === PAYLOAD_PORT_ID) {
            items.push({
                id: PAYLOAD_PORT_ID,
                group: 'absolute-in-solid',
                args: { x: 0, y: PAYLOAD_PORT_Y, dx: 0 },
            });
        } else if (p.id === 'success' || p.id === 'out') {
            items.push({
                id: 'success',
                group: 'absolute-out-solid',
                args: { x: w, y: OSS_LAYOUT.successPortY(OSS_LAYOUT.height), dx: 0 },
            });
        } else if (p.id === 'fail') {
            items.push({
                id: 'fail',
                group: 'absolute-out-hollow',
                args: { x: w, y: OSS_LAYOUT.failPortY(OSS_LAYOUT.height), dx: 0 },
            });
        }
    }
    if (!seen.has(PAYLOAD_PORT_ID)) {
        items.unshift({
            id: PAYLOAD_PORT_ID,
            group: 'absolute-in-solid',
            args: { x: 0, y: PAYLOAD_PORT_Y, dx: 0 },
        });
    }
    if (!seen.has('success')) {
        items.push({
            id: 'success',
            group: 'absolute-out-solid',
            args: { x: w, y: OSS_LAYOUT.successPortY(OSS_LAYOUT.height), dx: 0 },
        });
        items.push({
            id: 'fail',
            group: 'absolute-out-hollow',
            args: { x: w, y: OSS_LAYOUT.failPortY(OSS_LAYOUT.height), dx: 0 },
        });
    }
    return items;
};

export const ossNodeRegistration: NodeRegistration = {
    type: 'oss',
    label: '对象存储 (OSS)',
    category: '调用节点',
    color: OSS_COLOR,
    tagColor: 'purple',
    description:
        'MinIO/S3 兼容对象存储 put/get/delete/list/presignGet。\n\n' +
        '· 引用 OSS 连接 connectionCode\n' +
        '· 成功 success / 失败 fail',
    sortOrder: 47,
    hasInputs: true,
    shape: {
        shapeName: 'flow-oss',
        kind: 'react',
        component: OssNodeComponent,
        reactPorts: {
            items: [
                {
                    id: PAYLOAD_PORT_ID,
                    group: 'absolute-in-solid',
                    args: { x: 0, y: PAYLOAD_PORT_Y, dx: 0 },
                },
                {
                    id: 'success',
                    group: 'absolute-out-solid',
                    args: { x: OSS_LAYOUT.width, y: OSS_LAYOUT.successPortY(OSS_LAYOUT.height), dx: 0 },
                },
                {
                    id: 'fail',
                    group: 'absolute-out-hollow',
                    args: { x: OSS_LAYOUT.width, y: OSS_LAYOUT.failPortY(OSS_LAYOUT.height), dx: 0 },
                },
            ],
        },
    },
    defaults: {
        ports: [
            { id: PAYLOAD_PORT_ID, group: 'absolute-in-solid' },
            { id: 'success', group: 'absolute-out-solid' },
            { id: 'fail', group: 'absolute-out-hollow' },
        ],
        data: {
            operation: 'put',
            connectionCode: '',
            bucket: '',
            objectKey: '',
            contentType: '',
            localBytesVar: '',
            listPrefix: '',
            listMaxKeys: 100,
            presignExpireSeconds: 300,
            inputs: {},
        },
        size: { width: OSS_LAYOUT.width, height: OSS_LAYOUT.height },
    },
    importConfig: {
        portMode: 'manual',
        buildPortItems: buildOssPortItems,
    },
    buildLabel: (data) => {
        const op = data.operation || 'put';
        const key = data.objectKey ? String(data.objectKey).slice(0, 18) : '';
        return key ? `OSS ${op}: ${key}` : `OSS ${op}`;
    },
    PropertyEditor: OssEditor,
};

export default ossNodeRegistration;
