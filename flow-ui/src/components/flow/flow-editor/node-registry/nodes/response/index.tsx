// ============================================================================
// node-registry/nodes/response/index.tsx
// Response (HTTP 响应 / 终止) 节点 —— 自包含注册模块
// ============================================================================

import React from 'react';
import { InputNumber } from 'antd';
import type { NodeRegistration, PropertyEditorProps } from '../../types';
import { ResponseNodeComponent } from './ResponseNodeComponent';
import {
    PropertyCodeField,
    PropertyField,
    PropertyHint,
    PropertySection,
} from '../../shared/PropertyPanel';

const RESPONSE_COLOR = '#FF6B35';

function ResponseEditor({ data, onChange }: PropertyEditorProps) {
    const bodyStr =
        typeof data.body === 'object' ? JSON.stringify(data.body, null, 2) : (data.body || '');

    return (
        <PropertySection title="HTTP Response" tip="组装并返回 HTTP 响应">
            <PropertyField label="Status">
                <InputNumber
                    size="small"
                    value={data.status || 200}
                    min={100}
                    max={599}
                    style={{ width: '100%' }}
                    onChange={(val) => onChange({ status: val })}
                />
            </PropertyField>
            <PropertyHint>Headers 请在画布节点变量行管理与连线。</PropertyHint>
            <PropertyCodeField
                label="Body"
                tip="JSON / 文本；支持 ${var} 替换"
                language="json"
                value={bodyStr}
                onChange={(val) => onChange({ body: val })}
                height="md"
                placeholder='{"message": "ok"} 或 ${result}'
            />
        </PropertySection>
    );
}

export const responseNodeRegistration: NodeRegistration = {
    type: 'response',
    label: 'HTTP 响应 (Response)',
    category: '基础节点',
    color: RESPONSE_COLOR,
    tagColor: 'orange',
    description:
        '组装并返回 HTTP 响应。\n\n' +
        '· 配置状态码、Headers、Body\n' +
        '· Body 支持 JSON 模板与变量替换\n' +
        '· 通常作为流程终点之一',
    hasInputs: false,

    shape: {
        shapeName: 'flow-response',
        kind: 'react',
        component: ResponseNodeComponent,
        reactPorts: {
            items: [],
        },
    },

    defaults: {
        ports: [],
        data: { status: 200, headers: {}, body: '' },
        size: { width: 320, height: 200 },
    },

    importConfig: {
        portMode: 'standard',
        buildAttrs: () => ({
            body: { stroke: RESPONSE_COLOR, strokeWidth: 2, fill: '#ffffff' },
        }),
    },

    buildLabel: () => 'Response',

    PropertyEditor: ResponseEditor,
};
