// ============================================================================
// for-each/index.tsx — 串行 ForEach 节点注册
// ============================================================================

import React from 'react';
import type { DslPort } from '../../../types';
import type { NodeRegistration } from '../../types';
import { ForEachNodeComponent, FOREACH_LAYOUT, FOREACH_COLOR } from './ForEachNodeComponent';
import { PropertyHint, PropertySection } from '../../shared/PropertyPanel';

function ForEachEditor() {
    return (
        <PropertySection title="串行循环">
            <PropertyHint>
                将列表连到 <code>in</code>（或配置 inputs.list）。对每一项沿 <code>item</code> 同步执行子流，
                全部完成后走 <code>done</code>。需要并发扇出请用 For + Collect。
            </PropertyHint>
        </PropertySection>
    );
}

export const forEachNodeRegistration: NodeRegistration = {
    type: 'forEach',
    label: 'ForEach (串行)',
    category: '循环节点',
    color: FOREACH_COLOR,
    tagColor: 'cyan',
    description:
        '串行循环：按顺序处理列表每一项。\n\n' +
        '· item：每轮迭代的下游入口\n' +
        '· done：全部完成后继续\n' +
        '· 适合限流、依赖上一轮结果；并发请用 For/Collect',
    sortOrder: 90,
    hasInputs: true,

    shape: {
        shapeName: 'flow-forEach',
        kind: 'react',
        component: ForEachNodeComponent,
        reactPorts: {
            items: [
                { id: 'in', group: 'absolute-in-solid', args: { x: 0, y: FOREACH_LAYOUT.portY.in, dx: 0 } },
                { id: 'item', group: 'absolute-out-solid', args: { x: FOREACH_LAYOUT.width, y: FOREACH_LAYOUT.portY.item, dx: 0 } },
                { id: 'done', group: 'absolute-out-hollow', args: { x: FOREACH_LAYOUT.width, y: FOREACH_LAYOUT.portY.done, dx: 0 } },
            ],
        },
    },

    defaults: {
        ports: [
            { id: 'in', group: 'absolute-in-solid' },
            { id: 'item', group: 'absolute-out-solid' },
            { id: 'done', group: 'absolute-out-hollow' },
        ],
        data: { inputs: {} },
        size: { width: FOREACH_LAYOUT.width, height: FOREACH_LAYOUT.height },
    },

    importConfig: {
        portMode: 'manual',
        buildPortItems: (ports: DslPort[]) => {
            const w = FOREACH_LAYOUT.width;
            const map: Record<string, { x: number; y: number; group: string }> = {
                in: { x: 0, y: FOREACH_LAYOUT.portY.in, group: 'absolute-in-solid' },
                item: { x: w, y: FOREACH_LAYOUT.portY.item, group: 'absolute-out-solid' },
                done: { x: w, y: FOREACH_LAYOUT.portY.done, group: 'absolute-out-hollow' },
            };
            return ports
                .filter((p) => map[p.id])
                .map((p) => ({
                    id: p.id,
                    group: map[p.id].group,
                    args: { x: map[p.id].x, y: map[p.id].y, dx: 0 },
                }));
        },
    },

    buildLabel: () => 'ForEach',

    PropertyEditor: ForEachEditor,
};
