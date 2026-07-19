// ============================================================================
// request/index.tsx — Request 入口节点注册
// ============================================================================

import React from 'react';
import { Select } from 'antd';
import type { DslPort } from '../../../types';
import type { NodeRegistration, PropertyEditorProps } from '../../types';
import { RequestNodeComponent, REQUEST_LAYOUT } from './RequestNodeComponent';
import ValidationRulesEditor from './ValidationRulesEditor';
import { PropertyField, PropertySection } from '../../shared/PropertyPanel';

const METHOD_OPTIONS = [
    { value: 'GET', label: 'GET' },
    { value: 'POST', label: 'POST' },
    { value: 'PUT', label: 'PUT' },
    { value: 'DELETE', label: 'DELETE' },
    { value: 'PATCH', label: 'PATCH' },
];

function RequestEditor({ data, onChange }: PropertyEditorProps) {
    return (
        <>
            <PropertySection title="入口配置">
                <PropertyField label="请求方法">
                    <Select
                        size="small"
                        value={data.method || 'GET'}
                        options={METHOD_OPTIONS}
                        style={{ width: '100%' }}
                        getPopupContainer={() => document.body}
                        onChange={(val) => onChange({ method: val })}
                    />
                </PropertyField>
            </PropertySection>
            <PropertySection title="参数校验" tip="对齐后端 ValidationRule / ParamValidator">
                <ValidationRulesEditor
                    value={data.validations || {}}
                    onChange={(validations) => onChange({ validations })}
                />
            </PropertySection>
        </>
    );
}

export const requestNodeRegistration: NodeRegistration = {
    type: 'request',
    label: '请求入口 (Request)',
    category: '基础节点',
    color: '#52c41a',
    tagColor: 'green',
    description:
        'HTTP API 流程的唯一入口（单例）。\n\n' +
        '· 右侧输出 headers / params / body\n' +
        '· 下游用 $.request.params.xxx、$.request.body.xxx 读取\n' +
        '· 可在属性面板配置参数校验规则',
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
            ],
        },
    },

    defaults: {
        ports: [{ id: 'headers' }, { id: 'params' }],
        data: { method: 'GET', validations: {} },
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

    PropertyEditor: RequestEditor,
};
