// ============================================================================
// node-registry/nodes/for-node/index.tsx  V3
// For (Scatter 分发) 节点注册 —— 端口坐标与 ForNodeComponent V3 严格一致
// ============================================================================

import React from 'react';
import { InputNumber, Tag, Typography } from 'antd';
import type { DslPort } from '../../../types';
import type { NodeRegistration, PropertyEditorProps } from '../../types';
import { ForNodeComponent, FOR_LAYOUT, FOR_COLOR } from './ForNodeComponent';
import { PropertyField, PropertyHint, PropertySection } from '../../shared/PropertyPanel';

const { Text } = Typography;
const PORT_POS = FOR_LAYOUT.portY;

// ── 属性面板编辑器 ────────────────────────────────────────────────────────────
function ForEditor({ data, onChange }: PropertyEditorProps) {
    return (
        <PropertySection title="Scatter 配置" tip="数组由上游连线传入 in 端口">
            <PropertyHint>
                数组来源由上游连线自动传入（连接到 <code>in</code> 端口），无需手动配置。
            </PropertyHint>
            <PropertyField label="超时" tip="等待分支完成的最长时间" extra="ms">
                <InputNumber
                    size="small"
                    min={1000}
                    max={300000}
                    step={1000}
                    value={data.timeoutMs ?? 30000}
                    style={{ width: '100%' }}
                    onChange={(val) => onChange({ timeoutMs: val })}
                />
            </PropertyField>
            <PropertyField label="Collect">
                {data.collectStepId
                    ? <Tag color="purple" style={{ fontSize: 11, margin: 0 }}>{String(data.collectStepId)}</Tag>
                    : <Text type="secondary" style={{ fontSize: 12 }}>保存时自动检测</Text>
                }
            </PropertyField>
        </PropertySection>
    );
}

// ── 注册配置 ──────────────────────────────────────────────────────────────────
export const forNodeRegistration: NodeRegistration = {
    type: 'for',
    label: 'For (Loop)',
    category: '循环节点',
    color: FOR_COLOR,
    tagColor: 'purple',
    description:
        'Scatter：将数组并发拆成多项处理。\n\n' +
        '· 输入列表后，对每个元素发出 item\n' +
        '· 需与 Collect 节点配对汇聚\n' +
        '· 适合扇出并发，不是串行 forEach',
    sortOrder: 85,
    hasInputs: true,

    shape: {
        shapeName: 'flow-for',
        kind: 'react',
        component: ForNodeComponent,
        reactPorts: {
            items: [
                // args.dx 将端口向外偏置 4px，使其边缘刚好贴合节点矩形
                { id: 'in', group: 'absolute-in-solid', args: { x: 0, y: PORT_POS.list, dx: 0 } },
                { id: 'start', group: 'absolute-in-hollow', args: { x: 0, y: PORT_POS.start, dx: 0 } },
                { id: 'item', group: 'absolute-out-solid', args: { x: FOR_LAYOUT.width, y: PORT_POS.item, dx: 0 } },
            ],
        },
    },

    defaults: {
        ports: [
            { id: 'in', group: 'absolute-in-solid' },
            { id: 'start', group: 'absolute-in-hollow' },
            { id: 'item', group: 'absolute-out-solid' },
        ],
        data: {
            collectStepId: '',
            timeoutMs: 30000,
            inputs: {},
        },
        size: { width: FOR_LAYOUT.width, height: FOR_LAYOUT.height },
    },

    importConfig: {
        portMode: 'manual',
        buildPortItems: (ports: DslPort[]) => {
            const posMap: Record<string, { x: number; y: number; group: string; dx: number }> = {
                in: { x: 0, y: PORT_POS.list, group: 'absolute-in-solid', dx: 0 },
                start: { x: 0, y: PORT_POS.start, group: 'absolute-in-hollow', dx: 0 },
                item: { x: FOR_LAYOUT.width, y: PORT_POS.item, group: 'absolute-out-solid', dx: 0 },
            };
            const seen = new Set<string>();
            return ports
                .filter((p) => { if (p.id === 'done' || seen.has(p.id)) return false; seen.add(p.id); return true; })
                .map((p) => {
                    const m = posMap[p.id] || { x: 0, y: PORT_POS.list, group: 'absolute-in-solid', dx: 0 };
                    return { id: p.id, group: m.group, args: { x: m.x, y: m.y, dx: m.dx } };
                });
        },
        buildAttrs: () => ({ body: { stroke: FOR_COLOR, strokeWidth: 2, fill: '#faf5ff' } }),
    },

    buildLabel: (data) =>
        data.collectStepId ? `For→${String(data.collectStepId).substring(0, 10)}` : 'For',

    PropertyEditor: ForEditor,
};
