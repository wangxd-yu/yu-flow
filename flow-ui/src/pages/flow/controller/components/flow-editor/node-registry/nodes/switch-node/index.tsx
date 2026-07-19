// ============================================================================
// switch-node/index.tsx
// DSL type = switch；UI 名 Switch。分支 {id,name,value}，出口 case_<id>
// ============================================================================

import React from 'react';
import { Button, Input, Select } from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import type { DslPort } from '../../../types';
import type { NodeRegistration, PropertyEditorProps } from '../../types';
import { SwitchNodeComponent, SWITCH_LAYOUT } from './SwitchNodeComponent';
import { PAYLOAD_PORT_ID, PAYLOAD_PORT_Y } from '../../shared/usePayloadEntryPort';
import {
    PROPERTY_THEME,
    PropertyCodeField,
    PropertyField,
    PropertyHint,
    PropertySection,
} from '../../shared/PropertyPanel';
import { casePortId, createEmptyCase, normalizeCases, type SwitchCaseItem } from './switchCases';

const LANGUAGE_OPTIONS = [
    { value: 'JavaScript', label: 'JavaScript (默认)' },
    { value: 'Aviator', label: 'Aviator' },
    { value: 'SpEL', label: 'SpEL' },
    { value: 'Python', label: 'Python (GraalPy)' },
    { value: 'Groovy', label: 'Java / Groovy' },
];

function SwitchEditor({ data, onChange }: PropertyEditorProps) {
    const cases = normalizeCases(data.cases);
    const lang = data.language || 'JavaScript';
    const updateCases = (next: SwitchCaseItem[]) => onChange({ cases: next });

    return (
        <PropertySection title="Switch" tip="多路值匹配；出口 case_&lt;id&gt;">
            <PropertyHint>
                每条分支有稳定出口 <code>case_&lt;id&gt;</code>。名称可改；匹配值与上方表达式结果比较。
                左上角 <code>in:payload</code> 为数据总入口，无需额外控制流 in。
            </PropertyHint>
            <PropertyField label="表达式语言">
                <Select
                    size="small"
                    value={lang}
                    options={LANGUAGE_OPTIONS}
                    style={{ width: '100%' }}
                    getPopupContainer={() => document.body}
                    onChange={(val) => onChange({ language: val })}
                />
            </PropertyField>
            <PropertyCodeField
                label="匹配表达式"
                expressionLang={lang}
                value={data.expression || ''}
                onChange={(val) => onChange({ expression: val })}
                height="sm"
                placeholder="例如: status 或 r"
            />
            <PropertyField label="Cases" layout="vertical">
                {cases.map((c, idx) => (
                    <div
                        key={c.id}
                        style={{
                            marginBottom: 8,
                            padding: 8,
                            border: `1px solid ${PROPERTY_THEME.border}`,
                            borderRadius: 6,
                            background: PROPERTY_THEME.cardBg,
                        }}
                    >
                        <Input
                            size="small"
                            value={c.name}
                            placeholder={`Case ${idx + 1}`}
                            style={{
                                marginBottom: 6,
                                fontWeight: 600,
                                height: PROPERTY_THEME.controlHeight,
                            }}
                            onChange={(e) => {
                                const next = [...cases];
                                next[idx] = { ...c, name: e.target.value };
                                updateCases(next);
                            }}
                        />
                        <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                            <Input
                                size="small"
                                value={c.value}
                                placeholder="匹配值"
                                style={{
                                    flex: 1,
                                    fontFamily: PROPERTY_THEME.mono,
                                    height: PROPERTY_THEME.controlHeight,
                                }}
                                onChange={(e) => {
                                    const next = [...cases];
                                    next[idx] = { ...c, value: e.target.value };
                                    updateCases(next);
                                }}
                            />
                            <Button
                                type="text"
                                size="small"
                                danger
                                icon={<DeleteOutlined />}
                                onClick={() => updateCases(cases.filter((_, i) => i !== idx))}
                                style={{
                                    width: PROPERTY_THEME.controlHeight,
                                    height: PROPERTY_THEME.controlHeight,
                                }}
                            />
                        </div>
                    </div>
                ))}
                <Button
                    type="dashed"
                    size="small"
                    block
                    icon={<PlusOutlined />}
                    onClick={() => updateCases([...cases, createEmptyCase(cases.length)])}
                >
                    Add case
                </Button>
            </PropertyField>
        </PropertySection>
    );
}

function listCaseItems(ports: DslPort[], dataCases?: unknown): SwitchCaseItem[] {
    const fromData = normalizeCases(dataCases);
    if (fromData.length > 0) return fromData;
    return ports
        .filter((p) => p.id.startsWith('case_'))
        .map((p, i) => ({
            id: p.id.substring(5) || `c${i}`,
            name: `Case ${i + 1}`,
            value: '',
        }));
}

