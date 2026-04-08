// ============================================================================
// node-registry/nodes/switch-node.tsx
// Switch (多路选择) 节点 —— 自包含注册模块
// ============================================================================

import React from 'react';
import { Divider, Input, Typography } from 'antd';
import { InfoCircleOutlined } from '@ant-design/icons';
import type { DslPort } from '../../types';
import type { NodeRegistration, PropertyEditorProps } from '../types';

const { Text, Paragraph } = Typography;

// ── 属性面板编辑器 ──
function SwitchEditor({ data, onChange }: PropertyEditorProps) {
    return (
        <div style={{ marginTop: 12 }}>
            <Divider orientation="left" style={{ fontSize: 12, margin: '8px 0' }}>
                Switch 配置
            </Divider>
            <div>
                <Text type="secondary" style={{ fontSize: 12 }}>匹配表达式</Text>
                <Input
                    size="small"
                    value={data.expression || ''}
                    placeholder="例如: r (变量名)"
                    style={{ marginTop: 4, fontFamily: 'monospace' }}
                    onChange={(e) => onChange({ expression: e.target.value })}
                />
            </div>
            <Paragraph type="secondary" style={{ fontSize: 11, marginTop: 8 }}>
                <InfoCircleOutlined /> Case 端口通过 Switch 节点上右键 → 添加端口管理。
                端口 ID 格式为 <code>case_VALUE</code>。
            </Paragraph>
        </div>
    );
}

// ── 注册配置 ──
export const switchNodeRegistration: NodeRegistration = {
    type: 'switch',
    label: '多路选择 (Switch)',
    category: '逻辑节点',
    color: '#722ed1',
    tagColor: 'purple',
    sortOrder: 80,
    hasInputs: true,

    shape: {
        shapeName: 'flow-switch',
        kind: 'svg',
        svgConfig: {
            width: 200,
            height: 80,
            markup: [
                { tagName: 'rect', selector: 'body' },
                { tagName: 'text', selector: 'label' },
            ],
            attrs: {
                body: {
                    rx: 12, ry: 12, stroke: '#722ed1', strokeWidth: 2,
                    fill: '#f9f0ff', refWidth: '100%', refHeight: '100%',
                },
                label: {
                    text: 'Switch', fill: '#1f1f1f', fontSize: 12,
                    textAnchor: 'middle', textVerticalAnchor: 'middle',
                    refX: 0.5, refY: 0.5,
                },
            },
            ports: {
                items: [
                    { id: 'in', group: 'in-solid' },
                    {
                        id: 'default', group: 'out-solid',
                        attrs: { text: { text: 'Default', fill: '#8c8c8c', fontSize: 10 } },
                    },
                ],
            },
        },
    },

    defaults: {
        ports: [{ id: 'in' }, { id: 'default' }],
        data: { expression: '', inputs: {} },
        size: { width: 200, height: 80 },
        dynamicSize: (ports: DslPort[]) => ({ width: 200, height: Math.max(80, 50 + ports.length * 28) }),
    },

    importConfig: {
        portMode: 'standard',
        buildAttrs: () => ({
            body: { stroke: '#722ed1', fill: '#f9f0ff', rx: 12, ry: 12, strokeWidth: 2 },
        }),
    },

    buildLabel: (data) =>
        data.expression ? `switch (${String(data.expression).substring(0, 20)})` : 'Switch',

    PropertyEditor: SwitchEditor,
};
