// ============================================================================
// template/index.tsx — Template 节点注册（React Shape，对齐 Evaluate）
// ============================================================================

import React from 'react';
import type { DslPort } from '../../../types';
import type { NodeRegistration, PropertyEditorProps } from '../../types';
import { TemplateNodeComponent, TEMPLATE_LAYOUT, TEMPLATE_COLOR } from './TemplateNodeComponent';
import { PropertyCodeField, PropertyHint, PropertySection } from '../../shared/PropertyPanel';
import { PAYLOAD_PORT_ID, PAYLOAD_PORT_Y } from '../../shared/usePayloadEntryPort';
import { NODE_FOOTER_HEIGHT, NODE_FOOTER_PORT_OFFSET_Y } from '../../shared/useNodeSelection';

function TemplateEditor({ data, onChange }: PropertyEditorProps) {
    return (
        <PropertySection title="模板配置" tip="长文本 / 消息文案；拼对象请用 Record">
            <PropertyHint>
                画布上方变量行映射 <code>{'{{key}}'}</code>；左上角为总入口 <code>in:payload</code>。
                结构化对象请用 Record。
            </PropertyHint>
            <PropertyCodeField
                label="模板正文"
                language="text"
                value={data.template || ''}
                onChange={(val) => onChange({ template: val })}
                height="lg"
                placeholder="Hello {{name}}, score={{score}}"
                lineNumbers={false}
            />
        </PropertySection>
    );
}

const buildTemplatePortItems = (ports: DslPort[]) => {
    const w = TEMPLATE_LAYOUT.width;
    const outY = TEMPLATE_LAYOUT.totalHeight - NODE_FOOTER_HEIGHT + NODE_FOOTER_PORT_OFFSET_Y;
    const seen = new Set<string>();
    const items: any[] = [];

    for (const p of ports) {
        if (seen.has(p.id) || p.id === 'in' || p.id.startsWith('in:var:')) continue;
        seen.add(p.id);
        if (p.id === PAYLOAD_PORT_ID) {
            items.push({
                id: PAYLOAD_PORT_ID,
                group: 'absolute-in-solid',
                args: { x: 0, y: PAYLOAD_PORT_Y, dx: 0 },
            });
        } else if (p.id === 'out') {
            items.push({
                id: 'out',
                group: 'absolute-out-solid',
                args: { x: w, y: outY, dx: 0 },
            });
        }
    }
    if (!seen.has(PAYLOAD_PORT_ID)) {
        items.unshift({
            id: PAYLOAD_PORT_ID,
            group: 'absolute-in-solid',
            args: { x: 0, y: PAYLOAD_PORT_Y, dx: 0 },
        });
    }
    if (!seen.has('out')) {
        items.push({
            id: 'out',
            group: 'absolute-out-solid',
            args: { x: w, y: outY, dx: 0 },
        });
    }
    return items;
};

export const templateNodeRegistration: NodeRegistration = {
    type: 'template',
    label: '模板 (Template)',
    category: '数据节点',
    color: TEMPLATE_COLOR,
    tagColor: 'purple',
    description:
        '用 {{key}} 占位符生成长文本（对齐 Postman Template）。\n\n' +
        '· 左上角 in:payload 总入口；变量行可逐项连线\n' +
        '· 模板中写 {{变量名}}，与上方变量行对应\n' +
        '· 结构化对象请用 Record，不要用本节点拼 JSON',
    sortOrder: 85,
    hasInputs: true,

    shape: {
        shapeName: 'flow-template',
        kind: 'react',
        component: TemplateNodeComponent,
        reactPorts: {
            items: [
                {
                    id: PAYLOAD_PORT_ID,
                    group: 'absolute-in-solid',
                    args: { x: 0, y: PAYLOAD_PORT_Y, dx: 0 },
                },
                {
                    id: 'out',
                    group: 'absolute-out-solid',
                    args: { x: TEMPLATE_LAYOUT.width, y: TEMPLATE_LAYOUT.outPortY, dx: 0 },
                },
            ],
        },
    },

    defaults: {
        ports: [
            { id: PAYLOAD_PORT_ID, group: 'absolute-in-solid' },
            { id: 'out' },
        ],
        data: { template: '', inputs: {} },
        size: { width: TEMPLATE_LAYOUT.width, height: TEMPLATE_LAYOUT.totalHeight },
    },

    importConfig: {
        portMode: 'manual',
        buildPortItems: buildTemplatePortItems,
    },

    buildLabel: (data) =>
        data.template ? String(data.template).replace(/\s+/g, ' ').slice(0, 28) : 'Template',

    PropertyEditor: TemplateEditor,
};
