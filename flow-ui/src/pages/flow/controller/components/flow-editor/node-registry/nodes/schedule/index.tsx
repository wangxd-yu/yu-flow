// ============================================================================
// node-registry/nodes/schedule/index.tsx
// Schedule（定时调度入口）节点 —— 自包含注册模块
// ============================================================================

import React from 'react';
import type { NodeRegistration, PropertyEditorProps } from '../../types';
import { ScheduleNodeComponent, SCHEDULE_LAYOUT } from './ScheduleNodeComponent';
import { PropertyField, PropertyHint, PropertySection } from '../../shared/PropertyPanel';

const SchedulePropertyEditor = ({ data }: PropertyEditorProps) => {
    return (
        <PropertySection title="调度入口" tip="Cron 在任务管理页配置，此处只读展示">
            <PropertyHint>
                每次调度触发时自动注入上下文变量，供下游通过路径引用。
            </PropertyHint>
            <PropertyField label="任务名称">
                <code style={codeStyle}>{data.taskName || '（运行时注入）'}</code>
            </PropertyField>
            <PropertyField label="Cron">
                <code style={codeStyle}>{data.cron || '（任务管理配置）'}</code>
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
                <div><code>$.schedule.taskName</code> — 任务名称</div>
                <div><code>$.schedule.cron</code> — Cron 表达式</div>
                <div><code>$.schedule.triggerTime</code> — 触发时间戳（ms）</div>
            </div>
        </PropertySection>
    );
};

const codeStyle: React.CSSProperties = {
    fontSize: 12,
    color: '#595959',
    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
};

export const scheduleNodeRegistration: NodeRegistration = {
    type: 'schedule',
    label: '调度入口 (Schedule)',
    category: '基础节点',
    color: '#722ed1',
    tagColor: 'purple',
    hasInputs: false,
    singleton: true,

    shape: {
        shapeName: 'flow-schedule',
        kind: 'react',
        component: ScheduleNodeComponent,
        reactPorts: {
            items: [
                {
                    id: 'out',
                    group: 'absolute-out-solid',
                    args: {
                        x: SCHEDULE_LAYOUT.width,
                        y: SCHEDULE_LAYOUT.rowCenterY(1),
                        dx: 0,
                    },
                },
            ],
        },
    },

    defaults: {
        ports: [{ id: 'out' }],
        data: {},
        size: { width: SCHEDULE_LAYOUT.width, height: SCHEDULE_LAYOUT.totalHeight },
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
                        x: SCHEDULE_LAYOUT.width,
                        y: SCHEDULE_LAYOUT.rowCenterY(1),
                        dx: 0,
                    },
                });
            }
            if (!seen.has('out')) {
                items.push({
                    id: 'out',
                    group: 'absolute-out-solid',
                    args: {
                        x: SCHEDULE_LAYOUT.width,
                        y: SCHEDULE_LAYOUT.rowCenterY(1),
                        dx: 0,
                    },
                });
            }
            return items;
        },
    },

    buildLabel: () => 'Schedule',
    PropertyEditor: SchedulePropertyEditor,
};

export default scheduleNodeRegistration;
