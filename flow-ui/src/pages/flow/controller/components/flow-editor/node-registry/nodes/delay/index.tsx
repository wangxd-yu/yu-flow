// ============================================================================
// delay/index.tsx — Delay / Wait 节点注册
// ============================================================================

import React from 'react';
import { InputNumber } from 'antd';
import type { DslPort } from '../../../types';
import type { NodeRegistration, PropertyEditorProps } from '../../types';
import { DelayNodeComponent, DELAY_LAYOUT, DELAY_COLOR } from './DelayNodeComponent';
import { PropertyField, PropertyHint, PropertySection } from '../../shared/PropertyPanel';

function DelayEditor({ data, onChange }: PropertyEditorProps) {
    return (
        <PropertySection title="延迟配置">
            <PropertyHint>
                阻塞当前分支指定毫秒后继续。也可通过 inputs.delayMs 动态覆盖。
            </PropertyHint>
            <PropertyField label="等待时长" extra="ms">
                <InputNumber
                    size="small"
                    min={0}
                    max={3600000}
                    step={100}
                    value={data.delayMs ?? 1000}
                    style={{ width: '100%' }}
                    onChange={(val) => onChange({ delayMs: val ?? 0 })}
                />
            </PropertyField>
        </PropertySection>
    );
}

export const delayNodeRegistration: NodeRegistration = {
    type: 'delay',
    label: '延迟 (Delay)',
    category: '逻辑节点',
    color: DELAY_COLOR,
    tagColor: 'cyan',
    description:
        '等待指定毫秒后再继续执行。\n\n' +
        '· 用于限流间隔、轮询间隔、重试退避\n' +
        '· 可在画布或属性面板设置 delayMs\n' +
        '· 演示模式有最大等待上限',
    sortOrder: 60,
    hasInputs: true,

    shape: {
        shapeName: 'flow-delay',
        kind: 'react',
        component: DelayNodeComponent,
        reactPorts: {
            items: [
                { id: 'in', group: 'absolute-in-solid', args: { x: 0, y: DELAY_LAYOUT.inPortY, dx: 0 } },
                { id: 'out', group: 'absolute-out-solid', args: { x: DELAY_LAYOUT.width, y: DELAY_LAYOUT.outPortY, dx: 0 } },
            ],
        },
    },

    defaults: {
        ports: [
            { id: 'in', group: 'absolute-in-solid' },
            { id: 'out', group: 'absolute-out-solid' },
        ],
        data: { delayMs: 1000, inputs: {} },
        size: { width: DELAY_LAYOUT.width, height: DELAY_LAYOUT.height },
    },

    importConfig: {
        portMode: 'manual',
        buildPortItems: (ports: DslPort[]) => {
            const w = DELAY_LAYOUT.width;
            const map: Record<string, { x: number; y: number; group: string }> = {
                in: { x: 0, y: DELAY_LAYOUT.inPortY, group: 'absolute-in-solid' },
                out: { x: w, y: DELAY_LAYOUT.outPortY, group: 'absolute-out-solid' },
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

    buildLabel: (data) =>
        data.delayMs != null ? `Delay ${data.delayMs}ms` : 'Delay',

    PropertyEditor: DelayEditor,
};
