// ============================================================================
// node-registry/nodes/request.tsx
// Request (请求入口) 节点 —— 自包含注册模块
// V3.2: 新增 Method 选择 (GET/POST/PUT/DELETE/PATCH)
// ============================================================================

import React from 'react';
import { Divider, Input, Select, Typography } from 'antd';
import type { DslPort } from '../../../types';
import type { NodeRegistration, PropertyEditorProps } from '../../types';
import { RequestNodeComponent, REQUEST_LAYOUT, methodHasBody } from './RequestNodeComponent';

const { Text, Paragraph } = Typography;
const { TextArea } = Input;

const METHOD_OPTIONS = [
    { value: 'GET', label: 'GET' },
    { value: 'POST', label: 'POST' },
    { value: 'PUT', label: 'PUT' },
    { value: 'DELETE', label: 'DELETE' },
    { value: 'PATCH', label: 'PATCH' },
];

// ── 属性面板编辑器 (Start/Request 共用，导出供 start.ts 使用) ──
export function StartRequestEditor({ data, onChange }: PropertyEditorProps) {
    return (
        <div style={{ marginTop: 12 }}>
            <Divider orientation="left" style={{ fontSize: 12, margin: '8px 0' }}>
                入口节点
            </Divider>
            <div style={{ marginBottom: 12 }}>
                <Text type="secondary" style={{ fontSize: 12 }}>请求方法</Text>
                <Select
                    size="small"
                    value={data.method || 'GET'}
                    options={METHOD_OPTIONS}
                    style={{ width: '100%', marginTop: 4 }}
                    onChange={(val) => onChange({ method: val })}
                />
            </div>
            <Paragraph type="secondary" style={{ fontSize: 11 }}>
                Start / Request 节点为流程入口，运行时参数通过{' '}
                <code>$.start.args.*</code> 路径在下游节点中引用。
            </Paragraph>
            <div>
                <Text type="secondary" style={{ fontSize: 12 }}>
                    参数校验规则 (JSON, 可选)
                </Text>
                <TextArea
                    size="small"
                    value={JSON.stringify(data.validations || {}, null, 2)}
                    autoSize={{ minRows: 3, maxRows: 10 }}
                    placeholder={'{\n  "username": { "required": true, "message": "用户名不能为空" }\n}'}
                    style={{ marginTop: 4, fontFamily: 'monospace' }}
                    onChange={(e) => {
                        try { onChange({ validations: JSON.parse(e.target.value) }); } catch { /* wait */ }
                    }}
                />
            </div>
        </div>
    );
}

// ── 注册配置 ──
export const requestNodeRegistration: NodeRegistration = {
    type: 'request',
    label: '请求入口 (Request)',
    category: '基础节点',
    color: '#52c41a',
    tagColor: 'green',
    hasInputs: false,
    singleton: true,

    shape: {
        shapeName: 'flow-request',
        kind: 'react',
        component: RequestNodeComponent,
        reactPorts: {
            items: [
                { id: 'headers', group: 'absolute-out-solid', args: { x: REQUEST_LAYOUT.width, y: REQUEST_LAYOUT.rowCenterY(0), dx: 0 } },
                { id: 'params', group: 'absolute-out-solid', args: { x: REQUEST_LAYOUT.width, y: REQUEST_LAYOUT.rowCenterY(1), dx: 0 } },
                // body port is NOT included by default — added dynamically when method has body
            ],
        },
    },

    defaults: {
        ports: [{ id: 'headers' }, { id: 'params' }],
        data: { method: 'GET' },
        size: { width: REQUEST_LAYOUT.width, height: REQUEST_LAYOUT.totalHeight2 },
    },

    importConfig: {
        portMode: 'manual',
        buildPortItems: (ports: DslPort[]) => {
            const portIdOrder = ['headers', 'params', 'body'];
            const seenIds = new Set<string>();
            const items: any[] = [];
            for (const p of ports) {
                if (seenIds.has(p.id)) continue;
                seenIds.add(p.id);
                const idx = portIdOrder.indexOf(p.id);
                items.push({
                    id: p.id,
                    group: 'absolute-out-solid',
                    args: { x: REQUEST_LAYOUT.width, y: REQUEST_LAYOUT.rowCenterY(idx >= 0 ? idx : 0), dx: 0 },
                });
            }
            return items;
        },
        buildAttrs: () => ({
            body: { stroke: '#52c41a', strokeWidth: 2, fill: '#f6ffed' },
        }),
    },

    buildLabel: (data) => {
        const method = data.method || 'GET';
        return `Request [${method}]`;
    },

    PropertyEditor: StartRequestEditor,
};
