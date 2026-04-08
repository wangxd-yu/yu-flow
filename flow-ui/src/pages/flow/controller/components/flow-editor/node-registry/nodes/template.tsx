// ============================================================================
// node-registry/nodes/template.tsx
// Template (模板) 节点 —— 自包含注册模块
// ============================================================================

import React from 'react';
import { Divider, Input, Typography } from 'antd';
import type { NodeRegistration, PropertyEditorProps } from '../types';

const { Text } = Typography;
const { TextArea } = Input;

// ── 属性面板编辑器 ──
function TemplateEditor({ data, onChange }: PropertyEditorProps) {
    return (
        <div style={{ marginTop: 12 }}>
            <Divider orientation="left" style={{ fontSize: 12, margin: '8px 0' }}>
                模板配置
            </Divider>
            <div>
                <Text type="secondary" style={{ fontSize: 12 }}>
                    Template (使用 {'{{key}}'} 占位符)
                </Text>
                <TextArea
                    size="small"
                    value={data.template || ''}
                    autoSize={{ minRows: 3, maxRows: 10 }}
                    placeholder="User: {{name}}, Score: {{score}}"
                    style={{ marginTop: 4, fontFamily: 'monospace' }}
                    onChange={(e) => onChange({ template: e.target.value })}
                />
            </div>
        </div>
    );
}

// ── 注册配置 ──
export const templateNodeRegistration: NodeRegistration = {
    type: 'template',
    label: '模板 (Template)',
    category: '数据节点',
    color: '#9254de',
    tagColor: 'purple',
    hasInputs: true,

    shape: {
        shapeName: 'flow-template',
        kind: 'svg',
        svgConfig: {
            width: 200,
            height: 60,
            markup: [
                { tagName: 'rect', selector: 'body' },
                { tagName: 'text', selector: 'label' },
            ],
            attrs: {
                body: {
                    rx: 8, ry: 8, stroke: '#9254de', strokeWidth: 2,
                    fill: '#ffffff', refWidth: '100%', refHeight: '100%',
                },
                label: {
                    text: 'Template', fill: '#1f1f1f', fontSize: 12,
                    textAnchor: 'middle', textVerticalAnchor: 'middle',
                    refX: 0.5, refY: 0.5,
                },
            },
            ports: {
                items: [
                    { id: 'in', group: 'in-solid' },
                    { id: 'out', group: 'out-solid' },
                ],
            },
        },
    },

    defaults: {
        ports: [{ id: 'in' }, { id: 'out' }],
        data: { template: '', inputs: {} },
        size: { width: 200, height: 60 },
    },

    importConfig: { portMode: 'standard' },

    buildLabel: () => 'Template',

    PropertyEditor: TemplateEditor,
};
