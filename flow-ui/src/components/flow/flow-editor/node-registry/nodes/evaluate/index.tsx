// ============================================================================
// node-registry/nodes/evaluate.tsx
// Evaluate (表达式) 节点 —— 自包含注册模块
// ============================================================================

import React from 'react';
import { Select } from 'antd';
import type { NodeRegistration, PropertyEditorProps } from '../../types';
import { EvaluateNodeComponent, EVALUATE_LAYOUT } from './EvaluateNodeComponent';
import { PAYLOAD_PORT_ID, PAYLOAD_PORT_Y } from '../../shared/usePayloadEntryPort';
import {
    PropertyCodeField,
    PropertyField,
    PropertyHint,
    PropertySection,
} from '../../shared/PropertyPanel';
import type { DslPort } from '../../../types';

const LANGUAGE_OPTIONS = [
    { value: 'Aviator', label: 'Aviator' },
    { value: 'SpEL', label: 'SpEL' },
    { value: 'JavaScript', label: 'JavaScript' },
    { value: 'Python', label: 'Python (GraalPy)' },
    { value: 'Groovy', label: 'Java / Groovy' },
];

// ── 属性面板编辑器 ──
function EvaluateEditor({ data, onChange }: PropertyEditorProps) {
    const lang = data.language || 'JavaScript';

    return (
        <PropertySection title="表达式配置" tip="也可在画布节点内直接编辑表达式">
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
            {(lang === 'Python' || lang === 'python' || lang === 'py') && (
                <PropertyHint>
                    可选能力：需部署机安装 GraalPy。多行脚本请 return，或赋给 result；未安装时返回 null。
                </PropertyHint>
            )}
            {(lang === 'Groovy' || lang === 'groovy') && (
                <PropertyHint>
                    支持多行脚本，最后一个表达式作为结果；Spring Bean 仅限服务端白名单。
                </PropertyHint>
            )}
            <PropertyCodeField
                label="表达式"
                expressionLang={lang}
                value={data.expression || ''}
                onChange={(val) => onChange({ expression: val })}
                height="md"
                placeholder="表达式或脚本"
            />
        </PropertySection>
    );
}

const buildEvaluatePortItems = (ports: DslPort[]) => {
    const seen = new Set<string>();
    const items: any[] = [];
    for (const p of ports) {
        // in:var 由组件动态管理；禁止无 args 的 absolute 口落到 (0,0)
        if (seen.has(p.id) || p.id === 'in' || p.id.startsWith('in:var:')) continue;
        seen.add(p.id);
        const isOut = p.id === 'out' || p.id.startsWith('out');
        const item: any = {
            id: p.id,
            group: p.group || (isOut ? 'absolute-out-solid' : 'absolute-in-solid'),
        };
        if (p.id === 'out') {
            item.args = { x: EVALUATE_LAYOUT.width, y: EVALUATE_LAYOUT.outPortY, dx: 0 };
        } else if (p.id === PAYLOAD_PORT_ID) {
            item.args = { x: 0, y: PAYLOAD_PORT_Y, dx: 0 };
        } else {
            continue; // 未知口留给组件，避免幽灵端口
        }
        items.push(item);
    }
    if (!seen.has(PAYLOAD_PORT_ID)) {
        items.push({
            id: PAYLOAD_PORT_ID,
            group: 'absolute-in-solid',
            args: { x: 0, y: PAYLOAD_PORT_Y, dx: 0 },
        });
    }
    if (!seen.has('out')) {
        items.push({
            id: 'out',
            group: 'absolute-out-solid',
            args: { x: EVALUATE_LAYOUT.width, y: EVALUATE_LAYOUT.outPortY, dx: 0 },
        });
    }
    return items;
};

// ── 注册配置 ──
export const evaluateNodeRegistration: NodeRegistration = {
    type: 'evaluate',
    label: '表达式 (Evaluate)',
    category: '逻辑节点',
    color: '#1677ff',
    tagColor: 'blue',
    description:
        '用表达式计算并输出结果。\n\n' +
        '· 支持 JavaScript / Aviator / SpEL / Python / Groovy\n' +
        '· 上方变量行映射输入，表达式中直接使用变量名\n' +
        '· 结果从 Result 输出，下游用 $.本节点.out 读取',
    hasInputs: true,

    shape: {
        shapeName: 'flow-evaluate',
        kind: 'react',
        component: EvaluateNodeComponent,
        reactPorts: {
            items: [
                {
                    id: PAYLOAD_PORT_ID,
                    group: 'absolute-in-solid',
                    args: { x: 0, y: PAYLOAD_PORT_Y, dx: 0 },
                },
                {
                    id: 'out',
                    group: 'absolute-out-solid',
                    args: { x: EVALUATE_LAYOUT.width, y: EVALUATE_LAYOUT.outPortY, dx: 0 },
                },
            ],
        },
    },

    defaults: {
        ports: [
            { id: PAYLOAD_PORT_ID, group: 'absolute-in-solid' },
            { id: 'out' },
        ],
        data: { expression: '', language: 'JavaScript', inputs: {} },
        size: { width: EVALUATE_LAYOUT.width, height: EVALUATE_LAYOUT.totalHeight },
    },

    importConfig: {
        portMode: 'manual',
        buildPortItems: buildEvaluatePortItems,
    },

    buildLabel: (data) =>
        data.expression ? `= ${String(data.expression).substring(0, 25)}` : 'Evaluate',

    PropertyEditor: EvaluateEditor,
};
