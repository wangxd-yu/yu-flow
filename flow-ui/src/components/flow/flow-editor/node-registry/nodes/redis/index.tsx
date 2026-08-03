// ============================================================================
// redis/index.tsx — Redis 节点注册
// ============================================================================

import React from 'react';
import { Input, InputNumber, Select } from 'antd';
import type { DslPort } from '../../../types';
import type { NodeRegistration, PropertyEditorProps } from '../../types';
import { RedisNodeComponent, REDIS_LAYOUT, REDIS_COLOR } from './RedisNodeComponent';
import { PropertyField, PropertyHint, PropertySection } from '../../shared/PropertyPanel';

const OP_OPTIONS = [
    { value: 'get', label: 'GET' },
    { value: 'set', label: 'SET' },
    { value: 'del', label: 'DEL' },
    { value: 'incr', label: 'INCR' },
];

function RedisEditor({ data, onChange }: PropertyEditorProps) {
    const op = data.operation || 'get';
    return (
        <PropertySection title="Redis">
            <PropertyField label="操作">
                <Select
                    size="small"
                    value={op}
                    options={OP_OPTIONS}
                    style={{ width: '100%' }}
                    getPopupContainer={() => document.body}
                    onChange={(v) => onChange({ operation: v })}
                />
            </PropertyField>
            <PropertyField label="Key" tip="支持 ${var}">
                <Input
                    size="small"
                    value={data.key || ''}
                    placeholder="cache:user:${userId}"
                    onChange={(e) => onChange({ key: e.target.value })}
                />
            </PropertyField>
            {(op === 'set' || op === 'incr') && (
                <PropertyField label={op === 'incr' ? '增量' : 'Value'} tip="支持 ${var}">
                    <Input
                        size="small"
                        value={data.value || ''}
                        onChange={(e) => onChange({ value: e.target.value })}
                    />
                </PropertyField>
            )}
            {op === 'set' && (
                <PropertyField label="TTL" extra="秒">
                    <InputNumber
                        size="small"
                        min={0}
                        value={data.ttlSeconds}
                        style={{ width: '100%' }}
                        onChange={(v) => onChange({ ttlSeconds: v ?? undefined })}
                    />
                </PropertyField>
            )}
            <PropertyHint>结果写入 $.本节点.out</PropertyHint>
        </PropertySection>
    );
}

export const redisNodeRegistration: NodeRegistration = {
    type: 'redis',
    label: 'Redis',
    category: '数据节点',
    color: REDIS_COLOR,
    tagColor: 'red',
    description:
        'Redis 读写：GET / SET / DEL / INCR。\n\n' +
        '· key / value 支持 ${var}\n' +
        '· SET 可设 TTL（秒）\n' +
        '· 结果从 out 输出',
    sortOrder: 48,
    hasInputs: true,

    shape: {
        shapeName: 'flow-redis',
        kind: 'react',
        component: RedisNodeComponent,
        reactPorts: {
            items: [
                { id: 'in', group: 'absolute-in-solid', args: { x: 0, y: REDIS_LAYOUT.inPortY, dx: 0 } },
                { id: 'out', group: 'absolute-out-solid', args: { x: REDIS_LAYOUT.width, y: REDIS_LAYOUT.outPortY, dx: 0 } },
            ],
        },
    },

    defaults: {
        ports: [
            { id: 'in', group: 'absolute-in-solid' },
            { id: 'out', group: 'absolute-out-solid' },
        ],
        data: { operation: 'get', key: '', value: '', inputs: {} },
        size: { width: REDIS_LAYOUT.width, height: REDIS_LAYOUT.height },
    },

    importConfig: {
        portMode: 'manual',
        buildPortItems: (ports: DslPort[]) => {
            const w = REDIS_LAYOUT.width;
            const map: Record<string, { x: number; y: number; group: string }> = {
                in: { x: 0, y: REDIS_LAYOUT.inPortY, group: 'absolute-in-solid' },
                out: { x: w, y: REDIS_LAYOUT.outPortY, group: 'absolute-out-solid' },
            };
            return ports
                .filter((p) => map[p.id])
                .map((p) => ({
                    id: p.id,
                    group: map[p.id].group,
                    args: { x: map[p.id].x, y: map[p.id].y, dx: 0 },
                }));
        },
    },

    buildLabel: (data) => {
        const op = (data.operation || 'get').toUpperCase();
        const key = data.key ? String(data.key).slice(0, 16) : '';
        return key ? `Redis ${op} ${key}` : `Redis ${op}`;
    },

    PropertyEditor: RedisEditor,
};
