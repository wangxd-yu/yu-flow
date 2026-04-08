// ============================================================================
// node-registry/nodes/collect-node/index.tsx  V3
// Collect (Gather 汇聚屏障) 节点注册 —— 端口坐标与 CollectNodeComponent V3 严格一致
// ============================================================================

import React from 'react';
import { Divider, InputNumber, Typography } from 'antd';
import type { DslPort } from '../../../types';
import type { NodeRegistration, PropertyEditorProps } from '../../types';
import { CollectNodeComponent, COLLECT_LAYOUT, COLLECT_COLOR } from './CollectNodeComponent';

const { Text } = Typography;
const PORT_POS = COLLECT_LAYOUT.portY;

// ── 属性面板编辑器 ────────────────────────────────────────────────────────────
function CollectEditor({ data, onChange }: PropertyEditorProps) {
    return (
        <div style={{ marginTop: 12 }}>
            <Divider orientation="left" style={{ fontSize: 12, margin: '8px 0' }}>
                Gather (汇聚屏障) 配置
            </Divider>

            <div style={{ marginBottom: 12, padding: '8px', background: '#f0f9ff', borderRadius: 6, border: '1px solid #bae0ff' }}>
                <Text style={{ fontSize: 11, color: '#0369a1' }}>
                    🔒 并发屏障：所有分支到达后，最后一条线程汇聚结果并接管流程。
                </Text>
            </div>

            <div style={{ marginBottom: 12 }}>
                <Text type="secondary" style={{ fontSize: 12 }}>超时 (毫秒)</Text>
                <InputNumber
                    size="small"
                    min={1000}
                    max={600000}
                    step={1000}
                    value={data.timeoutMs ?? 30000}
                    style={{ width: '100%', marginTop: 4 }}
                    onChange={(val) => onChange({ timeoutMs: val })}
                />
            </div>
        </div>
    );
}

// ── 注册配置 ──────────────────────────────────────────────────────────────────
export const collectNodeReactRegistration: NodeRegistration = {
    type: 'collect',
    label: 'Collect (Gather)',
    category: '循环节点',
    color: COLLECT_COLOR,
    tagColor: 'cyan',
    sortOrder: 84,
    hasInputs: true,

    shape: {
        shapeName: 'flow-collect',
        kind: 'react',
        component: CollectNodeComponent,
        reactPorts: {
            items: [
                // 入口端口 item —— 匹配后端 CollectStep.getInputPorts() 的 "item"
                { id: 'item', group: 'absolute-in-solid', args: { x: 0, y: PORT_POS.item, dx: 0 } },
                { id: 'list', group: 'absolute-out-solid', args: { x: COLLECT_LAYOUT.width, y: PORT_POS.list, dx: 0 } },
                { id: 'finish', group: 'absolute-out-hollow', args: { x: COLLECT_LAYOUT.width, y: PORT_POS.finish, dx: 0 } },
            ],
        },
    },

    defaults: {
        ports: [
            { id: 'item', group: 'absolute-in-solid' },
            { id: 'list', group: 'absolute-out-solid' },
            { id: 'finish', group: 'absolute-out-hollow' },
        ],
        data: {
            timeoutMs: 30000,
            inputs: {},
        },
        size: { width: COLLECT_LAYOUT.width, height: COLLECT_LAYOUT.height },
    },

    importConfig: {
        portMode: 'manual',
        buildPortItems: (ports: DslPort[]) => {
            const posMap: Record<string, { x: number; y: number; group: string; dx: number }> = {
                // 入口端口只认 item（匹配后端契约）
                item: { x: 0, y: PORT_POS.item, group: 'absolute-in-solid', dx: 0 },
                list: { x: COLLECT_LAYOUT.width, y: PORT_POS.list, group: 'absolute-out-solid', dx: 0 },
                finish: { x: COLLECT_LAYOUT.width, y: PORT_POS.finish, group: 'absolute-out-hollow', dx: 0 },
            };
            const seen = new Set<string>();
            return ports
                .filter((p) => { if (seen.has(p.id)) return false; seen.add(p.id); return true; })
                .map((p) => {
                    const m = posMap[p.id] || { x: 0, y: PORT_POS.item, group: 'absolute-in-solid', dx: 0 };
                    return { id: p.id, group: m.group, args: { x: m.x, y: m.y, dx: m.dx } };
                });
        },
        buildAttrs: () => ({ body: { stroke: COLLECT_COLOR, strokeWidth: 2, fill: '#f0f9ff' } }),
    },

    buildLabel: () => 'Collect',

    PropertyEditor: CollectEditor,
};
