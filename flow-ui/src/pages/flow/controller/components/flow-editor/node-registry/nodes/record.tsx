// ============================================================================
// node-registry/nodes/record.tsx
// Record (数据构造) 节点 —— 自包含注册模块
// ============================================================================

import React from 'react';
import { Divider, Input, Typography } from 'antd';
import type { NodeRegistration, PropertyEditorProps } from '../types';

const { Text } = Typography;
const { TextArea } = Input;

// ── 属性面板编辑器 ──
function RecordEditor({ data, onChange }: PropertyEditorProps) {
    return (
        <div style={{ marginTop: 12 }}>
            <Divider orientation="left" style={{ fontSize: 12, margin: '8px 0' }}>
                数据构造配置
            </Divider>
            <div>
                <Text type="secondary" style={{ fontSize: 12 }}>Schema (JSON)</Text>
                <TextArea
                    size="small"
                    value={JSON.stringify(data.schema || {}, null, 2)}
                    autoSize={{ minRows: 3, maxRows: 10 }}
                    placeholder={'{\n  "fieldName": "$[\'nodeId\'][\'result\']"\n}'}
                    style={{ marginTop: 4, fontFamily: 'monospace' }}
                    onChange={(e) => {
                        try {
                            onChange({ schema: JSON.parse(e.target.value) });
                        } catch { /* wait */ }
                    }}
                />
            </div>
        </div>
    );
}

// ── 注册配置 ──
export const recordNodeRegistration: NodeRegistration = {
    type: 'record',
    label: '数据构造 (Record)',
    category: '数据节点',
    color: '#2f54eb',
    tagColor: 'geekblue',
    hasInputs: true,

    shape: {
        shapeName: 'flow-record',
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
                    rx: 8, ry: 8, stroke: '#2f54eb', strokeWidth: 2,
                    fill: '#ffffff', refWidth: '100%', refHeight: '100%',
                },
                label: {
                    text: 'Record', fill: '#1f1f1f', fontSize: 12,
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
        data: { schema: {}, inputs: {} },
        size: { width: 200, height: 60 },
    },

    importConfig: { portMode: 'standard' },

    buildLabel: () => 'Record',

    PropertyEditor: RecordEditor,
};