const buildSwitchPortItems = (ports: DslPort[]) => {
    const seen = new Set<string>();
    const casePorts = ports.filter((p) => p.id.startsWith('case_'));
    const caseCount = Math.max(casePorts.length, 1);
    const totalHeight = SWITCH_LAYOUT.totalHeight(0, caseCount);
    const items: any[] = [];

    for (const p of ports) {
        if (seen.has(p.id) || p.id.startsWith('in:var:') || p.id === 'in') continue;
        seen.add(p.id);

        if (p.id === 'default') {
            items.push({
                id: 'default',
                group: 'absolute-out-solid',
                args: { x: SWITCH_LAYOUT.width, y: SWITCH_LAYOUT.defaultPortY(totalHeight, caseCount), dx: 0 },
            });
        } else if (p.id === PAYLOAD_PORT_ID) {
            items.push({
                id: PAYLOAD_PORT_ID,
                group: 'absolute-in-solid',
                args: { x: 0, y: PAYLOAD_PORT_Y, dx: 0 },
            });
        } else if (p.id.startsWith('case_')) {
            const idx = casePorts.findIndex((x) => x.id === p.id);
            items.push({
                id: p.id,
                group: 'absolute-out-solid',
                args: {
                    x: SWITCH_LAYOUT.width,
                    y: SWITCH_LAYOUT.casePortY(totalHeight, Math.max(idx, 0), caseCount),
                    dx: 0,
                },
            });
        }
    }

    if (!seen.has(PAYLOAD_PORT_ID)) {
        items.push({
            id: PAYLOAD_PORT_ID,
            group: 'absolute-in-solid',
            args: { x: 0, y: PAYLOAD_PORT_Y, dx: 0 },
        });
    }
    if (!seen.has('default')) {
        items.push({
            id: 'default',
            group: 'absolute-out-solid',
            args: { x: SWITCH_LAYOUT.width, y: SWITCH_LAYOUT.defaultPortY(totalHeight, caseCount), dx: 0 },
        });
    }

    return items;
};

export const switchNodeRegistration: NodeRegistration = {
    type: 'switch',
    label: 'Switch',
    category: '逻辑节点',
    color: '#722ed1',
    tagColor: 'purple',
    description:
        '多路值匹配分支（type=switch）。\n\n' +
        '· 每条 Case 可改名称，右侧均有出口 case_<id>\n' +
        '· 上方表达式求值后与匹配值比较，未命中走 Default\n' +
        '· 仅左上角 in:payload',
    sortOrder: 80,
    hasInputs: true,

    shape: {
        shapeName: 'flow-switch',
        kind: 'react',
        component: SwitchNodeComponent,
        reactPorts: {
            items: [
                {
                    id: PAYLOAD_PORT_ID,
                    group: 'absolute-in-solid',
                    args: { x: 0, y: PAYLOAD_PORT_Y, dx: 0 },
                },
                {
                    id: 'default',
                    group: 'absolute-out-solid',
                    args: {
                        x: SWITCH_LAYOUT.width,
                        y: SWITCH_LAYOUT.defaultPortY(SWITCH_LAYOUT.totalHeight(0, 1), 1),
                        dx: 0,
                    },
                },
            ],
        },
    },

    defaults: {
        ports: [
            { id: PAYLOAD_PORT_ID, group: 'absolute-in-solid' },
            { id: 'default' },
        ],
        data: {
            expression: '',
            language: 'JavaScript',
            cases: [createEmptyCase(0)],
            inputs: {},
            themeColor: 'purple',
        },
        size: { width: SWITCH_LAYOUT.width, height: SWITCH_LAYOUT.totalHeight(0, 1) },
        dynamicSize: (ports: DslPort[]) => {
            const caseCount = Math.max(ports.filter((p) => p.id.startsWith('case_')).length, 1);
            return {
                width: SWITCH_LAYOUT.width,
                height: SWITCH_LAYOUT.totalHeight(0, caseCount),
            };
        },
    },

    importConfig: {
        portMode: 'manual',
        buildPortItems: buildSwitchPortItems,
        buildAttrs: () => ({
            body: { stroke: '#722ed1', strokeWidth: 2, fill: '#ffffff' },
        }),
    },

    buildLabel: (data) =>
        data.expression ? `switch (${String(data.expression).substring(0, 18)})` : 'Switch',

    PropertyEditor: SwitchEditor,
};

// 供 adapter 导入 DSL 时预建出口
export function switchCasePortsFromData(cases: unknown): DslPort[] {
    return normalizeCases(cases).map((c) => ({
        id: casePortId(c.id),
        group: 'absolute-out-solid' as const,
    }));
}

export { listCaseItems };
