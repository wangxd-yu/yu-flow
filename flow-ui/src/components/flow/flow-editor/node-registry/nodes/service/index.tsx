// ============================================================================
// node-registry/nodes/service/index.tsx
// Service（内部服务编排入口）节点 —— 自包含注册模块
// ============================================================================

import React from 'react';
import type { NodeRegistration, PropertyEditorProps } from '../../types';
import { ServiceNodeComponent, SERVICE_LAYOUT } from './ServiceNodeComponent';
import { PropertyField, PropertyHint, PropertySection } from '../../shared/PropertyPanel';

const ServicePropertyEditor = ({ data }: PropertyEditorProps) => {
    const inputs: string[] = Array.isArray(data.__contractInputs) ? data.__contractInputs : [];
    const outputs: string[] = Array.isArray(data.__contractOutputs) ? data.__contractOutputs : [];
    const outputDesc = (data.__outputDescription as string) || '';

    return (
        <PropertySection title="服务入口" tip="完整契约请在顶部「服务契约」Tab 编辑；此处与卡片同步显示摘要">
            <PropertyHint>
                被调用时注入 <code>$.service.input</code>；出参经唯一出口 <code>out</code> 传出（可为对象）。
            </PropertyHint>
            <PropertyField label="契约入参">
                {inputs.length ? (
                    <div style={boxStyle}>
                        {inputs.map((p) => (
                            <div key={p}><code>{p}</code></div>
                        ))}
                    </div>
                ) : (
                    <span style={{ fontSize: 12, color: '#8c8c8c' }}>未定义（请在「服务契约」中配置）</span>
                )}
            </PropertyField>
            <PropertyField label="返回结构">
                {outputs.length || outputDesc ? (
                    <div style={boxStyle}>
                        {outputDesc ? <div style={{ marginBottom: 4 }}>{outputDesc}</div> : null}
                        {outputs.map((p) => (
                            <div key={p}><code>{p}</code></div>
                        ))}
                    </div>
                ) : (
                    <span style={{ fontSize: 12, color: '#8c8c8c' }}>未定义（可选，出参仍走唯一 out 口）</span>
                )}
            </PropertyField>
            <div style={boxStyle}>
                <div><code>$.service.input</code> — 调用方入参对象</div>
                <div><code>$.service.serviceName</code> — 服务名称</div>
                <div><code>$.service.triggerTime</code> — 触发时间戳（ms）</div>
                <div><code>out</code> — 唯一出口，载荷可为任意对象</div>
            </div>
        </PropertySection>
    );
};

const boxStyle: React.CSSProperties = {
    marginTop: 4,
    padding: '8px 10px',
    background: '#f7f8fa',
    border: '1px solid #eef0f3',
    borderRadius: 6,
    fontSize: 11,
    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
    lineHeight: 1.85,
    color: '#595959',
};

export const serviceNodeRegistration: NodeRegistration = {
    type: 'service',
    label: '服务入口 (Service)',
    category: '基础节点',
    color: '#1677ff',
    tagColor: 'blue',
    description:
        '内部服务编排的入口（单例）。\n\n' +
        '· 在「服务契约」Tab 定义入参/返回说明\n' +
        '· 卡片同步展示入参摘要；唯一出口 out，载荷可为对象\n' +
        '· 运行时读取 $.service.input',
    hasInputs: false,
    singleton: true,

    shape: {
        shapeName: 'flow-service',
        kind: 'react',
        component: ServiceNodeComponent,
        reactPorts: {
            items: [
                {
                    id: 'out',
                    group: 'absolute-out-solid',
                    args: {
                        x: SERVICE_LAYOUT.width,
                        y: SERVICE_LAYOUT.rowCenterY(1),
                        dx: 0,
                    },
                },
            ],
        },
    },

    defaults: {
        ports: [{ id: 'out' }],
        data: {},
        size: { width: SERVICE_LAYOUT.width, height: SERVICE_LAYOUT.totalHeight },
    },

    importConfig: {
        portMode: 'manual',
        buildPortItems: (ports) => {
            const items: any[] = [];
            const seen = new Set<string>();
            for (const p of ports) {
                if (!p.id || seen.has(p.id)) continue;
                seen.add(p.id);
                items.push({
                    id: p.id,
                    group: 'absolute-out-solid',
                    args: {
                        x: SERVICE_LAYOUT.width,
                        y: SERVICE_LAYOUT.rowCenterY(1),
                        dx: 0,
                    },
                });
            }
            if (!seen.has('out')) {
                items.push({
                    id: 'out',
                    group: 'absolute-out-solid',
                    args: {
                        x: SERVICE_LAYOUT.width,
                        y: SERVICE_LAYOUT.rowCenterY(1),
                        dx: 0,
                    },
                });
            }
            return items;
        },
    },

    buildLabel: () => 'Service',
    PropertyEditor: ServicePropertyEditor,
};

export default serviceNodeRegistration;
