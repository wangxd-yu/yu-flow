// ============================================================================
// node-registry/nodes/for-node/index.tsx  V3
// For (Scatter 分发) 节点注册 —— 端口坐标与 ForNodeComponent V3 严格一致
// ============================================================================

import React from 'react';
import { Divider, InputNumber, Tag, Typography } from 'antd';
import type { DslPort } from '../../../types';
import type { NodeRegistration, PropertyEditorProps } from '../../types';
import { ForNodeComponent, FOR_LAYOUT, FOR_COLOR } from './ForNodeComponent';

const { Text } = Typography;
const PORT_POS = FOR_LAYOUT.portY;

// ── 属性面板编辑器 ────────────────────────────────────────────────────────────
function ForEditor({ data, onChange }: PropertyEditorProps) {
    return (
        <div style={{ marginTop: 12 }}>
            <Divider orientation="left" style={{ fontSize: 12, margin: '8px 0' }}>
                Scatter (分发) 配置
            </Divider>

            {/* 数组来源说明：通过连线指向 in 端口来传入，无需手动填写 */}
            <div style={{ marginBottom: 12, padding: '8px 10px', background: '#f9fafb', borderRadius: 6, border: '1px solid #e5e7eb' }}>
                <Text style={{ fontSize: 11, color: '#6b7280' }}>
                    💡 数组来源由上游连线自动传入（连接到 <code>in</code> 端口），无需手动配置。
                </Text>
            </div>

            <div style={{ marginBottom: 14 }}>
                <Text type="secondary" style={{ fontSize: 12 }}>超时 (毫秒)</Text>
                <InputNumber
                    size="small"
                    min={1000}
                    max={300000}
                    step={1000}
                    value={data.timeoutMs ?? 30000}
                    style={{ width: '100%', marginTop: 4 }}
                    onChange={(val) => onChange({ timeoutMs: val })}
                />
            </div>

            <div style={{ background: '#faf5ff', borderRadius: 6, padding: '8px 10px', border: '1px solid #ede9fe' }}>
                <Text style={{ fontSize: 11, color: '#6d28d9', fontWeight: 500 }}>配对 Collect 节点（画布自动识别）</Text>
                <div style={{ marginTop: 4 }}>
                    {data.collectStepId
                        ? <Tag color="purple" style={{ fontSize: 10 }}>{String(data.collectStepId)}</Tag>
                        : <Text type="secondary" style={{ fontSize: 11 }}>保存时自动检测（发后即忘模式则为空）</Text>
                    }
                </div>
            </div>
        </div>
    );
}

// ── 注册配置 ──────────────────────────────────────────────────────────────────
export const forNodeRegistration: NodeRegistration = {
    type: 'for',
    label: 'For (Loop)',
    category: '循环节点',
    color: FOR_COLOR,
    tagColor: 'purple',
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
