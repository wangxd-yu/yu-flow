// ============================================================================
// node-registry/nodes/schedule/index.tsx
// Schedule（定时调度入口）节点 —— 自包含注册模块
//
// 作为任务管理流程的唯一入口节点，类似 request 节点但无任何入参端口，
// 仅有一个右侧 out 输出端口。
// ============================================================================

import React from 'react';
import { Divider, Typography } from 'antd';
import type { NodeRegistration, PropertyEditorProps } from '../../types';
import { ScheduleNodeComponent, SCHEDULE_LAYOUT } from './ScheduleNodeComponent';

const { Paragraph } = Typography;

// ── 属性面板编辑器 ──
const SchedulePropertyEditor = ({ data, onChange }: PropertyEditorProps) => {
    return (
        <div style={{ marginTop: 12 }}>
            <Divider orientation="left" style={{ fontSize: 12, margin: '8px 0' }}>
                调度入口节点
            </Divider>
            <Paragraph type="secondary" style={{ fontSize: 11 }}>
                Schedule 节点为任务流程入口，每次调度触发时自动将以下变量注入上下文：
            </Paragraph>
            <div style={{
                background: 'linear-gradient(180deg, #f9f0ff 0%, #ffffff 100%)',
                border: '1px solid #efdbff',
                borderRadius: 8,
                padding: '10px 12px',
                fontSize: 11,
                fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace',
                lineHeight: 1.9,
                color: '#531dab',
            }}>
                <div><code>$.schedule.taskName</code> — 任务名称</div>
                <div><code>$.schedule.cron</code> — Cron 表达式</div>
                <div><code>$.schedule.triggerTime</code> — 触发时间戳（ms）</div>
            </div>
            <Paragraph type="secondary" style={{ fontSize: 11, marginTop: 8 }}>
                Cron 表达式在「任务管理」页面中配置，此处仅为只读展示。
            </Paragraph>
        </div>
    );
};

// ── 注册配置 ──
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
                // 唯一输出端口：对齐第二行（triggerTime）
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
        buildPortItems: () => [
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
        buildAttrs: () => ({
            body: { stroke: '#d3adf7', strokeWidth: 1, fill: '#ffffff' },
        }),
    },

    buildLabel: () => 'Schedule',

    PropertyEditor: SchedulePropertyEditor,
};
