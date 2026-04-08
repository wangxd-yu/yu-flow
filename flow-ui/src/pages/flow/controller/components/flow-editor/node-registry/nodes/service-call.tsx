// ============================================================================
// node-registry/nodes/service-call.tsx
// ServiceCall (服务调用) 节点 —— 自包含注册模块
// ============================================================================

import React from 'react';
import { Divider, Input, Typography } from 'antd';
import type { NodeRegistration, PropertyEditorProps } from '../types';

const { Text } = Typography;
const { TextArea } = Input;

// ── 属性面板编辑器 ──
function ServiceCallEditor({ data, onChange }: PropertyEditorProps) {
    return (
        <div style={{ marginTop: 12 }}>
            <Divider orientation="left" style={{ fontSize: 12, margin: '8px 0' }}>
                服务调用配置
            </Divider>
            <div style={{ marginBottom: 8 }}>
                <Text type="secondary" style={{ fontSize: 12 }}>Service</Text>
                <Input
                    size="small"
                    value={data.service || ''}
                    placeholder="例如: testService"
                    style={{ marginTop: 4 }}
                    onChange={(e) => onChange({ service: e.target.value })}
                />
            </div>
            <div style={{ marginBottom: 8 }}>
                <Text type="secondary" style={{ fontSize: 12 }}>Method</Text>
                <Input
                    size="small"
                    value={data.method || ''}
                    placeholder="例如: greet"
                    style={{ marginTop: 4 }}
                    onChange={(e) => onChange({ method: e.target.value })}
                />
            </div>
            <div>
                <Text type="secondary" style={{ fontSize: 12 }}>Args (JSON 数组)</Text>
                <TextArea
                    size="small"
                    value={Array.isArray(data.args) ? JSON.stringify(data.args) : '[]'}
                    autoSize={{ minRows: 2 }}
                    placeholder='例如: ["n"]'
                    style={{ marginTop: 4, fontFamily: 'monospace' }}
                    onChange={(e) => {
                        try {
                            const parsed = JSON.parse(e.target.value);
                            if (Array.isArray(parsed)) onChange({ args: parsed });
                        } catch {
                            // ignore until valid JSON
                        }
                    }}
                />
            </div>
        </div>
    );
}

// ── 注册配置 ──
export const serviceCallNodeRegistration: NodeRegistration = {
    type: 'serviceCall',
    label: '服务调用 (ServiceCall)',
    category: '调用节点',
    color: '#13c2c2',
    tagColor: 'cyan',
    hasInputs: true,

    shape: {
        shapeName: 'flow-serviceCall',
        kind: 'svg',
        svgConfig: {
            width: 240,
            height: 60,
            markup: [
                { tagName: 'rect', selector: 'body' },
                { tagName: 'text', selector: 'label' },
            ],
            attrs: {
                body: {
                    rx: 8, ry: 8, stroke: '#13c2c2', strokeWidth: 2,
                    fill: '#ffffff', refWidth: '100%', refHeight: '100%',
                },
                label: {
                    text: 'ServiceCall', fill: '#1f1f1f', fontSize: 12,
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
        data: { service: '', method: '', args: [], inputs: {} },
        size: { width: 240, height: 60 },
    },

    importConfig: {
        portMode: 'standard',
    },

    buildLabel: (data) =>
        data.service && data.method
            ? `${data.service}.${data.method}()`
            : 'Service Call',

    PropertyEditor: ServiceCallEditor,
};
