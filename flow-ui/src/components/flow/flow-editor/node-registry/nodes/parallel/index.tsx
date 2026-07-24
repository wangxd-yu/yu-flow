// ============================================================================
// parallel/index.tsx — 并行网关注册（正式收敛：图扇出优先）
// ============================================================================

import React from 'react';
import { Select } from 'antd';
import type { DslPort } from '../../../types';
import type { NodeRegistration, PropertyEditorProps } from '../../types';
import { ParallelNodeComponent, PARALLEL_LAYOUT, PARALLEL_COLOR } from './ParallelNodeComponent';
import { PropertyField, PropertyHint, PropertySection } from '../../shared/PropertyPanel';

function ParallelEditor({ data, onChange }: PropertyEditorProps) {
    return (
        <PropertySection title="并行网关">
            <PropertyHint>
                推荐用法：从本节点 <code>out</code> 拉多条边到不同下游，引擎原生并行执行并在汇聚点 join。
            </PropertyHint>
            <PropertyField label="错误模式" tip="多分支并行时的失败策略">
                <Select
                    size="small"
                    style={{ width: '100%' }}
                    value={data.errorMode || 'FAST_FAIL'}
                    options={[
                        { value: 'FAST_FAIL', label: 'FAST_FAIL（任一失败即中断）' },
                        { value: 'CONTINUE', label: 'CONTINUE（忽略失败）' },
                    ]}
                    getPopupContainer={() => document.body}
                    onChange={(val) => onChange({ errorMode: val })}
                />
            </PropertyField>
        </PropertySection>
    );
}

export const parallelNodeRegistration: NodeRegistration = {
    type: 'parallel',
    label: '并行 (Parallel)',
    category: '循环节点',
    color: PARALLEL_COLOR,
    tagColor: 'purple',
    description:
        '并行网关：从 out 拉多条线即并行执行。\n\n' +
        '· 多分支汇入同一节点时自动 join\n' +
        '· 数组扇出+汇聚请用 For + Collect',
    sortOrder: 70,
    hasInputs: false,

    shape: {
        shapeName: 'flow-parallel',
        kind: 'react',
        component: ParallelNodeComponent,
        reactPorts: {
            items: [
                { id: 'in', group: 'absolute-in-solid', args: { x: 0, y: PARALLEL_LAYOUT.inPortY, dx: 0 } },
                { id: 'out', group: 'absolute-out-solid', args: { x: PARALLEL_LAYOUT.width, y: PARALLEL_LAYOUT.outPortY, dx: 0 } },
            ],
        },
    },

    defaults: {
        ports: [
            { id: 'in', group: 'absolute-in-solid' },
            { id: 'out', group: 'absolute-out-solid' },
        ],
        data: { errorMode: 'FAST_FAIL' },
        size: { width: PARALLEL_LAYOUT.width, height: PARALLEL_LAYOUT.height },
    },

    importConfig: {
        portMode: 'manual',
        buildPortItems: (ports: DslPort[]) => {
            const w = PARALLEL_LAYOUT.width;
            const map: Record<string, { x: number; y: number; group: string }> = {
                in: { x: 0, y: PARALLEL_LAYOUT.inPortY, group: 'absolute-in-solid' },
                out: { x: w, y: PARALLEL_LAYOUT.outPortY, group: 'absolute-out-solid' },
                // 兼容旧 join 口 → 映射为 out 坐标（导入时若仅有 join 仍可见）
                join: { x: w, y: PARALLEL_LAYOUT.outPortY, group: 'absolute-out-solid' },
            };
            return ports
                .filter((p) => map[p.id])
                .map((p) => ({
                    id: p.id === 'join' ? 'out' : p.id,
                    group: map[p.id].group,
                    args: { x: map[p.id].x, y: map[p.id].y, dx: 0 },
                }));
        },
    },

    buildLabel: () => 'Parallel',

    PropertyEditor: ParallelEditor,
};
