// ============================================================================
// node-registry/nodes/if-node.tsx
// If (条件判断) 节点 —— 自包含注册模块
// ============================================================================

import React from 'react';
import { Divider, Input, Select, Typography } from 'antd';
import type { DslPort } from '../../../types';
import type { NodeRegistration, PropertyEditorProps } from '../../types';
import { IfNodeComponent, IF_LAYOUT } from './IfNodeComponent';

const { Text } = Typography;
const { TextArea } = Input;

const LANGUAGE_OPTIONS = [
    { value: 'JavaScript', label: 'JavaScript (默认)' },
    { value: 'Aviator', label: 'Aviator' },
    { value: 'SpEL', label: 'SpEL' },
];

const editorLanguage = (lang: string) => (lang === 'JavaScript' || lang === 'js') ? 'javascript' : 'text';

// ── 属性面板编辑器 ──
function IfEditor({ data, onChange }: PropertyEditorProps) {
    return (
        <div style={{ marginTop: 12 }}>
            <Divider orientation="left" style={{ fontSize: 12, margin: '8px 0' }}>
                条件配置
            </Divider>
            <div style={{ marginBottom: 12 }}>
                <Text type="secondary" style={{ fontSize: 12 }}>表达式语言</Text>
                <Select
                    size="small"
                    value={data.language || 'JavaScript'}
                    options={LANGUAGE_OPTIONS}
                    style={{ width: '100%', marginTop: 4 }}
                    onChange={(val) => onChange({ language: val })}
                />
            </div>
            <div>
                <Text type="secondary" style={{ fontSize: 12 }}>条件表达式</Text>
                <TextArea
                    size="small"
                    value={data.condition || ''}
                    autoSize={{ minRows: 2, maxRows: 6 }}
                    placeholder="例如: age >= 18"
                    style={{ marginTop: 4, fontFamily: 'monospace' }}
                    onChange={(e) => onChange({ condition: e.target.value })}
                />
            </div>
        </div>
    );
}

// ── 注册配置 ──
export const ifNodeRegistration: NodeRegistration = {
    type: 'if',
    label: '条件判断 (If)',
    category: '逻辑节点',
    color: '#1677ff',
    tagColor: 'red',
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
            const posMap: Record<string, { x: number; y: number }> = {
                in: { x: 0, y: IF_LAYOUT.inPortY },
                true: { x: IF_LAYOUT.width, y: IF_LAYOUT.truePortY },
                false: { x: IF_LAYOUT.width, y: IF_LAYOUT.falsePortY },
            };
            const seenIds = new Set<string>();
            const items: any[] = [];
            for (const p of ports) {
                if (seenIds.has(p.id)) continue;
                seenIds.add(p.id);
                items.push({
                    id: p.id,
                    group: p.id === 'in' ? 'absolute-in-solid' : p.id === 'false' ? 'absolute-out-hollow' : 'absolute-out-solid',
                    args: { ...(posMap[p.id] || { x: 0, y: 0 }), dx: 0 },
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
