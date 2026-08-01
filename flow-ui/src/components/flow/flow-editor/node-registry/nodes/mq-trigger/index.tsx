// ============================================================================
// node-registry/nodes/mq-trigger/index.tsx
// MqTrigger（消息队列触发入口）节点 —— 自包含注册模块
// ============================================================================

import React from 'react';
import type { NodeRegistration, PropertyEditorProps } from '../../types';
import { MqTriggerNodeComponent, MQ_TRIGGER_LAYOUT } from './MqTriggerNodeComponent';
import { PropertyField, PropertyHint, PropertySection } from '../../shared/PropertyPanel';

const MqTriggerPropertyEditor = ({ data }: PropertyEditorProps) => {
    return (
        <PropertySection title="MQ 触发入口" tip="连接 / Topic 在 MQ 任务管理页配置，此处只读展示">
            <PropertyHint>
                每次消息触发时自动注入上下文变量，供下游通过路径引用。
            </PropertyHint>
            <PropertyField label="任务名称">
                <code style={codeStyle}>{data.taskName || '（运行时注入）'}</code>
            </PropertyField>
            <PropertyField label="Topic">
                <code style={codeStyle}>{data.topicHint || '（MQ 任务配置）'}</code>
            </PropertyField>
            <div
                style={{
                    marginTop: 4,
                    padding: '8px 10px',
                    background: '#f7f8fa',
                    border: '1px solid #eef0f3',
                    borderRadius: 6,
                    fontSize: 11,
                    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
                    lineHeight: 1.85,
                    color: '#595959',
                }}
            >
                <div><code>$.mq.message</code> — 消息体（JSON 自动解析）</div>
                <div><code>$.mq.headers</code> — 消息头 Map</div>
                <div><code>$.mq.topic</code> — 来源 topic / 队列名</div>
                <div><code>$.mq.messageId</code> — 消息ID（幂等去重键）</div>
                <div><code>$.mq.taskName</code> — MQ 任务名称</div>
            </div>
        </PropertySection>
    );
};

const codeStyle: React.CSSProperties = {
    fontSize: 12,
    color: '#595959',
    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
};

export const mqTriggerNodeRegistration: NodeRegistration = {
    type: 'mqTrigger',
    label: '消息触发 (MQ Trigger)',
    category: '基础节点',
    color: '#0958d9',
    tagColor: 'blue',
    description:
        'MQ 任务流程的入口（单例）。\n\n' +
        '· 连接 / Topic / 消费组在 MQ 任务管理中配置，不在此节点编辑\n' +
        '· 收到消息后从 out 口进入后续节点\n' +
        '· 可读取消息上下文变量（$.mq.message / $.mq.headers 等）',
    hasInputs: false,
    singleton: true,

    shape: {
        shapeName: 'flow-mq-trigger',
        kind: 'react',
        component: MqTriggerNodeComponent,
        reactPorts: {
            items: [
                {
                    id: 'out',
                    group: 'absolute-out-solid',
                    args: {
                        x: MQ_TRIGGER_LAYOUT.width,
                        y: MQ_TRIGGER_LAYOUT.rowCenterY(1),
                        dx: 0,
                    },
                },
            ],
        },
    },

    defaults: {
        ports: [{ id: 'out' }],
        data: {},
        size: { width: MQ_TRIGGER_LAYOUT.width, height: MQ_TRIGGER_LAYOUT.totalHeight },
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
                        x: MQ_TRIGGER_LAYOUT.width,
                        y: MQ_TRIGGER_LAYOUT.rowCenterY(1),
                        dx: 0,
                    },
                });
            }
            if (!seen.has('out')) {
                items.push({
                    id: 'out',
                    group: 'absolute-out-solid',
                    args: {
                        x: MQ_TRIGGER_LAYOUT.width,
                        y: MQ_TRIGGER_LAYOUT.rowCenterY(1),
                        dx: 0,
                    },
                });
            }
            return items;
        },
    },

    buildLabel: () => 'MQ Trigger',
    PropertyEditor: MqTriggerPropertyEditor,
};

export default mqTriggerNodeRegistration;
