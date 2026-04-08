// ============================================================================
// node-registry/nodes/response/index.tsx
// Response (HTTP 响应 / 终止) 节点 —— 自包含注册模块
// kind: 'react'，使用 ResponseNodeComponent 渲染
// Headers 使用 useNodeVariables 动态端口，Body 使用固定端口
// ============================================================================

import React from 'react';
import { Divider, Input, InputNumber, Typography } from 'antd';
import type { NodeRegistration, PropertyEditorProps } from '../../types';
import { ResponseNodeComponent } from './ResponseNodeComponent';

const { Text } = Typography;
const { TextArea } = Input;

const RESPONSE_COLOR = '#FF6B35';

// ── 属性面板编辑器 ──
function ResponseEditor({ data, onChange }: PropertyEditorProps) {
    return React.createElement('div', { style: { marginTop: 12 } },
        React.createElement(Divider, { orientation: 'left', style: { fontSize: 12, margin: '8px 0' } }, 'HTTP Response 配置'),
        React.createElement('div', { style: { marginBottom: 8 } },
            React.createElement(Text, { type: 'secondary', style: { fontSize: 12 } }, 'Status Code'),
            React.createElement(InputNumber, {
                size: 'small',
                value: data.status || 200,
                min: 100,
                max: 599,
                style: { width: '100%', marginTop: 4 },
                onChange: (val: any) => onChange({ status: val }),
            }),
        ),
        React.createElement('div', { style: { marginBottom: 8 } },
            React.createElement(Text, { type: 'secondary', style: { fontSize: 12 } }, 'Headers'),
            React.createElement(Text, {
                type: 'secondary',
                style: { fontSize: 11, display: 'block', marginTop: 4, color: '#8c8c8c' },
            }, 'Headers 通过节点卡片上的变量行管理。连接即可自动添加新变量。'),
        ),
        React.createElement('div', null,
            React.createElement(Text, { type: 'secondary', style: { fontSize: 12 } }, 'Body (JSON / 表达式)'),
            React.createElement(TextArea, {
                size: 'small',
                value: typeof data.body === 'object' ? JSON.stringify(data.body, null, 2) : (data.body || ''),
                autoSize: { minRows: 3, maxRows: 10 },
                placeholder: '{"message": "ok"}',
                style: { marginTop: 4, fontFamily: 'monospace' },
                onChange: (e: any) => onChange({ body: e.target.value }),
            }),
        ),
    );
}

// ── 注册配置 ──
export const responseNodeRegistration: NodeRegistration = {
    type: 'response',
    label: 'HTTP 响应 (Response)',
    category: '基础节点',
    color: RESPONSE_COLOR,
    tagColor: 'orange',
    hasInputs: false,

    shape: {
        shapeName: 'flow-response',
        kind: 'react',
        component: ResponseNodeComponent,
        // 端口由组件动态管理 (useNodeVariables + in:body)
        reactPorts: {
            items: [],
        },
    },

    defaults: {
        ports: [],  // 端口由组件动态管理
        data: { status: 200, headers: {}, body: '' },
        size: { width: 320, height: 200 },
    },

    importConfig: {
        portMode: 'standard',
        buildAttrs: () => ({
            body: { stroke: RESPONSE_COLOR, strokeWidth: 2, fill: '#ffffff' },
        }),
    },

    buildLabel: (data) => `Response ${data.status || ''}`.trim(),

    PropertyEditor: ResponseEditor,
};
