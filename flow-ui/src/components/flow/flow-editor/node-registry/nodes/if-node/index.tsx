// ============================================================================
// node-registry/nodes/if-node.tsx
// If (条件判断) 节点 —— 自包含注册模块
// ============================================================================

import React from 'react';
import { Select } from 'antd';
import type { DslPort } from '../../../types';
import type { NodeRegistration, PropertyEditorProps } from '../../types';
import { IfNodeComponent, IF_LAYOUT } from './IfNodeComponent';
import { PropertyCodeField, PropertyField, PropertySection } from '../../shared/PropertyPanel';

const LANGUAGE_OPTIONS = [
    { value: 'JavaScript', label: 'JavaScript (默认)' },
    { value: 'Aviator', label: 'Aviator' },
    { value: 'SpEL', label: 'SpEL' },
    { value: 'Python', label: 'Python (GraalPy)' },
    { value: 'Groovy', label: 'Java / Groovy' },
];

// ── 属性面板编辑器 ──
function IfEditor({ data, onChange }: PropertyEditorProps) {
    const lang = data.language || 'JavaScript';
    return (
        <PropertySection title="条件配置" tip="也可在画布节点内直接编辑">
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
                label="条件表达式"
                expressionLang={lang}
                value={data.condition || ''}
                onChange={(val) => onChange({ condition: val })}
                height="sm"
                placeholder="例如: age >= 18"
            />
        </PropertySection>
    );
}

// ── 注册配置 ──
export const ifNodeRegistration: NodeRegistration = {
    type: 'if',
    label: '条件判断 (If)',
    category: '逻辑节点',
    color: '#1677ff',
    tagColor: 'red',
    description:
        '根据条件表达式分支执行。\n\n' +
        '· 条件为真走 THEN，为假走 ELSE\n' +
        '· 变量行映射参与判断的数据\n' +
        '· 表达式语言可在标题栏切换',
    sortOrder: 90,
    hasInputs: true,

    shape: {
        shapeName: 'flow-if',
        kind: 'react',
        component: IfNodeComponent,
        reactPorts: {
            items: [
                { id: 'in', group: 'absolute-in-solid', args: { x: 0, y: IF_LAYOUT.inPortY, dx: 0 } },
                { id: 'true', group: 'absolute-out-solid', args: { x: IF_LAYOUT.width, y: IF_LAYOUT.truePortY, dx: 0 } },
                { id: 'false', group: 'absolute-out-hollow', args: { x: IF_LAYOUT.width, y: IF_LAYOUT.falsePortY, dx: 0 } },
            ],
        },
    },

    defaults: {
        ports: [{ id: 'in' }, { id: 'true' }, { id: 'false' }],
        data: { condition: '', language: 'JavaScript', inputs: {} },
        size: { width: IF_LAYOUT.width, height: IF_LAYOUT.totalHeight },
    },

    importConfig: {
        portMode: 'manual',
        buildPortItems: (ports: DslPort[]) => {
            const posMap: Record<string, { x: number; y: number; group: string }> = {
                in: { x: 0, y: IF_LAYOUT.inPortY, group: 'absolute-in-solid' },
                true: { x: IF_LAYOUT.width, y: IF_LAYOUT.truePortY, group: 'absolute-out-solid' },
                false: { x: IF_LAYOUT.width, y: IF_LAYOUT.falsePortY, group: 'absolute-out-hollow' },
            };
            const seenIds = new Set<string>();
            const items: any[] = [];
            for (const p of ports) {
                if (seenIds.has(p.id) || p.id.startsWith('in:var:')) continue;
                seenIds.add(p.id);
                const m = posMap[p.id];
                if (!m) continue; // 未知口留给组件，避免 (0,0) 幽灵端口
                items.push({
                    id: p.id,
                    group: m.group,
                    args: { x: m.x, y: m.y, dx: 0 },
                });
            }
            return items;
        },
        buildAttrs: () => ({
            body: { stroke: '#1677ff', strokeWidth: 2, fill: '#ffffff' },
        }),
    },

    buildLabel: (data) =>
        data.condition ? `if (${String(data.condition).substring(0, 20)})` : 'If',

    PropertyEditor: IfEditor,
};
